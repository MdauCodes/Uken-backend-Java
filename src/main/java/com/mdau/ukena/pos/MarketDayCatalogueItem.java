package com.mdau.ukena.pos;

import com.mdau.ukena.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One product's slot in a MarketDayCatalogue — name/image/price are snapshotted
 * from the real Product when added, then independently editable from that point
 * on (this is what makes a catalogue "that specific market day's" own copy, not
 * a live view of the product). {@code product} itself stays a real reference so
 * stock decrements and order lines still resolve to the genuine catalogue item.
 */
@Entity
@Table(name = "market_day_catalogue_items", indexes = {
        @Index(name = "idx_mdci_catalogue_id", columnList = "catalogue_id")
})
@Getter @Setter @NoArgsConstructor
public class MarketDayCatalogueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalogue_id", nullable = false)
    private MarketDayCatalogue catalogue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "hero_image", columnDefinition = "TEXT")
    private String heroImage;

    @Column(name = "price_pence", nullable = false)
    private int pricePence;
}
