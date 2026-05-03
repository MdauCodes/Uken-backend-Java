package com.mdau.ukena.creator;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "creators", indexes = {
        @Index(name = "idx_creators_craft",      columnList = "craft"),
        @Index(name = "idx_creators_region",     columnList = "region"),
        @Index(name = "idx_creators_deleted_at", columnList = "deleted_at"),
        @Index(name = "idx_creators_created_at", columnList = "created_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Creator {

    @Id
    @Column(length = 80)
    private String id;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 120)
    private String craft;

    @Column(nullable = false, length = 120)
    private String region;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String hook;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String image;

    @Column(name = "portrait_image", nullable = false, columnDefinition = "TEXT")
    private String portraitImage;

    @Column(name = "header_image", nullable = false, columnDefinition = "TEXT")
    private String headerImage;

    @Column(name = "story_paragraphs", columnDefinition = "TEXT")
    @Builder.Default
    private String storyParagraphs = "[]";

    @Column(name = "pull_quote", columnDefinition = "TEXT")
    private String pullQuote;

    @Column(name = "process_steps", columnDefinition = "TEXT")
    @Builder.Default
    private String processSteps = "[]";

    @Column(name = "cultural_meaning", columnDefinition = "TEXT")
    @Builder.Default
    private String culturalMeaning = "[]";

    @Column(name = "map_pin", columnDefinition = "TEXT")
    private String mapPin;

    // Soft-delete — orders and products retain the creator reference
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