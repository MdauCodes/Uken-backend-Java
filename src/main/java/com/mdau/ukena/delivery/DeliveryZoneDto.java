package com.mdau.ukena.delivery;

import java.util.UUID;

public record DeliveryZoneDto(
        UUID   id,
        String name,
        String country,
        int    shippingPence,
        boolean active,
        int    sortOrder
) {}