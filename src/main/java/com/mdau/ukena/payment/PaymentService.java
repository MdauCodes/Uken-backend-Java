package com.mdau.ukena.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderChannel;
import com.mdau.ukena.order.OrderItem;
import com.mdau.ukena.order.OrderRepository;
import com.mdau.ukena.order.OrderStatus;
import com.mdau.ukena.pos.StripeTerminalService;
import com.mdau.ukena.product.ProductService;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.user.UserRepository;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.mdau.ukena.order.OrderService.WALK_IN_EMAIL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway           paymentGateway;
    private final OrderRepository          orderRepository;
    private final EarningsLedgerRepository ledgerRepository;
    private final PayoutUpdateService      payoutUpdateService;
    private final UserRepository           userRepository;
    private final EmailService             emailService;
    private final ObjectMapper             objectMapper;
    private final ProductService           productService;
    private final StripeTerminalService    stripeTerminalService;

    @Value("${ukena.payment.commission-rate:0.40}")
    private BigDecimal commissionRate;

    @Value("${ukena.payment.provider:stripe}")
    private String provider;

    @Transactional
    public PaymentInitResponse initiate(String displayId, CurrentUser currentUser) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + displayId));

        boolean authorized = order.getBuyer() != null
                ? order.getBuyer().getId().equals(currentUser.id())
                : order.getBuyerEmail().equalsIgnoreCase(currentUser.email());

        if (!authorized)
            throw ApiException.forbidden("This order does not belong to you");
        if (order.getStatus() == OrderStatus.PAID)
            throw ApiException.badRequest("Order is already paid");
        if (order.getStatus() != OrderStatus.PENDING)
            throw ApiException.badRequest("Order is not in PENDING state");

        PaymentInitResult result = paymentGateway.initiatePayment(new PaymentInitRequest(
                order.getId(), displayId, order.getTotalPence(),
                "GBP", order.getBuyerEmail(), order.getBuyerFullName(),
                "Complete your Ukena order " + displayId));

        order.setGatewayRef(result.gatewayRef());
        orderRepository.save(order);
        return new PaymentInitResponse(result.paymentLink(), result.gatewayRef());
    }

    @Transactional
    public void handleWebhook(String payload, String signature) {
        if (payload == null || payload.isBlank()) return;
        if ("paystack".equalsIgnoreCase(provider)) {
            handlePaystackWebhook(payload, signature);
        } else {
            handleStripeWebhook(payload, signature);
        }
    }

    private void handleStripeWebhook(String payload, String signature) {
        // POS (Terminal) always needs real Stripe verification even when the *online*
        // gateway is configured as Paystack — paymentGateway may be a PaystackGateway
        // bean in that case, so fall back to an independent Stripe check.
        boolean verified = paymentGateway.verifyWebhookSignature(payload, signature)
                || stripeTerminalService.verifyWebhookSignature(payload, signature);
        if (!verified) {
            log.warn("Stripe webhook signature invalid - ignoring");
            return;
        }
        try {
            JsonNode node  = objectMapper.readTree(payload);
            String   event = node.path("type").asText();
            log.info("Stripe webhook event: {}", event);

            if ("checkout.session.completed".equalsIgnoreCase(event)) {
                String sessionId = node.path("data").path("object").path("id").asText();
                String displayId = node.path("data").path("object")
                        .path("client_reference_id").asText();
                String status    = node.path("data").path("object")
                        .path("payment_status").asText();
                if (!"paid".equalsIgnoreCase(status)) return;

                orderRepository.findByDisplayId(displayId).ifPresentOrElse(
                        order -> {
                            // Stripe explicitly redelivers webhooks — PENDING is the only
                            // pre-payment state, so anything else means this already
                            // landed (and for POS specifically, moves straight past PAID
                            // to DELIVERED, so checking only "== PAID" would miss that and
                            // reprocess: double stock decrement, double ledger credit).
                            if (order.getStatus() != OrderStatus.PENDING) return;
                            markOrderPaid(order, sessionId);
                        },
                        () -> log.warn("Stripe: order not found displayId={}", displayId));

            } else if ("payment_intent.succeeded".equalsIgnoreCase(event)) {
                // POS (Terminal, server-driven) completion. A Checkout Session's own
                // PaymentIntent also emits this event, but never carries our display_id
                // metadata (that's set on the Session, not the PI Checkout creates) —
                // gate on it being present so an online sale doesn't log a spurious
                // "order not found" warning below.
                String paymentIntentId = node.path("data").path("object").path("id").asText();
                String displayId = node.path("data").path("object")
                        .path("metadata").path("display_id").asText();
                if (displayId == null || displayId.isBlank()) return;

                // Locked read — this can race PaymentService.reconcilePosOrder (an
                // operator's "Refresh status" tap) for the same order; see
                // OrderRepository.findByDisplayIdForUpdate.
                orderRepository.findByDisplayIdForUpdate(displayId).ifPresentOrElse(
                        order -> {
                            // See the identical comment above — a completed POS order is
                            // DELIVERED, not PAID, so the guard must cover both.
                            if (order.getStatus() != OrderStatus.PENDING) return;
                            markOrderPaid(order, paymentIntentId);
                        },
                        () -> log.warn("Stripe: POS order not found displayId={}", displayId));

            } else if ("payment_intent.payment_failed".equalsIgnoreCase(event)) {
                JsonNode pi = node.path("data").path("object");
                String displayId = pi.path("metadata").path("display_id").asText();
                String paymentIntentId = pi.path("id").asText();
                String reason = pi.path("last_payment_error").path("message").asText("Card declined");
                resolveOrderForTerminalEvent(displayId, paymentIntentId)
                        .ifPresent(order -> recordChargeFailure(order, reason));

            } else if ("terminal.reader.action_failed".equalsIgnoreCase(event)) {
                JsonNode action = node.path("data").path("object").path("action");
                String paymentIntentId = action.path("process_payment_intent").path("payment_intent").asText();
                String failureCode = action.path("failure_code").asText("");
                JsonNode apiError = action.path("api_error");
                // Stripe: an api_error of type "card_error" is safe to show the cardholder
                // verbatim; anything else could leak internal detail, so map by failure_code
                // instead. https://docs.stripe.com/terminal/features/manage-reader/reader-webhooks
                String reason;
                if ("card_error".equalsIgnoreCase(apiError.path("type").asText())) {
                    reason = apiError.path("message").asText("Card declined");
                } else {
                    reason = switch (failureCode) {
                        case "customer_canceled" -> "Cancelled on the reader";
                        case "connection_error" -> "The reader lost connection — check whether the payment actually went through";
                        default -> failureCode.isBlank() ? "The reader could not complete the sale" : failureCode;
                    };
                }
                resolveOrderForTerminalEvent(null, paymentIntentId).ifPresentOrElse(
                        order -> {
                            // A connection_error is an explicit false negative in Stripe's own
                            // docs — the charge may have actually gone through. Check before
                            // giving up on it.
                            if ("connection_error".equals(failureCode)) {
                                PaymentIntent pi = stripeTerminalService.retrievePaymentIntent(paymentIntentId);
                                if ("succeeded".equals(pi.getStatus())) {
                                    if (order.getStatus() == OrderStatus.PENDING) markOrderPaid(order, paymentIntentId);
                                    return;
                                }
                            }
                            recordChargeFailure(order, reason);
                        },
                        () -> log.warn("Stripe: reader action_failed for unknown intent={}", paymentIntentId));

            } else if ("charge.refunded".equalsIgnoreCase(event)) {
                // Bookkeeping only — catches a refund initiated directly from the Stripe
                // Dashboard rather than through OrderService.adminRefund, so Order.refundedPence
                // doesn't silently drift from what actually happened at the gateway.
                JsonNode charge = node.path("data").path("object");
                String paymentIntentId = charge.path("payment_intent").asText();
                int amountRefunded = charge.path("amount_refunded").asInt(0);
                resolveOrderForTerminalEvent(null, paymentIntentId).ifPresent(order -> {
                    Integer current = order.getRefundedPence();
                    if (current != null && current >= amountRefunded) return; // already reflected
                    order.setRefundedPence(amountRefunded);
                    if (amountRefunded >= order.getTotalPence()) order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);
                });
            }
        } catch (Exception e) {
            log.error("Stripe webhook parse error", e);
        }
    }

    private void handlePaystackWebhook(String payload, String signature) {
        if (!paymentGateway.verifyWebhookSignature(payload, signature)) {
            log.warn("Paystack webhook signature invalid - ignoring");
            return;
        }
        try {
            JsonNode node      = objectMapper.readTree(payload);
            String   event     = node.path("event").asText();
            if (!"charge.success".equalsIgnoreCase(event)) return;

            String reference = node.path("data").path("reference").asText();
            String status    = node.path("data").path("status").asText();
            if (!"success".equalsIgnoreCase(status)) return;

            String displayId = reference.contains("-")
                    ? reference.substring(0, reference.lastIndexOf('-'))
                    : reference;

            orderRepository.findByDisplayId(displayId).ifPresentOrElse(
                    order -> {
                        if (order.getStatus() != OrderStatus.PENDING) return;
                        if (paymentGateway.verifyPayment(reference)) {
                            markOrderPaid(order, reference);
                        } else {
                            log.warn("Paystack verify failed ref={}", reference);
                        }
                    },
                    () -> log.warn("Paystack: order not found displayId={}", displayId));

        } catch (Exception e) {
            log.error("Paystack webhook parse error", e);
        }
    }

    /**
     * Best-effort fallback for when payment_intent.succeeded is slow, or never arrives
     * at all (a missing subscription on the Stripe Dashboard's webhook endpoint, a
     * rotated signing secret, an outage) — asks Stripe directly what really happened
     * instead of leaving the order stuck PENDING forever with money already moved.
     * Used by the POS page once its poll times out, and by its manual "Refresh status"
     * action, so a card that was actually charged doesn't strand the sale invisibly.
     *
     * Idempotent and safe to call repeatedly: no-ops once the order is no longer
     * PENDING (already resolved, by webhook or an earlier reconcile), and deliberately
     * will NOT resurrect a PaymentIntent that's since been refunded (its `status` stays
     * "succeeded" even after a refund — see StripeTerminalService.hasRefund) as a fresh
     * paid sale.
     */
    @Transactional
    public void reconcilePosOrder(String displayId) {
        // Locked read — this can race the payment_intent.succeeded webhook for the
        // same order (an operator's "Refresh status" tap while it's landing, or two
        // taps in a row); see OrderRepository.findByDisplayIdForUpdate.
        Order order = orderRepository.findByDisplayIdForUpdate(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + displayId));
        if (order.getStatus() != OrderStatus.PENDING) return;

        String paymentIntentId = order.getPaymentIntentId();
        if (paymentIntentId == null || paymentIntentId.isBlank()) return; // no charge attempted yet

        PaymentIntent intent = stripeTerminalService.retrievePaymentIntent(paymentIntentId);
        if ("succeeded".equals(intent.getStatus()) && !stripeTerminalService.hasRefund(paymentIntentId)) {
            markOrderPaid(order, paymentIntentId);
        }
    }

    /** Resolves a Terminal webhook back to its order — by display_id metadata when
     *  present, else by the PaymentIntent id already persisted at charge time
     *  (see PosService.charge). Some Terminal events (action_failed, charge.refunded)
     *  never carry display_id at all, only the payment_intent id. */
    private java.util.Optional<Order> resolveOrderForTerminalEvent(String displayId, String paymentIntentId) {
        if (displayId != null && !displayId.isBlank()) {
            java.util.Optional<Order> byDisplayId = orderRepository.findByDisplayId(displayId);
            if (byDisplayId.isPresent()) return byDisplayId;
        }
        if (paymentIntentId == null || paymentIntentId.isBlank()) return java.util.Optional.empty();
        return orderRepository.findByPaymentIntentId(paymentIntentId);
    }

    /** A declined/cancelled/failed charge — order stays PENDING (still chargeable),
     *  but the reason is now visible to the POS operator instead of a silent timeout. */
    private void recordChargeFailure(Order order, String reason) {
        // A stale/redelivered failure notification for an order that already succeeded
        // (POS lands on DELIVERED, not PAID) must never overwrite a real success with
        // a leftover error message.
        if (order.getStatus() != OrderStatus.PENDING) return;
        order.setLastPaymentError(reason);
        orderRepository.save(order);
        log.info("Order {} charge failed: {}", order.getDisplayId(), reason);
    }

    private void markOrderPaid(Order order, String gatewayRef) {
        order.setStatus(OrderStatus.PAID);
        order.setGatewayRef(gatewayRef);
        order.setPaidAt(Instant.now());
        order.setLastPaymentError(null);
        // A market-stall sale is a walk-out handover, not a shipment — land it terminal
        // rather than leaving it to be walked PAID -> PREPARING -> SHIPPED -> DELIVERED
        // by an admin (which would also email a buyer address that's a sentinel, not
        // a real inbox, at every step in between).
        if (order.getChannel() == OrderChannel.POS) {
            order.setStatus(OrderStatus.DELIVERED);
        }
        orderRepository.save(order);

        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null) continue;
            boolean ok = productService.decrementStock(item.getProduct().getId(), item.getQuantity());
            if (!ok) log.warn("Stock decrement failed for product={} order={} — sold past tracked stock",
                    item.getProduct().getId(), order.getDisplayId());
        }

        creditLedger(order);

        try {
            payoutUpdateService.updatePayoutRecord(order);
        } catch (Exception e) {
            log.error("Payout balance update failed order={}: {}",
                    order.getDisplayId(), e.getMessage());
        }

        // Anonymous POS sale (no customer email given) — WALK_IN_EMAIL is a NOT NULL
        // sentinel on the column, never a real inbox to send mail to.
        if (!WALK_IN_EMAIL.equalsIgnoreCase(order.getBuyerEmail())) {
            emailService.sendOrderConfirmation(
                    order.getBuyerEmail(), order.getBuyerFullName(),
                    order.getDisplayId(), order.getTotalPence(),
                    order.getItems().stream()
                            .map(OrderItem::getCreatorFullName).distinct()
                            .collect(Collectors.joining(", ")));
        }

        sendCreatorNotifications(order);
        log.info("Order {} marked PAID via {}", order.getDisplayId(), gatewayRef);
    }

    /**
     * Ledger rules:
     *   Uken catalogue item  → status=PAID immediately, commissionRate=1.0, net=gross
     *   Creator item         → status=PENDING, commissionRate from config, net=gross*(1-rate)
     */
    private void creditLedger(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getCreator() == null) continue;

            boolean isUkenItem = ProductService.UKENA_CREATOR_ID
                    .equals(item.getCreator().getId());

            int gross = item.getPricePence() * item.getQuantity();
            int net   = isUkenItem ? gross
                    : new BigDecimal(gross)
                            .multiply(BigDecimal.ONE.subtract(commissionRate))
                            .setScale(0, RoundingMode.HALF_UP).intValue();

            ledgerRepository.save(EarningsLedger.builder()
                    .creatorId(item.getCreator().getId())
                    .artisanProfileId(item.getCreator().getId())
                    .orderId(order.getId())
                    .orderItemId(item.getId())
                    .grossPence(gross)
                    .amountPence(net)
                    .commissionRate(isUkenItem ? BigDecimal.ONE : commissionRate)
                    .netPence(net)
                    .status(isUkenItem ? LedgerStatus.PAID : LedgerStatus.PENDING)
                    .build());

            if (isUkenItem)
                log.info("Uken catalogue revenue: order={} product={} gross={}p",
                        order.getDisplayId(), item.getProductName(), gross);
        }
    }

    /** Skip Uken sentinel — no email goes to a creator for Uken-owned products. */
    private void sendCreatorNotifications(Order order) {
        order.getItems().stream()
                .filter(i -> i.getCreator() != null
                          && !ProductService.UKENA_CREATOR_ID
                                .equals(i.getCreator().getId()))
                .collect(Collectors.groupingBy(i -> i.getCreator().getId()))
                .forEach((creatorId, items) -> {
                    OrderItem first = items.get(0);
                    userRepository.findByCreatorId(creatorId).ifPresentOrElse(
                            user -> emailService.sendNewOrderNotification(
                                    user.getEmail(), user.getFullName(),
                                    order.getDisplayId(), first.getProductName(),
                                    items.stream().mapToInt(OrderItem::getQuantity).sum()),
                            () -> log.warn("No user for creatorId={}", creatorId));
                });
    }

    @Transactional
    public PayoutResult gatewayPayout(String creatorId,
                                       String accountNumber, String accountName) {
        if (ProductService.UKENA_CREATOR_ID.equals(creatorId))
            throw ApiException.badRequest(
                    "Cannot initiate payout for Uken platform account");

        int netPence = ledgerRepository
                .sumNetPenceByArtisanProfileIdAndStatus(creatorId, LedgerStatus.PENDING);
        if (netPence <= 0)
            throw ApiException.badRequest("No pending earnings for creator " + creatorId);

        PayoutResult result = paymentGateway.initiateTransfer(new PayoutRequest(
                creatorId, netPence, accountNumber, accountName,
                "Ukena payout to " + accountName));

        if (result.success()) {
            List<EarningsLedger> entries = ledgerRepository
                    .findByArtisanProfileIdAndStatus(creatorId, LedgerStatus.PENDING);
            entries.forEach(e -> e.setStatus(LedgerStatus.PAID));
            ledgerRepository.saveAll(entries);
        }
        return result;
    }
}