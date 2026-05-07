package com.mdau.ukena.payment;

public record PaymentInitResult(
        String paymentLink,
        String gatewayRef
) {}