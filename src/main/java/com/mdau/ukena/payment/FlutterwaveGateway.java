package com.mdau.ukena.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdau.ukena.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class FlutterwaveGateway implements PaymentGateway {

    private final ObjectMapper objectMapper;

    @Value("${ukena.flutterwave.secret-key:}")
    private String secretKey;

    @Value("${ukena.flutterwave.webhook-secret:}")
    private String webhookSecret;

    @Value("${ukena.flutterwave.base-url:https://api.flutterwave.com/v3}")
    private String baseUrl;

    @Value("${ukena.flutterwave.redirect-url:http://localhost:5173/checkout/confirmation}")
    private String redirectUrl;

    @Value("${ukena.flutterwave.currency:GBP}")
    private String currency;

    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public PaymentInitResult initiatePayment(PaymentInitRequest req) {
        if (!isConfigured()) {
            throw ApiException.badRequest(
                    "Flutterwave credentials not configured. " +
                    "Set FLUTTERWAVE_SECRET_KEY or switch PAYMENT_PROVIDER=pesapal.");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("tx_ref",         req.displayId() + "-" + System.currentTimeMillis());
            body.put("amount",         req.amountPence() / 100.0);
            body.put("currency",       currency);
            body.put("redirect_url",   redirectUrl);
            body.put("customer_email", req.buyerEmail());
            body.put("customer_name",  req.buyerName());
            body.put("meta",           req.orderId().toString());

            JsonNode resp   = post("/payments", body);
            String   status = resp.path("status").asText();
            if (!"success".equalsIgnoreCase(status)) {
                throw ApiException.internalError("Flutterwave initiation failed: "
                        + resp.path("message").asText());
            }
            String link   = resp.path("data").path("link").asText();
            String flwRef = resp.path("data").path("flw_ref").asText();
            return new PaymentInitResult(link, flwRef);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Flutterwave initiatePayment error", e);
            throw ApiException.internalError("Payment gateway error: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return computed.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Flutterwave signature verification error", e);
            return false;
        }
    }

    @Override
    public boolean verifyPayment(String gatewayRef) {
        if (!isConfigured()) return false;
        try {
            JsonNode resp  = get("/transactions/verify_by_reference?tx_ref=" + gatewayRef);
            String   status = resp.path("data").path("status").asText();
            return "successful".equalsIgnoreCase(status);
        } catch (Exception e) {
            log.error("Flutterwave verifyPayment error for ref {}", gatewayRef, e);
            return false;
        }
    }

    @Override
    public PayoutResult initiateTransfer(PayoutRequest req) {
        if (!isConfigured()) return new PayoutResult(false, null, "Flutterwave not configured");
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("account_bank",     "MPS");
            body.put("account_number",   req.accountNumber());
            body.put("amount",           req.amountPence() / 100.0);
            body.put("narration",        req.narration());
            body.put("currency",         "KES");
            body.put("reference",        "UKENA-PAY-" + req.creatorId()
                                         + "-" + System.currentTimeMillis());
            body.put("beneficiary_name", req.accountName());

            JsonNode resp   = post("/transfers", body);
            String   status = resp.path("status").asText();
            String   ref    = resp.path("data").path("reference").asText();
            return new PayoutResult("success".equalsIgnoreCase(status), ref,
                    resp.path("message").asText());
        } catch (Exception e) {
            log.error("Flutterwave transfer error for creator {}", req.creatorId(), e);
            return new PayoutResult(false, null, e.getMessage());
        }
    }

    private boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    private JsonNode post(String path, ObjectNode body) throws Exception {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", "Bearer " + secretKey)
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        return execute(req);
    }

    private JsonNode get(String path) throws Exception {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", "Bearer " + secretKey)
                .addHeader("Accept", "application/json")
                .get().build();
        return execute(req);
    }

    private JsonNode execute(Request req) throws Exception {
        try (Response resp = http.newCall(req).execute()) {
            String bodyStr = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                log.error("Flutterwave HTTP {} for {}: {}", resp.code(), req.url(), bodyStr);
                throw new IOException("Flutterwave API error " + resp.code());
            }
            return objectMapper.readTree(bodyStr);
        }
    }
}