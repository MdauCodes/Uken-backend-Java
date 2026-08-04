package com.mdau.ukena.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * JSON payload part of the multipart send/reply/forward/draft requests —
 * attachments travel as separate file parts (see {@code EmailController}).
 */
public record SendEmailRequest(
        @NotEmpty List<@NotBlank String> to,
        List<String> cc,
        List<String> bcc,
        @NotBlank String subject,
        @NotBlank String htmlBody,
        /** Overrides the mailbox's own display name as the visible "From" sender
         *  for this one send — e.g. picked up from the chosen template. Null = use
         *  the mailbox's own display name. */
        String senderName) {}
