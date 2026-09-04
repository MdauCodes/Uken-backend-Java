package com.mdau.ukena.payment;

/** amountPence null = full refund. */
public record RefundRequest(String displayId, String gatewayRef, Integer amountPence) {}
