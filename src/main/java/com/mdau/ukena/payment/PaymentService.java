package com.mdau.ukena.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderItem;
import com.mdau.ukena.order.OrderRepository;
import com.mdau.ukena.order.OrderStatus;
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

        String description = "Ukena order " + displayId + " - " +
                order.getItems().stream()
                        .map(OrderItem::getProductName)
                        .distinct()
                        .collect(Collectors.joining(", "));

        PaymentInitResult result = paymentGateway.initiatePayment(new PaymentInitRequest(
                order.getId(), displayId, order.getTotalPence(),
                "GBP", order.getBuyerEmail(), order.getBuyerFullName(), description));

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
        if (!paymentGateway.verifyWebhookSignature(payload, signature)) {
            log.warn("Stripe webhook signature invalid - ignoring");
            return;
        }
        try {
            JsonNode node  = objectMapper.readTree(payload);
            String   event = node.path("type").asText();
            log.info("Stripe webhook event: {}", event);

            if (!"checkout.session.completed".equalsIgnoreCase(event)) return;

            String sessionId = node.path("data").path("object").path("id").asText();
            String displayId = node.path("data").path("object").path("client_reference_id").asText();
            String status    = node.path("data").path("object").path("payment_status").asText();

            log.info("Stripe webhook: sessionId={} displayId={} status={}", sessionId, displayId, status);

            if (!"paid".equalsIgnoreCase(status)) return;

            orderRepository.findByDisplayId(displayId).ifPresentOrElse(order -> {
                if (order.getStatus() == OrderStatus.PAID) {
                    log.info("Order {} already PAID, skipping", displayId);
                    return;
                }
                markOrderPaid(order, sessionId);
            }, () -> log.warn("Stripe webhook: order not found for displayId={}", displayId));

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
            JsonNode node  = objectMapper.readTree(payload);
            String   event = node.path("event").asText();
            log.info("Paystack webhook event: {}", event);

            if (!"charge.success".equalsIgnoreCase(event)) return;

            String reference = node.path("data").path("reference").asText();
            String status    = node.path("data").path("status").asText();

            if (!"success".equalsIgnoreCase(status)) return;

            String displayId = reference.contains("-")
                    ? reference.substring(0, reference.lastIndexOf('-'))
                    : reference;

            log.info("Paystack webhook: ref={} displayId={}", reference, displayId);

            orderRepository.findByDisplayId(displayId).ifPresentOrElse(order -> {
                if (order.getStatus() == OrderStatus.PAID) {
                    log.info("Order {} already PAID, skipping", displayId);
                    return;
                }
                if (paymentGateway.verifyPayment(reference)) {
                    markOrderPaid(order, reference);
                } else {
                    log.warn("Paystack verify failed for reference={}", reference);
                }
            }, () -> log.warn("Paystack webhook: order not found for displayId={}", displayId));

        } catch (Exception e) {
            log.error("Paystack webhook parse error", e);
        }
    }

    private void markOrderPaid(Order order, String gatewayRef) {
        // 1. Mark order PAID and persist — this must succeed first
        order.setStatus(OrderStatus.PAID);
        order.setGatewayRef(gatewayRef);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);

        // 2. Credit earnings ledger (same transaction — safe, no optimistic locking)
        creditLedger(order);

        // 3. Update payout balance — isolated REQUIRES_NEW transaction, won't poison this one
        payoutUpdateService.updatePayoutRecord(order);

        // 4. Send buyer confirmation email
        emailService.sendOrderConfirmation(
                order.getBuyerEmail(), order.getBuyerFullName(),
                order.getDisplayId(), order.getTotalPence(),
                order.getItems().stream()
                        .map(OrderItem::getCreatorFullName).distinct()
                        .collect(Collectors.joining(", ")));

        // 5. Send creator new-order notification — only fires after confirmed payment
        sendCreatorOrderNotifications(order);

        log.info("Order {} marked PAID via {}", order.getDisplayId(), gatewayRef);
    }

    private void creditLedger(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getCreator() == null) continue;
            int gross = item.getPricePence() * item.getQuantity();
            // Creator earns 60% — Ukena takes 40% commission
            int net = new BigDecimal(gross)
                    .multiply(BigDecimal.ONE.subtract(commissionRate))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
            ledgerRepository.save(EarningsLedger.builder()
                    .artisanProfileId(item.getCreator().getId())
                    .orderId(order.getId())
                    .orderItemId(item.getId())
                    .grossPence(gross)
                    .commissionRate(commissionRate)
                    .netPence(net)
                    .status(LedgerStatus.PENDING)
                    .build());
        }
    }

    private void sendCreatorOrderNotifications(Order order) {
        order.getItems().stream()
                .filter(i -> i.getCreator() != null)
                .collect(Collectors.groupingBy(i -> i.getCreator().getId()))
                .forEach((creatorId, items) -> {
                    OrderItem first = items.get(0);
                    userRepository.findByCreatorId(creatorId).ifPresentOrElse(
                            user -> emailService.sendNewOrderNotification(
                                    user.getEmail(), user.getFullName(),
                                    order.getDisplayId(), first.getProductName(),
                                    items.stream().mapToInt(OrderItem::getQuantity).sum()),
                            () -> log.warn("No user found for creatorId={}", creatorId));
                });
    }

    @Transactional
    public PayoutResult gatewayPayout(String creatorId, String accountNumber, String accountName) {
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