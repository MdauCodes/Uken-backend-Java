package com.mdau.ukena.auth;

import com.mdau.ukena.auth.dto.*;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.OrderService;
import com.mdau.ukena.security.JwtService;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import com.mdau.ukena.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final OrderService orderService;

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private static final int MAX_RESET_ATTEMPTS = 5;

    @Value("${ukena.email.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email().toLowerCase().trim())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        boolean business = "BUSINESS".equals(req.accountType());
        if (business && (req.companyName() == null || req.companyName().isBlank())) {
            throw ApiException.badRequest("Enter your company name");
        }
        User user = User.builder()
                .email(req.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .role(UserRole.ROLE_BUYER)
                .accountType(business ? "BUSINESS" : "INDIVIDUAL")
                .companyName(business ? req.companyName().trim() : null)
                .jobTitle(business ? blankToNull(req.jobTitle()) : null)
                .vatNumber(business ? blankToNull(req.vatNumber()) : null)
                .build();
        userRepository.save(user);
        // Guest orders placed with this email are NOT linked here: the email isn't
        // proven yet, and linking would hand someone else's orders (and address) to
        // whoever registers first. They're linked once the inbox is proven — after a
        // password reset, or when the buyer opens one with an emailed order code.
        return new AuthResponse(jwtService.issue(user), toDto(user));
    }

    public AuthResponse login(LoginRequest req) {
        // Normalize so "User@Email.com" matches the stored "user@email.com"
        String email = req.email().toLowerCase().trim();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.password()));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.isSuspended()) {
            throw ApiException.forbidden("Your account has been suspended");
        }
        return new AuthResponse(jwtService.issue(user), toDto(user));
    }

    public AuthUserDto me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return toDto(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.email().toLowerCase().trim();
        userRepository.findByEmail(email).ifPresent(user -> {
            resetTokenRepository.deleteExpired(Instant.now());
            resetTokenRepository.invalidateForUser(user.getId());
            String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(java.util.UUID.randomUUID().toString())
                    .codeHash(passwordEncoder.encode(otp))
                    .attempts(0)
                    .user(user)
                    .expiresAt(Instant.now().plusSeconds(600))
                    .used(false)
                    .build();
            resetTokenRepository.save(resetToken);
            emailService.sendPasswordReset(user.getEmail(), user.getFullName(), otp);
            log.info("OTP issued for user {}", user.getId());
        });
    }

    /**
     * The code only works for the account it was sent to, is checked against a
     * hash, and locks after 5 wrong tries — so it can't be guessed across accounts.
     * Wrong tries are committed even though the request fails.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(ResetPasswordRequest req) {
        String email = req.email().toLowerCase().trim();
        PasswordResetToken resetToken = resetTokenRepository
                .findFirstByUserEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> ApiException.badRequest("That code doesn't match. Ask for a new one."));
        if (Instant.now().isAfter(resetToken.getExpiresAt())) {
            throw ApiException.badRequest("That code has expired. Ask for a new one.");
        }
        int attempts = resetToken.getAttempts() != null ? resetToken.getAttempts() : 0;
        if (resetToken.getCodeHash() == null || attempts >= MAX_RESET_ATTEMPTS) {
            throw ApiException.badRequest("Too many tries. Ask for a new code.");
        }
        if (!passwordEncoder.matches(req.token(), resetToken.getCodeHash())) {
            resetToken.setAttempts(attempts + 1);
            resetTokenRepository.save(resetToken);
            throw ApiException.badRequest("That code doesn't match. Check the latest email from us.");
        }
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
        // They've just proven they read this inbox — guest orders placed with it are theirs.
        orderService.linkGuestOrders(user.getEmail(), user);
        log.info("Password reset completed for user {}", user.getId());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Signs a user in straight after an account is created elsewhere (e.g. an account-first maker application). */
    public AuthResponse issueFor(User user) {
        return new AuthResponse(jwtService.issue(user), toDto(user));
    }

    AuthUserDto toDto(User user) {
        return new AuthUserDto(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getCreatorId(),
                Boolean.TRUE.equals(user.getSuperAdmin()),
                user.getAccountType() != null ? user.getAccountType() : "INDIVIDUAL",
                user.getCompanyName()
        );
    }
}