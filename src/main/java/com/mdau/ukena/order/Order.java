package com.mdau.ukena.order;

import com.mdau.ukena.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_display_id",    columnList = "display_id"),
        @Index(name = "idx_orders_buyer_id",      columnList = "buyer_user_id"),
        @Index(name = "idx_orders_status",        columnList = "status"),
        @Index(name = "idx_orders_created_at",    columnList = "created_at"),
        @Index(name = "idx_orders_buyer_created", columnList = "buyer_user_id,created_at"),
        @Index(name = "idx_orders_pending_reminder", columnList = "status,created_at,reminder_sent_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "display_id", unique = true, nullable = false, length = 20)
    private String displayId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_user_id")
    private User buyer;

    @Column(name = "buyer_full_name", nullable = false, length = 120)
    private String buyerFullName;

    @Column(name = "buyer_email", nullable = false, length = 254)
    private String buyerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_pence", nullable = false)
    private int totalPence;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String delivery;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "gateway_ref", length = 120)
    private String gatewayRef;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}