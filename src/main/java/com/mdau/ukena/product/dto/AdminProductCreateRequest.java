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
        /** Required when availableOnline is true (shipping needs a weight); optional for
         *  market-only pieces that will only ever be handed over in person. Enforced in
         *  ProductService, not here, since the requirement depends on another field. */
        @Min(1) @Max(50_000) Integer weightGrams,
        /** Genuine "was" price for a real markdown — must exceed pricePence. Optional, rarely set at creation. */
        @Min(1) @Max(10_000_000) Integer compareAtPricePence,
        /** Null = don't track stock for this piece (unlimited). Positive = starting count. */
        @Min(0) Integer unitsAvailable,
        /** False = market-stall only — hidden from the public shop/catalogue/search, still
         *  fully sellable via POS. Null on the wire is treated as true (list online) — the
         *  safe default so a missing field never silently hides a product. */
        Boolean availableOnline
) {}