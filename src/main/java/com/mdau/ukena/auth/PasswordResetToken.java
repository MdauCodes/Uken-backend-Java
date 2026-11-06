package com.mdau.ukena.auth;

import com.mdau.ukena.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_prt_token",   columnList = "token"),
        @Index(name = "idx_prt_user_id", columnList = "user_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean used = false;

    /** BCrypt hash of the 6-digit code. {@code token} is now just a random unique id. */
    @Column(name = "code_hash", length = 100)
    private String codeHash;

    /** Wrong guesses so far; locked at 5. Nullable for rows made before this existed. */
    @Column(name = "attempts")
    private Integer attempts;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}