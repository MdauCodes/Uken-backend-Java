package com.mdau.ukena.email.dto;

import java.time.Instant;
import java.util.UUID;

public record EmailTemplateDto(
        UUID id,
        String name,
        String htmlContent,
        String thumbnailUrl,
        Instant createdAt,
        Instant updatedAt) {}
