package com.mdau.ukena.saved;

import com.mdau.ukena.product.Product;
import com.mdau.ukena.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_items",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_saved_user_product",
               columnNames = {"user_id", "product_id"}),
       indexes = {
               @Index(name = "idx_saved_user_id",    columnList = "user_id"),
               @Index(name = "idx_saved_product_id", columnList = "product_id")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SavedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @CreationTimestamp
    @Column(name = "saved_at", updatable = false)
    private Instant savedAt;
}