package com.mdau.ukena.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface EarningsLedgerRepository extends JpaRepository<EarningsLedger, UUID> {

    List<EarningsLedger> findByArtisanProfileIdAndStatus(
            String artisanProfileId, LedgerStatus status);

    @Query("SELECT COALESCE(SUM(e.netPence), 0) FROM EarningsLedger e " +
           "WHERE e.artisanProfileId = :creatorId AND e.status = :status")
    int sumNetPenceByArtisanProfileIdAndStatus(
            @Param("creatorId") String creatorId,
            @Param("status") LedgerStatus status);

    List<EarningsLedger> findByOrderId(UUID orderId);
}
