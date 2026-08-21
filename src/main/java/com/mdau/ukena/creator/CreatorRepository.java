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

    /** Real, publicly-visible makers only — excludes the "ukena" sentinel, and
     *  (critically) excludes a creator with zero live products: a creator row
     *  can exist (e.g. seeded but not yet activated, or every listing
     *  suspended) without having anything a buyer could actually see, and the
     *  trust-strip stat this powers must never count someone who isn't really
     *  represented on the live site. */
    @Query("""
        SELECT COUNT(c) FROM Creator c
        WHERE c.deletedAt IS NULL AND c.id <> 'ukena'
        AND EXISTS (
            SELECT 1 FROM Product p
            WHERE p.creator = c AND p.deletedAt IS NULL
            AND p.status = com.mdau.ukena.product.ProductStatus.ACTIVE
            AND p.availableOnline = true
        )
    """)
    long countActiveMakers();

    @Query("""
        SELECT COUNT(DISTINCT c.region) FROM Creator c
        WHERE c.deletedAt IS NULL AND c.id <> 'ukena'
        AND EXISTS (
            SELECT 1 FROM Product p
            WHERE p.creator = c AND p.deletedAt IS NULL
            AND p.status = com.mdau.ukena.product.ProductStatus.ACTIVE
            AND p.availableOnline = true
        )
    """)
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