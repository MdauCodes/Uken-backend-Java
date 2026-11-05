package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin correction of a mistaken stage. The reason goes into the history. */
public record OverrideStageRequest(@NotBlank String stage, @NotBlank @Size(max = 500) String reason) {}
