package com.mdau.ukena.notification;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface EmailService {

    CompletableFuture<Boolean> sendOrderConfirmation(
            String buyerEmail, String buyerName,
            String orderRef, int totalPence, String creatorNames);

    CompletableFuture<Boolean> sendNewOrderNotification(
            String creatorEmail, String creatorName,
            String orderRef, String productName, int quantity);

    CompletableFuture<Boolean> sendApplicationReceived(
            String email, String fullName, String applicationId);

    CompletableFuture<Boolean> sendCreatorWelcome(
            String email, String fullName,
            String creatorId, String tempPassword);

    CompletableFuture<Boolean> sendPayoutConfirmation(
            String email, String fullName,
            int amountPence, String currency);

    CompletableFuture<Boolean> sendPasswordReset(
            String email, String fullName, String resetLink);

    CompletableFuture<Boolean> sendPaymentReminder(
            String email, String fullName,
            String orderRef, int totalPence,
            String paymentLink);

    boolean sendHtml(String toEmail, String subject,
                     String templateName, Map<String, Object> model);
}