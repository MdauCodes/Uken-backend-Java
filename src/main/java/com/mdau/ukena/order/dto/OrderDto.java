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
        int refundedPence,
        /** HUB (UK delivery) or PICKUP (collect at the market stall). */
        String fulfilment,
        /** Hub journey stage — see FulfilmentStage. Null for POS sales. */
        String stage,
        Instant stageChangedAt,
        /** Weekly flight this order is booked on, and when it lands. */
        String shipmentId,
        String flightArrivesOn,
        Integer packedWeightGrams,
        String courier,
        String trackingNumber,
        String assignedTo,
        String issueKind,
        String issueNote,
        Instant buyerConfirmedAt,
        /** creatorId -> true once that maker has dropped their pieces at the hub. */
        java.util.Map<String, Boolean> makerReady,
        java.util.List<OrderEventDto> history,
        /** Set only on the response to a guest placing an order: lets that browser open
         *  the order (review, payment return, confirmation) for an hour without the
         *  emailed code. Never stored on the order or returned anywhere else. */
        String accessToken
) {
    public OrderDto withAccessToken(String token) {
        return new OrderDto(displayId, createdAt, status, channel, productsTotalPence, shippingPence,
                promoCode, discountPence, totalPence, buyer, items, delivery, lastPaymentError, refundedPence,
                fulfilment, stage, stageChangedAt, shipmentId, flightArrivesOn, packedWeightGrams, courier,
                trackingNumber, assignedTo, issueKind, issueNote, buyerConfirmedAt, makerReady, history, token);
    }
}