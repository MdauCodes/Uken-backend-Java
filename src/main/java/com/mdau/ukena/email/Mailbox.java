package com.mdau.ukena.email;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mailboxes", indexes = {
        @Index(name = "idx_mailboxes_address", columnList = "address"),
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Mailbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The mailbox's email address, e.g. andrew.soi@ukena.co.uk — also the IMAP/SMTP username. */
    @Column(nullable = false, unique = true, length = 254)
    private String address;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Builder.Default
    @Column(name = "imap_host", nullable = false, length = 120)
    private String imapHost = "mail.privateemail.com";

    @Builder.Default
    @Column(name = "imap_port", nullable = false)
    private int imapPort = 993;

    @Builder.Default
    @Column(name = "smtp_host", nullable = false, length = 120)
    private String smtpHost = "mail.privateemail.com";

    @Builder.Default
    @Column(name = "smtp_port", nullable = false)
    private int smtpPort = 465;

    /** Encrypted at rest — see {@link EncryptedStringConverter}. Usually equal to `address`. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "username", nullable = false, length = 500)
    private String username;

    /** Encrypted at rest — see {@link EncryptedStringConverter}. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "password", nullable = false, length = 500)
    private String password;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Sign-off shown at the end of every email sent from this mailbox,
     *  regardless of which letterhead template is used — e.g. "Andrew Soi" /
     *  "Founder &amp; CEO, UKEN" / "+254 700 000 000" for a personal mailbox.
     *  All nullable: a mailbox with no signatureName configured gets no
     *  signature block appended at all. */
    @Column(name = "signature_name", length = 120)
    private String signatureName;

    @Column(name = "signature_title", length = 160)
    private String signatureTitle;

    @Column(name = "signature_phone", length = 40)
    private String signaturePhone;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
