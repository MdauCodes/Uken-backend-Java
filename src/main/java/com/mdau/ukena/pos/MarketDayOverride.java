package com.mdau.ukena.pos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** An admin manually calling a specific date a Market Day, regardless of how many
 *  POS sales it actually had — e.g. the stall was there but sales were slow, or a
 *  handful of cash-only sales never went through the till. Additive only: this
 *  can flag a day the automatic >3-sales rule missed, never suppress one the rule
 *  already flagged — see PosService.salesByDate. */
@Entity
@Table(name = "market_day_overrides")
@Getter @Setter @NoArgsConstructor
public class MarketDayOverride {

    @Id
    private LocalDate date;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
