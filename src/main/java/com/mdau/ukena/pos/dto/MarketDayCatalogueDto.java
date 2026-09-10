package com.mdau.ukena.pos.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarketDayCatalogueDto(
        UUID id,
        String name,
        Instant createdAt,
        List<MarketDayCatalogueItemDto> items
) {}
