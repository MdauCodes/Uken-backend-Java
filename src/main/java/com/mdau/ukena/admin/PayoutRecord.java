package com.mdau.ukena.admin;

import com.mdau.ukena.creator.Creator;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "payouts", indexes = {
        @Index(name = "idx_payouts_creator_id", columnList = "creator_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PayoutRecord {

    @Id
    @Column(name = "creator_id", length = 80)
    private String creatorId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "creator_id")
    private Creator creator;

    @Builder.Default
    @Column(name = "pending_pence", nullable = false)
    private int pendingPence = 0;

    @Builder.Default
    @Column(name = "paid_this_month_pence", nullable = false)
    private int paidThisMonthPence = 0;

    @Builder.Default
    @Column(name = "total_lifetime_pence", nullable = false)
    private int totalLifetimePence = 0;

    @Column(name = "last_paid_at")
    private Instant lastPaidAt;
}