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

    @Value("${ukena.email.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = User.builder()
                .email(req.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .role(UserRole.ROLE_BUYER)
                .build();
        userRepository.save(user);

        // Link any guest orders placed with this email
        orderService.linkGuestOrders(user.getEmail(), user);

        return new AuthResponse(jwtService.issue(user), toDto(user));
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        User user = userRepository.findByEmail(req.email())
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
        userRepository.findByEmail(req.email().toLowerCase().trim()).ifPresent(user -> {
            resetTokenRepository.deleteExpired(Instant.now());
            String otp = String.format("%06d",
                    ThreadLocalRandom.current().nextInt(0, 1_000_000));
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(otp)
                    .user(user)
                    .expiresAt(Instant.now().plusSeconds(600))
                    .used(false)
                    .build();
            resetTokenRepository.save(resetToken);
            emailService.sendPasswordReset(user.getEmail(), user.getFullName(), otp);
            log.info("OTP issued for user {}", user.getId());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(req.token())
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired OTP"));
        if (resetToken.isUsed()) {
            throw ApiException.badRequest("This OTP has already been used");
        }
        if (Instant.now().isAfter(resetToken.getExpiresAt())) {
            throw ApiException.badRequest("This OTP has expired");
        }
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
        log.info("Password reset completed for user {}", user.getId());
    }

    private AuthUserDto toDto(User user) {
        return new AuthUserDto(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getCreatorId()
        );
    }
}