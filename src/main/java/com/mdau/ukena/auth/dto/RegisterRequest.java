package com.mdau.ukena.auth.dto;

import jakarta.validation.constraints.*;

/**
 * Buyer sign-up. accountType BUSINESS requires companyName; the other business
 * fields are optional. Older clients that send only email/password/fullName
 * still create an individual account.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @Pattern(regexp = "INDIVIDUAL|BUSINESS") String accountType,
        @Size(max = 160) String companyName,
        @Size(max = 120) String jobTitle,
        @Size(max = 40) String vatNumber
) {}
