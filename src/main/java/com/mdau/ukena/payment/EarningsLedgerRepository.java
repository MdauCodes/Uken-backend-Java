package com.mdau.ukena.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EarningsLedgerRepository extends JpaRepository<EarningsLedger, UUID> {

    List<EarningsLedger> findByArtisanProfileIdAndStatus(
            String artisanProfileId, LedgerStatus status);

    @Query("""
        SELECT COALESCE(SUM(e.netPence), 0)
        FROM EarningsLedger e
        WHERE e.artisanProfileId = :artisanProfileId
        AND e.status = :status
    """)
    int sumNetPenceByArtisanProfileIdAndStatus(
            @Param("artisanProfileId") String artisanProfileId,
            @Param("status") LedgerStatus status);

    long countByCreatorId(String creatorId);

    @Query("""
        SELECT e FROM EarningsLedger e
        WHERE e.creatorId = :creatorId
    """)
    List<EarningsLedger> findAllByCreatorId(@Param("creatorId") String creatorId);
}