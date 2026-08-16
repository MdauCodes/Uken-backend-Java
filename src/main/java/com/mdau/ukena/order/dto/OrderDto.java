package com.mdau.ukena.order.dto;

import java.time.Instant;
import java.util.List;

public record OrderDto(
        String displayId,
        Instant createdAt,
        String status,
        /** "ONLINE" or "POS" — never null even for pre-POS orders (see OrderService.toDto). */
        String channel,
        int productsTotalPence,
        int shippingPence,
        String promoCode,
        int discountPence,
        int totalPence,
        OrderBuyerDto buyer,
        List<OrderItemDto> items,
        DeliveryDto delivery
) {}