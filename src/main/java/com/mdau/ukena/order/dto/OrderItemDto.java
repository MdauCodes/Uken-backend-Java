package com.mdau.ukena.order.dto;

public record OrderItemDto(
        String productId,
        String name,
        int quantity,
        int pricePence,
        String image,
        OrderItemCreatorDto creator
) {}