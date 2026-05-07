package com.mdau.ukena.payment;

public record PayoutResult(
        boolean success,
        String  gatewayRef,
        String  message
) {}
