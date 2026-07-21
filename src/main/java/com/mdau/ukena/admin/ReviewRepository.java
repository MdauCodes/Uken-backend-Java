package com.mdau.ukena.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReviewRepository extends JpaRepository<ReviewRecord, String> {
    List<ReviewRecord> findAllByOrderBySubmittedAtDesc();
    List<ReviewRecord> findByProductIdOrderBySubmittedAtDesc(String productId);

    /** Always exactly one row: [0] = average rating (Double, null if no published
     *  reviews), [1] = count (Long). Declared as List<Object[]>, not Object[] —
     *  a bare Object[] return type is ambiguous to Spring Data JPA for a
     *  multi-column aggregate query (it can't tell "the one row" apart from
     *  "all rows as an array"), so it wraps the single row inside another
     *  array instead of unwrapping it. */
    @Query("SELECT AVG(r.rating), COUNT(r) FROM ReviewRecord r " +
           "WHERE r.product.id = :productId AND r.status = com.mdau.ukena.admin.ReviewStatus.PUBLISHED")
    List<Object[]> ratingSummary(@Param("productId") String productId);
}