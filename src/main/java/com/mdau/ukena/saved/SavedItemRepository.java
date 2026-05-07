package com.mdau.ukena.saved;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedItemRepository extends JpaRepository<SavedItem, UUID> {

    @Query("SELECT s FROM SavedItem s JOIN FETCH s.product p " +
           "JOIN FETCH p.creator WHERE s.user.id = :userId " +
           "ORDER BY s.savedAt DESC")
    List<SavedItem> findByUserIdWithProduct(@Param("userId") UUID userId);

    Optional<SavedItem> findByUserIdAndProductId(UUID userId, String productId);

    boolean existsByUserIdAndProductId(UUID userId, String productId);
}