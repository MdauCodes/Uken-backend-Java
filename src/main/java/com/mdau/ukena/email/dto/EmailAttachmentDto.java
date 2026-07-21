package com.mdau.ukena.email.dto;

/** {@code partId} is the position of this body part within the MIME structure — passed
 *  back verbatim to the attachment-download endpoint to re-locate it. */
public record EmailAttachmentDto(
        String partId,
        String filename,
        String contentType,
        long size) {}
