package com.mdau.ukena.payment;

import com.mdau.ukena.admin.PayoutRecord;
import com.mdau.ukena.admin.PayoutRepository;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.order.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
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
    private final ApplicationContext applicationContext;

    @Value("${ukena.payment.commission-rate:0.40}")
    private BigDecimal commissionRate;

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
                    retryUpdate(creatorId, netTotal, order.getDisplayId());
                });
    }

    private void retryUpdate(String creatorId, int netTotal, String displayId) {
        // Get the Spring-proxied version of this bean so @Transactional(REQUIRES_NEW) is honoured
        PayoutUpdateService proxy = applicationContext.getBean(PayoutUpdateService.class);
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                proxy.attemptUpdate(creatorId, netTotal, displayId);
                return;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    log.error("Payout update failed after {} attempts for creator={} order={}: {}",
                            maxAttempts, creatorId, displayId, e.getMessage());
                } else {
                    log.warn("Payout update attempt {}/{} failed for creator={}, retrying: {}",
                            attempt, maxAttempts, creatorId, e.getMessage());
                    try {
                        Thread.sleep(50L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attemptUpdate(String creatorId, int netTotal, String displayId) {
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
    }
}