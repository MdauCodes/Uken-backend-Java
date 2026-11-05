package com.mdau.ukena.application.dto;

public record ApplicationDto(
        String id,
        String fullName,
        String email,
        String region,
        String craft,
        int yearsOfPractice,
        String story,
        String submittedAt,
        String status,
        String portrait,
        String workSample,
        String notes,
        /** INDIVIDUAL or BUSINESS. */
        String applicantType,
        String phone,
        String tradingName,
        String businessType,
        String registrationNumber,
        Integer memberCount,
        String contactPerson,
        /** True when the applicant already has a UKEN account (account-first or signed-in buyer). */
        boolean hasAccount
) {}
