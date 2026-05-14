package com.mdau.ukena.delivery;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record DeliveryZoneRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String country,
        @Min(0) int shippingPence,
        boolean active,
        int sortOrder
) {}