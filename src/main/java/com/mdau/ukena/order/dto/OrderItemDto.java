package com.mdau.ukena.order.dto;

import com.mdau.ukena.product.dto.ProductCategoryDto;

public record OrderItemDto(
        String productId,
        String name,
        int quantity,
        int pricePence,
        /** Null for orders placed before weight-based shipping shipped. */
        Integer weightGrams,
        String image,
        OrderItemCreatorDto creator,
        /** Null when the product has been deleted, or hadn't been categorized
         *  yet at order time — never fabricated. */
        ProductCategoryDto category
) {}