package com.mdau.ukena.pos;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.pos.dto.PosPaymentIntentResponse;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.terminal.Reader;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
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

    @PostConstruct
    void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    public PosPaymentIntentResponse createPaymentIntent(String displayId, int amountPence) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount((long) amountPence)
                    .setCurrency("gbp")
                    .addPaymentMethodType("card_present")
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                    .putMetadata("display_id", displayId)
                    .build();
            PaymentIntent intent = PaymentIntent.create(params);
            return new PosPaymentIntentResponse(intent.getId(), intent.getClientSecret());
        } catch (Exception e) {
            log.error("Stripe Terminal payment intent error for order {}", displayId, e);
            throw ApiException.internalError("Could not start the card payment: " + e.getMessage());
        }
    }

    /** Tells the configured reader to prompt the customer for this payment. Fire-and-forget
     *  from here — actual success/failure arrives later as a payment_intent.succeeded webhook. */
    public void dispatchToReader(String paymentIntentId) {
        if (readerId == null || readerId.isBlank())
            throw ApiException.internalError(
                    "No card reader configured — set ukena.stripe.terminal.reader-id once the reader is registered in the Stripe Dashboard");
        try {
            Reader reader = Reader.retrieve(readerId);
            reader.processPaymentIntent(ReaderProcessPaymentIntentParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .build());
        } catch (Exception e) {
            log.error("Stripe Terminal dispatch-to-reader error for intent {}", paymentIntentId, e);
            throw ApiException.internalError("Could not reach the card reader: " + e.getMessage());
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
}
