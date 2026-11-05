package com.mdau.ukena.order.dto;

import java.time.Instant;

/** One step in an order's history. {@code note} is only filled for staff views. */
public record OrderEventDto(String stage, String actor, String note, Instant at) {}
