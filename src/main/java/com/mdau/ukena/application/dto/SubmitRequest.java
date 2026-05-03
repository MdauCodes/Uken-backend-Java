package com.mdau.ukena.application.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record SubmitRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String region,
        @NotBlank @Size(max = 120) String craft,
        @Min(0) @Max(80) int yearsOfPractice,
        @NotBlank @Size(max = 4000) String story,
        @NotEmpty @Size(min = 1, max = 5) List<@NotBlank String> photos
) {}