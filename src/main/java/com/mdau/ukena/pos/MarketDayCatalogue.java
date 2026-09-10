package com.mdau.ukena.pos;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A named, independent snapshot of {@code {product, price}} pairs sellable on a
 * market day — see MarketDay.catalogue. Independent on purpose: composing a new
 * catalogue from one or more existing ones (MarketDayCatalogueItem rows are
 * copied, never shared) means editing this catalogue's products/prices later can
 * never change what an earlier market day actually sold under. A catalogue with
 * no MarketDay pointing at it yet is just a reusable draft/template.
 */
@Entity
@Table(name = "market_day_catalogues")
@Getter @Setter @NoArgsConstructor
public class MarketDayCatalogue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "catalogue", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("productName ASC")
    private List<MarketDayCatalogueItem> items = new ArrayList<>();
}
