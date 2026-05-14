package com.mdau.ukena.payment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "earnings_ledger", indexes = {
        @Index(name = "idx_ledger_creator_status", columnList = "artisan_profile_id,status"),
        @Index(name = "idx_ledger_order_id",       columnList = "order_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EarningsLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "creator_id", nullable = false, length = 80)
    private String creatorId;

    @Column(name = "artisan_profile_id", length = 80)
    private String artisanProfileId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "gross_pence", nullable = false)
    private int grossPence;

    // amount_pence = net_pence — kept as a separate column to match the DB schema
    @Column(name = "amount_pence", nullable = false)
    private int amountPence;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "net_pence", nullable = false)
    private int netPence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private LedgerStatus status = LedgerStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}