package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Move an order to its next stage. Packing needs the packed weight; handing to
 * Royal Mail needs the service and tracking number.
 */
public record AdvanceOrderRequest(
        @NotBlank String to,
        @Min(1) Integer packedWeightGrams,
        @Size(max = 60) String courier,
        @Size(max = 60) String trackingNumber,
        @Size(max = 500) String note) {}
