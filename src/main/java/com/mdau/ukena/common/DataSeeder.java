package com.mdau.ukena.common;

import com.mdau.ukena.admin.FeaturedSlot;
import com.mdau.ukena.admin.FeaturedSlotRepository;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import com.mdau.ukena.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository         userRepository;
    private final FeaturedSlotRepository featuredSlotRepository;
    private final CreatorRepository      creatorRepository;
    private final PasswordEncoder        passwordEncoder;

    /** The one account that can manage other admin accounts (create/promote/
     *  demote — see AdminService.requireSuperAdmin). Hardcoded, not an env
     *  var: this is a specific real person's account, not environment
     *  config that should vary between deploys. */
    private static final String DESIGNATED_SUPERADMIN_EMAIL = "mdaucodes@gmail.com";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureDesignatedSuperAdmin();
        seedFeaturedSlots();
        seedUkenaCreator();
    }

    /** Idempotent and self-healing: creates the designated superadmin with a
     *  random, never-logged password if it doesn't exist yet — the real
     *  owner sets their own real password via the existing "Forgot password"
     *  email-OTP flow, so nobody (including this seeder's own logs) ever
     *  holds a usable password for this account. If the account already
     *  exists under some other role (e.g. it registered as a buyer before
     *  being designated), promotes it to ROLE_ADMIN + superAdmin=true in
     *  place on the next boot — but never touches an existing password
     *  hash. Deliberately does NOT seed the old env-var-driven bootstrap
     *  admin any more: that account is being retired, and re-seeding it on
     *  every boot would just recreate it after removal. */
    private void ensureDesignatedSuperAdmin() {
        User user = userRepository.findByEmail(DESIGNATED_SUPERADMIN_EMAIL).orElse(null);
        if (user == null) {
            user = User.builder()
                    .email(DESIGNATED_SUPERADMIN_EMAIL)
                    .passwordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                    .fullName("Ukena Superadmin")
                    .role(UserRole.ROLE_ADMIN)
                    .superAdmin(true)
                    .build();
            userRepository.save(user);
            log.info("Designated superadmin created: {} - set a real password via Forgot Password", DESIGNATED_SUPERADMIN_EMAIL);
            return;
        }
        boolean changed = false;
        if (user.getRole() != UserRole.ROLE_ADMIN) {
            user.setRole(UserRole.ROLE_ADMIN);
            changed = true;
        }
        if (!Boolean.TRUE.equals(user.getSuperAdmin())) {
            user.setSuperAdmin(true);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
            log.info("Designated superadmin promoted in place: {}", DESIGNATED_SUPERADMIN_EMAIL);
        }
    }

    private void seedFeaturedSlots() {
        for (int i = 1; i <= 4; i++) {
            int position = i;
            if (!featuredSlotRepository.existsById(position)) {
                featuredSlotRepository.save(new FeaturedSlot(position, null, null));
                log.info("Featured slot {} initialised", position);
            }
        }
    }

    private void seedUkenaCreator() {
        if (creatorRepository.existsById("ukena")) return;
        Creator ukena = Creator.builder()
                .id("ukena")
                .firstName("Uken")
                .fullName("Uken")
                .craft("General")
                .region("United Kingdom")
                .hook("Uken's own curated catalogue")
                .image("")
                .portraitImage("")
                .headerImage("")
                .build();
        creatorRepository.save(ukena);
        log.info("Seeded Uken sentinel creator");
    }
}