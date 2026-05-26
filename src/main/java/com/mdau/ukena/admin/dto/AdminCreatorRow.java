package com.mdau.ukena.admin.dto;

import java.time.Instant;

public record AdminCreatorRow(
        String id,
        String firstName,
        String fullName,
        String craft,
        String region,
        String image,
        boolean suspended,
        Instant createdAt
) {}