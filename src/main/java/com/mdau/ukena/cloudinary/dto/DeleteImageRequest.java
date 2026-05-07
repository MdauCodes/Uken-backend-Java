package com.mdau.ukena.cloudinary.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteImageRequest(
        @NotBlank String publicId
) {}