package com.mdau.ukena.pos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCatalogueItemRequest(
        @NotNull @Min(0) Integer pricePence
) {}
