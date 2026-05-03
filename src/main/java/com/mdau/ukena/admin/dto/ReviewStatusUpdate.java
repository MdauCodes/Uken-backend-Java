package com.mdau.ukena.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewStatusUpdate(
        @NotBlank String status
) {}