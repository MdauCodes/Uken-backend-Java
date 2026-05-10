package com.mdau.ukena.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentInitiateRequest(
        @NotBlank String orderId,
        @NotNull UUID buyerId  // TODO: remove when auth is re-enabled
) {}