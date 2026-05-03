package com.mdau.ukena.order.dto;

import java.time.Instant;
import java.util.List;

public record OrderDto(
        String id,
        Instant createdAt,
        String status,
        int totalPence,
        OrderBuyerDto buyer,
        List<OrderItemDto> items,
        DeliveryDto delivery
) {}