package com.mdau.ukena.application.dto;

import jakarta.validation.constraints.*;

/**
 * Account-first maker application: creates the applicant's account and their
 * application together, so they can sign in to their studio straight away and
 * follow the review there. Business applicants also send the business fields.
 */
public record AccountApplicationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(min = 2, max = 120) String fullName,
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
