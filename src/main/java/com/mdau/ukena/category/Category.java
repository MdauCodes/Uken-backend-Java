package com.mdau.ukena.category;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

/**
 * Real category taxonomy — replaces the old approach of grouping products by
 * matching a creator's free-text `craft` string in frontend code. A category
 * still resolves to a set of real craft values (see {@code craftValues},
 * stored as a JSON string array) so today's products can be auto-assigned
 * without requiring every creator to be re-onboarded; an admin can widen
 * that list at any time as new craft spellings come online, from the admin
 * Categories screen.
 *
 * {@code id} is an app-generated slug (e.g. "farm-produce"), not a UUID —
 * it's used directly in shop URLs (?category=farm-produce), so it needs to
 * be stable and readable, same as Product/Creator's own ids.
 */
@Entity
@Table(name = "categories", indexes = {
        @Index(name = "idx_categories_active",     columnList = "active"),
        @Index(name = "idx_categories_sort_order",  columnList = "sort_order")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Category {

    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    /** One of styles.css's brand color tokens (e.g. "sage", "clay") — the
     *  frontend maps this to a fixed, known set of Tailwind classes rather
     *  than constructing class names dynamically. */
    @Column(name = "color_token", nullable = false, length = 40)
    @Builder.Default
    private String colorToken = "clay";

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** JSON array of real creator `craft` strings that belong to this
     *  category — e.g. ["Horticulture and AgriBusiness","HASS FARMING"].
     *  Nullable/defaulted-in-Java only, matching the codebase's safe
     *  ddl-auto pattern for adding columns to a populated table. */
    @Column(name = "craft_values", columnDefinition = "TEXT")
    @Builder.Default
    private String craftValues = "[]";

    /** Representative image for the category showcase section — nullable,
     *  admin-editable from the Categories screen. */
    @Column(name = "thumbnail_image", columnDefinition = "TEXT")
    private String thumbnailImage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
