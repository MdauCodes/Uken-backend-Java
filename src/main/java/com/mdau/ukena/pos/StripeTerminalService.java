package com.mdau.ukena.pos;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.pos.dto.PosPaymentIntentResponse;
import com.mdau.ukena.pos.dto.PosReaderStatus;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.terminal.Reader;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.RefundListParams;
import com.stripe.param.terminal.ReaderCancelActionParams;
import com.stripe.param.terminal.ReaderProcessPaymentIntentParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stripe Terminal — server-driven integration for a smart reader (WisePOS E /
 * Stripe Reader S700). No SDK, no app, no Bluetooth pairing: this service creates
 * a card_present PaymentIntent and hands it to a specific, pre-registered reader
 * over the internet via the Stripe API; the reader itself prompts the customer to
 * tap/insert. Completion is reported back via webhook (see PaymentService), not a
 * client-side confirm call, so nothing here ever needs to be "trusted" client-side.
 *
 * Deliberately separate from PaymentGateway — this is reader hardware integration,
 * not a swappable online-checkout strategy, and it's needed regardless of which
 * gateway (Stripe/Paystack) is configured for online payments. Sets Stripe.apiKey
 * itself rather than relying on StripeGateway having been constructed (that only
 * happens when ukena.payment.provider=stripe).
 */
@Slf4j
@Service
public class StripeTerminalService {

    @Value("${ukena.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${ukena.stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    /** The one physical reader for this pilot — set once the client registers it
     *  in the Stripe Dashboard (Terminal -> Readers) and hands over its tmr_... id. */
    @Value("${ukena.stripe.terminal.reader-id:}")
    private String readerId;

    @Value("${ukena.stripe.currency:gbp}")
    private String currency;

    @PostConstruct
    void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    public PosPaymentIntentResponse createPaymentIntent(String displayId, int amountPence, String receiptEmail) {
        try {
            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount((long) amountPence)
                    .setCurrency(currency)
                    .addPaymentMethodType("card_present")
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                    .putMetadata("display_id", displayId);
            // Card-network rule: a card-present sale must offer the customer a physical
            // or email receipt. There's no printer on this device, so email is it —
            // Stripe sends its own compliant receipt (with the EMV fields a card
            // network requires) on capture; skip when there's no real address to send.
            if (receiptEmail != null && !receiptEmail.isBlank()) {
                builder.setReceiptEmail(receiptEmail);
            }
            PaymentIntent intent = PaymentIntent.create(builder.build());
            return new PosPaymentIntentResponse(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            log.error("Stripe Terminal payment intent error for order {}", displayId, e);
            throw ApiException.internalError("Could not start the card payment: " + e.getMessage());
        }
    }

    /** Tells the configured reader to prompt the customer for this payment. Fire-and-forget
     *  from here — actual success/failure arrives later as a payment_intent.succeeded (or
     *  payment_intent.payment_failed / terminal.reader.action_failed) webhook. Customer
     *  cancellation is enabled so a customer who walks away can back out from the reader
     *  itself rather than leaving the operator stuck waiting on a prompt nobody will answer. */
    public void dispatchToReader(String paymentIntentId) {
        requireReaderConfigured();
        try {
            Reader reader = Reader.retrieve(readerId);
            reader.processPaymentIntent(ReaderProcessPaymentIntentParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setProcessConfig(ReaderProcessPaymentIntentParams.ProcessConfig.builder()
                            .setEnableCustomerCancellation(true)
                            .build())
                    .build());
        } catch (StripeException e) {
            log.error("Stripe Terminal dispatch-to-reader error for intent {}", paymentIntentId, e);
            throw ApiException.badRequest(mapReaderError(e));
        }
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) {
        try {
            return PaymentIntent.retrieve(paymentIntentId);
        } catch (StripeException e) {
            log.error("Stripe Terminal retrieve-intent error for {}", paymentIntentId, e);
            throw ApiException.internalError("Could not check the card payment: " + e.getMessage());
        }
    }

    /** Cancels a PaymentIntent that never completed — used before starting a fresh one
     *  when the previous attempt landed in a state it can't just be re-dispatched from
     *  (see PosService.charge), and tolerant of one that's already cancelled/gone. */
    public void cancelPaymentIntent(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) return;
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            if ("canceled".equals(intent.getStatus()) || "succeeded".equals(intent.getStatus())) return;
            intent.cancel(PaymentIntentCancelParams.builder().build());
        } catch (StripeException e) {
            log.warn("Stripe Terminal cancel-intent {} failed (continuing): {}", paymentIntentId, e.getMessage());
        }
    }

