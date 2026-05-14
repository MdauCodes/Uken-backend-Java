package com.mdau.ukena.admin;

import com.mdau.ukena.admin.dto.*;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.payment.PaymentService;
import com.mdau.ukena.payment.PayoutResult;
import com.mdau.ukena.product.ProductRepository;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import com.mdau.ukena.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final ProductRepository productRepository;
    private final PayoutRepository payoutRepository;
    private final ReviewRepository reviewRepository;
    private final FeaturedSlotRepository featuredSlotRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PaymentService paymentService;

    // -- Staff management ---------------------------------------------------

    @Transactional
    public StaffDto createStaff(CreateStaffRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User staff = User.builder()
                .email(req.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .role(UserRole.ROLE_SUPPORT)
                .build();
        userRepository.save(staff);
        log.info("Support staff account created: {}", req.email());
        return new StaffDto(staff.getId().toString(), staff.getEmail(),
                staff.getFullName(), staff.getRole().name());
    }

    @Transactional(readOnly = true)
    public List<StaffDto> listStaff() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ROLE_SUPPORT
                          || u.getRole() == UserRole.ROLE_ADMIN)
                .map(u -> new StaffDto(u.getId().toString(), u.getEmail(),
                        u.getFullName(), u.getRole().name()))
                .toList();
    }

    // -- Buyers -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminBuyerRow> listBuyers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ROLE_BUYER)
                .map(u -> new AdminBuyerRow(u.getId().toString(),
                        u.getFullName(), u.getEmail(), u.isSuspended()))
                .toList();
    }

    @Transactional
    public void suspendBuyer(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setSuspended(true);
        userRepository.save(user);
        log.info("Buyer {} suspended", userId);
    }

    @Transactional
    public void unsuspendBuyer(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setSuspended(false);
        userRepository.save(user);
        log.info("Buyer {} unsuspended", userId);
    }

    // -- Creators -----------------------------------------------------------

    @Transactional
    public void suspendCreator(String creatorId) {
        // 1. Soft-delete the creator profile (hides from storefront)
        Creator creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        creator.setDeletedAt(Instant.now());
        creatorRepository.save(creator);

        // 2. Suspend the linked user account (blocks login)
        userRepository.findByCreatorId(creatorId).ifPresent(user -> {
            user.setSuspended(true);
            userRepository.save(user);
            log.info("Creator user account suspended: {}", user.getEmail());
        });

        // 3. Suspend all their products (removes from shop)
        int suspended = productRepository.suspendAllByCreatorId(creatorId);
        log.info("Creator {} suspended — {} products suspended", creatorId, suspended);
    }

    @Transactional
    public void unsuspendCreator(String creatorId) {
        // 1. Restore the creator profile
        Creator creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        creator.setDeletedAt(null);
        creatorRepository.save(creator);

        // 2. Unsuspend the linked user account
        userRepository.findByCreatorId(creatorId).ifPresent(user -> {
            user.setSuspended(false);
            userRepository.save(user);
            log.info("Creator user account unsuspended: {}", user.getEmail());
        });

        // 3. Restore their products
        int restored = productRepository.restoreAllByCreatorId(creatorId);
        log.info("Creator {} unsuspended — {} products restored", creatorId, restored);
    }

    // -- Payouts ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CreatorPayoutDto> listPayouts() {
        return payoutRepository.findAllByOrderByCreatorIdAsc()
                .stream().map(this::toPayoutDto).toList();
    }

    @Transactional
    public CreatorPayoutDto processPayout(String creatorId, String accountNumber,
                                          String accountName) {
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));

        PayoutRecord payout = payoutRepository.findByCreatorId(creatorId)
                .orElseGet(() -> PayoutRecord.builder()
                        .creatorId(creatorId)
                        .creator(creator)
                        .build());

        int amount = payout.getPendingPence();
        if (amount <= 0)
            throw ApiException.badRequest("No pending earnings to pay out");

        PayoutResult result = paymentService.gatewayPayout(
                creatorId, accountNumber, accountName);
        log.info("Payout for creator {}: success={} ref={} msg={}",
                creatorId, result.success(), result.gatewayRef(), result.message());

        if (result.success()) {
            payout.setPaidThisMonthPence(payout.getPaidThisMonthPence() + amount);
            payout.setTotalLifetimePence(payout.getTotalLifetimePence() + amount);
            payout.setPendingPence(0);
            payout.setLastPaidAt(Instant.now());
            payoutRepository.save(payout);

            userRepository.findByCreatorId(creatorId).ifPresent(user ->
                    emailService.sendPayoutConfirmation(
                            user.getEmail(), user.getFullName(), amount, "GBP"));
        }

        return toPayoutDto(payout);
    }

    // -- Reviews ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ProductReviewDto> listReviews() {
        return reviewRepository.findAllByOrderBySubmittedAtDesc()
                .stream().map(this::toReviewDto).toList();
    }

    @Transactional
    public ProductReviewDto updateReviewStatus(String reviewId, ReviewStatusUpdate req) {
        ReviewRecord review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review not found"));
        try {
            review.setStatus(ReviewStatus.valueOf(req.status().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid review status: " + req.status());
        }
        return toReviewDto(reviewRepository.save(review));
    }

    // -- Featured slots -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<FeaturedSlotDto> getFeaturedSlots() {
        return featuredSlotRepository.findAllByOrderByPositionAsc()
                .stream().map(this::toSlotDto).toList();
    }

    @Transactional
    public List<FeaturedSlotDto> updateFeaturedSlots(List<FeaturedSlotDto> slots) {
        slots.forEach(dto -> {
            FeaturedSlot slot = featuredSlotRepository.findById(dto.position())
                    .orElseGet(() -> new FeaturedSlot(dto.position(), null, null));
            Creator creator = dto.creatorId() != null
                    ? creatorRepository.findById(dto.creatorId()).orElse(null) : null;
            slot.setCreator(creator);
            featuredSlotRepository.save(slot);
        });
        return getFeaturedSlots();
    }

    // -- Mappers ------------------------------------------------------------

    private CreatorPayoutDto toPayoutDto(PayoutRecord p) {
        Creator c = p.getCreator();
        return new CreatorPayoutDto(c.getId(), c.getFullName(), c.getRegion(),
                c.getCraft(), p.getPendingPence(), p.getPaidThisMonthPence(),
                p.getTotalLifetimePence(), p.getLastPaidAt());
    }

    private ProductReviewDto toReviewDto(ReviewRecord r) {
        return new ProductReviewDto(r.getId(),
                r.getProduct() != null ? r.getProduct().getId() : null,
                r.getProduct() != null ? r.getProduct().getName() : null,
                r.getBuyerName(), r.getRating(), r.getBody(),
                r.getSubmittedAt(), r.getStatus().name());
    }

    private FeaturedSlotDto toSlotDto(FeaturedSlot s) {
        return new FeaturedSlotDto(s.getPosition(),
                s.getCreator() != null ? s.getCreator().getId() : null,
                s.getProduct() != null ? s.getProduct().getId() : null);
    }
}