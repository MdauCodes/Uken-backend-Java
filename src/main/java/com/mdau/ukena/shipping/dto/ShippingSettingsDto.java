package com.mdau.ukena.shipping.dto;

import java.time.Instant;

/**
 * deliveryFeePence / freeDeliveryOverPence price what the buyer pays (flat UK fee).
 * ratePencePerKg / fallbackWeightGrams are kept for older clients and for weight records;
 * they no longer price orders.
 */
public record ShippingSettingsDto(
        int deliveryFeePence,
        int freeDeliveryOverPence,
        int ratePencePerKg,
        int fallbackWeightGrams,
        Instant updatedAt) {}
