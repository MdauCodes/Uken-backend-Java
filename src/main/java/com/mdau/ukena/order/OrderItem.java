package com.mdau.ukena.order;

import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.product.Product;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order_id", columnList = "order_id"),
        @Index(name = "idx_order_items_creator_id", columnList = "creator_id"),
        @Index(name = "idx_order_items_product_id", columnList = "product_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private Creator creator;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    @Column(name = "price_pence", nullable = false)
    private int pricePence;

    /** Per-unit weight (grams) used to bill shipping at order time — real product
     *  weight if the creator had set one, otherwise the platform fallback. Snapshotted
     *  so historical orders stay accurate even if the product's weight changes later.
     *  Nullable (not {@code nullable = false}) so adding this column to the existing,
     *  already-populated {@code order_items} table doesn't fail on deploy — orders
     *  placed before this feature simply have no weight on record. Always set for
     *  orders placed from here on ({@link OrderService#place}). */
    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String image;

    @Column(name = "creator_full_name", nullable = false, length = 120)
    private String creatorFullName;

    @Column(name = "creator_region", nullable = false, length = 120)
    private String creatorRegion;
}