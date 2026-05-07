package com.mdau.ukena.review;

import java.time.Instant;

public record PublicReviewDto(
        String  id,
        String  buyerName,
        int     rating,
        String  body,
        Instant submittedAt
) {}