package com.mdau.ukena.email.dto;

import java.time.Instant;

/** Inbox/folder list row — headers only, no body. */
public record EmailMessageDto(
        long uid,
        String subject,
        String fromAddress,
        String fromName,
        Instant date,
        String snippet,
        boolean seen,
        boolean hasAttachments) {}
