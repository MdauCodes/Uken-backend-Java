package com.mdau.ukena.order.dto;

import java.time.Instant;
import java.util.List;

public record OrderDto(
        String displayId,
        Instant createdAt,
        String status,
        /** "ONLINE" or "POS" — never null even for pre-POS orders (see OrderService.toDto). */
        String channel,
        int productsTotalPence,
        int shippingPence,
        String promoCode,
        int discountPence,
        int totalPence,
        OrderBuyerDto buyer,
        List<OrderItemDto> items,
        DeliveryDto delivery,
        /** Reason the last card-charge attempt failed/was declined/was cancelled —
         *  null unless a charge attempt has actually failed. POS surfaces this
         *  directly instead of a generic timeout message. */
        String lastPaymentError,
        /** Total refunded so far, in pence. 0 = no refund. */
        int refundedPence
) {}