package com.mdau.ukena.email.dto;

import java.util.UUID;

public record MailboxAccessDto(
        UUID userId,
        String email,
        String fullName) {}
