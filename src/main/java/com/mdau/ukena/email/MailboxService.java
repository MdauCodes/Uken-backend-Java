package com.mdau.ukena.email;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.email.dto.*;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import com.mdau.ukena.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailboxService {

    private final MailboxRepository mailboxRepository;
    private final MailboxAccessRepository mailboxAccessRepository;
    private final UserRepository userRepository;

    // ── staff/admin-facing ───────────────────────────────────────────────

    public List<MailboxDto> listAccessible(CurrentUser user) {
        List<Mailbox> mailboxes;
        if (user.role() == UserRole.ROLE_ADMIN) {
            mailboxes = mailboxRepository.findByActiveTrue();
        } else {
            List<UUID> ids = mailboxAccessRepository.findByUserId(user.id())
                    .stream().map(MailboxAccess::getMailboxId).toList();
            mailboxes = mailboxRepository.findAllById(ids).stream().filter(Mailbox::isActive).toList();
        }
        return mailboxes.stream().map(this::toDto).toList();
    }

    public Mailbox getActiveOrThrow(UUID id) {
        Mailbox m = mailboxRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Mailbox not found"));
        if (!m.isActive()) throw ApiException.notFound("Mailbox not found");
        return m;
    }

    // ── admin CRUD ───────────────────────────────────────────────────────

    public List<MailboxAdminDto> listAllAdmin() {
        return mailboxRepository.findAll().stream().map(this::toAdminDto).toList();
    }

    @Transactional
    public MailboxAdminDto create(MailboxCreateRequest req) {
        if (mailboxRepository.existsByAddress(req.address())) {
            throw ApiException.conflict("A mailbox with this address already exists");
        }
        Mailbox.MailboxBuilder builder = Mailbox.builder()
                .address(req.address())
                .displayName(req.displayName())
                .username(req.username() != null && !req.username().isBlank() ? req.username() : req.address())
                .password(req.password());
        if (req.imapHost() != null && !req.imapHost().isBlank()) builder.imapHost(req.imapHost());
        if (req.imapPort() != null) builder.imapPort(req.imapPort());
        if (req.smtpHost() != null && !req.smtpHost().isBlank()) builder.smtpHost(req.smtpHost());
        if (req.smtpPort() != null) builder.smtpPort(req.smtpPort());
        Mailbox saved = mailboxRepository.save(builder.build());
        return toAdminDto(saved);
    }

    @Transactional
    public MailboxAdminDto update(UUID id, MailboxUpdateRequest req) {
        Mailbox m = mailboxRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Mailbox not found"));
        m.setDisplayName(req.displayName());
        if (req.imapHost() != null && !req.imapHost().isBlank()) m.setImapHost(req.imapHost());
        if (req.imapPort() != null) m.setImapPort(req.imapPort());
        if (req.smtpHost() != null && !req.smtpHost().isBlank()) m.setSmtpHost(req.smtpHost());
        if (req.smtpPort() != null) m.setSmtpPort(req.smtpPort());
        if (req.username() != null && !req.username().isBlank()) m.setUsername(req.username());
        if (req.password() != null && !req.password().isBlank()) m.setPassword(req.password());
        m.setActive(req.active());
        m.setSignatureName(blankToNull(req.signatureName()));
        m.setSignatureTitle(blankToNull(req.signatureTitle()));
        m.setSignaturePhone(blankToNull(req.signaturePhone()));
        return toAdminDto(mailboxRepository.save(m));
    }

    @Transactional
    public void delete(UUID id) {
        if (!mailboxRepository.existsById(id)) throw ApiException.notFound("Mailbox not found");
        mailboxAccessRepository.findByMailboxId(id)
                .forEach(a -> mailboxAccessRepository.deleteByMailboxIdAndUserId(a.getMailboxId(), a.getUserId()));
        mailboxRepository.deleteById(id);
    }

    // ── access assignment ────────────────────────────────────────────────

    public List<MailboxAccessDto> listAccess(UUID mailboxId) {
        return mailboxAccessRepository.findByMailboxId(mailboxId).stream()
                .map(a -> userRepository.findById(a.getUserId()).orElse(null))
                .filter(u -> u != null)
                .map(u -> new MailboxAccessDto(u.getId(), u.getEmail(), u.getFullName()))
                .toList();
    }

    @Transactional
    public void updateAccess(UUID mailboxId, List<UUID> userIds) {
        if (!mailboxRepository.existsById(mailboxId)) throw ApiException.notFound("Mailbox not found");
        List<MailboxAccess> existing = mailboxAccessRepository.findByMailboxId(mailboxId);
        existing.stream()
                .filter(a -> userIds == null || !userIds.contains(a.getUserId()))
                .forEach(a -> mailboxAccessRepository.deleteByMailboxIdAndUserId(mailboxId, a.getUserId()));

        if (userIds == null) return;
        for (UUID userId : userIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ApiException.badRequest("User not found: " + userId));
            if (user.getRole() != UserRole.ROLE_SUPPORT && user.getRole() != UserRole.ROLE_ADMIN) {
                throw ApiException.badRequest("Only staff or admin users can be granted mailbox access");
            }
            if (!mailboxAccessRepository.existsByMailboxIdAndUserId(mailboxId, userId)) {
                mailboxAccessRepository.save(MailboxAccess.builder()
                        .mailboxId(mailboxId)
                        .userId(userId)
                        .build());
            }
        }
    }

    // ── mapping ──────────────────────────────────────────────────────────

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private MailboxDto toDto(Mailbox m) {
        return new MailboxDto(m.getId(), m.getAddress(), m.getDisplayName(), m.isActive(),
                m.getSignatureName(), m.getSignatureTitle(), m.getSignaturePhone());
    }

    private MailboxAdminDto toAdminDto(Mailbox m) {
        return new MailboxAdminDto(m.getId(), m.getAddress(), m.getDisplayName(),
                m.getImapHost(), m.getImapPort(), m.getSmtpHost(), m.getSmtpPort(),
                m.getUsername(), m.isActive(),
                m.getSignatureName(), m.getSignatureTitle(), m.getSignaturePhone(),
                m.getCreatedAt(), m.getUpdatedAt());
    }
}
