package com.mdau.ukena.email.dto;

import jakarta.validation.constraints.NotBlank;

public record MailboxUpdateRequest(
        @NotBlank String displayName,
        String imapHost,
        Integer imapPort,
        String smtpHost,
        Integer smtpPort,
        String username,
        /** Blank/null keeps the existing password — only rotate it when a value is sent. */
        String password,
        boolean active) {}
