package com.mdau.ukena.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty List<@Valid OrderItemRequest> items,
        @NotNull @Valid DeliveryDto delivery
) {}