package com.mdau.ukena.order.dto;

import jakarta.validation.constraints.*;

public record OrderItemRequest(
        @NotBlank String productId,
        @Min(1) @Max(99) int quantity,
        /** POS only — a hint that this line was tapped from the browse grid (which
         *  shows today's MarketDayCatalogue when one's assigned), not found via search
         *  or "New item". Only ever a hint: OrderService.placePos still looks the price
         *  up itself from the actual catalogue rather than trusting the client, and
         *  ignores this entirely for the online place() flow. Null/false = price at the
         *  product's live rate, matching search/quick-add's deliberately unrestricted,
         *  un-repriced behaviour on a catalogue day. */
        Boolean fromCatalogue
) {}
