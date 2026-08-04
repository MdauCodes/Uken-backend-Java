package com.mdau.ukena.email.dto;

import java.time.Instant;
import java.util.UUID;

public record EmailTemplateDto(
        UUID id,
        String name,
        /** Editable body only — see {@link com.mdau.ukena.email.EmailTemplate#getHtmlContent()}. */
        String htmlContent,
        String thumbnailUrl,
        /** "PITCH", "REPLY", "CREATOR", "GENERAL", or null for older templates. */
        String category,
        /** Null = default UKEN logo. */
        String logoUrl,
        /** Hex color, null = default brand clay. */
        String accentColor,
        /** Overrides the mailbox's display name when sending with this template. */
        String senderName,
        /** Call-to-action button rendered after the body — both null or both set. */
        String ctaLabel,
        String ctaUrl,
        Instant createdAt,
        Instant updatedAt) {}
