package com.mdau.ukena.payment;

/**
 * Gateway-agnostic payout request.
 * amountPence is in GBP pence; gateway converts to KES or local currency.
 */
public record PayoutRequest(
        String creatorId,
        int    amountPence,
        String accountNumber,   // M-Pesa / bank account
        String accountName,
        String narration
) {}
