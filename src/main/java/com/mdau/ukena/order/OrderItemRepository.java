package com.mdau.ukena.order;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);
    List<OrderItem> findByCreatorId(String creatorId);

    /** Total units of this product across confirmed (paid+) orders — a genuine, non-fabricated "X sold" signal. */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.product.id = :productId AND oi.order.status IN " +
           "(com.mdau.ukena.order.OrderStatus.PAID, com.mdau.ukena.order.OrderStatus.PREPARING, " +
           " com.mdau.ukena.order.OrderStatus.SHIPPED, com.mdau.ukena.order.OrderStatus.DELIVERED)")
    long unitsSold(@Param("productId") String productId);

    /** [0] = co-purchased product id, [1] = co-occurrence count — real order data, ranked by frequency. */
    @Query("SELECT oi2.product.id, COUNT(oi2) FROM OrderItem oi1, OrderItem oi2 " +
           "WHERE oi1.order = oi2.order AND oi1.product.id = :productId " +
           "AND oi2.product.id IS NOT NULL AND oi2.product.id <> :productId " +
           "GROUP BY oi2.product.id ORDER BY COUNT(oi2) DESC")
    List<Object[]> frequentlyBoughtWith(@Param("productId") String productId, Pageable pageable);
}