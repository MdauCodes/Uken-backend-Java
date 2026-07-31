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
        @Index(name = "idx_products_creator_id",     columnList = "creator_id"),
        @Index(name = "idx_products_status",         columnList = "status"),
        @Index(name = "idx_products_deleted_at",     columnList = "deleted_at"),
        @Index(name = "idx_products_created_at",     columnList = "created_at"),
        @Index(name = "idx_products_creator_status", columnList = "creator_id,status"),
        @Index(name = "idx_products_price",          columnList = "price_pence"),
        @Index(name = "idx_products_ukena_owned",    columnList = "is_ukena_owned")
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

    /** Manually-set "was" price for a genuine markdown — the creator/admin's real
     *  intended regular price, never auto-generated. Shown as a strikethrough only
     *  when it's actually higher than pricePence. Null = no markdown active. */
    @Column(name = "compare_at_price_pence")
    private Integer compareAtPricePence;

    @Column(name = "hero_image", columnDefinition = "TEXT")
    private String heroImage;

    @Column(name = "piece_story", columnDefinition = "TEXT")
    private String pieceStory;

    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private String materials = "[]";

    @Column(length = 200)
    private String dimensions;

    @Column(columnDefinition = "TEXT")
    private String care;

    /** Weight of a single unit, in grams — drives weight-based shipping calc.
     *  Null until the creator sets it (existing products created before this
     *  feature); required going forward for new products. */
    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @OneToMany(mappedBy = "product",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    /**
     * True for products uploaded directly by Uken via the admin dashboard.
     * These are fulfilled by Uken internally — not by a creator.
     * Frontend uses this flag to render the "Fulfilled by Uken" badge.
     */
    @Column(name = "is_ukena_owned", nullable = false)
    @Builder.Default
    private boolean isUkenaOwned = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Null/false for the first 7 days after deletion — images still sit in
     *  Cloudinary and an admin can fully restore the listing. A scheduled job
     *  purges the images and flips this to true once the grace period expires,
     *  after which restore only brings back the metadata, not the photos.
     *  Boxed (not primitive) and nullable so adding this column via ddl-auto
     *  never risks a NOT-NULL ALTER TABLE failure against existing rows. */
    @Column(name = "images_purged")
    private Boolean imagesPurged;

    public boolean isDeleted() { return deletedAt != null; }

    public boolean isImagesPurged() { return Boolean.TRUE.equals(imagesPurged); }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}