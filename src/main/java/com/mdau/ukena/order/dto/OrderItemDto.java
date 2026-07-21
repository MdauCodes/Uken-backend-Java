package com.mdau.ukena.order.dto;

public record OrderItemDto(
        String productId,
        String name,
        int quantity,
        int pricePence,
        /** Null for orders placed before weight-based shipping shipped. */
        Integer weightGrams,
        String image,
        OrderItemCreatorDto creator
) {}