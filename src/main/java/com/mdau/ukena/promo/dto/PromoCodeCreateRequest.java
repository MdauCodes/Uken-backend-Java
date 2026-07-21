package com.mdau.ukena.promo.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record PromoCodeCreateRequest(
        @NotBlank @Size(max = 40) String code,
        @Min(1) @Max(90) int percentOff,
        @Size(max = 200) String description,
        Instant expiresAt,
        @Min(1) Integer maxRedemptions
) {}
