package com.mdau.ukena.promo;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.promo.dto.PromoCodeCreateRequest;
import com.mdau.ukena.promo.dto.PromoCodeDto;
import com.mdau.ukena.promo.dto.PromoCodeStatusResponse;
import com.mdau.ukena.promo.dto.PromoCodeUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    /** The one standing welcome-offer code — admin creates it once in the promo codes
     *  admin page; the guest email-capture flow just looks it up by this exact name. */
    public static final String WELCOME_CODE = "WELCOME10";

    private final PromoCodeRepository repository;
    private final EmailService emailService;

    /** Validates a code for use at checkout — active, unexpired, under its redemption cap. */
    @Transactional(readOnly = true)
    public PromoCode validate(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw ApiException.badRequest("Enter a promo code.");
        }
        String code = rawCode.trim().toUpperCase();
        PromoCode promo = repository.findByCode(code)
                .orElseThrow(() -> ApiException.badRequest("That code isn't valid."));
        if (!promo.isActive()) {
            throw ApiException.badRequest("That code is no longer active.");
        }
        if (promo.getExpiresAt() != null && promo.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("That code has expired.");
        }
        if (promo.getMaxRedemptions() != null && promo.getRedemptionCount() >= promo.getMaxRedemptions()) {
            throw ApiException.badRequest("That code has already been fully redeemed.");
        }
        return promo;
    }

    /** Called once an order using this code has actually been placed. */
    @Transactional
    public void redeem(PromoCode promo) {
        promo.setRedemptionCount(promo.getRedemptionCount() + 1);
        repository.save(promo);
    }

    /** Guest email-capture flow — emails the standing welcome code if one is active.
     *  Silently no-ops (doesn't error the visitor) if the admin hasn't set one up yet. */
    @Transactional(readOnly = true)
    public void captureWelcomeEmail(String email) {
        repository.findByCode(WELCOME_CODE)
                .filter(this::isUsable)
                .ifPresentOrElse(
                        promo -> emailService.sendWelcomeDiscount(email, promo.getCode(), promo.getPercentOff()),
                        () -> log.info("Welcome email capture requested but no active {} code exists", WELCOME_CODE));
    }

    /** Lets the storefront fade promo/coupon UI until the admin has actually
     *  configured something usable — never exposes the codes themselves. */
    @Transactional(readOnly = true)
    public PromoCodeStatusResponse status() {
        List<PromoCode> all = repository.findAll();
        boolean welcomeActive = all.stream()
                .anyMatch(p -> p.getCode().equals(WELCOME_CODE) && isUsable(p));
        boolean anyActive = all.stream().anyMatch(this::isUsable);
        return new PromoCodeStatusResponse(anyActive, welcomeActive);
    }

    private boolean isUsable(PromoCode p) {
        return p.isActive()
                && (p.getExpiresAt() == null || p.getExpiresAt().isAfter(Instant.now()))
                && (p.getMaxRedemptions() == null || p.getRedemptionCount() < p.getMaxRedemptions());
    }

    // ── admin CRUD ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PromoCodeDto> listAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public PromoCodeDto create(PromoCodeCreateRequest req) {
        String code = req.code().trim().toUpperCase();
        if (repository.existsByCode(code)) {
            throw ApiException.conflict("A promo code with this name already exists.");
        }
        PromoCode saved = repository.save(PromoCode.builder()
                .code(code)
                .percentOff(req.percentOff())
                .description(req.description())
                .active(true)
                .expiresAt(req.expiresAt())
                .maxRedemptions(req.maxRedemptions())
                .build());
        return toDto(saved);
    }

    @Transactional
    public PromoCodeDto update(UUID id, PromoCodeUpdateRequest req) {
        PromoCode promo = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Promo code not found"));
        promo.setPercentOff(req.percentOff());
        promo.setDescription(req.description());
        promo.setActive(req.active());
        promo.setExpiresAt(req.expiresAt());
        promo.setMaxRedemptions(req.maxRedemptions());
        return toDto(repository.save(promo));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) throw ApiException.notFound("Promo code not found");
        repository.deleteById(id);
    }

    private PromoCodeDto toDto(PromoCode p) {
        return new PromoCodeDto(p.getId(), p.getCode(), p.getPercentOff(), p.getDescription(),
                p.isActive(), p.getExpiresAt(), p.getMaxRedemptions(), p.getRedemptionCount(), p.getCreatedAt());
    }
}
