package com.mdau.ukena.email;

import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time seed of a starter template library so staff have professional,
 * on-brand messages to reuse from day one instead of writing from scratch.
 * Idempotent per template name — safe to run on every boot; re-running after
 * an admin edits/renames a seeded template won't overwrite their changes,
 * it'll just leave a gap (no "reseed" behavior).
 *
 * {@code htmlContent} here is body text ONLY — no logo, no styled buttons, no
 * wrapping divs. The rich text editor these load into can't faithfully carry
 * an &lt;img&gt; or a custom-styled &lt;a&gt; through its schema (see
 * EmailTemplate's Javadoc), so the letterhead (logo/accent color/footer) and
 * the call-to-action button are both structured fields applied around this
 * body at send time instead of markup inside it.
 *
 * Runs after {@link com.mdau.ukena.common.DataSeeder} so the superadmin
 * account (used as createdBy) is guaranteed to exist.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class EmailTemplateSeeder implements ApplicationRunner {

    private static final String SALES = "UKEN Sales";
    private static final String OPERATIONS = "UKEN Operations";
    private static final String SHOP_URL = "https://ukena.co.uk/shop";

    private final EmailTemplateRepository templateRepository;
    private final UserRepository userRepository;

    @Value("${ukena.admin.email:admin@ukena.co.uk}")
    private String adminEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User owner = userRepository.findByEmail(adminEmail).orElse(null);
        if (owner == null) {
            log.info("Superadmin not found yet ({}) - skipping template seed this boot", adminEmail);
            return;
        }
        for (Seed seed : SEEDS) {
            if (templateRepository.existsByName(seed.name())) continue;
            templateRepository.save(EmailTemplate.builder()
                    .name(seed.name())
                    .category(seed.category())
                    .htmlContent(seed.body())
                    .senderName(seed.senderName())
                    .ctaLabel(seed.ctaLabel())
                    .ctaUrl(seed.ctaUrl())
                    .createdBy(owner.getId())
                    .build());
            log.info("Seeded email template: {}", seed.name());
        }
    }

    private record Seed(String name, String category, String senderName,
                         String body, String ctaLabel, String ctaUrl) {}

    private static String p(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append("<p>").append(line).append("</p>");
        return sb.toString();
    }

    private static final List<Seed> SEEDS = List.of(

        // ── Pitch ──────────────────────────────────────────────────────
        // No sign-off baked in below — the per-mailbox signature (name, title,
        // contact) is appended automatically at send time (see emailLetterhead.ts
        // signatureBlock). A hardcoded "Warmly, The UKEN team" here would just
        // duplicate it once a mailbox has one configured.
        new Seed("Pitch — General introduction", "PITCH", SALES,
            p(
                "Hi [Name],",
                "UKEN brings you handcrafted goods and produce from independent artisans and farmers " +
                    "across Kenya, Uganda, Tanzania, Rwanda and beyond — each piece carries a story, a " +
                    "region, and a maker's name behind it.",
                "We'd love for you to see what's on the shelf right now: coffee, tea, beadwork, textiles, " +
                    "home pieces, and more, every one traceable back to who made or grew it."
            ),
            "Browse the collection", SHOP_URL),

        new Seed("Pitch — New arrivals", "PITCH", SALES,
            p(
                "Hi [Name],",
                "<strong>Just added</strong> — new pieces from this season's makers.",
                "Every item is made to order or in small batches — once a run sells out, it's gone. " +
                    "Here's your first look before the rest of the list gets it.",
                "Free from the middleman markup — prices go straight back to the person who made it."
            ),
            "Shop the new arrivals", SHOP_URL),

        // ── Reply ──────────────────────────────────────────────────────
        new Seed("Reply — General inquiry", "REPLY", null,
            p(
                "Hi [Name],",
                "Thanks for reaching out to UKEN — happy to help.",
                "[Your reply here]",
                "If anything else comes up, just reply to this email — a real person reads every message."
            ),
            null, null),

        new Seed("Reply — Order support", "REPLY", null,
            p(
                "Hi [Name],",
                "Thanks for getting in touch about order <strong>#[Order number]</strong>.",
                "[Order status / resolution details here]",
                "You can track this order any time at the link below."
            ),
            "Track your order", "https://ukena.co.uk/orders/track"),

        new Seed("Reply — Partnership / wholesale inquiry", "REPLY", null,
            p(
                "Hi [Name],",
                "Thanks for your interest in working with UKEN — we're always glad to hear from people " +
                    "who want to bring these pieces to a wider audience.",
                "[Details on wholesale terms / next steps here]",
                "Let me know if you'd like to set up a call to go through the details."
            ),
            null, null),

        // ── Creator communications ────────────────────────────────────
        new Seed("Creator — Welcome & next steps", "CREATOR", OPERATIONS,
            p(
                "Hi [Creator name],",
                "Welcome to UKEN — we're excited to have your work on the platform.",
                "A few things to do before your first listing goes live: add clear photos, fill in the " +
                    "story behind each piece, and set the weight per unit so shipping calculates correctly " +
                    "at checkout.",
                "Reach out any time if something's unclear — we're rooting for you."
            ),
            "Open your Maker Studio", "https://ukena.co.uk/creator"),

        new Seed("Creator — Listing needs attention", "CREATOR", OPERATIONS,
            p(
                "Hi [Creator name],",
                "Quick note on <strong>[Product name]</strong> — [what's missing: weight / photos / " +
                    "description, etc.] still needs to be filled in before it can go live for buyers.",
                "It only takes a couple of minutes to update from your dashboard.",
                "Let us know if you'd like a hand with it."
            ),
            "Update your listing", "https://ukena.co.uk/creator/products"),

        new Seed("Creator — Payout sent", "CREATOR", OPERATIONS,
            p(
                "Hi [Creator name],",
                "Good news — a payout of <strong>[Amount]</strong> for your recent sales has been sent " +
                    "and should reach you within [timeframe].",
                "You can see the full breakdown any time in your earnings dashboard.",
                "Thank you for the work you put into every piece — it shows."
            ),
            "View your earnings", "https://ukena.co.uk/creator/earnings")
    );
}
