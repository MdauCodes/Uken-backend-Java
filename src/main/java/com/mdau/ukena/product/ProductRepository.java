package com.mdau.ukena.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    // Public browse — only ACTIVE, not deleted
    @Query("""
        SELECT p FROM Product p
        WHERE p.deletedAt IS NULL
        AND p.status = com.mdau.ukena.product.ProductStatus.ACTIVE
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

    // Creator's own products — all statuses, not deleted
    @Query("""
        SELECT p FROM Product p
        WHERE p.deletedAt IS NULL
        AND p.creator.id = :creatorId
        ORDER BY p.createdAt DESC
    """)
    List<Product> findByCreatorIdNotDeleted(@Param("creatorId") String creatorId);

    // Single product — not deleted
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findActiveById(@Param("id") String id);

    boolean existsByIdAndCreatorId(String id, String creatorId);
}