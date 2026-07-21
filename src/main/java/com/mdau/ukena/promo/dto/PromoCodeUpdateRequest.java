package com.mdau.ukena.promo.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record PromoCodeUpdateRequest(
        @Min(1) @Max(90) int percentOff,
        @Size(max = 200) String description,
        boolean active,
        Instant expiresAt,
        @Min(1) Integer maxRedemptions
) {}
