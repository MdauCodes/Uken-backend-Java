package com.mdau.ukena.payment;

import com.mdau.ukena.admin.PayoutRecord;
import com.mdau.ukena.admin.PayoutRepository;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutUpdateService {

    private final PayoutRepository  payoutRepository;
    private final CreatorRepository creatorRepository;

    @Value("${ukena.payment.commission-rate:0.40}")
    private BigDecimal commissionRate;

    /**
     * Runs in its own transaction (REQUIRES_NEW).
     * If optimistic locking fails, the outer order-marking transaction
     * is NOT affected — the order will still be saved as PAID.
     * Retried up to 3 times before giving up.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePayoutRecord(Order order) {
        order.getItems().stream()
                .filter(i -> i.getCreator() != null)
                .collect(Collectors.groupingBy(i -> i.getCreator().getId()))
                .forEach((creatorId, items) -> {
                    int netTotal = items.stream().mapToInt(i -> {
                        int gross = i.getPricePence() * i.getQuantity();
                        return gross - new BigDecimal(gross)
                                .multiply(commissionRate)
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
                    }).sum();
                    updateWithRetry(creatorId, netTotal, order.getDisplayId(), 3);
                });
    }

    private void updateWithRetry(String creatorId, int netTotal, String displayId, int attempts) {
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                PayoutRecord payout = payoutRepository.findByCreatorId(creatorId)
                        .orElseGet(() -> creatorRepository.findActiveById(creatorId)
                                .map(creator -> PayoutRecord.builder()
                                        .creatorId(creatorId)
                                        .creator(creator)
                                        .build())
                                .orElse(null));

                if (payout == null) {
                    log.warn("Creator {} not found for payout update (order={})", creatorId, displayId);
                    return;
                }

                payout.setPendingPence(payout.getPendingPence() + netTotal);
                payoutRepository.saveAndFlush(payout);
                log.info("Payout updated for creator={} net={} (order={})", creatorId, netTotal, displayId);
                return;

            } catch (Exception e) {
                if (attempt == attempts) {
                    log.error("Payout update failed after {} attempts for creator={} order={}: {}",
                            attempts, creatorId, displayId, e.getMessage());
                } else {
                    log.warn("Payout update attempt {}/{} failed for creator={}, retrying: {}",
                            attempt, attempts, creatorId, e.getMessage());
                    try { Thread.sleep(50L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}