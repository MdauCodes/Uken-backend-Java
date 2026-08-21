package com.mdau.ukena.category;

import java.util.List;

public record CategoryDto(
        String id,
        String name,
        String colorToken,
        int sortOrder,
        boolean active,
        List<String> craftValues,
        String thumbnailImage
) {}
