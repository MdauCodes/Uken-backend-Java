package com.mdau.ukena.shipment.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/** Book packed orders onto a flight. freightPence defaults to weight × the per-kg rate. */
public record CreateShipmentRequest(
        @NotEmpty List<@NotBlank String> orderIds,
        @NotBlank @Size(max = 40) String awb,
        @NotNull LocalDate departsOn,
        @NotNull LocalDate arrivesOn,
        @Size(max = 160) String forwarder,
        @Min(0) Integer freightPence) {}
