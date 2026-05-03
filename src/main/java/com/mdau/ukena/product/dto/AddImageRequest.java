package com.mdau.ukena.product.dto;

import jakarta.validation.constraints.NotBlank;

public record AddImageRequest(
        @NotBlank String url,
        String cloudinaryId,
        boolean isPrimary,
        int displayOrder,
        String altText
) {}