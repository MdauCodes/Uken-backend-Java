package com.mdau.ukena.shipment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One weekly air shipment from the Nairobi hub. Packed orders are booked onto
 * it, fly together, and clear UK customs once at the Nottingham base.
 */
@Entity
@Table(name = "shipments")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Shipment {

    /** Display id, e.g. AIR-0048. */
    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false, length = 160)
    private String forwarder;

    @Column(nullable = false, length = 160)
    private String route;

    /** Air waybill number from the forwarder. */
    @Column(nullable = false, length = 40)
    private String awb;

    @Column(name = "departs_on", nullable = false)
    private LocalDate departsOn;

    @Column(name = "arrives_on", nullable = false)
    private LocalDate arrivesOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private ShipmentStatus status = ShipmentStatus.BOOKED;

    @Column(name = "weight_grams", nullable = false)
    private int weightGrams;

    /** Air freight and handling, in pence, from the forwarder's quote or invoice. */
    @Column(name = "freight_pence", nullable = false)
    private int freightPence;

    /** Customs broker fee for clearing the whole shipment once, in pence. */
    @Column(name = "clearance_pence", nullable = false)
    private int clearancePence;

    /** Customs entry reference from the broker, set when cleared. */
    @Column(name = "customs_ref", length = 60)
    private String customsRef;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
