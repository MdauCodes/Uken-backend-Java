package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.*;

public record OrderItemRequest(
        @NotBlank String productId,
        @Min(1) @Max(99) int quantity
) {}