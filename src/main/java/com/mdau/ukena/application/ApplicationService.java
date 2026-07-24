package com.mdau.ukena.application;

import com.mdau.ukena.application.dto.*;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.common.Slugify;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import com.mdau.ukena.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepo;
    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public ApplicationDto submit(SubmitRequest req) {
        if (applicationRepo.existsByEmail(req.email())) {
            throw ApiException.conflict(
                    "An application with this email has already been submitted");
        }
        String id = generateApplicationId();
        List<String> photos = req.photos() != null ? req.photos() : List.of();
        String portrait   = photos.isEmpty() ? null : photos.get(0);
        String workSample = photos.size() > 1 ? photos.get(1) : portrait;

        ArtisanApplication app = ArtisanApplication.builder()
                .id(id).fullName(req.fullName()).email(req.email())
                .region(req.region()).craft(req.craft())
                .yearsOfPractice(req.yearsOfPractice()).story(req.story())
                .portrait(portrait).workSample(workSample)
                .status(ApplicationStatus.PENDING).build();

        applicationRepo.save(app);
        emailService.sendApplicationReceived(req.email(), req.fullName(), id);
        return toDto(app);
    }

    /** Used by the join form to warn a returning buyer before they fill out a whole application. */
    @Transactional(readOnly = true)
    public boolean emailRegistered(String email) {
        String normalized = email.trim();
        return userRepository.existsByEmailIgnoreCase(normalized)
                || applicationRepo.existsByEmailIgnoreCase(normalized);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> listAll() {
        return applicationRepo.findAllByOrderBySubmittedAtDesc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDto getById(String id) {
        return toDto(applicationRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Application not found")));
    }

    @Transactional
    public ApplicationDto updateStatus(String id, StatusUpdateRequest req) {
        ArtisanApplication app = applicationRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Application not found"));

        ApplicationStatus newStatus = parseStatus(req.status());

        // Guard: if already in this status, just return — no duplicate provisioning
        if (app.getStatus() == newStatus) {
            return toDto(app);
        }

        if (req.notes() != null) app.setNotes(req.notes());

        if (newStatus == ApplicationStatus.APPROVED) {
            // Applied before saving the status — if this throws (role conflict
            // needing confirmation), the application stays in its prior status
            // instead of being silently marked APPROVED with nothing provisioned.
            handleApproval(app, Boolean.TRUE.equals(req.confirmRoleChange()));
        }

        app.setStatus(newStatus);
        applicationRepo.save(app);

        return toDto(app);
    }

    /**
     * All approval logic lives here. Called only when transitioning TO APPROVED.
     * Three possible cases:
     *   1. No account exists yet for this email — fresh provisioning (happy path).
     *   2. An account exists and is already a creator — re-approval, just
     *      re-activate it (handles a previously suspended/rejected creator).
     *   3. An account exists with a different role (buyer, admin, support) —
     *      converting it to a creator changes what that account can do, so it
     *      requires an explicit {@code confirmRoleChange} from the admin. A
     *      person only ever has one role; there's no such thing as "buyer and
     *      creator" on the same account.
     */
    private void handleApproval(ArtisanApplication app, boolean confirmRoleChange) {
        String email = app.getEmail().toLowerCase().trim();
        Optional<User> existingOpt = userRepository.findByEmail(email);

        if (existingOpt.isEmpty()) {
            String tempPassword = provisionNewCreatorAccount(app);
            String creatorId = userRepository.findByEmail(email)
                    .map(User::getCreatorId)
                    .orElse(Slugify.slugify(app.getFullName()));
            log.info("Provisioned new creator account: {} ({})", email, creatorId);
            emailService.sendCreatorWelcome(
                    app.getEmail(), app.getFullName(), creatorId, tempPassword);
            return;
        }

        User existingUser = existingOpt.get();

        if (existingUser.getRole() == UserRole.ROLE_CREATOR) {
            // Re-activate a previously suspended/rejected creator account
            existingUser.setSuspended(false);
            userRepository.save(existingUser);

            if (existingUser.getCreatorId() != null) {
                creatorRepository.findById(existingUser.getCreatorId()).ifPresent(c -> {
                    c.setDeletedAt(null);
                    creatorRepository.save(c);
                });
            }

            log.info("Re-activated existing creator account: {}", email);
            emailService.sendCreatorWelcome(
                    app.getEmail(), app.getFullName(),
                    existingUser.getCreatorId() != null ? existingUser.getCreatorId() : "",
                    null);
            return;
        }

        // Account exists with a non-creator role — require explicit confirmation
        if (!confirmRoleChange) {
            throw ApiException.conflict(
                    "ROLE_CONFLICT: This email already has an existing "
                            + existingUser.getRole().name()
                            + " account. Approving will convert that account into a creator"
                            + " — confirm to proceed.");
        }

        String finalSlug = provisionCreatorProfile(app);
        UserRole previousRole = existingUser.getRole();
        existingUser.setRole(UserRole.ROLE_CREATOR);
        existingUser.setCreatorId(finalSlug);
        existingUser.setSuspended(false);
        userRepository.save(existingUser);

        log.info("Converted existing {} account to creator: {} ({})",
                previousRole, email, finalSlug);
        // No temp password — they keep whatever password they already had.
        emailService.sendCreatorWelcome(
                app.getEmail(), app.getFullName(), finalSlug, null);
    }

    /** Creates the {@code creators} row only — shared by fresh provisioning and role conversion. */
    private String provisionCreatorProfile(ArtisanApplication app) {
        String slug = Slugify.slugify(app.getFullName());
        String finalSlug = slug;
        int count = 1;
        while (creatorRepository.existsById(finalSlug)) {
            finalSlug = slug + count++;
        }

        // Use empty string fallback for NOT NULL image columns when no portrait uploaded
        String portrait = app.getPortrait() != null ? app.getPortrait() : "";

        Creator creator = Creator.builder()
                .id(finalSlug)
                .firstName(app.getFullName().split(" ")[0])
                .fullName(app.getFullName())
                .craft(app.getCraft())
                .region(app.getRegion())
                .hook(app.getStory().length() > 120
                        ? app.getStory().substring(0, 117) + "..."
                        : app.getStory())
                .image(portrait)
                .portraitImage(portrait)
                .headerImage(portrait)
                .build();
        creatorRepository.save(creator);
        return finalSlug;
    }

    private String provisionNewCreatorAccount(ArtisanApplication app) {
        String finalSlug = provisionCreatorProfile(app);

        String tempPassword = "Ukena"
                + ThreadLocalRandom.current().nextInt(1000, 9999) + "!";
        User user = User.builder()
                .email(app.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .fullName(app.getFullName())
                .role(UserRole.ROLE_CREATOR)
                .creatorId(finalSlug)
                .build();
        userRepository.save(user);

        return tempPassword;
    }

    private String generateApplicationId() {
        int rand = ThreadLocalRandom.current().nextInt(100, 9999);
        String id = "APP-" + rand;
        while (applicationRepo.existsById(id)) {
            id = "APP-" + ThreadLocalRandom.current().nextInt(100, 9999);
        }
        return id;
    }

    private ApplicationStatus parseStatus(String status) {
        try { return ApplicationStatus.valueOf(status.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid status: " + status); }
    }

    private ApplicationDto toDto(ArtisanApplication a) {
        return new ApplicationDto(
                a.getId(), a.getFullName(), a.getEmail(), a.getRegion(),
                a.getCraft(), a.getYearsOfPractice(), a.getStory(),
                a.getSubmittedAt() != null ? a.getSubmittedAt().toString() : null,
                a.getStatus().name(), a.getPortrait(), a.getWorkSample(),
                a.getNotes());
    }
}