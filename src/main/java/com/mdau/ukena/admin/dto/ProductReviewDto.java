package com.mdau.ukena.admin.dto;

import java.time.Instant;

public record ProductReviewDto(
        String id,
        String productId,
        String productName,
        String buyerName,
        int rating,
        String body,
        Instant submittedAt,
        String status
) {}