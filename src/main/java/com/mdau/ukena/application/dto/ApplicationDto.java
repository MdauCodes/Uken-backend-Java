package com.mdau.ukena.application.dto;

import java.time.Instant;

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
        String notes
) {}