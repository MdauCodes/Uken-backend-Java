package com.mdau.ukena.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<PayoutRecord, String> {

    List<PayoutRecord> findAllByOrderByCreatorIdAsc();

    @Query("SELECT p FROM PayoutRecord p WHERE p.creator.id = :creatorId")
    Optional<PayoutRecord> findByCreatorId(@Param("creatorId") String creatorId);
}