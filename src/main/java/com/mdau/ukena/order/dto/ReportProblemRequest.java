package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportProblemRequest(@NotBlank @Size(max = 60) String kind, @Size(max = 2000) String note) {}
