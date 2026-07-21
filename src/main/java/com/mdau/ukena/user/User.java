package com.mdau.ukena.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_creator_id", columnList = "creator_id"),
        @Index(name = "idx_users_role", columnList = "role")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.ROLE_BUYER;

    @Column(name = "creator_id", length = 80)
    private String creatorId;

    @Builder.Default
    @Column(nullable = false)
    private boolean suspended = false;

    /** Null/false for everyone except the one account that can create,
     *  promote, or demote other admin accounts. Nullable (not a primitive
     *  default) so adding this column is a safe no-op ALTER TABLE on the
     *  existing, populated users table. */
    @Column(name = "super_admin")
    private Boolean superAdmin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}