package com.mdau.ukena.creator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CreatorRepository extends JpaRepository<Creator, String> {

    @Query("""
        SELECT c FROM Creator c
        WHERE c.deletedAt IS NULL
        AND (:craft IS NULL OR LOWER(c.craft) = LOWER(:craft))
        AND (:region IS NULL OR LOWER(c.region) = LOWER(:region))
        ORDER BY c.createdAt DESC
    """)
    List<Creator> findFiltered(
            @Param("craft") String craft,
            @Param("region") String region);

    @Query("SELECT c FROM Creator c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Creator> findActiveById(@Param("id") String id);

    boolean existsByIdAndDeletedAtIsNull(String id);
}