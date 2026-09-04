package com.mdau.ukena.order.dto;

/** amountPence null/absent = full refund of the order's remaining (unrefunded) total. */
public record RefundOrderRequest(Integer amountPence) {}
