package com.mdau.ukena.pos.dto;

import java.time.Instant;
import java.util.UUID;

/** List-view row — no items, so listing catalogues doesn't hydrate every one's
 *  full item collection. See MarketDayCatalogueDto for the full detail shape. */
public record MarketDayCatalogueSummaryDto(
        UUID id,
        String name,
        Instant createdAt,
        int itemCount
) {}
