package com.mdau.ukena.product.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ProductUpdateRequest(
        @NotBlank @Size(max = 200) String name,
        @Min(1) @Max(10_000_000) int pricePence,
        String heroImage,
        @Size(max = 4000) String pieceStory,
        @Size(max = 20) List<String> materials,
        @Size(max = 200) String dimensions,
        String care,
        /** Optional here — lets a creator patch weight onto an existing product
         *  (the missing-weight backfill flow) without resubmitting every field. */
        @Min(1) @Max(50_000) Integer weightGrams,
        /** Genuine "was" price for a real markdown — must exceed pricePence. Null = leave
         *  unchanged; 0 = explicitly clear/end the markdown; positive = set/replace it. */
        @Min(0) @Max(10_000_000) Integer compareAtPricePence,
        /** Null here means "leave as-is" (not "untrack") — applyFields only overwrites
         *  unitsAvailable when a value is actually sent. Use 0 to mean genuinely zero. */
        @Min(0) Integer unitsAvailable
) {}