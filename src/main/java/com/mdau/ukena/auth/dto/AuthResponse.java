package com.mdau.ukena.auth.dto;

public record AuthResponse(
        String token,
        AuthUserDto user
) {}