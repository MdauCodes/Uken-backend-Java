package com.mdau.ukena.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Unified notification service.
 * Combines email (always active) and SMS (UK buyers, when Twilio enabled).
 * Call sites use this instead of EmailService directly for order events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;
    private final SmsService   smsService;

    /**
     * Notify buyer when their order is placed.
     * Email: always. SMS: UK numbers only when Twilio enabled.
     */
    public void notifyOrderPlaced(String email, String fullName,
                                  String displayId, int totalPence,
                                  String creatorNames, String phoneNumber) {
        // Email is always the primary channel
        emailService.sendOrderConfirmation(
                email, fullName, displayId, totalPence, creatorNames);

        // SMS as supplementary channel for UK buyers
        if (smsService.isEnabled() && phoneNumber != null) {
            String gbp     = String.format("%.2f", totalPence / 100.0);
            String message = "Ukena: Order " + displayId + " confirmed! "
                    + "Total GBP " + gbp + ". "
                    + "Handcrafted by " + creatorNames + ". Thank you!";
            smsService.sendSms(phoneNumber, message);
        }
    }

    /**
     * Notify buyer when their order is shipped.
     * Email: via status update flow. SMS: UK numbers only.
     */
    public void notifyOrderShipped(String email, String fullName,
                                   String displayId, String phoneNumber) {
        if (smsService.isEnabled() && phoneNumber != null) {
            String message = "Ukena: Your order " + displayId
                    + " has been shipped! "
                    + "It is on its way to you. Track via your account.";
            smsService.sendSms(phoneNumber, message);
        } else {
            log.debug("Order shipped SMS skipped for order {}", displayId);
        }
    }
}