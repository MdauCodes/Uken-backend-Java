package com.mdau.ukena.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConvertToCreatorRequest(
        @NotBlank @Size(max = 120) String craft,
        @NotBlank @Size(max = 120) String region,
        @NotBlank @Size(max = 4000) String hook
) {}
