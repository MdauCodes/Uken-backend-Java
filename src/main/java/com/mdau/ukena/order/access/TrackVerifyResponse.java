package com.mdau.ukena.order.access;

import com.mdau.ukena.order.dto.OrderDto;

/** The order plus a short-lived token the buyer's browser sends as X-Order-Access for follow-up actions. */
public record TrackVerifyResponse(String accessToken, OrderDto order) {}
