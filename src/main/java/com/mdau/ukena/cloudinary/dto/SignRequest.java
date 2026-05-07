package com.mdau.ukena.cloudinary.dto;

import jakarta.validation.constraints.NotBlank;

public record SignRequest(
        @NotBlank(message = "Folder is required") String folder,
        String publicId
) {}