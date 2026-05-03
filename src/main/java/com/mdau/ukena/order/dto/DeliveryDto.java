package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.*;

public record DeliveryDto(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String addressLine1,
        String addressLine2,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 20) String postcode,
        @NotBlank @Size(max = 120) String country
) {}