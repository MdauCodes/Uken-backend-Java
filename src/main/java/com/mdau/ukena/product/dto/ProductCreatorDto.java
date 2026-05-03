package com.mdau.ukena.product.dto;

public record ProductCreatorDto(
        String id,
        String firstName,
        String fullName,
        String region,
        String craft,
        String portrait
) {}