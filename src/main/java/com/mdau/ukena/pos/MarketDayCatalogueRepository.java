package com.mdau.ukena.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MarketDayCatalogueRepository extends JpaRepository<MarketDayCatalogue, UUID> {

    @Query("SELECT c FROM MarketDayCatalogue c ORDER BY c.createdAt DESC")
    List<MarketDayCatalogue> findAllNewestFirst();
}
