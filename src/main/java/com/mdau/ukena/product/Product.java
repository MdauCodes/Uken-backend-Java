package com.mdau.ukena.product;

import com.mdau.ukena.creator.Creator;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_creator_id",       columnList = "creator_id"),
        @Index(name = "idx_products_status",           columnList = "status"),
        @Index(name = "idx_products_deleted_at",       columnList = "deleted_at"),
        @Index(name = "idx_products_created_at",       columnList = "created_at"),
        @Index(name = "idx_products_creator_status",   columnList = "creator_id,status"),
        @Index(name = "idx_products_price",            columnList = "price_pence")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Product {

    @Id
    @Column(length = 80)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Creator creator;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "price_pence", nullable = false)
    private int pricePence;

    @Column(name = "hero_image", columnDefinition = "TEXT")
    private String heroImage;

    @Column(name = "piece_story", columnDefinition = "TEXT")
    private String pieceStory;

    // JSON array of strings — kept for materials list
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private String materials = "[]";

    @Column(length = 200)
    private String dimensions;

    @Column(columnDefinition = "TEXT")
    private String care;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    // Images now in a proper table — gallery JSON removed
    @OneToMany(mappedBy = "product",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    // Soft-delete — never hard-delete a product
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}