package com.mdau.ukena.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        /** The account the code was sent to — a code only works for its own account. */
        @NotBlank @Email String email,
        /** The 6-digit code from the email. */
        @NotBlank @Pattern(regexp = "[0-9]{6}") String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {}