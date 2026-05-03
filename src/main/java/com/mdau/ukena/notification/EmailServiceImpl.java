package com.mdau.ukena.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final Configuration freemarkerConfig;

    @Value("${ukena.email.from-address}")
    private String fromAddress;

    @Value("${ukena.email.from-name}")
    private String fromName;

    @Value("${ukena.email.brevo-api-key:}")
    private String brevoApiKey;

    @Value("${ukena.email.brevo-api-url}")
    private String brevoApiUrl;

    @Value("${ukena.email.use-brevo-api:true}")
    private boolean useBrevoApi;

    @Value("${ukena.email.frontend-url}")
    private String frontendUrl;

    private OkHttpClient httpClient;

    public EmailServiceImpl(JavaMailSender mailSender,
                            ObjectMapper objectMapper,
                            @Qualifier("freemarkerConfiguration") Configuration freemarkerConfig) {
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.freemarkerConfig = freemarkerConfig;
    }

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        String method = (useBrevoApi && !brevoApiKey.isBlank()) ? "Brevo API" : "SMTP";
        log.info("Email service ready — method: {}, from: {} <{}>", method, fromName, fromAddress);
    }

    // ── 1. Order confirmation ─────────────────────────────────────

    @Async("emailTaskExecutor")
    @Override
    public CompletableFuture<Boolean> sendOrderConfirmation(
            String buyerEmail, String buyerName,
            String orderRef, int totalPence, String creatorNames) {
        Map<String, Object> model = new HashMap<>();
        model.put("name", buyerName);
        model.put("orderRef", orderRef);
        model.put("totalPence", totalPence);
        model.put("totalFormatted", formatPence(totalPence));
        model.put("creatorNames", creatorNames);
        model.put("ordersUrl", frontendUrl + "/account/orders");
        model.put("baseUrl", frontendUrl);
        return send(buyerEmail,
                "Your Ukena order is confirmed — " + orderRef,
                "order-confirmation.ftl", model);
    }

    // ── 2. New order to creator ───────────────────────────────────

    @Async("emailTaskExecutor")
    @Override
    public CompletableFuture<Boolean> sendNewOrderNotification(
            String creatorEmail, String creatorName,
            String orderRef, String productName, int quantity) {
        Map<String, Object> model = new HashMap<>();
        model.put("name", creatorName);
        model.put("orderRef", orderRef);
        model.put("productName", productName);
        model.put("quantity", quantity);
        model.put("ordersUrl", frontendUrl + "/dashboard/orders");
        model.put("baseUrl", frontendUrl);
        return send(creatorEmail,
                "New order received — " + orderRef,
                "new-order-creator.ftl", model);
    }

    // ── 3. Application received ───────────────────────────────────

    @Async("emailTaskExecutor")
    @Override
    public CompletableFuture<Boolean> sendApplicationReceived(
            String email, String fullName, String applicationId) {
        Map<String, Object> model = new HashMap<>();
        model.put("name", fullName);
        model.put("applicationId", applicationId);
        model.put("baseUrl", frontendUrl);
        return send(email,
                "We received your application — " + applicationId,
                "application-received.ftl", model);
    }

    // ── 4. Creator welcome ────────────────────────────────────────

    @Async("emailTaskExecutor")
    @Override
    public CompletableFuture<Boolean> sendCreatorWelcome(
            String email, String fullName,
            String creatorId, String tempPassword) {
        Map<String, Object> model = new HashMap<>();
        model.put("name", fullName);
        model.put("creatorId", creatorId);
        model.put("tempPassword", tempPassword);
        model.put("loginUrl", frontendUrl + "/signin");
        model.put("dashboardUrl", frontendUrl + "/dashboard");
        model.put("baseUrl", frontendUrl);
        return send(email,
                "Welcome to Ukena — your creator account is ready",
                "creator-welcome.ftl", model);
    }

    // ── 5. Payout confirmation ────────────────────────────────────

    @Async("emailTaskExecutor")
    @Override
    public CompletableFuture<Boolean> sendPayoutConfirmation(
            String email, String fullName,
            int amountPence, String currency) {
        Map<String, Object> model = new HashMap<>();
        model.put("name", fullName);
        model.put("amountFormatted", formatPence(amountPence));
        model.put("currency", currency);
        model.put("dashboardUrl", frontendUrl + "/dashboard");
        model.put("baseUrl", frontendUrl);
        return send(email,
                "Your Ukena payout has been sent",
                "payout-confirmation.ftl", model);
    }

    // ── 6. Password reset ─────────────────────────────────────────

    @Async("emailTaskExecutor")
    @Override
    public CompletableFuture<Boolean> sendPasswordReset(
            String email, String fullName, String resetLink) {
        Map<String, Object> model = new HashMap<>();
        model.put("name", fullName);
        model.put("resetLink", resetLink);
        model.put("baseUrl", frontendUrl);
        return send(email,
                "Reset your Ukena password",
                "password-reset.ftl", model);
    }

    // ── Core sender ───────────────────────────────────────────────

    @Override
    public boolean sendHtml(String toEmail, String subject,
                            String templateName, Map<String, Object> model) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

            if (useBrevoApi && !brevoApiKey.isBlank()) {
                try {
                    sendViaBrevo(toEmail, subject, html);
                    log.debug("Email sent via Brevo to {}", toEmail);
                    return true;
                } catch (IOException e) {
                    log.warn("Brevo API failed, falling back to SMTP: {}", e.getMessage());
                }
            }

            sendViaSMTP(toEmail, subject, html);
            log.debug("Email sent via SMTP to {}", toEmail);
            return true;

        } catch (Exception e) {
            log.error("All email methods failed for {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    // ── Brevo REST API ────────────────────────────────────────────

    private void sendViaBrevo(String toEmail, String subject, String html) throws IOException {
        Map<String, Object> sender = new HashMap<>();
        sender.put("name", fromName);
        sender.put("email", fromAddress);

        Map<String, Object> recipient = new HashMap<>();
        recipient.put("email", toEmail);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", sender);
        payload.put("to", new Object[]{recipient});
        payload.put("subject", subject);
        payload.put("htmlContent", html);

        String json = objectMapper.writeValueAsString(payload);

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(brevoApiUrl)
                .addHeader("api-key", brevoApiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "no body";
                throw new IOException("Brevo error " + response.code() + ": " + err);
            }
        }
    }

    // ── SMTP fallback ─────────────────────────────────────────────

    private void sendViaSMTP(String toEmail, String subject, String html) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private CompletableFuture<Boolean> send(String email, String subject,
                                            String template, Map<String, Object> model) {
        try {
            boolean result = sendHtml(email, subject, template, model);
            if (result) log.info("Email sent: {} -> {}", subject, email);
            else log.error("Email failed: {} -> {}", subject, email);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Email exception: {} -> {}: {}", subject, email, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    private String formatPence(int pence) {
        return String.format("£%.2f", pence / 100.0);
    }
}