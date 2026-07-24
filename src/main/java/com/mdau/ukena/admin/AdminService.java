package com.mdau.ukena.admin;

import com.mdau.ukena.admin.dto.*;
import com.mdau.ukena.cloudinary.CloudinaryService;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.common.Slugify;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.OrderRepository;
import com.mdau.ukena.payment.EarningsLedger;
import com.mdau.ukena.payment.EarningsLedgerRepository;
import com.mdau.ukena.payment.LedgerStatus;
import com.mdau.ukena.payment.PaymentService;
import com.mdau.ukena.payment.PayoutResult;
import com.mdau.ukena.product.ProductRepository;
import com.mdau.ukena.product.ProductService;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import com.mdau.ukena.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository           userRepository;
    private final CreatorRepository        creatorRepository;
    private final ProductRepository        productRepository;
    private final PayoutRepository         payoutRepository;
    private final ReviewRepository         reviewRepository;
    private final FeaturedSlotRepository   featuredSlotRepository;
    private final EarningsLedgerRepository ledgerRepository;
    private final OrderRepository          orderRepository;
    private final PasswordEncoder          passwordEncoder;
    private final EmailService             emailService;
    private final PaymentService           paymentService;
    private final CloudinaryService        cloudinaryService;

    // ── Staff ────────────────────────────────────────────────

    @Transactional
    public StaffDto createStaff(CreateStaffRequest req, CurrentUser actingUser) {
        if (userRepository.existsByEmail(req.email()))
            throw ApiException.conflict("An account with this email already exists");

        UserRole role = UserRole.ROLE_SUPPORT;
        if ("ADMIN".equalsIgnoreCase(req.role())) {
            requireSuperAdmin(actingUser, "Only the superadmin can create admin accounts");
            role = UserRole.ROLE_ADMIN;
        }

        User staff = User.builder()
                .email(req.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .role(role)
                .build();
        userRepository.save(staff);
        log.info("{} account created: {}", role, req.email());
        return toStaffDto(staff);
    }

    @Transactional(readOnly = true)
    public List<StaffDto> listStaff() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ROLE_SUPPORT
                          || u.getRole() == UserRole.ROLE_ADMIN)
                .map(this::toStaffDto)
                .toList();
    }

    /** Superadmin-only: promotes an existing support account to admin. */
    @Transactional
    public StaffDto promoteToAdmin(UUID targetUserId, CurrentUser actingUser) {
        requireSuperAdmin(actingUser, "Only the superadmin can promote staff to admin");
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.getRole() != UserRole.ROLE_SUPPORT) {
            throw ApiException.badRequest("Only support staff can be promoted to admin");
        }
        target.setRole(UserRole.ROLE_ADMIN);
        userRepository.save(target);
        log.info("{} promoted to admin by {}", target.getEmail(), actingUser.email());
        return toStaffDto(target);
    }

    /** Superadmin-only: demotes an admin back to support. Cannot demote the superadmin. */
    @Transactional
    public StaffDto demoteToSupport(UUID targetUserId, CurrentUser actingUser) {
        requireSuperAdmin(actingUser, "Only the superadmin can demote admins");
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.getRole() != UserRole.ROLE_ADMIN) {
            throw ApiException.badRequest("Only admins can be demoted to support");
        }
        if (Boolean.TRUE.equals(target.getSuperAdmin())) {
            throw ApiException.badRequest("The superadmin account can't be demoted");
        }
        target.setRole(UserRole.ROLE_SUPPORT);
        userRepository.save(target);
        log.info("{} demoted to support by {}", target.getEmail(), actingUser.email());
        return toStaffDto(target);
    }

    private void requireSuperAdmin(CurrentUser actingUser, String message) {
        boolean isSuperAdmin = userRepository.findById(actingUser.id())
                .map(u -> Boolean.TRUE.equals(u.getSuperAdmin()))
                .orElse(false);
        if (!isSuperAdmin) throw ApiException.forbidden(message);
    }

    private StaffDto toStaffDto(User u) {
        return new StaffDto(u.getId().toString(), u.getEmail(), u.getFullName(),
                u.getRole().name(), Boolean.TRUE.equals(u.getSuperAdmin()));
    }

    // ── Buyers ───────────────────────────────────────────────

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
    }

    @Transactional
    public void unsuspendBuyer(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setSuspended(false);
        userRepository.save(user);
    }

    /**
     * Fully converts an existing account (any non-creator role) into a
     * creator — used from the Buyers/Users page when there's no application
     * on file to source craft/region/story from, so the admin supplies them
     * directly. A person only ever has one role, so this is a conversion,
     * never an addition: the account keeps its id, email and password, but
     * its role and dashboard access change completely.
     */
    @Transactional
    public void convertToCreator(UUID userId, ConvertToCreatorRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getRole() == UserRole.ROLE_CREATOR) {
            throw ApiException.badRequest("This account is already a creator");
        }

        String slug = Slugify.slugify(user.getFullName());
        String finalSlug = slug;
        int count = 1;
        while (creatorRepository.existsById(finalSlug)) {
            finalSlug = slug + count++;
        }

        Creator creator = Creator.builder()
                .id(finalSlug)
                .firstName(user.getFullName().split(" ")[0])
                .fullName(user.getFullName())
                .craft(req.craft())
                .region(req.region())
                .hook(req.hook())
                .image("").portraitImage("").headerImage("")
                .build();
        creatorRepository.save(creator);

        UserRole previousRole = user.getRole();
        user.setRole(UserRole.ROLE_CREATOR);
        user.setCreatorId(finalSlug);
        userRepository.save(user);

        log.info("Converted {} account to creator via admin users page: {} ({})",
                previousRole, user.getEmail(), finalSlug);
        // No temp password — they keep whatever password they already had.
        emailService.sendCreatorWelcome(user.getEmail(), user.getFullName(), finalSlug, null);
    }

    // ── Creators ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminCreatorRow> listCreators(String status) {
        String param = (status == null || status.isBlank()) ? null : status.toUpperCase();
        List<Creator> creators = creatorRepository.findAllForAdmin(param);
        Map<String, String> emailByCreatorId = userRepository
                .findAllByCreatorIdIn(creators.stream().map(Creator::getId).toList())
                .stream()
                .collect(Collectors.toMap(User::getCreatorId, User::getEmail, (a, b) -> a));
        return creators.stream()
                .map(c -> toAdminCreatorRow(c, emailByCreatorId.get(c.getId())))
                .toList();
    }

    @Transactional
    public void suspendCreator(String creatorId) {
        Creator creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        creator.setDeletedAt(Instant.now());
        creatorRepository.save(creator);
        userRepository.findByCreatorId(creatorId).ifPresent(u -> {
            u.setSuspended(true);
            userRepository.save(u);
        });
        int n = productRepository.suspendAllByCreatorId(creatorId);
        log.info("Creator {} suspended - {} products suspended", creatorId, n);
    }

    @Transactional
    public void unsuspendCreator(String creatorId) {
        Creator creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        creator.setDeletedAt(null);
        creatorRepository.save(creator);
        userRepository.findByCreatorId(creatorId).ifPresent(u -> {
            u.setSuspended(false);
            userRepository.save(u);
        });
        int n = productRepository.restoreAllByCreatorId(creatorId);
        log.info("Creator {} unsuspended - {} products restored", creatorId, n);
    }

    // ── Payouts ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CreatorPayoutDto> listPayouts() {
        return payoutRepository.findAllByOrderByCreatorIdAsc()
                .stream().map(this::toPayoutDto).toList();
    }

    @Transactional
    public CreatorPayoutDto processPayout(String creatorId,
                                          String accountNumber, String accountName) {
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        PayoutRecord payout = payoutRepository.findByCreatorId(creatorId)
                .orElseGet(() -> PayoutRecord.builder()
                        .creatorId(creatorId).creator(creator).build());
        int amount = payout.getPendingPence();
        if (amount <= 0)
            throw ApiException.badRequest("No pending earnings to pay out");
        PayoutResult result = paymentService.gatewayPayout(
                creatorId, accountNumber, accountName);
        if (result.success()) {
            payout.setPaidThisMonthPence(payout.getPaidThisMonthPence() + amount);
            payout.setTotalLifetimePence(payout.getTotalLifetimePence() + amount);
            payout.setPendingPence(0);
            payout.setLastPaidAt(Instant.now());
            payoutRepository.save(payout);
            userRepository.findByCreatorId(creatorId).ifPresent(u ->
                    emailService.sendPayoutConfirmation(
                            u.getEmail(), u.getFullName(), amount, "GBP"));
        }
        return toPayoutDto(payout);
    }

    // ── Reviews ──────────────────────────────────────────────

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

    // ── Featured slots ───────────────────────────────────────

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

    // ── Revenue dashboard ────────────────────────────────────

    @Transactional(readOnly = true)
    public UkenaRevenueDto getRevenue() {
        List<EarningsLedger> all = ledgerRepository.findAll();

        // Split into Uken-owned vs creator entries
        List<EarningsLedger> ukenEntries = all.stream()
                .filter(e -> ProductService.UKENA_CREATOR_ID.equals(e.getCreatorId()))
                .toList();
        List<EarningsLedger> creatorEntries = all.stream()
                .filter(e -> !ProductService.UKENA_CREATOR_ID.equals(e.getCreatorId()))
                .toList();

        int ukenaProductRevenue  = ukenEntries.stream()
                .mapToInt(EarningsLedger::getGrossPence).sum();
        int platformCommission   = creatorEntries.stream()
                .mapToInt(e -> e.getGrossPence() - e.getNetPence()).sum();
        int creatorPayouts       = creatorEntries.stream()
                .mapToInt(EarningsLedger::getNetPence).sum();
        int totalRevenue         = ukenaProductRevenue
                + creatorEntries.stream().mapToInt(EarningsLedger::getGrossPence).sum();

        long totalOrderCount = orderRepository.countPaidOrders();
        long ukenaOrderCount = ledgerRepository.countByCreatorId(
                ProductService.UKENA_CREATOR_ID);

        // Per-product breakdown — group ledger entries by orderItemId's product
        // We use productName stored on OrderItem (already denormalised in the ledger
        // via the order item). We group by creatorId+productName as a proxy since
        // productId is not stored on the ledger directly.
        List<UkenaRevenueDto.ProductRevenueLine> topProducts = all.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCreatorId() + "|" + e.getOrderItemId()))
                .entrySet().stream()
                .map(entry -> {
                    EarningsLedger sample = entry.getValue().get(0);
                    boolean isUken = ProductService.UKENA_CREATOR_ID
                            .equals(sample.getCreatorId());
                    int gross = entry.getValue().stream()
                            .mapToInt(EarningsLedger::getGrossPence).sum();
                    // orderItemId is UUID — use it as product key for grouping
                    return new UkenaRevenueDto.ProductRevenueLine(
                            sample.getOrderItemId().toString(),
                            "",   // productName resolved below
                            isUken,
                            1,    // each ledger entry = 1 order line
                            gross);
                })
                // Re-group by orderItemId to sum units across orders
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> list.stream()
                                .sorted(Comparator.comparingInt(
                                        UkenaRevenueDto.ProductRevenueLine::grossPence)
                                        .reversed())
                                .limit(20)
                                .toList()));

        return new UkenaRevenueDto(
                totalRevenue,
                platformCommission,
                ukenaProductRevenue,
                platformCommission + ukenaProductRevenue,
                creatorPayouts,
                totalOrderCount,
                ukenaOrderCount,
                topProducts);
    }

    // ── Mappers ──────────────────────────────────────────────

    private AdminCreatorRow toAdminCreatorRow(Creator c, String email) {
        return new AdminCreatorRow(c.getId(), c.getFirstName(), c.getFullName(),
                c.getCraft(), c.getRegion(), c.getImage(),
                c.getDeletedAt() != null, c.getCreatedAt(), email);
    }

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