package com.mdau.ukena.promo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record WelcomeCaptureRequest(@NotBlank @Email String email) {}
