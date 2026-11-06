package com.mdau.ukena.auth;

import com.mdau.ukena.auth.dto.ForgotPasswordRequest;
import com.mdau.ukena.auth.dto.RegisterRequest;
import com.mdau.ukena.auth.dto.ResetPasswordRequest;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.common.SecurePasswords;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.OrderService;
import com.mdau.ukena.security.JwtService;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import com.mdau.ukena.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetAndLinkingTest {

    private UserRepository users;
    private PasswordResetTokenRepository tokens;
    private EmailService email;
    private OrderService orders;
    private AuthService auth;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final List<PasswordResetToken> saved = new ArrayList<>();
    private User ella;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(PasswordResetTokenRepository.class);
        email = mock(EmailService.class);
        orders = mock(OrderService.class);
        JwtService jwt = mock(JwtService.class);
        when(jwt.issue(any())).thenReturn("jwt");
        ella = User.builder().id(UUID.randomUUID()).email("ella@example.com").fullName("Ella")
                .passwordHash(encoder.encode("old-password")).role(UserRole.ROLE_BUYER).build();
        when(users.findByEmail("ella@example.com")).thenReturn(Optional.of(ella));
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID()); // as the database would
            return u;
        });
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(inv -> {
            PasswordResetToken t = inv.getArgument(0);
            if (!saved.contains(t)) saved.add(t);
            return t;
        });
        when(tokens.findFirstByUserEmailAndUsedFalseOrderByCreatedAtDesc(anyString())).thenAnswer(inv ->
                saved.stream().filter(t -> !t.isUsed() && t.getUser().getEmail().equals(inv.getArgument(0)))
                        .reduce((a, b) -> b));
        auth = new AuthService(users, encoder, jwt, mock(AuthenticationManager.class), email, tokens, orders);
    }

    private String requestCode() {
        auth.forgotPassword(new ForgotPasswordRequest("Ella@example.com"));
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).sendPasswordReset(eq("ella@example.com"), eq("Ella"), code.capture());
        return code.getValue();
    }

    @Test
    void theCodeIsStoredHashedAndOnlyWorksForItsOwnEmail() {
        String code = requestCode();
        assertThat(saved.get(0).getCodeHash()).isNotEqualTo(code);
        assertThat(saved.get(0).getToken()).isNotEqualTo(code);

        assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest("someone@else.com", code, "new-password-1")))
                .isInstanceOf(ApiException.class);
        auth.resetPassword(new ResetPasswordRequest("ella@example.com", code, "new-password-1"));
        assertThat(encoder.matches("new-password-1", ella.getPasswordHash())).isTrue();
    }

    @Test
    void locksAfterFiveWrongTries() {
        String code = requestCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest("ella@example.com", wrong, "new-password-1")))
                    .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest("ella@example.com", code, "new-password-1")))
                .hasMessageContaining("Too many tries");
        assertThat(encoder.matches("old-password", ella.getPasswordHash())).isTrue();
    }

    @Test
    void registeringDoesNotClaimGuestOrdersButAResetDoes() {
        when(users.existsByEmail("new@example.com")).thenReturn(false);
        auth.register(new RegisterRequest("new@example.com", "long-enough-1", "Newcomer", null, null, null, null));
        verify(orders, never()).linkGuestOrders(anyString(), any());

        String code = requestCode();
        auth.resetPassword(new ResetPasswordRequest("ella@example.com", code, "new-password-1"));
        verify(orders).linkGuestOrders("ella@example.com", ella);
    }

    @Test
    void generatedPasswordsAreLongAndDifferent() {
        var seen = new HashSet<String>();
        for (int i = 0; i < 200; i++) {
            String p = SecurePasswords.generate();
            assertThat(p).hasSize(15).doesNotContain("0", "O", "1", "l", "I");
            seen.add(p);
        }
        assertThat(seen).hasSize(200);
    }
}
