package com.mdau.ukena.email.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailTemplateRequest(
        @NotBlank String name,
        @NotBlank String htmlContent,
        String thumbnailUrl,
        String category) {}
