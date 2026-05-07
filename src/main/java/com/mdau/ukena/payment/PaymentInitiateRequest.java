package com.mdau.ukena.payment;

import jakarta.validation.constraints.NotBlank;

public record PaymentInitiateRequest(@NotBlank String orderId) {}
