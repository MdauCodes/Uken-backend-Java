package com.mdau.ukena.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderItem;
import com.mdau.ukena.order.OrderRepository;
import com.mdau.ukena.order.OrderStatus;
import com.mdau.ukena.pos.StripeTerminalService;
import com.mdau.ukena.product.ProductService;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                            if (order.getStatus() == OrderStatus.PAID) return;
                            markOrderPaid(order, sessionId);
                        },
                        () -> log.warn("Stripe: order not found displayId={}", displayId));

            } else if ("payment_intent.succeeded".equalsIgnoreCase(event)) {
                // POS (Terminal, server-driven) completion — online Checkout payments
                // complete via checkout.session.completed above, so this only fires
                // for POS PaymentIntents, which carry display_id in their own metadata.
                String paymentIntentId = node.path("data").path("object").path("id").asText();
                String displayId = node.path("data").path("object")
                        .path("metadata").path("display_id").asText();

                orderRepository.findByDisplayId(displayId).ifPresentOrElse(
                        order -> {
                            if (order.getStatus() == OrderStatus.PAID) return;
                            markOrderPaid(order, paymentIntentId);
                        },
                        () -> log.warn("Stripe: POS order not found displayId={}", displayId));
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
                        if (order.getStatus() == OrderStatus.PAID) return;
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

    private void markOrderPaid(Order order, String gatewayRef) {
        order.setStatus(OrderStatus.PAID);
        order.setGatewayRef(gatewayRef);
        order.setPaidAt(Instant.now());
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

        emailService.sendOrderConfirmation(
                order.getBuyerEmail(), order.getBuyerFullName(),
                order.getDisplayId(), order.getTotalPence(),
                order.getItems().stream()
                        .map(OrderItem::getCreatorFullName).distinct()
                        .collect(Collectors.joining(", ")));

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