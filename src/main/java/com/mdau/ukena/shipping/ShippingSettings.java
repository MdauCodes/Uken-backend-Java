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

    /**
     * Flat UK delivery fee in pence. Orders fly from the Nairobi hub in a combined
     * weekly shipment, so buyers pay one simple fee rather than by weight.
     * Nullable so Hibernate's ddl-auto can add the column to the existing row;
     * {@link #effectiveDeliveryFeePence()} applies the default.
     */
    @Column(name = "delivery_fee_pence")
    private Integer deliveryFeePence;

    /** Orders whose items total at least this many pence ship free. Nullable, see above. */
    @Column(name = "free_delivery_over_pence")
    private Integer freeDeliveryOverPence;

    public static final int DEFAULT_DELIVERY_FEE_PENCE = 495;
    public static final int DEFAULT_FREE_DELIVERY_OVER_PENCE = 6000;

    public int effectiveDeliveryFeePence() {
        return deliveryFeePence != null ? deliveryFeePence : DEFAULT_DELIVERY_FEE_PENCE;
    }

    public int effectiveFreeDeliveryOverPence() {
        return freeDeliveryOverPence != null ? freeDeliveryOverPence : DEFAULT_FREE_DELIVERY_OVER_PENCE;
    }

    /** UK delivery charge for an order whose items (before any promo) total {@code itemsPence}. */
    public int deliveryChargeFor(int itemsPence) {
        return itemsPence >= effectiveFreeDeliveryOverPence() ? 0 : effectiveDeliveryFeePence();
    }

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
