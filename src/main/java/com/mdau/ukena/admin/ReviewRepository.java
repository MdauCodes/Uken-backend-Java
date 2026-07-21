package com.mdau.ukena.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReviewRepository extends JpaRepository<ReviewRecord, String> {
    List<ReviewRecord> findAllByOrderBySubmittedAtDesc();
    List<ReviewRecord> findByProductIdOrderBySubmittedAtDesc(String productId);

    /** [0] = average rating (Double, null if no published reviews), [1] = count (Long). */
    @Query("SELECT AVG(r.rating), COUNT(r) FROM ReviewRecord r " +
           "WHERE r.product.id = :productId AND r.status = com.mdau.ukena.admin.ReviewStatus.PUBLISHED")
    Object[] ratingSummary(@Param("productId") String productId);
}