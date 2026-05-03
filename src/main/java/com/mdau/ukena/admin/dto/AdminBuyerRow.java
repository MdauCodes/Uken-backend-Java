package com.mdau.ukena.admin.dto;

import java.time.Instant;

public record AdminBuyerRow(
        String id,
        String fullName,
        String email,
        boolean suspended
) {}