    /** The operator's escape hatch for a stuck reader prompt (customer walked away, or
     *  the operator mis-rang the sale) — without this, the NEXT charge attempt fails with
     *  terminal_reader_busy since the reader considers itself still mid-action.
     *
     *  Deliberately swallows every failure rather than surfacing one, including a
     *  reader that was already idle (no exact, documented error code to match that
     *  case reliably) — this call's only job is best-effort cleanup, and if it
     *  genuinely didn't work, the very next charge attempt already reports that
     *  clearly via dispatchToReader's own terminal_reader_busy message. Throwing
     *  here would just block that self-correcting retry for no safety benefit. */
    public void cancelReaderAction() {
        if (readerId == null || readerId.isBlank()) return;
        try {
            Reader.retrieve(readerId).cancelAction(ReaderCancelActionParams.builder().build());
        } catch (StripeException e) {
            log.warn("Stripe Terminal cancel-action failed (continuing): {}", e.getMessage());
        }
    }

    /** Polled by the POS screen so an offline/busy reader is visible before a customer
     *  is standing there, instead of only surfacing as a failed charge. */
    public PosReaderStatus readerStatus() {
        if (readerId == null || readerId.isBlank()) {
            return new PosReaderStatus(false, "No reader configured", null, null);
        }
        try {
            Reader reader = Reader.retrieve(readerId);
            Reader.Action action = reader.getAction();
            return new PosReaderStatus(
                    "online".equalsIgnoreCase(reader.getStatus()),
                    reader.getLabel(),
                    action != null ? action.getStatus() : null,
                    action != null ? action.getFailureCode() : null);
        } catch (StripeException e) {
            log.warn("Stripe Terminal reader-status error: {}", e.getMessage());
            return new PosReaderStatus(false, "Unreachable", null, null);
        }
    }

    /** card_present charges refund exactly like any other Stripe charge — full API
     *  refund, no card or reader needed. amountPence null = full refund. */
    public String createRefund(String paymentIntentId, Integer amountPence) {
        try {
            RefundCreateParams.Builder builder = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId);
            if (amountPence != null) builder.setAmount((long) amountPence);
            Refund refund = Refund.create(builder.build());
            return refund.getId();
        } catch (StripeException e) {
            log.error("Stripe Terminal refund error for intent {}", paymentIntentId, e);
            throw ApiException.badRequest("Could not process the refund: " + e.getMessage());
        }
    }

    /** Whether ANY refund has ever been recorded against this PaymentIntent. A refunded
     *  PaymentIntent's own `status` stays "succeeded" — Stripe never flips it back — so
     *  this is the only reliable way to tell a genuinely-fresh success apart from one
     *  that was already reversed (e.g. by hand in the Stripe Dashboard) before the
     *  reconcile fallback got to it. Unlike this service's other non-critical lookups,
     *  this one gates a money decision — PaymentService.reconcilePosOrder would mark a
     *  refunded sale paid again if this silently answered "no refund" on a transient
     *  Stripe error, so it fails CLOSED: propagate the error and let reconcile abort
     *  rather than guess. The next reconcile attempt (poll retry, or another "Refresh
     *  status" tap) just tries again. */
    public boolean hasRefund(String paymentIntentId) {
        try {
            RefundListParams params = RefundListParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setLimit(1L)
                    .build();
            return !Refund.list(params).getData().isEmpty();
        } catch (StripeException e) {
            log.warn("Stripe Terminal refund-check failed for {}: {}", paymentIntentId, e.getMessage());
            throw ApiException.internalError("Could not verify refund status: " + e.getMessage());
        }
    }

    /** Independent of PaymentGateway/ukena.payment.provider on purpose — POS always
     *  needs real Stripe webhook verification even if online checkout is on Paystack. */
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (payload == null || signature == null || stripeWebhookSecret == null || stripeWebhookSecret.isBlank())
            return false;
        try {
            Webhook.constructEvent(payload, signature, stripeWebhookSecret);
            return true;
        } catch (Exception e) {
            log.warn("Stripe Terminal webhook signature invalid: {}", e.getMessage());
            return false;
        }
    }

    private void requireReaderConfigured() {
        if (readerId == null || readerId.isBlank())
            throw ApiException.internalError(
                    "No card reader configured — set ukena.stripe.terminal.reader-id once the reader is registered in the Stripe Dashboard");
    }

    /** Translates the handful of reader-specific error codes Stripe documents into
     *  something an operator standing at a market stall can actually act on, instead
     *  of a raw "Could not reach the card reader: ..." 500. */
    private String mapReaderError(StripeException e) {
        String code = e.getCode();
        if (code == null) return "Could not reach the card reader: " + e.getMessage();
        return switch (code) {
            case "terminal_reader_busy" -> "The reader is still on a previous sale — cancel it, then try again.";
            case "terminal_reader_offline" -> "The reader is offline — check its WiFi, then try again.";
            case "terminal_reader_timeout" -> "Didn't hear back from the reader in time — check its screen before retrying, the charge may have gone through.";
            case "intent_invalid_state" -> "That sale is no longer chargeable — start a new sale.";
            default -> "Could not reach the card reader: " + e.getMessage();
        };
    }
}
