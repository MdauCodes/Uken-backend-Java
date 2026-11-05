package com.mdau.ukena.order.access;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A 6-digit code emailed to the address an order was placed with, so a guest
 * can see their full order (address included) without an account. Verifying
 * the code issues a short-lived access token for that one order.
 */
@Entity
@Table(name = "order_access_codes", indexes = {
        @Index(name = "idx_order_access_order", columnList = "display_id"),
        @Index(name = "idx_order_access_token", columnList = "token")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderAccessCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "display_id", nullable = false, length = 20)
    private String displayId;

    @Column(nullable = false, length = 254)
    private String email;

    /** BCrypt hash — the code itself is never stored. */
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 64)
    private String token;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
