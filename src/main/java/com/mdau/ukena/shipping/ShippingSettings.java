package com.mdau.ukena.shipping;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Singleton row holding the platform-wide weight-based shipping configuration.
 * Admin-editable via {@code ShippingSettingsController} — replaces the old
 * per-{@code DeliveryZone} flat shipping fee, which stays only for picking
 * the delivery country/address.
 */
@Entity
@Table(name = "shipping_settings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ShippingSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Cost in pence charged per kilogram of total order weight. */
    @Builder.Default
    @Column(name = "rate_pence_per_kg", nullable = false)
    private int ratePencePerKg = 500;

    /** Assumed weight (in grams) for a product a creator hasn't set a real weight for yet. */
    @Builder.Default
    @Column(name = "fallback_weight_grams", nullable = false)
    private int fallbackWeightGrams = 500;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
