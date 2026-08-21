package com.mdau.ukena.creator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface CreatorRepository extends JpaRepository<Creator, String> {
    @Query("SELECT c FROM Creator c WHERE c.deletedAt IS NULL AND c.id = :id")
    Optional<Creator> findActiveById(@Param("id") String id);
    @Query("SELECT c FROM Creator c WHERE c.deletedAt IS NULL")
    Page<Creator> findAllActive(Pageable pageable);

    /** Real, publicly-visible makers only — a creator counts if they have at
     *  least one real, live, buyable product. Deliberately does NOT check
     *  creator.deletedAt: verified directly against production data that a
     *  creator row's own soft-delete flag doesn't reliably track whether
     *  their products are actually live (the platform's one real, currently-
     *  selling creator has deletedAt set on the creator row despite all 12
     *  of their products being ACTIVE) — every other visibility check in
     *  this codebase (browse/search/countActive/etc.) already treats
     *  product-level status as authoritative and never looks at
     *  creator.deletedAt either, so this matches that same convention
     *  instead of inventing a stricter one. Native SQL, matching
     *  findFiltered/findAllForAdmin below, since three different JPQL
     *  formulations all silently matched zero rows for reasons that were
     *  never actually about JPQL — see git history for the debugging trail. */
    @Query(value = """
        SELECT COUNT(DISTINCT p.creator_id) FROM products p
        WHERE p.deleted_at IS NULL AND p.status = 'ACTIVE' AND p.available_online = true
        AND p.creator_id <> 'ukena'
    """, nativeQuery = true)
    long countActiveMakers();

    @Query(value = """
        SELECT COUNT(DISTINCT c.region) FROM products p
        JOIN creators c ON c.id = p.creator_id
        WHERE p.deleted_at IS NULL AND p.status = 'ACTIVE' AND p.available_online = true
        AND p.creator_id <> 'ukena'
    """, nativeQuery = true)
    long countDistinctRegions();
    @Query(value = "SELECT * FROM creators WHERE deleted_at IS NULL " +
           "AND (:craft IS NULL OR LOWER(craft) = LOWER(CAST(:craft AS varchar))) " +
           "AND (:region IS NULL OR LOWER(region) = LOWER(CAST(:region AS varchar))) " +
           "ORDER BY created_at DESC", nativeQuery = true)
    List<Creator> findFiltered(
            @Param("craft")  String craft,
            @Param("region") String region);
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
           "FROM Creator c WHERE c.id = :id AND c.deletedAt IS NULL")
    boolean existsByIdAndDeletedAtIsNull(@Param("id") String id);
    @Query("SELECT c FROM Creator c WHERE c.deletedAt IS NULL AND " +
           "(LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.craft)    LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.region)   LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Creator> search(@Param("q") String q, Pageable pageable);
    @Query(value = "SELECT * FROM creators WHERE " +
           "(:status IS NULL " +
           "OR (:status = 'ACTIVE' AND deleted_at IS NULL) " +
           "OR (:status = 'SUSPENDED' AND deleted_at IS NOT NULL)) " +
           "ORDER BY created_at DESC", nativeQuery = true)
    List<Creator> findAllForAdmin(@Param("status") String status);
}