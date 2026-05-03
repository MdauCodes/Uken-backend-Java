package com.mdau.ukena.product.dto;

public record ProductImageDto(
        Long id,
        String url,
        boolean isPrimary,
        int displayOrder,
        String altText
) {}