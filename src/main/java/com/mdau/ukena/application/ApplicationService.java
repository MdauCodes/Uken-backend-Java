package com.mdau.ukena.application;

import com.mdau.ukena.application.dto.*;
import com.mdau.ukena.common.ApiException;
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
        app.setStatus(newStatus);
        applicationRepo.save(app);

        if (newStatus == ApplicationStatus.APPROVED) {
            handleApproval(app);
        }

        return toDto(app);
    }

    /**
     * All approval logic lives here. Called only when transitioning TO APPROVED.
     * Three possible cases:
     *   1. User + Creator both exist (re-approval of a previously approved/suspended creator)
     *   2. User exists but no Creator (edge case: user record orphaned)
     *   3. Neither exists (fresh approval — happy path)
     */
    private void handleApproval(ArtisanApplication app) {
        String email = app.getEmail().toLowerCase().trim();

        userRepository.findByEmail(email).ifPresentOrElse(
                existingUser -> {
                    // Case 1 & 2 — account already exists, just re-activate
                    existingUser.setSuspended(false);
                    userRepository.save(existingUser);

                    if (existingUser.getCreatorId() != null) {
                        creatorRepository.findById(existingUser.getCreatorId()).ifPresent(c -> {
                            c.setDeletedAt(null);
                            creatorRepository.save(c);
                        });
                    }

                    log.info("Re-activated existing creator account: {}", email);

                    // tempPassword is null for re-activations — the template handles this
                    emailService.sendCreatorWelcome(
                            app.getEmail(), app.getFullName(),
                            existingUser.getCreatorId() != null ? existingUser.getCreatorId() : "",
                            null);
                },
                () -> {
                    // Case 3 — fresh provisioning
                    String tempPassword = provisionCreatorAccount(app);

                    String creatorId = userRepository.findByEmail(email)
                            .map(User::getCreatorId)
                            .orElse(slugify(app.getFullName()));

                    log.info("Provisioned new creator account: {} ({})", email, creatorId);

                    emailService.sendCreatorWelcome(
                            app.getEmail(), app.getFullName(),
                            creatorId, tempPassword);
                }
        );
    }

    private String provisionCreatorAccount(ArtisanApplication app) {
        String slug = slugify(app.getFullName());
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

    private String slugify(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
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