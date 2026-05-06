package com.mdau.ukena.creator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface CreatorRepository extends JpaRepository<Creator, String> {
    @Query("SELECT c FROM Creator c WHERE c.deletedAt IS NULL AND c.id = :id")
    Optional<Creator> findActiveById(@Param("id") String id);
    @Query("SELECT c FROM Creator c WHERE c.deletedAt IS NULL")
    Page<Creator> findAllActive(Pageable pageable);
    @Query(value = "SELECT * FROM creators WHERE deleted_at IS NULL " +
           "AND (:craft IS NULL OR LOWER(craft) = LOWER(CAST(:craft AS varchar))) " +
           "AND (:region IS NULL OR LOWER(region) = LOWER(CAST(:region AS varchar))) " +
           "ORDER BY created_at DESC", nativeQuery = true)
    java.util.List<Creator> findFiltered(
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
}