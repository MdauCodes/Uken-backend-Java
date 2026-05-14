package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkStatusUpdateRequest(
        @NotEmpty List<String> displayIds,
        @NotBlank String status
) {}