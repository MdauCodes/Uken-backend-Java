package com.mdau.ukena.product.dto;

import jakarta.validation.constraints.NotBlank;

public record ProductStatusUpdateRequest(
        @NotBlank String status
) {}