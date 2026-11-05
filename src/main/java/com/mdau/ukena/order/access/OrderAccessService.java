package com.mdau.ukena.order.access;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderRepository;
import com.mdau.ukena.order.OrderService;
import com.mdau.ukena.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Lets a guest see their full order (address included) without an account, by
 * proving they can read the inbox the order was placed with — the same idea as
 * Moments Packaging's OTP track-order flow.
 *
 * Requesting a code always answers the same way, so the page never reveals
 * which order numbers or emails exist. Codes last 10 minutes and allow 5 tries;
 * a verified code issues an access token for that one order for 1 hour.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAccessService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderAccessCodeRepository codeRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public void sendCode(TrackCodeRequest req) {
        String email = req.email().toLowerCase().trim();
        Optional<Order> order = orderRepository.findByDisplayId(req.displayId().trim())
                .filter(o -> o.getBuyerEmail().equalsIgnoreCase(email))
                .filter(o -> !OrderService.WALK_IN_EMAIL.equalsIgnoreCase(o.getBuyerEmail()));
        if (order.isEmpty()) {
            log.info("Track code requested for unknown order/email pair");
            return; // same response either way
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.save(OrderAccessCode.builder()
                .displayId(order.get().getDisplayId())
                .email(email)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(Instant.now().plus(CODE_TTL))
                .build());
        emailService.sendOrderAccessCode(order.get().getBuyerEmail(), order.get().getBuyerFullName(),
                order.get().getDisplayId(), code);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public String verify(TrackVerifyRequest req) {
        String email = req.email().toLowerCase().trim();
        OrderAccessCode entry = codeRepository
                .findFirstByDisplayIdAndEmailIgnoreCaseOrderByCreatedAtDesc(req.displayId().trim(), email)
                .orElseThrow(() -> ApiException.badRequest("That code doesn't match. Ask for a new one."));
        if (Instant.now().isAfter(entry.getExpiresAt()))
            throw ApiException.badRequest("That code has expired. Ask for a new one.");
        if (entry.getAttempts() >= MAX_ATTEMPTS)
            throw ApiException.badRequest("Too many tries. Ask for a new code.");
        entry.setAttempts(entry.getAttempts() + 1);
        if (!passwordEncoder.matches(req.code(), entry.getCodeHash())) {
            codeRepository.save(entry);
            throw ApiException.badRequest("That code doesn't match. Check the latest email from us.");
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        entry.setToken(HexFormat.of().formatHex(bytes));
        entry.setTokenExpiresAt(Instant.now().plus(TOKEN_TTL));
        entry.setAttempts(MAX_ATTEMPTS); // a code works once
        codeRepository.save(entry);
        return entry.getToken();
    }

    /**
     * The order the caller may act on: a signed-in buyer who owns it, staff, or
     * anyone holding a valid access token for it. Otherwise "not found" — never
     * "forbidden", so order numbers can't be probed.
     */
    @Transactional(readOnly = true)
    public Order resolve(String displayId, CurrentUser user, String accessToken) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (user != null) {
            String role = user.role() != null ? user.role().name() : "";
            if (role.equals("ROLE_ADMIN") || role.equals("ROLE_SUPPORT")) return order;
            if (order.getBuyer() != null && order.getBuyer().getId().equals(user.id())) return order;
        }
        if (accessToken != null && !accessToken.isBlank()) {
            Optional<OrderAccessCode> entry = codeRepository.findByToken(accessToken.trim());
            if (entry.isPresent() && entry.get().getDisplayId().equals(displayId)
                    && entry.get().getTokenExpiresAt() != null
                    && Instant.now().isBefore(entry.get().getTokenExpiresAt())) {
                return order;
            }
        }
        throw ApiException.notFound("Order not found");
    }
}
