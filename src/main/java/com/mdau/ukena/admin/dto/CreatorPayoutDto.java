package com.mdau.ukena.admin.dto;

import java.time.Instant;

public record CreatorPayoutDto(
        String creatorId,
        String fullName,
        String region,
        String craft,
        int pendingPence,
        int paidThisMonthPence,
        int totalLifetimePence,
        Instant lastPaidAt
) {}