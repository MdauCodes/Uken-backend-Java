package com.mdau.ukena.shipping.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShippingSettingsUpdateRequest(
        @NotNull @Min(0) Integer ratePencePerKg,
        @NotNull @Min(1) Integer fallbackWeightGrams) {}
