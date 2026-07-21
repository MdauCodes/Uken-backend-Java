package com.mdau.ukena.email;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Grants a {@code ROLE_SUPPORT} (or {@code ROLE_ADMIN}) user access to a
 * mailbox. Admins bypass this table entirely (see {@link MailboxAccessGuard});
 * rows here only matter for staff.
 */
@Entity
@Table(name = "mailbox_access", indexes = {
        @Index(name = "idx_mailbox_access_mailbox", columnList = "mailbox_id"),
        @Index(name = "idx_mailbox_access_user", columnList = "user_id"),
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_mailbox_access_mailbox_user", columnNames = {"mailbox_id", "user_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MailboxAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
