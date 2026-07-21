package com.mdau.ukena.product.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record AdminProductCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Min(1) @Max(10_000_000) int pricePence,
        @NotBlank String heroImage,
        @NotBlank @Size(max = 4000) String pieceStory,
        @Size(max = 20) List<String> materials,
        @Size(max = 200) String dimensions,
        String care,
        @NotNull @Min(1) @Max(50_000) Integer weightGrams,
        /** Genuine "was" price for a real markdown — must exceed pricePence. Optional, rarely set at creation. */
        @Min(1) @Max(10_000_000) Integer compareAtPricePence
) {}