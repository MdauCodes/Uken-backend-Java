package com.mdau.ukena.pos.dto;

import jakarta.validation.constraints.NotBlank;

/** pricePence is optional — omit it to snapshot the product's current live price. */
public record AddCatalogueItemRequest(
        @NotBlank String productId,
        Integer pricePence
) {}
