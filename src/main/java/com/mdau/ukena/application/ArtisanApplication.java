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

    @Column(columnDefinition = "TEXT")
    private String portrait;

    @Column(name = "work_sample", columnDefinition = "TEXT")
    private String workSample;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** INDIVIDUAL or BUSINESS. Null on older applications = individual. */
    @Column(name = "applicant_type", length = 20)
    private String applicantType;

    /** Phone or WhatsApp the team calls about the application. */
    @Column(length = 40)
    private String phone;

    /** Business applicants: the name buyers will see. */
    @Column(name = "trading_name", length = 160)
    private String tradingName;

    /** Sole trader, limited company, cooperative, farm group, community group. */
    @Column(name = "business_type", length = 60)
    private String businessType;

    /** Company registration number or KRA PIN, if they have one. */
    @Column(name = "registration_number", length = 60)
    private String registrationNumber;

    @Column(name = "member_count")
    private Integer memberCount;

    @Column(name = "contact_person", length = 120)
    private String contactPerson;

    /** The account that applied (account-first applications and signed-in buyers). Null on older ones. */
    @Column(name = "user_id")
    private java.util.UUID userId;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private Instant submittedAt;
}