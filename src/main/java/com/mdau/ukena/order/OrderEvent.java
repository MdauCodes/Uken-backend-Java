package com.mdau.ukena.order;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** One step in an order's history — what changed, when, by whom. Shown to staff and (minus notes) to the buyer. */
@Entity
@Table(name = "order_events", indexes = @Index(name = "idx_order_events_order", columnList = "order_id"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private FulfilmentStage stage;

    /** Who did it: a staff name, "Stripe", "Royal Mail", the buyer's name… */
    @Column(name = "actor", nullable = false, length = 120)
    private String actor;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "at", updatable = false)
    private Instant at;
}
