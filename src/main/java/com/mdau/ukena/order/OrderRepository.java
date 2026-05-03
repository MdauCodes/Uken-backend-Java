package com.mdau.ukena.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByDisplayId(String displayId);

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    @Query("""
        SELECT DISTINCT o FROM Order o
        JOIN o.items i
        WHERE i.creator.id = :creatorId
        AND o.status <> com.mdau.ukena.order.OrderStatus.PENDING
        ORDER BY o.createdAt DESC
    """)
    List<Order> findByCreatorId(@Param("creatorId") String creatorId);

    @Query("""
        SELECT o FROM Order o
        ORDER BY o.createdAt DESC
    """)
    List<Order> findAllOrderByCreatedAtDesc();
}