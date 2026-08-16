package com.mdau.ukena.pos.dto;

import com.mdau.ukena.order.dto.OrderItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PosOrderRequest(
        @NotEmpty List<@Valid OrderItemRequest> items,
        /** Optional — only collected if the admin wants to send the customer a receipt. */
        @Email String customerEmail,
        String customerFullName
) {}
