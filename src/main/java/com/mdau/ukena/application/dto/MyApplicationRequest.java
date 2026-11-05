package com.mdau.ukena.application.dto;

import jakarta.validation.constraints.*;

/** A signed-in buyer applying to sell with the account they already have. */
public record MyApplicationRequest(
        @NotBlank @Size(min = 7, max = 40) String phone,
        @Pattern(regexp = "INDIVIDUAL|BUSINESS") String applicantType,
        @NotBlank @Size(max = 120) String region,
        @NotBlank @Size(max = 120) String craft,
        @Min(0) @Max(80) int yearsOfPractice,
        @NotBlank @Size(min = 20, max = 4000) String story,
        @Size(max = 160) String tradingName,
        @Size(max = 60) String businessType,
        @Size(max = 60) String registrationNumber,
        @Min(1) @Max(100000) Integer memberCount,
        @Size(max = 120) String contactPerson
) {}
