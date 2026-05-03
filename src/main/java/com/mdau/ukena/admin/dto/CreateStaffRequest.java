package com.mdau.ukena.admin.dto;

import jakarta.validation.constraints.*;

public record CreateStaffRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(min = 2, max = 120) String fullName
) {}