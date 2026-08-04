package com.mdau.ukena.email;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_templates")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Editable message body only — no logo, header, or footer. The rich text
     *  editor's schema can't faithfully round-trip an &lt;img&gt; or a custom
     *  -styled &lt;a&gt; button (TipTap's base Link mark drops arbitrary style
     *  attributes and StarterKit has no Image node at all), so branding elements
     *  are never handed to it. The letterhead wrapper (logo/accent color/footer)
     *  is generated from {@link #logoUrl}/{@link #accentColor} and applied around
     *  this body at send time instead — see the frontend's letterhead-wrap helper. */
    @Lob
    @Column(name = "html_content", nullable = false)
    private String htmlContent;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** Header logo image URL. Null = use the default UKEN logo. */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** Hex color (e.g. "#C1694F") for the header accent and CTA buttons.
     *  Null = use the default brand clay. */
    @Column(name = "accent_color", length = 20)
    private String accentColor;

    /** Overrides the mailbox's own display name as the visible "From" sender
     *  when this template is used — e.g. "UKEN Sales" for a pitch template vs
     *  "UKEN Admin" for a creator-facing one, from the same mailbox address.
     *  Null = use the mailbox's own display name. */
    @Column(name = "sender_name", length = 120)
    private String senderName;

    /** Optional call-to-action button rendered after the editable body — a
     *  structured field rather than HTML inside {@link #htmlContent} for the
     *  same reason the logo lives outside it: a custom-styled &lt;a&gt; button
     *  can't survive a round trip through the rich text editor either. Both
     *  must be set together or both left null. */
    @Column(name = "cta_label", length = 80)
    private String ctaLabel;

    @Column(name = "cta_url", length = 500)
    private String ctaUrl;

    /** Free-form grouping for the template picker — "PITCH", "REPLY", "CREATOR",
     *  or "GENERAL" by convention, but not an enum so new categories don't need
     *  a migration. Nullable — older templates predate this field. */
    @Column(length = 30)
    private String category;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
