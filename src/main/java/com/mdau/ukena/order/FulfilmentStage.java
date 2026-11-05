package com.mdau.ukena.order;

/**
 * Where an online order is on the hub journey: maker → Nairobi hub → weekly
 * flight → UK customs → Nottingham base → Royal Mail or the market stall.
 *
 * Runs alongside the older {@link OrderStatus}, which reviews, reports and the
 * maker earnings queries still read; every stage maps onto one legacy status
 * (see {@link #legacyStatus()}) so those keep working unchanged.
 */
public enum FulfilmentStage {
    PAID,
    IN_PRODUCTION,
    AT_HUB,
    PACKED,
    IN_AIR,
    AT_UK_BASE,
    OUT_FOR_DELIVERY,
    READY_TO_COLLECT,
    DELIVERED,
    COMPLETED,
    /** The buyer reported a problem. The legacy status is left where it was. */
    ISSUE,
    CANCELLED;

    public OrderStatus legacyStatus() {
        return switch (this) {
            case PAID -> OrderStatus.PAID;
            case IN_PRODUCTION, AT_HUB, PACKED -> OrderStatus.PREPARING;
            case IN_AIR, AT_UK_BASE, OUT_FOR_DELIVERY, READY_TO_COLLECT -> OrderStatus.SHIPPED;
            case DELIVERED, COMPLETED -> OrderStatus.DELIVERED;
            case CANCELLED -> OrderStatus.CANCELLED;
            case ISSUE -> null;
        };
    }

    /** Best-guess stage for orders placed before stages existed. */
    public static FulfilmentStage fromLegacy(OrderStatus status) {
        return switch (status) {
            case PENDING, PAID -> PAID;
            case PREPARING -> IN_PRODUCTION;
            case SHIPPED -> OUT_FOR_DELIVERY;
            case DELIVERED -> COMPLETED;
            case CANCELLED -> CANCELLED;
        };
    }
}
