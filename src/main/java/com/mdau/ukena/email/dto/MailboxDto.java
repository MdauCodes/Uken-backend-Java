package com.mdau.ukena.email.dto;

import java.util.UUID;

/** Safe mailbox view — never carries credentials. */
public record MailboxDto(
        UUID id,
        String address,
        String displayName,
        boolean active) {}
