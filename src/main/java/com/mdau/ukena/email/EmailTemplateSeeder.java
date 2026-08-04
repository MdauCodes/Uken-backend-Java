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
import java.util.UUID;

/**
 * One-time seed of a starter template library so staff have professional,
 * on-brand letterheads to reuse from day one instead of writing from scratch.
 * Idempotent per template name — safe to run on every boot; re-running after
 * an admin edits/renames a seeded template won't overwrite their changes,
 * it'll just leave a gap (no "reseed" behavior).
 *
 * Runs after {@link com.mdau.ukena.common.DataSeeder} so the superadmin
 * account (used as createdBy) is guaranteed to exist.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class EmailTemplateSeeder implements ApplicationRunner {

    private static final String FONT = "'DM Sans', Arial, sans-serif";
    private static final String CLAY = "#C1694F";
    private static final String INK = "#1a0f07";
    private static final String BODY_TEXT = "#2C2420";
    private static final String FOOTER_TEXT = "#8B6040";
    private static final String ADDRESS = "UKEN &middot; Osmaston 10, Nottingham NG7 1SD &middot; ukena.co.uk";
    private static final String LOGO_URL = "https://ukena.co.uk/email-assets/uken-logo-light.png";
    private static final String ABOUT_BLURB = """
        <strong style="color:#1a0f07;">About UKEN</strong> &mdash; we connect independent artisans and \
        farmers across Kenya and the UK directly with buyers who value the story behind what they own. \
        Every piece is made by hand, priced fairly, and shipped with the maker's name attached &mdash; \
        no middleman markup, no anonymous factory batch.""";

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
                    .htmlContent(seed.html())
                    .createdBy(owner.getId())
                    .build());
            log.info("Seeded email template: {}", seed.name());
        }
    }

    private record Seed(String name, String category, String html) {}

    private static String letterhead(String body) {
        return """
            <div style="font-family: %s; max-width:560px; margin:0 auto;">
              <div style="padding:20px 0; border-bottom:2px solid %s; text-align:center; background:%s;">
                <img src="%s" alt="UKEN" height="44" style="height:44px; width:auto; display:inline-block;">
              </div>
              <div style="padding:28px 0; color:%s; line-height:1.7;">
                %s
              </div>
              <div style="padding:18px 24px; background:#f5f1eb; font-size:12px; line-height:1.6; color:%s; border-top:1px solid #ecdfd0;">
                %s
              </div>
              <div style="padding:12px 0; font-size:12px; color:%s; text-align:center;">
                %s
              </div>
            </div>""".formatted(
                FONT, CLAY, INK, LOGO_URL, BODY_TEXT, body, FOOTER_TEXT, ABOUT_BLURB, FOOTER_TEXT, ADDRESS);
    }

    private static String button(String label, String href) {
        return """
            <div style="text-align:center; margin:28px 0;">
              <a href="%s" style="display:inline-block; background:%s; color:#ffffff; text-decoration:none; padding:12px 32px; border-radius:999px; font-size:14px; font-weight:600; letter-spacing:0.02em;">%s</a>
            </div>""".formatted(href, CLAY, label);
    }

    private static final List<Seed> SEEDS = List.of(

        // ── Pitch ──────────────────────────────────────────────────────
        new Seed("Pitch — General introduction", "PITCH", letterhead("""
            <p style="margin:0 0 16px;">Hi [Name],</p>
            <p style="margin:0 0 16px;">UKEN brings you handcrafted goods made by artisans and farmers across Kenya and the UK — each piece carries a story, a region, and a maker's name behind it.</p>
            <p style="margin:0 0 24px;">We'd love for you to see what's on the shelf right now: beadwork, textiles, home pieces, and more, each one made by hand.</p>
            %s
            <p style="margin:0;">Warmly,<br/>The UKEN team</p>
            """.formatted(button("Browse the collection", "https://ukena.co.uk/shop")))),

        new Seed("Pitch — New arrivals", "PITCH", letterhead("""
            <p style="margin:0 0 4px; font-size:11px; letter-spacing:0.16em; text-transform:uppercase; color:%s; font-weight:600;">Just added</p>
            <h1 style="margin:0 0 16px; font-size:22px; color:%s; font-weight:600;">New pieces from this season's makers</h1>
            <p style="margin:0 0 24px;">Every item is made to order or in small batches — once a run sells out, it's gone. Here's your first look before the rest of the list gets it.</p>
            %s
            <p style="margin:0; font-size:13px; color:#5b4a3f;">Free from the middleman markup — prices go straight back to the person who made it.</p>
            """.formatted(CLAY, INK, button("Shop the new arrivals", "https://ukena.co.uk/shop")))),

        // ── Reply ──────────────────────────────────────────────────────
        new Seed("Reply — General inquiry", "REPLY", letterhead("""
            <p style="margin:0 0 16px;">Hi [Name],</p>
            <p style="margin:0 0 16px;">Thanks for reaching out to UKEN — happy to help.</p>
            <p style="margin:0 0 16px;">[Your reply here]</p>
            <p style="margin:0;">If anything else comes up, just reply to this email — a real person reads every message.</p>
            <p style="margin:16px 0 0;">Best,<br/>[Your name]<br/>UKEN</p>
            """)),

        new Seed("Reply — Order support", "REPLY", letterhead("""
            <p style="margin:0 0 16px;">Hi [Name],</p>
            <p style="margin:0 0 16px;">Thanks for getting in touch about order <strong>#[Order number]</strong>.</p>
            <p style="margin:0 0 16px;">[Order status / resolution details here]</p>
            <p style="margin:0 0 16px;">You can track this order any time at the link below.</p>
            %s
            <p style="margin:0;">Best,<br/>[Your name]<br/>UKEN</p>
            """.formatted(button("Track your order", "https://ukena.co.uk/orders/track")))),

        new Seed("Reply — Partnership / wholesale inquiry", "REPLY", letterhead("""
            <p style="margin:0 0 16px;">Hi [Name],</p>
            <p style="margin:0 0 16px;">Thanks for your interest in working with UKEN — we're always glad to hear from people who want to bring these pieces to a wider audience.</p>
            <p style="margin:0 0 16px;">[Details on wholesale terms / next steps here]</p>
            <p style="margin:0;">Let me know if you'd like to set up a call to go through the details.</p>
            <p style="margin:16px 0 0;">Best,<br/>[Your name]<br/>UKEN</p>
            """)),

        // ── Creator communications ────────────────────────────────────
        new Seed("Creator — Welcome & next steps", "CREATOR", letterhead("""
            <p style="margin:0 0 16px;">Hi [Creator name],</p>
            <p style="margin:0 0 16px;">Welcome to UKEN — we're excited to have your work on the platform.</p>
            <p style="margin:0 0 16px;">A few things to do before your first listing goes live: add clear photos, fill in the story behind each piece, and set the weight per unit so shipping calculates correctly at checkout.</p>
            %s
            <p style="margin:0;">Reach out any time if something's unclear — we're rooting for you.</p>
            <p style="margin:16px 0 0;">Warmly,<br/>The UKEN team</p>
            """.formatted(button("Open your Maker Studio", "https://ukena.co.uk/creator")))),

        new Seed("Creator — Listing needs attention", "CREATOR", letterhead("""
            <p style="margin:0 0 16px;">Hi [Creator name],</p>
            <p style="margin:0 0 16px;">Quick note on <strong>[Product name]</strong> — [what's missing: weight / photos / description, etc.] still needs to be filled in before it can go live for buyers.</p>
            <p style="margin:0 0 16px;">It only takes a couple of minutes to update from your dashboard.</p>
            %s
            <p style="margin:0;">Let us know if you'd like a hand with it.</p>
            <p style="margin:16px 0 0;">Best,<br/>The UKEN team</p>
            """.formatted(button("Update your listing", "https://ukena.co.uk/creator/products")))),

        new Seed("Creator — Payout sent", "CREATOR", letterhead("""
            <p style="margin:0 0 16px;">Hi [Creator name],</p>
            <p style="margin:0 0 16px;">Good news — a payout of <strong>[Amount]</strong> for your recent sales has been sent and should reach you within [timeframe].</p>
            <p style="margin:0 0 16px;">You can see the full breakdown any time in your earnings dashboard.</p>
            %s
            <p style="margin:0;">Thank you for the work you put into every piece — it shows.</p>
            <p style="margin:16px 0 0;">Warmly,<br/>The UKEN team</p>
            """.formatted(button("View your earnings", "https://ukena.co.uk/creator/earnings")))
        )
    );
}
