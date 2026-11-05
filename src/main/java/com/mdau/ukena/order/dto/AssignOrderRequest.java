package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.Size;

/** Staff member looking after the order; blank clears it. */
public record AssignOrderRequest(@Size(max = 120) String assignedTo) {}
