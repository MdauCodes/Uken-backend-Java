package com.mdau.ukena.email;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.email.dto.*;
import com.mdau.ukena.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Admin-only: mailbox credential CRUD, letterhead template CRUD, and staff↔mailbox access assignment. */
@RestController
@RequestMapping("/admin/email")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmailAdminController {

    private final MailboxService mailboxService;
    private final EmailTemplateService templateService;

    // ── mailboxes ────────────────────────────────────────────────────────

    @GetMapping("/mailboxes")
    public ResponseEntity<ApiResponse<List<MailboxAdminDto>>> listMailboxes() {
        return ResponseEntity.ok(ApiResponse.ok(mailboxService.listAllAdmin()));
    }

    @PostMapping("/mailboxes")
    public ResponseEntity<ApiResponse<MailboxAdminDto>> createMailbox(
            @Valid @RequestBody MailboxCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(mailboxService.create(req), "Mailbox added"));
    }

    @PutMapping("/mailboxes/{id}")
    public ResponseEntity<ApiResponse<MailboxAdminDto>> updateMailbox(
            @PathVariable UUID id,
            @Valid @RequestBody MailboxUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(mailboxService.update(id, req), "Mailbox updated"));
    }

    @DeleteMapping("/mailboxes/{id}")
    public ResponseEntity<Void> deleteMailbox(@PathVariable UUID id) {
        mailboxService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── staff access ─────────────────────────────────────────────────────

    @GetMapping("/mailboxes/{id}/access")
    public ResponseEntity<ApiResponse<List<MailboxAccessDto>>> listAccess(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(mailboxService.listAccess(id)));
    }

    @PutMapping("/mailboxes/{id}/access")
    public ResponseEntity<Void> updateAccess(
            @PathVariable UUID id,
            @RequestBody MailboxAccessUpdateRequest req) {
        mailboxService.updateAccess(id, req.userIds());
        return ResponseEntity.noContent().build();
    }

    // ── templates ────────────────────────────────────────────────────────

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<EmailTemplateDto>>> listTemplates() {
        return ResponseEntity.ok(ApiResponse.ok(templateService.list()));
    }

    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<EmailTemplateDto>> createTemplate(
            @Valid @RequestBody EmailTemplateRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(templateService.create(req, currentUser.id()), "Template created"));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<ApiResponse<EmailTemplateDto>> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody EmailTemplateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.update(id, req), "Template updated"));
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
