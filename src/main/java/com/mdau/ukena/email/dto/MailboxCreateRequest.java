package com.mdau.ukena.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MailboxCreateRequest(
        @NotBlank @Email String address,
        @NotBlank String displayName,
        String imapHost,
        Integer imapPort,
        String smtpHost,
        Integer smtpPort,
        /** Defaults to `address` when blank — Namecheap Private Email logs in with the full address. */
        String username,
        @NotBlank String password) {}
