package com.mdau.ukena.product.dto;

import java.util.List;

public record ProductDto(
        String id,
        String name,
        int pricePence,
        /** Genuine "was" price for a real markdown, set by hand — null when there's no active markdown. */
        Integer compareAtPricePence,
        String heroImage,
        List<ProductImageDto> images,
        String pieceStory,
        List<String> materials,
        String dimensions,
        String care,
        Integer weightGrams,
        String status,
        ProductCreatorDto creator,
        boolean isUkenaOwned,
        /** Null when the product has no published reviews yet. */
        Double averageRating,
        int reviewCount,
        /** Units sold across confirmed (paid+) orders — real data, never fabricated. */
        long unitsSold
) {}