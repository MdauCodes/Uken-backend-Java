package com.mdau.ukena.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Immutable record of a moderation/admin action — who did what, to what, and when.
 *  Never updated or deleted once written; that's the point of an audit trail. */
@Entity
@Table(name = "audit_log_entries", indexes = {
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_entity",     columnList = "entity_type,entity_id"),
        @Index(name = "idx_audit_actor",      columnList = "actor_email"),
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null when the action was taken by the system itself (e.g. an automated job), not a person. */
    @Column(name = "actor_email", length = 254)
    private String actorEmail;

    @Column(name = "actor_role", length = 40)
    private String actorRole;

    /** e.g. "PRODUCT_SUSPENDED", "PRODUCT_REINSTATED", "PRODUCT_DELETED", "CREATOR_SUSPENDED". */
    @Column(nullable = false, length = 60)
    private String action;

    /** e.g. "PRODUCT", "CREATOR". */
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 80)
    private String entityId;

    /** Human-readable snapshot of the entity's name at the time of the action —
     *  a product/creator can be renamed or vanish later, this stays accurate. */
    @Column(name = "entity_name", length = 300)
    private String entityName;

    @Column(length = 500)
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
