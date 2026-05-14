package com.mdau.ukena.order.dto;

import java.time.Instant;
import java.util.List;

public record OrderDto(
        String displayId,
        Instant createdAt,
        String status,
        int productsTotalPence,
        int shippingPence,
        int totalPence,
        OrderBuyerDto buyer,
        List<OrderItemDto> items,
        DeliveryDto delivery
) {}