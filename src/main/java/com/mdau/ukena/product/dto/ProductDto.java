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
        /** Null = untracked/unlimited stock. Never fabricated — reflects the real counter. */
        Integer unitsAvailable,
        String status,
        ProductCreatorDto creator,
        boolean isUkenaOwned,
        /** False = market-stall only, hidden from the public shop/catalogue/search. */
        boolean availableOnline,
        /** Null when the product has no published reviews yet. */
        Double averageRating,
        int reviewCount,
        /** Units sold across confirmed (paid+) orders — real data, never fabricated. */
        long unitsSold,
        /** True if soft-deleted (deletedAt set). Only ever populated on the admin
         *  all-products listing — every other query already excludes these rows. */
        boolean deleted,
        /** When it was deleted — null unless {@code deleted} is true. */
        java.time.Instant deletedAt,
        /** True once the 7-day recovery window has expired and images were purged —
         *  restore is only possible while this is false. */
        boolean imagesPurged
) {}