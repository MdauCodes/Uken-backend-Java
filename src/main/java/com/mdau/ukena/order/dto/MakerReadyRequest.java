package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.NotBlank;

/** Admin marks one maker's part of an order as dropped at the hub (or not). */
public record MakerReadyRequest(@NotBlank String creatorId, boolean ready) {}
