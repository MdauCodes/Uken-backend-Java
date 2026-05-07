package com.mdau.ukena.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.payment.FlutterwaveGateway;
import com.mdau.ukena.payment.PaymentGateway;
import com.mdau.ukena.payment.PesapalGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the active PaymentGateway implementation at startup.
 * Switch via Railway variable: PAYMENT_PROVIDER=pesapal (default) | flutterwave
 */
@Slf4j
@Configuration
public class PaymentGatewayConfig {

    @Value("${ukena.payment.provider:pesapal}")
    private String provider;

    @Bean
    public PaymentGateway paymentGateway(ObjectMapper objectMapper) {
        return switch (provider.toLowerCase().trim()) {
            case "flutterwave" -> {
                log.info("Payment gateway: Flutterwave");
                yield new FlutterwaveGateway(objectMapper);
            }
            default -> {
                log.info("Payment gateway: PesaPal (provider={})", provider);
                yield new PesapalGateway(objectMapper);
            }
        };
    }
}
