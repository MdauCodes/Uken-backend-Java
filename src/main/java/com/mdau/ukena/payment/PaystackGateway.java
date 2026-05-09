package com.mdau.ukena.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class PaystackGateway implements PaymentGateway {

    private final ObjectMapper objectMapper;
    private final String secretKey;
    private final String subaccountCode;
    private final String callbackUrl;
    private final double gbpToKesRate;

    private static final String BASE_URL = "https://api.paystack.co";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public PaymentInitResult initiatePayment(PaymentInitRequest req) {
        try {
            // Convert GBP pence to KES kobo (KES * 100)
            long amountKobo = Math.round((req.amountPence() / 100.0) * gbpToKesRate * 100);

            Map<String, Object> body = new HashMap<>();
            body.put("email",        req.buyerEmail());
            body.put("amount",       amountKobo);
            body.put("currency",     "KES");
            body.put("reference",    req.displayId());
            body.put("callback_url", callbackUrl + "?ref=" + req.displayId());
            body.put("metadata", Map.of(
                    "display_id",   req.displayId(),
                    "buyer_name",   req.buyerName(),
                    "description",  req.description()
            ));

            if (subaccountCode != null && !subaccountCode.isBlank()) {
                body.put("subaccount", subaccountCode);
                body.put("bearer",     "account"); // platform bears Paystack fees
            }

            JsonNode resp = post("/transaction/initialize", body);
            String authUrl   = resp.path("data").path("authorization_url").asText();
            String reference = resp.path("data").path("reference").asText();

            if (authUrl.isBlank()) {
                log.error("Paystack init unexpected response: {}", resp);
                throw ApiException.internalError("Payment initiation failed");
            }

            log.info("Paystack payment initialized: ref={} url={}", reference, authUrl);
            return new PaymentInitResult(authUrl, reference);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Paystack initiatePayment error", e);
            throw ApiException.internalError("Payment gateway error: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (payload == null || signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes("UTF-8"), "HmacSHA512"));
            byte[] hash = mac.doFinal(payload.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().equals(signature);
        } catch (Exception e) {
            log.error("Paystack signature verification error", e);
            return false;
        }
    }

    @Override
    public boolean verifyPayment(String reference) {
        try {
            JsonNode resp   = get("/transaction/verify/" + reference);
            String   status = resp.path("data").path("status").asText();
            log.info("Paystack verify ref={} status={}", reference, status);
            return "success".equalsIgnoreCase(status);
        } catch (Exception e) {
            log.error("Paystack verifyPayment error for ref {}", reference, e);
            return false;
        }
    }

    @Override
    public PayoutResult initiateTransfer(PayoutRequest req) {
        // Paystack split handles creator payouts automatically at charge time.
        // Manual transfers via Paystack Transfers API can be added later.
        log.info("Paystack payout queued for creator={} amountPence={}", 
                req.creatorId(), req.amountPence());
        return new PayoutResult(true, null,
                "Payout handled via Paystack split at charge time");
    }

    private JsonNode post(String path, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        Request req = new Request.Builder()
                .url(BASE_URL + path)
                .addHeader("Authorization", "Bearer " + secretKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();
        return execute(req);
    }

    private JsonNode get(String path) throws Exception {
        Request req = new Request.Builder()
                .url(BASE_URL + path)
                .addHeader("Authorization", "Bearer " + secretKey)
                .get()
                .build();
        return execute(req);
    }

    private JsonNode execute(Request req) throws Exception {
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                log.error("Paystack HTTP {} for {}: {}", resp.code(), req.url(), body);
                throw new IOException("Paystack API error " + resp.code() + ": " + body);
            }
            return objectMapper.readTree(body);
        }
    }
}