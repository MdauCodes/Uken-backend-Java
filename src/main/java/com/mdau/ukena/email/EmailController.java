package com.mdau.ukena.email;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.email.dto.*;
import com.mdau.ukena.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Staff/admin mailbox operations — inbox browsing, compose/reply/forward,
 * drafts, attachment download, and the template picker. Every mailbox-scoped
 * endpoint is gated by {@code mailboxAccessGuard}: admins always pass,
 * ROLE_SUPPORT staff need an explicit {@link MailboxAccess} grant.
 */
@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailController {

    private final MailboxService mailboxService;
    private final InboxService inboxService;
    private final MailSendService mailSendService;
    private final EmailTemplateService templateService;

    @GetMapping("/mailboxes")
    public ResponseEntity<ApiResponse<List<MailboxDto>>> listMailboxes(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(mailboxService.listAccessible(currentUser)));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<EmailTemplateDto>>> listTemplates() {
        return ResponseEntity.ok(ApiResponse.ok(templateService.list()));
    }

    @GetMapping("/mailboxes/{mailboxId}/messages")
    @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")
    public ResponseEntity<ApiResponse<List<EmailMessageDto>>> listMessages(
            @PathVariable UUID mailboxId,
            @RequestParam(defaultValue = "INBOX") EmailFolder folder,
            @RequestParam(defaultValue = "0") int page) {
        Mailbox mailbox = mailboxService.getActiveOrThrow(mailboxId);
        return ResponseEntity.ok(ApiResponse.ok(
                inboxService.listMessages(mailbox, folder, page, 25)));
    }

    @GetMapping("/mailboxes/{mailboxId}/messages/{uid}")
    @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")
    public ResponseEntity<ApiResponse<EmailMessageDetailDto>> getMessage(
            @PathVariable UUID mailboxId,
            @PathVariable long uid,
            @RequestParam(defaultValue = "INBOX") EmailFolder folder) {
        Mailbox mailbox = mailboxService.getActiveOrThrow(mailboxId);
        return ResponseEntity.ok(ApiResponse.ok(inboxService.getMessage(mailbox, folder, uid)));
    }

    @GetMapping("/mailboxes/{mailboxId}/messages/{uid}/attachments/{partId}")
    @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")
    public ResponseEntity<ByteArrayResource> downloadAttachment(
            @PathVariable UUID mailboxId,
            @PathVariable long uid,
            @PathVariable String partId,
            @RequestParam(defaultValue = "INBOX") EmailFolder folder,
            @RequestParam String filename) {
        Mailbox mailbox = mailboxService.getActiveOrThrow(mailboxId);
        byte[] bytes = inboxService.getAttachmentBytes(mailbox, folder, uid, partId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitizeFilename(filename) + "\"")
                .body(new ByteArrayResource(bytes));
    }

    @PostMapping(value = "/mailboxes/{mailboxId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")
    public ResponseEntity<Void> send(
            @PathVariable UUID mailboxId,
            @Valid @RequestPart("payload") SendEmailRequest payload,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        Mailbox mailbox = mailboxService.getActiveOrThrow(mailboxId);
        mailSendService.send(mailbox, payload, attachments);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/mailboxes/{mailboxId}/messages/{uid}/reply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")
    public ResponseEntity<Void> reply(
            @PathVariable UUID mailboxId,
            @PathVariable long uid,
            @RequestParam(defaultValue = "INBOX") EmailFolder folder,
            @Valid @RequestPart("payload") SendEmailRequest payload,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        Mailbox mailbox = mailboxService.getActiveOrThrow(mailboxId);
        EmailMessageDetailDto original = inboxService.getMessage(mailbox, folder, uid);
        mailSendService.reply(mailbox, payload, attachments, original.messageIdHeader());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/mailboxes/{mailboxId}/messages/{uid}/forward", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")
    public ResponseEntity<Void> forward(
            @PathVariable UUID mailboxId,
            @PathVariable long uid,
            @Valid @RequestPart("payload") SendEmailRequest payload,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        Mailbox mailbox = mailboxService.getActiveOrThrow(mailboxId);
        mailSendService.forward(mailbox, payload, attachments);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/mailboxes/{mailboxId}/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")
    public ResponseEntity<ApiResponse<Long>> saveDraft(
            @PathVariable UUID mailboxId,
            @Valid @RequestPart("payload") SendEmailRequest payload,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        Mailbox mailbox = mailboxService.getActiveOrThrow(mailboxId);
        long uid = mailSendService.saveDraft(mailbox, payload, attachments);
        return ResponseEntity.ok(ApiResponse.ok(uid, "Draft saved"));
    }

    @PutMapping(value = "/mailboxes/{mailboxId}/drafts/{uid}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")
    public ResponseEntity<ApiResponse<Long>> updateDraft(
            @PathVariable UUID mailboxId,
            @PathVariable long uid,
            @Valid @RequestPart("payload") SendEmailRequest payload,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        Mailbox mailbox = mailboxService.getActiveOrThrow(mailboxId);
        long newUid = mailSendService.updateDraft(mailbox, uid, payload, attachments);
        return ResponseEntity.ok(ApiResponse.ok(newUid, "Draft updated"));
    }

    @DeleteMapping("/mailboxes/{mailboxId}/drafts/{uid}")
    @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable UUID mailboxId,
            @PathVariable long uid) {
        Mailbox mailbox = mailboxService.getActiveOrThrow(mailboxId);
        mailSendService.deleteMessage(mailbox, EmailFolder.DRAFTS, uid);
        return ResponseEntity.noContent().build();
    }

    private String sanitizeFilename(String filename) {
        String cleaned = filename == null ? "attachment" : filename.replaceAll("[\\r\\n\"]", "_");
        return cleaned.isBlank() ? "attachment" : cleaned;
    }
}
