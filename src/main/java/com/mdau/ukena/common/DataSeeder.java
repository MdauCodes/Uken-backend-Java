package com.mdau.ukena.common;

import com.mdau.ukena.admin.FeaturedSlot;
import com.mdau.ukena.admin.FeaturedSlotRepository;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import com.mdau.ukena.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final FeaturedSlotRepository featuredSlotRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ukena.admin.email:admin@ukena.co.uk}")
    private String adminEmail;

    @Value("${ukena.admin.password:#{null}}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedSuperAdmin();
        seedFeaturedSlots();
    }

    private void seedSuperAdmin() {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Superadmin already exists — skipping seed");
            return;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.error("UKENA_ADMIN_PASSWORD env variable not set — superadmin not created");
            return;
        }

        User superAdmin = User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .fullName("Ukena Superadmin")
                .role(UserRole.ROLE_ADMIN)
                .build();

        userRepository.save(superAdmin);
        log.info("Superadmin created: {}", adminEmail);
    }

    private void seedFeaturedSlots() {
        for (int i = 1; i <= 4; i++) {
            int position = i;
            if (!featuredSlotRepository.existsById(position)) {
                featuredSlotRepository.save(
                        new FeaturedSlot(position, null, null));
                log.info("Featured slot {} initialised", position);
            }
        }
    }
}