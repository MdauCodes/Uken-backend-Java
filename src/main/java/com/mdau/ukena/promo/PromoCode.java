package com.mdau.ukena.promo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * A genuine, admin-managed discount code — reduces the real price paid, never
 * a fabricated "was" price. See {@code Product.compareAtPricePence} for the
 * separate (also genuine, manually-set) markdown mechanism.
 */
@Entity
@Table(name = "promo_codes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stored uppercase, e.g. "WELCOME10". */
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "percent_off", nullable = false)
    private int percentOff;

    /** Admin-facing note, e.g. "First-order welcome discount". */
    @Column(length = 200)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Null = never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Null = unlimited redemptions. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Builder.Default
    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
