package com.mdau.ukena.cloudinary.dto;

public record SignResponse(
        String signature,
        long timestamp,
        String apiKey,
        String cloudName,
        String folder,
        String publicId,
        String uploadUrl
) {}