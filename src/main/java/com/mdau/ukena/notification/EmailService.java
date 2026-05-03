package com.mdau.ukena.notification;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface EmailService {

    // 1. Order confirmation to buyer
    CompletableFuture<Boolean> sendOrderConfirmation(
            String buyerEmail, String buyerName,
            String orderRef, int totalPence,
            String creatorNames);

    // 2. New order notification to creator
    CompletableFuture<Boolean> sendNewOrderNotification(
            String creatorEmail, String creatorName,
            String orderRef, String productName, int quantity);

    // 3. Application received — to applicant
    CompletableFuture<Boolean> sendApplicationReceived(
            String email, String fullName, String applicationId);

    // 4. Welcome email — when application approved + account created
    CompletableFuture<Boolean> sendCreatorWelcome(
            String email, String fullName,
            String creatorId, String tempPassword);

    // 5. Payout confirmation to artisan
    CompletableFuture<Boolean> sendPayoutConfirmation(
            String email, String fullName,
            int amountPence, String currency);

    // 6. Password reset
    CompletableFuture<Boolean> sendPasswordReset(
            String email, String fullName, String resetLink);

    // Core HTML sender — used internally by all methods above
    boolean sendHtml(String toEmail, String subject,
                     String templateName, Map<String, Object> model);
}