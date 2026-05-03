package com.mdau.ukena.admin;

import com.mdau.ukena.product.Product;
import com.mdau.ukena.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_product_id", columnList = "product_id"),
        @Index(name = "idx_reviews_status", columnList = "status"),
        @Index(name = "idx_reviews_buyer_id", columnList = "buyer_user_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ReviewRecord {

    @Id
    @Column(length = 20)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_user_id")
    private User buyer;

    @Column(name = "buyer_name", nullable = false, length = 120)
    private String buyerName;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private Instant submittedAt;
}