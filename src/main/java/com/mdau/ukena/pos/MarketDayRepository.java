package com.mdau.ukena.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface MarketDayRepository extends JpaRepository<MarketDay, LocalDate> {

    /** Whether any market day currently points at this catalogue — deleting one
     *  out from under a day that's using it would silently un-restrict the till. */
    boolean existsByCatalogue_Id(UUID catalogueId);
}
