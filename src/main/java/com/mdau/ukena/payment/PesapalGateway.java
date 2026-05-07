package com.mdau.ukena.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdau.ukena.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class PesapalGateway implements PaymentGateway {

    private final ObjectMapper objectMapper;

    @Value("${ukena.pesapal.consumer-key:}")
    private String consumerKey;

    @Value("${ukena.pesapal.consumer-secret:}")
    private String consumerSecret;

    @Value("${ukena.pesapal.base-url:https://cybqa.pesapal.com/pesapalv3}")
    private String baseUrl;

    @Value("${ukena.pesapal.ipn-url:}")
    private String ipnUrl;

    @Value("${ukena.pesapal.redirect-url:http://localhost:5173/checkout/confirmation}")
    private String redirectUrl;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;
    private volatile String cachedIpnId;

    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public PaymentInitResult initiatePayment(PaymentInitRequest req) {
        if (!isConfigured()) {
            throw ApiException.badRequest(
                    "PesaPal credentials not yet configured. " +
                    "Set PESAPAL_CONSUMER_KEY and PESAPAL_CONSUMER_SECRET.");
        }
        try {
            String token = getAccessToken();
            String ipnId = getOrRegisterIpn(token);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("id",               req.displayId());
            body.put("currency",         req.currency());
            body.put("amount",           req.amountPence() / 100.0);
            body.put("description",      req.description());
            body.put("callback_url",     redirectUrl + "?ref=" + req.displayId());
            body.put("notification_id",  ipnId);
            body.put("cancellation_url", redirectUrl + "?cancelled=true");

            ObjectNode billing = objectMapper.createObjectNode();
            billing.put("email_address", req.buyerEmail());
            billing.put("first_name",    splitFirst(req.buyerName()));
            billing.put("last_name",     splitLast(req.buyerName()));
            body.set("billing_address", billing);

            JsonNode resp      = post("/api/Transactions/SubmitOrderRequest", body, token);
            String trackingId  = resp.path("order_tracking_id").asText();
            String paymentLink = resp.path("redirect_url").asText();

            if (trackingId.isBlank() || paymentLink.isBlank()) {
                log.error("PesaPal submit order unexpected response: {}", resp);
                throw ApiException.internalError("Payment initiation failed");
            }
            return new PaymentInitResult(paymentLink, trackingId);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("PesaPal initiatePayment error", e);
            throw ApiException.internalError("Payment gateway error: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        return true;
    }

    @Override
    public boolean verifyPayment(String gatewayRef) {
        if (!isConfigured()) return false;
        try {
            String token  = getAccessToken();
            JsonNode resp = get(
                "/api/Transactions/GetTransactionStatus?orderTrackingId=" + gatewayRef,
                token);
            String status = resp.path("payment_status_description").asText();
            log.info("PesaPal payment status for {}: {}", gatewayRef, status);
            return "Completed".equalsIgnoreCase(status);
        } catch (Exception e) {
            log.error("PesaPal verifyPayment error for ref {}", gatewayRef, e);
            return false;
        }
    }

    @Override
    public PayoutResult initiateTransfer(PayoutRequest req) {
        log.warn("PesaPal B2C not yet configured - queued manually: creator={} pence={}",
                req.creatorId(), req.amountPence());
        return new PayoutResult(false, null,
                "Payout queued - B2C transfer will be processed manually");
    }

    private boolean isConfigured() {
        return consumerKey != null && !consumerKey.isBlank() &&
               consumerSecret != null && !consumerSecret.isBlank();
    }

    private synchronized String getAccessToken() throws Exception {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("consumer_key",    consumerKey);
        body.put("consumer_secret", consumerSecret);
        JsonNode resp = postNoAuth("/api/Auth/RequestToken", body);
        String token  = resp.path("token").asText();
        if (token.isBlank()) throw new IOException("PesaPal auth failed: " + resp);
        cachedToken = token;
        tokenExpiry = Instant.now().plusSeconds(270);
        return token;
    }

    private synchronized String getOrRegisterIpn(String token) throws Exception {
        if (cachedIpnId != null) return cachedIpnId;
        ObjectNode body = objectMapper.createObjectNode();
        body.put("url", ipnUrl);
        body.put("ipn_notification_type", "GET");
        JsonNode resp = post("/api/URLSetup/RegisterIPN", body, token);
        String ipnId  = resp.path("ipn_id").asText();
        if (ipnId.isBlank()) throw new IOException("PesaPal IPN registration failed: " + resp);
        cachedIpnId = ipnId;
        log.info("PesaPal IPN registered: id={} url={}", ipnId, ipnUrl);
        return ipnId;
    }

    private JsonNode post(String path, ObjectNode body, String token) throws Exception {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        return execute(req);
    }

    private JsonNode postNoAuth(String path, ObjectNode body) throws Exception {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        return execute(req);
    }

    private JsonNode get(String path, String token) throws Exception {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/json")
                .get().build();
        return execute(req);
    }

    private JsonNode execute(Request req) throws Exception {
        try (Response resp = http.newCall(req).execute()) {
            String bodyStr = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                log.error("PesaPal HTTP {} for {}: {}", resp.code(), req.url(), bodyStr);
                throw new IOException("PesaPal API error " + resp.code());
            }
            return objectMapper.readTree(bodyStr);
        }
    }

    private String splitFirst(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Buyer";
        int idx = fullName.indexOf(' ');
        return idx < 0 ? fullName : fullName.substring(0, idx);
    }

    private String splitLast(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        int idx = fullName.indexOf(' ');
        return idx < 0 ? "" : fullName.substring(idx + 1);
    }
}