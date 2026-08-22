package com.mdau.ukena.application;

import com.mdau.ukena.application.dto.*;
import com.mdau.ukena.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    // Public — anyone can apply
    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<ApplicationDto>> submit(
            @Valid @RequestBody SubmitRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        applicationService.submit(req),
                        "Application submitted successfully"));
    }

    // Public — lets the join form warn a returning buyer/applicant before they fill out the whole form
    @GetMapping("/applications/email-check")
    public ResponseEntity<ApiResponse<EmailCheckResponse>> emailCheck(
            @RequestParam String email) {
        return ResponseEntity.ok(ApiResponse.ok(
                new EmailCheckResponse(applicationService.emailRegistered(email))));
    }

    // Admin only
    @GetMapping("/admin/applications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ApplicationDto>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.listAll()));
    }

    @GetMapping("/admin/applications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ApplicationDto>> getOne(
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getById(id)));
    }

    // Approving an application can provision a brand-new creator or
    // re-activate a previously suspended one (ApplicationService.handleApproval)
    // — without evicting here, that creator wouldn't show up in the public
    // GET /creators listing until something unrelated cleared the cache.
    @PatchMapping("/admin/applications/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = {"creators", "creator", "featured"}, allEntries = true)
    public ResponseEntity<ApiResponse<ApplicationDto>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody StatusUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                applicationService.updateStatus(id, req),
                "Application status updated"));
    }
}