package com.mdau.ukena.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface MarketDayOverrideRepository extends JpaRepository<MarketDayOverride, LocalDate> {
}
