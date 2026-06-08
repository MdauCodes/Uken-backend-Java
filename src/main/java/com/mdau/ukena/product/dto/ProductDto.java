package com.mdau.ukena.product.dto;

import java.util.List;

public record ProductDto(
        String id,
        String name,
        int pricePence,
        String heroImage,
        List<ProductImageDto> images,
        String pieceStory,
        List<String> materials,
        String dimensions,
        String care,
        String status,
        ProductCreatorDto creator,
        boolean isUkenaOwned
) {}