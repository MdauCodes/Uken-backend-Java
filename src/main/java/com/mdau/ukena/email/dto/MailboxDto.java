package com.mdau.ukena.email.dto;

import java.util.UUID;

/** Safe mailbox view — never carries credentials. */
public record MailboxDto(
        UUID id,
        String address,
        String displayName,
        boolean active,
        /** Null when this mailbox has no signature configured — compose should
         *  append no signature block in that case. */
        String signatureName,
        String signatureTitle,
        String signaturePhone) {}
