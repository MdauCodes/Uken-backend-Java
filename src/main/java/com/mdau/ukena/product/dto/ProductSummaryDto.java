package com.mdau.ukena.product.dto;

public record ProductSummaryDto(
        String id,
        String name,
        int    pricePence,
        String heroImage,
        String creatorId,
        String creatorName,
        String craft
) {}