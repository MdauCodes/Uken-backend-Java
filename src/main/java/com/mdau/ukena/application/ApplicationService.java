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
        String portrait  = req.photos().get(0);
        String workSample = req.photos().size() > 1
                ? req.photos().get(1) : req.photos().get(0);

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
        app.setStatus(newStatus);
        if (req.notes() != null) app.setNotes(req.notes());
        applicationRepo.save(app);

        if (newStatus == ApplicationStatus.APPROVED) {
            boolean alreadyHasAccount = userRepository.existsByEmail(app.getEmail());
            if (alreadyHasAccount) {
                // Creator was previously suspended — unsuspend their existing account
                userRepository.findByEmail(app.getEmail()).ifPresent(user -> {
                    user.setSuspended(false);
                    userRepository.save(user);
                    // Also clear deletedAt on their creator profile
                    if (user.getCreatorId() != null) {
                        creatorRepository.findById(user.getCreatorId()).ifPresent(c -> {
                            c.setDeletedAt(null);
                            creatorRepository.save(c);
                        });
                    }
                    log.info("Re-activated existing creator account: {}", user.getEmail());
                });
                emailService.sendCreatorWelcome(
                        app.getEmail(), app.getFullName(),
                        userRepository.findByEmail(app.getEmail())
                                .map(User::getCreatorId).orElse(""),
                        null);
            } else {
                String tempPassword = provisionCreatorAccount(app);
                String slug = slugify(app.getFullName());
                emailService.sendCreatorWelcome(
                        app.getEmail(), app.getFullName(),
                        slug, tempPassword);
            }
        }

        return toDto(app);
    }

    private String provisionCreatorAccount(ArtisanApplication app) {
        String slug = slugify(app.getFullName());
        String finalSlug = slug;
        int count = 1;
        // Check including soft-deleted to avoid slug conflicts
        while (creatorRepository.existsById(finalSlug)) {
            finalSlug = slug + count++;
        }

        Creator creator = Creator.builder()
                .id(finalSlug)
                .firstName(app.getFullName().split(" ")[0])
                .fullName(app.getFullName())
                .craft(app.getCraft()).region(app.getRegion())
                .hook(app.getStory().length() > 120
                        ? app.getStory().substring(0, 117) + "..."
                        : app.getStory())
                .image(app.getPortrait())
                .portraitImage(app.getPortrait())
                .headerImage(app.getPortrait())
                .build();
        creatorRepository.save(creator);

        String tempPassword = "Ukena"
                + ThreadLocalRandom.current().nextInt(1000, 9999) + "!";
        User user = User.builder()
                .email(app.getEmail())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .fullName(app.getFullName())
                .role(UserRole.ROLE_CREATOR)
                .creatorId(finalSlug)
                .build();
        userRepository.save(user);

        log.info("Creator account provisioned: {} ({})", app.getEmail(), finalSlug);
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