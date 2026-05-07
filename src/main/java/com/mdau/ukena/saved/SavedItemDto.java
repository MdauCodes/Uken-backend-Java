package com.mdau.ukena.saved;

import java.time.Instant;

public record SavedItemDto(
        String  id,
        String  productId,
        String  productName,
        String  primaryImageUrl,
        int     pricePence,
        String  craftType,
        String  creatorName,
        Instant savedAt
) {}