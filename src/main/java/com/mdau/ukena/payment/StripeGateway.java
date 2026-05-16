package com.mdau.ukena.payment;

import com.mdau.ukena.common.ApiException;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StripeGateway implements PaymentGateway {

    private final String secretKey;
    private final String webhookSecret;
    private final String callbackUrl;
    private final String cancelUrl;

    public StripeGateway(String secretKey, String webhookSecret,
                         String callbackUrl, String cancelUrl) {
        this.secretKey     = secretKey;
        this.webhookSecret = webhookSecret;
        this.callbackUrl   = callbackUrl;
        this.cancelUrl     = cancelUrl;
        Stripe.apiKey      = secretKey;
    }

    @Override
    public PaymentInitResult initiatePayment(PaymentInitRequest req) {
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(callbackUrl
                            + "?ref=" + req.displayId()
                            + "&email=" + java.net.URLEncoder.encode(req.buyerEmail(), java.nio.charset.StandardCharsets.UTF_8)
                            + "&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl + "?cancelled=true&ref=" + req.displayId())
                    .setCustomerEmail(req.buyerEmail())
                    .putMetadata("display_id",  req.displayId())
                    .putMetadata("buyer_name",  req.buyerName())
                    .setClientReferenceId(req.displayId())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("gbp")
                                    .setUnitAmount((long) req.amountPence())
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(req.description())
                                            .build())
                                    .build())
                            .build())
                    .build();

            Session session = Session.create(params);
            log.info("Stripe session created: id={} url={}", session.getId(), session.getUrl());
            return new PaymentInitResult(session.getUrl(), session.getId());

        } catch (Exception e) {
            log.error("Stripe initiatePayment error", e);
            throw ApiException.internalError("Payment gateway error: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (payload == null || signature == null || webhookSecret == null || webhookSecret.isBlank())
            return false;
        try {
            Webhook.constructEvent(payload, signature, webhookSecret);
            return true;
        } catch (Exception e) {
            log.warn("Stripe webhook signature invalid: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyPayment(String sessionId) {
        try {
            Session session = Session.retrieve(sessionId);
            String status = session.getPaymentStatus();
            log.info("Stripe verify sessionId={} status={}", sessionId, status);
            return "paid".equalsIgnoreCase(status);
        } catch (Exception e) {
            log.error("Stripe verifyPayment error for sessionId {}", sessionId, e);
            return false;
        }
    }

    @Override
    public PayoutResult initiateTransfer(PayoutRequest req) {
        log.info("Stripe payout queued for creator={} — Stripe Connect not yet configured", req.creatorId());
        return new PayoutResult(true, null, "Payout via Stripe Connect coming soon");
    }
}