package com.mdau.ukena.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.admin.PayoutRecord;
import com.mdau.ukena.admin.PayoutRepository;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderItem;
import com.mdau.ukena.order.OrderRepository;
import com.mdau.ukena.order.OrderStatus;
import com.mdau.ukena.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway           paymentGateway;
    private final OrderRepository          orderRepository;
    private final EarningsLedgerRepository ledgerRepository;
    private final PayoutRepository         payoutRepository;
    private final CreatorRepository        creatorRepository;
    private final EmailService             emailService;
    private final ObjectMapper             objectMapper;

    @Value("${ukena.payment.commission-rate:0.15}")
    private BigDecimal commissionRate;

    // ── Initiate payment ──────────────────────────────────────────────────

    @Transactional
    public PaymentInitResponse initiate(String displayId, CurrentUser currentUser) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + displayId));

        if (!order.getBuyer().getId().equals(currentUser.id()))
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
                "KES", order.getBuyerEmail(), order.getBuyerFullName(), description));

        order.setGatewayRef(result.gatewayRef());
        orderRepository.save(order);

        return new PaymentInitResponse(result.paymentLink(), result.gatewayRef());
    }

    // ── Webhook entry point ───────────────────────────────────────────────

    @Transactional
    public void handlePaystackWebhook(String payload, String signature) {
        if (payload == null || payload.isBlank()) return;

        if (!paymentGateway.verifyWebhookSignature(payload, signature)) {
            log.warn("Paystack webhook: invalid signature — ignoring");
            return;
        }

        try {
            JsonNode node  = objectMapper.readTree(payload);
            String   event = node.path("event").asText();
            log.info("Paystack webhook event: {}", event);

            if (!"charge.success".equalsIgnoreCase(event)) return;

            String reference = node.path("data").path("reference").asText();
            String status    = node.path("data").path("status").asText();

            if (!"success".equalsIgnoreCase(status)) {
                log.info("Paystack webhook: non-success status={} ref={}", status, reference);
                return;
            }

            // Reference format: UKN-202605-XXXX-{timestamp} — strip the suffix
            String displayId = reference.contains("-")
                    ? reference.substring(0, reference.lastIndexOf('-'))
                    : reference;

            log.info("Paystack webhook: ref={} displayId={}", reference, displayId);

            orderRepository.findByDisplayId(displayId).ifPresentOrElse(
                    order -> {
                        if (order.getStatus() == OrderStatus.PAID) {
                            log.info("Order {} already PAID — skipping", displayId);
                            return;
                        }
                        if (paymentGateway.verifyPayment(reference)) {
                            markOrderPaid(order, reference);
                        } else {
                            log.warn("Paystack verify failed for ref={}", reference);
                        }
                    },
                    () -> log.warn("Paystack webhook: no order for displayId={}", displayId)
            );

        } catch (Exception e) {
            log.error("Paystack webhook processing error: {}", e.getMessage(), e);
        }
    }

    // ── Mark order paid ───────────────────────────────────────────────────

    private void markOrderPaid(Order order, String gatewayRef) {
        order.setStatus(OrderStatus.PAID);
        order.setGatewayRef(gatewayRef);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);

        creditLedger(order);
        updatePayoutRecords(order);

        String creatorNames = order.getItems().stream()
                .map(OrderItem::getCreatorFullName)
                .distinct()
                .collect(Collectors.joining(", "));

        emailService.sendOrderConfirmation(
                order.getBuyerEmail(), order.getBuyerFullName(),
                order.getDisplayId(), order.getTotalPence(), creatorNames);

        log.info("Order {} marked PAID via {}", order.getDisplayId(), gatewayRef);
    }

    // ── Credit earnings ledger ────────────────────────────────────────────

    private void creditLedger(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getCreator() == null) continue;
            int gross = item.getPricePence() * item.getQuantity();
            int net   = netFromGross(gross);
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

    // ── Update payout records (one per creator, retry on optimistic lock) ─

    private void updatePayoutRecords(Order order) {
        Map<String, Integer> netByCreator = order.getItems().stream()
                .filter(i -> i.getCreator() != null)
                .collect(Collectors.groupingBy(
                        i -> i.getCreator().getId(),
                        Collectors.summingInt(i -> netFromGross(i.getPricePence() * i.getQuantity()))
                ));

        netByCreator.forEach(this::creditPayoutRecord);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void creditPayoutRecord(String creatorId, int netPence) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                PayoutRecord payout = payoutRepository.findByCreatorId(creatorId)
                        .orElseGet(() -> creatorRepository.findActiveById(creatorId)
                                .map(creator -> PayoutRecord.builder()
                                        .creatorId(creatorId)
                                        .creator(creator)
                                        .build())
                                .orElse(null));

                if (payout == null) {
                    log.warn("Creator {} not found — skipping payout credit", creatorId);
                    return;
                }

                payout.setPendingPence(payout.getPendingPence() + netPence);
                payoutRepository.saveAndFlush(payout);
                log.info("Payout credited: creator={} net={}p", creatorId, netPence);
                return;

            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                attempts++;
                log.warn("Optimistic lock on payout for creator={}, attempt {}/3",
                        creatorId, attempts);
                if (attempts >= 3) {
                    log.error("Failed to credit payout for creator={} after 3 attempts",
                            creatorId);
                }
            }
        }
    }

    // ── Gateway payout (admin triggered) ─────────────────────────────────

    @Transactional
    public PayoutResult gatewayPayout(String creatorId,
                                      String accountNumber,
                                      String accountName) {
        int netPence = ledgerRepository.sumNetPenceByArtisanProfileIdAndStatus(
                creatorId, LedgerStatus.PENDING);

        if (netPence <= 0)
            throw ApiException.badRequest(
                    "No pending earnings for creator " + creatorId);

        PayoutResult result = paymentGateway.initiateTransfer(new PayoutRequest(
                creatorId, netPence, accountNumber, accountName,
                "Ukena payout to " + accountName));

        if (result.success()) {
            List<EarningsLedger> entries = ledgerRepository
                    .findByArtisanProfileIdAndStatus(creatorId, LedgerStatus.PENDING);
            entries.forEach(e -> e.setStatus(LedgerStatus.PAID));
            ledgerRepository.saveAll(entries);
            log.info("Ledger entries marked PAID for creator={} count={}",
                    creatorId, entries.size());
        }

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private int netFromGross(int grossPence) {
        return grossPence - new BigDecimal(grossPence)
                .multiply(commissionRate)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }
}