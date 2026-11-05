package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** How a buyer-reported problem was settled: refund, send a replacement, or resolved without either. */
public record ResolveIssueRequest(
        @NotBlank @Pattern(regexp = "REFUND|RESEND|RESOLVED") String action,
        @Size(max = 500) String note) {}
