package com.mdau.ukena.shipping.dto;

import jakarta.validation.constraints.Min;

/** Every field is optional — only the ones sent are changed. */
public record ShippingSettingsUpdateRequest(
        @Min(0) Integer deliveryFeePence,
        @Min(0) Integer freeDeliveryOverPence,
        @Min(0) Integer ratePencePerKg,
        @Min(1) Integer fallbackWeightGrams) {}
