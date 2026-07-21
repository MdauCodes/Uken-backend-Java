package com.mdau.ukena.promo.dto;

import java.time.Instant;
import java.util.UUID;

public record PromoCodeDto(
        UUID id,
        String code,
        int percentOff,
        String description,
        boolean active,
        Instant expiresAt,
        Integer maxRedemptions,
        int redemptionCount,
        Instant createdAt) {}
