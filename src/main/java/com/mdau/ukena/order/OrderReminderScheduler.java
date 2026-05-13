package com.mdau.ukena.order;

import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.payment.PaymentGateway;
import com.mdau.ukena.payment.PaymentInitRequest;
import com.mdau.ukena.payment.PaymentInitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReminderScheduler {

    private final OrderRepository orderRepository;
    private final PaymentGateway  paymentGateway;
    private final EmailService    emailService;

    @Value("${ukena.order.reminder-delay-minutes:10}")
    private int reminderDelayMinutes;

    @Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public void sendPaymentReminders() {
        Instant cutoff = Instant.now().minus(reminderDelayMinutes, ChronoUnit.MINUTES);
        List<Order> orders = orderRepository.findPendingOrdersForReminder(cutoff);

        if (orders.isEmpty()) return;
        log.info("Sending payment reminders for {} pending orders", orders.size());

        for (Order order : orders) {
            try {
                PaymentInitResult result = paymentGateway.initiatePayment(
                        new PaymentInitRequest(
                                order.getId(),
                                order.getDisplayId(),
                                order.getTotalPence(),
                                "GBP",
                                order.getBuyerEmail(),
                                order.getBuyerFullName(),
                                "Complete your Ukena order " + order.getDisplayId()));

                emailService.sendPaymentReminder(
                        order.getBuyerEmail(),
                        order.getBuyerFullName(),
                        order.getDisplayId(),
                        order.getTotalPence(),
                        result.paymentLink());

                order.setReminderSentAt(Instant.now());
                orderRepository.save(order);
                log.info("Payment reminder sent for order {}", order.getDisplayId());

            } catch (Exception e) {
                log.error("Failed to send reminder for order {}: {}",
                        order.getDisplayId(), e.getMessage());
            }
        }
    }
}