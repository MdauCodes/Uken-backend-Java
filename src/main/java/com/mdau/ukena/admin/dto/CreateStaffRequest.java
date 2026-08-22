package com.mdau.ukena.admin.dto;

import jakarta.validation.constraints.*;

public record CreateStaffRequest(
        @NotBlank @Email @Size(max = 254) String email,
        /** Optional — when omitted, the server generates a secure random
         *  password and emails it to the new account along with their
         *  login. When provided, must still meet the minimum length. */
        @Size(min = 8, max = 128) String password,
        @NotBlank @Size(min = 2, max = 120) String fullName,
        /** "ADMIN" or "SUPPORT" (default). Only the superadmin may create "ADMIN". */
        String role
) {}