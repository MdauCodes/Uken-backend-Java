package com.mdau.ukena.order.access;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TrackVerifyRequest(
        @NotBlank @Size(max = 20) String displayId,
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}
