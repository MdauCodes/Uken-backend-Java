package com.mdau.ukena.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record PayoutRequestDto(
        @NotBlank String accountNumber,
        @NotBlank String accountName
) {}