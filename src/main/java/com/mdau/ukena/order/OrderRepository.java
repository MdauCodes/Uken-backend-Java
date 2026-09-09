package com.mdau.ukena.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByDisplayId(String displayId);

    /** Resolves a Terminal webhook (action_failed/action_succeeded) back to its order
     *  when the event payload carries the PaymentIntent id but not the display_id
     *  metadata — see PaymentService.handleStripeWebhook. */
    Optional<Order> findByPaymentIntentId(String paymentIntentId);

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<Order> findByBuyerEmailIgnoreCaseAndBuyerIsNull(String email);

    @Query("""
        SELECT DISTINCT o FROM Order o
        JOIN o.items i
        WHERE i.creator.id = :creatorId
        AND o.status <> com.mdau.ukena.order.OrderStatus.PENDING
        ORDER BY o.createdAt DESC
    """)
    List<Order> findByCreatorId(@Param("creatorId") String creatorId);

    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    List<Order> findAllOrderByCreatedAtDesc();

    @Query("""
        SELECT o FROM Order o
        WHERE o.status = com.mdau.ukena.order.OrderStatus.PENDING
        AND o.createdAt <= :cutoff
        AND o.reminderSentAt IS NULL
    """)


    List<Order> findPendingOrdersForReminder(@Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = com.mdau.ukena.order.OrderStatus.PAID")
    long countPaidOrders();

    /** One row per calendar day (Europe/London — where the physical stalls trade,
     *  regardless of what timezone the server itself runs in) that had at least one
     *  completed POS sale. Backs the market-stall sales-history view — see
     *  PosService.salesByDate, which turns "orderCount > 3" into a Market Day flag. */
    interface PosSalesDayProjection {
        LocalDate getSaleDate();
        Long getOrderCount();
        Long getTotalPence();
    }

    @Query(value = """
            SELECT (o.paid_at AT TIME ZONE 'Europe/London')::date AS sale_date,
                   COUNT(*)                                        AS order_count,
                   COALESCE(SUM(o.total_pence), 0)                 AS total_pence
            FROM orders o
            WHERE o.channel = 'POS'
              AND o.status IN ('PAID', 'DELIVERED')
              AND o.paid_at IS NOT NULL
            GROUP BY sale_date
            ORDER BY sale_date DESC
            """, nativeQuery = true)
    List<PosSalesDayProjection> aggregatePosSalesByDate();
}