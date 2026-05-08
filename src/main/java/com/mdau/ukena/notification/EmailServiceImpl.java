package com.mdau.ukena.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    private final ObjectMapper objectMapper;
    private final Configuration freemarkerConfig;

    @Value("${ukena.email.from-address}")
    private String fromAddress;

    @Value("${ukena.email.from-name}")
    private String fromName;

    @Value("${ukena.email.brevo-api-key}")
    private String brevoApiKey;

    @Value("${ukena.email.brevo-api-url}")
    private String brevoApiUrl;

    @Value("${ukena.email.frontend-url}")
    private String frontendUrl;

    private OkHttpClient httpClient;

    public EmailServiceImpl(ObjectMapper objectMapper,
                            @Qualifier("freemarkerConfiguration") Configuration freemarkerConfig) {
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

        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.error("EMAIL MISCONFIGURED: BREVO_API_KEY is blank — emails will not be sent");
        } else {
            log.info("Email service ready — Brevo API, from: {} <{}>", fromName, fromAddress);
        }
    }

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
        return send(buyerEmail, "Your Ukena order is confirmed — " + orderRef, "order-confirmation.ftl", model);
    }

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
        return send(creatorEmail, "New order received — " + orderRef, "new-order-creator.ftl", model);
    }

    @Async("emailTaskExecutor")
    @Override
    public CompletableFuture<Boolean> sendApplicationReceived(
            String email, String fullName, String applicationId) {
        Map<String, Object> model = new HashMap<>();
        model.put("name", fullName);
        model.put("applicationId", applicationId);
        model.put("baseUrl", frontendUrl);
        return send(email, "We received your application — " + applicationId, "application-received.ftl", model);
    }

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
        return send(email, "Welcome to Ukena — your creator account is ready", "creator-welcome.ftl", model);
    }

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
        return send(email, "Your Ukena payout has been sent", "payout-confirmation.ftl", model);
    }

    @Async("emailTaskExecutor")
    @Override
    public CompletableFuture<Boolean> sendPasswordReset(
            String email, String fullName, String resetLink) {
        Map<String, Object> model = new HashMap<>();
        model.put("name", fullName);
        model.put("resetLink", resetLink);
        model.put("baseUrl", frontendUrl);
        return send(email, "Reset your Ukena password", "password-reset.ftl", model);
    }

    @Override
    public boolean sendHtml(String toEmail, String subject,
                            String templateName, Map<String, Object> model) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            sendViaBrevo(toEmail, subject, html);
            return true;
        } catch (Exception e) {
            log.error("Email failed [{}] -> {}: {}", subject, toEmail, e.getMessage());
            return false;
        }
    }

    private void sendViaBrevo(String toEmail, String subject, String html) throws IOException {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new IOException("BREVO_API_KEY is not configured");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("name", fromName, "email", fromAddress));
        payload.put("to", new Object[]{Map.of("email", toEmail)});
        payload.put("subject", subject);
        payload.put("htmlContent", html);

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(payload),
                MediaType.parse("application/json; charset=utf-8"));

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

    private CompletableFuture<Boolean> send(String email, String subject,
                                            String template, Map<String, Object> model) {
        boolean result = sendHtml(email, subject, template, model);
        if (result) log.info("Email sent: {} -> {}", subject, email);
        else log.error("Email failed: {} -> {}", subject, email);
        return CompletableFuture.completedFuture(result);
    }

    private String formatPence(int pence) {
        return String.format("£%.2f", pence / 100.0);
    }
}