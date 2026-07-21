package com.mdau.ukena.shipping.dto;

import java.time.Instant;

public record ShippingSettingsDto(
        int ratePencePerKg,
        int fallbackWeightGrams,
        Instant updatedAt) {}
