package com.mdau.ukena.payment;

public record RefundResult(boolean success, String gatewayRefundRef, String message) {}
