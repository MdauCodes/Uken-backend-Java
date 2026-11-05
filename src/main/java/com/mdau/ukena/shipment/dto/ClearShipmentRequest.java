package com.mdau.ukena.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClearShipmentRequest(@NotBlank @Size(max = 60) String customsRef) {}
