package com.mdau.ukena.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.payment.PaymentGateway;
import com.mdau.ukena.payment.PaystackGateway;
import com.mdau.ukena.payment.StripeGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class PaymentGatewayConfig {

    @Value("${ukena.payment.provider:stripe}")
    private String provider;

    @Value("${ukena.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${ukena.stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @Value("${ukena.stripe.callback-url:http://localhost:5173/checkout/confirmation}")
    private String stripeCallbackUrl;

    @Value("${ukena.stripe.cancel-url:http://localhost:5173/checkout}")
    private String stripeCancelUrl;

    @Value("${ukena.paystack.secret-key:}")
    private String paystackSecretKey;

    @Value("${ukena.paystack.subaccount-code:}")
    private String paystackSubaccountCode;

    @Value("${ukena.paystack.callback-url:http://localhost:5173/checkout/confirmation}")
    private String paystackCallbackUrl;

    @Value("${ukena.paystack.gbp-to-kes-rate:165.0}")
    private double gbpToKesRate;

    @Bean
    public PaymentGateway paymentGateway(ObjectMapper objectMapper) {
        return switch (provider.toLowerCase().trim()) {
            case "paystack" -> {
                log.info("Payment gateway: Paystack (rate=1 GBP = {} KES)", gbpToKesRate);
                yield new PaystackGateway(objectMapper, paystackSecretKey,
                        paystackSubaccountCode, paystackCallbackUrl, gbpToKesRate);
            }
            default -> {
                log.info("Payment gateway: Stripe (GBP)");
                yield new StripeGateway(stripeSecretKey, stripeWebhookSecret,
                        stripeCallbackUrl, stripeCancelUrl);
            }
        };
    }
}