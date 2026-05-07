package com.mdau.ukena.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewSubmitRequest(
        @NotBlank String productId,
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(min = 10, max = 2000) String body
) {}