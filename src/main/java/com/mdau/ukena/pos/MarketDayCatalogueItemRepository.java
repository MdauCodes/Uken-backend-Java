package com.mdau.ukena.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketDayCatalogueItemRepository extends JpaRepository<MarketDayCatalogueItem, UUID> {
    List<MarketDayCatalogueItem> findByCatalogue_IdOrderByProductNameAsc(UUID catalogueId);
    Optional<MarketDayCatalogueItem> findByIdAndCatalogue_Id(UUID id, UUID catalogueId);
    int countByCatalogue_Id(UUID catalogueId);
    Optional<MarketDayCatalogueItem> findByCatalogue_IdAndProduct_Id(UUID catalogueId, String productId);
}
