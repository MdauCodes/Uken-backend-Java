package com.mdau.ukena.payment;

import java.util.UUID;

/**
 * Gateway-agnostic payment initiation request.
 * amountPence is stored as integer pence; gateway impls divide by 100.
 */
public record PaymentInitRequest(
        UUID    orderId,
        String  displayId,
        int     amountPence,
        String  currency,
        String  buyerEmail,
        String  buyerName,
        String  description
) {}
