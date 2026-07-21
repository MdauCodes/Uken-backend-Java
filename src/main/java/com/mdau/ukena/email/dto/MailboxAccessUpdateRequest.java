package com.mdau.ukena.email.dto;

import java.util.List;
import java.util.UUID;

/** Replaces the full set of staff users granted access to a mailbox. */
public record MailboxAccessUpdateRequest(List<UUID> userIds) {}
