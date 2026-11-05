package com.mdau.ukena.order.access;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrackCodeRequest(@NotBlank @Size(max = 20) String displayId, @NotBlank @Email String email) {}
