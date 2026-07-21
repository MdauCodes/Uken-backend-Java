package com.mdau.ukena.email.dto;

import java.time.Instant;
import java.util.List;

public record EmailMessageDetailDto(
        long uid,
        String subject,
        String fromAddress,
        String fromName,
        List<String> to,
        List<String> cc,
        Instant date,
        String htmlBody,
        String textBody,
        boolean seen,
        List<EmailAttachmentDto> attachments,
        /** RFC 822 Message-ID header — needed to build In-Reply-To/References when replying. */
        String messageIdHeader) {}
