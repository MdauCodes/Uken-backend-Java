package com.mdau.ukena.auth.dto;

public record AuthUserDto(
        String id,
        String email,
        String fullName,
        String role,
        String creatorId
) {}