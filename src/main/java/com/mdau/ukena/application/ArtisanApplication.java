package com.mdau.ukena.application;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "artisan_applications", indexes = {
        @Index(name = "idx_applications_status", columnList = "status"),
        @Index(name = "idx_applications_email", columnList = "email"),
        @Index(name = "idx_applications_submitted_at", columnList = "submitted_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ArtisanApplication {

    // Display ID e.g. APP-204
    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 120)
    private String region;

    @Column(nullable = false, length = 120)
    private String craft;

    @Column(name = "years_of_practice", nullable = false)
    private int yearsOfPractice;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String story;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String portrait;

    @Column(name = "work_sample", nullable = false, columnDefinition = "TEXT")
    private String workSample;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private Instant submittedAt;
}