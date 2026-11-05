package com.mdau.ukena.shipment;

public enum ShipmentStatus {
    /** Space booked with the forwarder; orders are packed and waiting. */
    BOOKED,
    IN_AIR,
    /** Landed and cleared UK customs at the Nottingham base. */
    CLEARED
}
