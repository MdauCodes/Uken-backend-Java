package com.mdau.ukena.payment;

/**
 * Payment gateway abstraction.
 * Implementations: StripeGateway (default), PaystackGateway.
 * Switch via: ukena.payment.provider = stripe | paystack — see PaymentGatewayConfig.
 * (application.yaml also carries unused pesapal/flutterwave config blocks —
 * no PaymentGateway implementation exists for either provider.)
 */
public interface PaymentGateway {

    /**
     * Initiate a buyer payment. Returns a redirect URL and a gateway reference.
     */
    PaymentInitResult initiatePayment(PaymentInitRequest request);

    /**
     * Verify a raw webhook payload is authentic (signature check).
     * Returns true if the signature is valid.
     */
    boolean verifyWebhookSignature(String payload, String signature);

    /**
     * Query the gateway to confirm a transaction is truly paid.
     * @param gatewayRef the reference returned by initiatePayment / webhook
     */
    boolean verifyPayment(String gatewayRef);

    /**
     * Send money to an artisan (payout / B2C transfer).
     * Amount is in pence; implementation converts to target currency.
     */
    PayoutResult initiateTransfer(PayoutRequest request);
}
