package com.mdau.ukena.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    @Query("""
        SELECT p FROM Product p
        WHERE p.deletedAt IS NULL
        AND p.status = com.mdau.ukena.product.ProductStatus.ACTIVE
        AND p.availableOnline = true
        AND (:creatorId IS NULL OR p.creator.id = :creatorId)
        AND (:minPrice IS NULL OR p.pricePence >= :minPrice)
        AND (:maxPrice IS NULL OR p.pricePence <= :maxPrice)
        ORDER BY p.createdAt DESC
    """)
    Page<Product> browse(
            @Param("creatorId") String creatorId,
            @Param("minPrice")  Integer minPrice,
            @Param("maxPrice")  Integer maxPrice,
            Pageable pageable);

    /** POS browse grid — Uken's own catalogue only, ACTIVE only, but deliberately
     *  ignores availableOnline: market-only pieces must still be sellable at the stall. */
    @Query("""
        SELECT p FROM Product p
        WHERE p.deletedAt IS NULL
        AND p.creator.id = :creatorId
        AND p.status = com.mdau.ukena.product.ProductStatus.ACTIVE
        ORDER BY p.name ASC
    """)
    List<Product> browseForPos(@Param("creatorId") String creatorId);

    @Query("""
        SELECT p FROM Product p
        WHERE p.deletedAt IS NULL
        AND p.creator.id = :creatorId
        ORDER BY p.createdAt DESC
    """)
    List<Product> findByCreatorIdNotDeleted(@Param("creatorId") String creatorId);

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findActiveById(@Param("id") String id);

    /** Soft-deleted past the 7-day recovery window, images not yet purged —
     *  the pool the nightly purge job works through. */
    @Query("""
        SELECT p FROM Product p
        WHERE p.deletedAt IS NOT NULL
        AND p.deletedAt < :cutoff
        AND (p.imagesPurged IS NULL OR p.imagesPurged = false)
    """)
    List<Product> findExpiredSoftDeleted(@Param("cutoff") java.time.Instant cutoff);

    /** Admin moderation queue — every product regardless of status or soft-delete,
     *  so suspended/paused/deleted items aren't invisible to the people who'd fix them. */
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.creator
        ORDER BY p.createdAt DESC
    """)
    List<Product> findAllForAdmin();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.deletedAt IS NULL AND p.status = com.mdau.ukena.product.ProductStatus.ACTIVE")
    long countActive();

    boolean existsByIdAndCreatorId(String id, String creatorId);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.creator c
        WHERE p.deletedAt IS NULL
        AND p.status = com.mdau.ukena.product.ProductStatus.ACTIVE
        AND p.availableOnline = true
        AND (LOWER(p.name)       LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(p.pieceStory) LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(c.craft)      LIKE LOWER(CONCAT('%', :q, '%')))
    """)
    Page<Product> search(@Param("q") String q, Pageable pageable);

    @Modifying
    @Query("""
        UPDATE Product p SET p.status =
        com.mdau.ukena.product.ProductStatus.SUSPENDED_BY_ADMIN
        WHERE p.creator.id = :creatorId
        AND p.deletedAt IS NULL
        AND p.status <> com.mdau.ukena.product.ProductStatus.SUSPENDED_BY_ADMIN
    """)
    int suspendAllByCreatorId(@Param("creatorId") String creatorId);

    @Modifying
    @Query("""
        UPDATE Product p SET p.status =
        com.mdau.ukena.product.ProductStatus.ACTIVE
        WHERE p.creator.id = :creatorId
        AND p.deletedAt IS NULL
        AND p.status = com.mdau.ukena.product.ProductStatus.SUSPENDED_BY_ADMIN
    """)
    int restoreAllByCreatorId(@Param("creatorId") String creatorId);

    @Query("""
        SELECT p FROM Product p
        WHERE p.creator.id = :creatorId
        AND p.deletedAt IS NULL
    """)
    List<Product> findByCreatorId(@Param("creatorId") String creatorId);

    /** Atomic — only decrements when enough tracked stock exists. Returns affected row
     *  count: 0 means either untracked (unitsAvailable IS NULL) or insufficient stock,
     *  both of which the caller must handle without assuming the decrement happened. */
    @Modifying
    @Query("""
        UPDATE Product p SET p.unitsAvailable = p.unitsAvailable - :qty
        WHERE p.id = :id AND p.unitsAvailable IS NOT NULL AND p.unitsAvailable >= :qty
    """)
    int decrementStock(@Param("id") String id, @Param("qty") int qty);

    /** Active, creator-owned (not Uken catalogue) products still missing a weight —
     *  the pool the weight-reminder email is sent for. */
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.creator
        WHERE p.deletedAt IS NULL
        AND p.weightGrams IS NULL
        AND p.isUkenaOwned = false
        AND p.status = com.mdau.ukena.product.ProductStatus.ACTIVE
    """)
    List<Product> findActiveMissingWeight();
}