package com.mdau.ukena.pos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A calendar date an admin has planned or flagged as a Market Day — with an
 * optional name and an optional assigned MarketDayCatalogue. Three independent
 * things this row can mean, all optional beyond the date itself:
 *   - just a manual "this counts as a Market Day" flag (name/catalogue null) —
 *     the original, still-supported use, alongside the automatic >3-sales rule
 *     in PosService.salesByDate.
 *   - a named, planned day (a future date set up ahead of time).
 *   - a catalogue-restricted day — when catalogue is set, PosService.browseProducts
 *     shows ONLY this catalogue's products (at this catalogue's prices) on this
 *     date, and OrderService.placePos prices POS sales from it too. Search and
 *     the "New item" quick-add stay unrestricted even on a catalogue day — those
 *     are deliberate operator actions for something outside the plan, not the
 *     default browse surface a catalogue is meant to curate.
 */
@Entity
@Table(name = "market_days")
@Getter @Setter @NoArgsConstructor
public class MarketDay {

    @Id
    private LocalDate date;

    @Column(length = 160)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalogue_id")
    private MarketDayCatalogue catalogue;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
