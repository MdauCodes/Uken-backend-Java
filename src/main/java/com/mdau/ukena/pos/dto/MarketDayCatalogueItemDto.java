package com.mdau.ukena.pos.dto;

public record MarketDayCatalogueItemDto(
        java.util.UUID id,
        String productId,
        String productName,
        String heroImage,
        int pricePence
) {}
