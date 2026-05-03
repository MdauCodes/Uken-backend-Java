package com.mdau.ukena.admin;

import com.mdau.ukena.admin.dto.*;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
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
    private final PayoutRepository payoutRepository;
    private final ReviewRepository reviewRepository;
    private final FeaturedSlotRepository featuredSlotRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Staff management ──────────────────────────────────────────

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

    // ── Buyers ────────────────────────────────────────────────────

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
        log.info("User {} suspended", userId);
    }

    @Transactional
    public void unsuspendBuyer(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setSuspended(false);
        userRepository.save(user);
        log.info("User {} unsuspended", userId);
    }

    // ── Creators ──────────────────────────────────────────────────

    @Transactional
    public void softDeleteCreator(String creatorId) {
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        creator.setDeletedAt(Instant.now());
        creatorRepository.save(creator);
        log.info("Creator {} soft-deleted", creatorId);
    }

    // ── Payouts ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CreatorPayoutDto> listPayouts() {
        return payoutRepository.findAllByOrderByCreatorIdAsc()
                .stream().map(this::toPayoutDto).toList();
    }

    @Transactional
    public CreatorPayoutDto processPayout(String creatorId) {
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));

        PayoutRecord payout = payoutRepository.findByCreatorId(creatorId)
                .orElseGet(() -> PayoutRecord.builder()
                        .creatorId(creatorId)
                        .creator(creator)
                        .build());

        int amount = payout.getPendingPence();
        if (amount <= 0) {
            throw ApiException.badRequest("No pending earnings to pay out");
        }

        // TODO: call Flutterwave Transfer API or M-Pesa B2C here
        log.info("Processing payout of {}p to creator {}", amount, creatorId);

        payout.setPaidThisMonthPence(payout.getPaidThisMonthPence() + amount);
        payout.setTotalLifetimePence(payout.getTotalLifetimePence() + amount);
        payout.setPendingPence(0);
        payout.setLastPaidAt(Instant.now());
        return toPayoutDto(payoutRepository.save(payout));
    }

    // ── Reviews ───────────────────────────────────────────────────

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

    // ── Featured slots ────────────────────────────────────────────

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

    // ── Mappers ───────────────────────────────────────────────────

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