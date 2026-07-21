package com.mdau.ukena.email.dto;

import java.time.Instant;
import java.util.UUID;

/** Admin-only mailbox view for the settings screen — still never carries the password. */
public record MailboxAdminDto(
        UUID id,
        String address,
        String displayName,
        String imapHost,
        int imapPort,
        String smtpHost,
        int smtpPort,
        String username,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
