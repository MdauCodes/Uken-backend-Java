package com.mdau.ukena.admin;

import com.mdau.ukena.admin.dto.*;
import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.creator.CreatorService;
import com.mdau.ukena.creator.dto.CreatorDetail;
import com.mdau.ukena.creator.dto.CreatorProfileUpdate;
import com.mdau.ukena.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final CreatorService creatorService;

    // ── Staff ────────────────────────────────────────────────

    @PostMapping("/staff")
    public ResponseEntity<ApiResponse<StaffDto>> createStaff(
            @Valid @RequestBody CreateStaffRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(adminService.createStaff(req, currentUser), "Staff account created"));
    }

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<StaffDto>>> listStaff() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listStaff()));
    }

    /** Superadmin-only — enforced in {@code AdminService}, not here, since it
     *  depends on the acting user's {@code superAdmin} flag, not their role. */
    @PatchMapping("/staff/{id}/promote")
    public ResponseEntity<ApiResponse<StaffDto>> promoteStaff(
            @PathVariable UUID id, @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.promoteToAdmin(id, currentUser), "Promoted to admin"));
    }

    @PatchMapping("/staff/{id}/demote")
    public ResponseEntity<ApiResponse<StaffDto>> demoteStaff(
            @PathVariable UUID id, @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.demoteToSupport(id, currentUser), "Demoted to support"));
    }

    // ── Buyers ───────────────────────────────────────────────

    @GetMapping("/buyers")
    public ResponseEntity<ApiResponse<List<AdminBuyerRow>>> listBuyers() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listBuyers()));
    }

    @PatchMapping("/buyers/{id}/suspend")
    public ResponseEntity<Void> suspendBuyer(@PathVariable UUID id) {
        adminService.suspendBuyer(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/buyers/{id}/unsuspend")
    public ResponseEntity<Void> unsuspendBuyer(@PathVariable UUID id) {
        adminService.unsuspendBuyer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Directly converts an existing account (any non-creator role) into a
     * creator from the Users/Buyers page — for makers who never went through
     * the /join application flow. Requires craft/region/story since there's
     * no application on file to source them from.
     */
    @PostMapping("/buyers/{id}/convert-to-creator")
    public ResponseEntity<Void> convertToCreator(
            @PathVariable UUID id, @Valid @RequestBody ConvertToCreatorRequest req) {
        adminService.convertToCreator(id, req);
        return ResponseEntity.noContent().build();
    }

    // ── Creators ─────────────────────────────────────────────

    @GetMapping("/creators")
    public ResponseEntity<ApiResponse<List<AdminCreatorRow>>> listCreators(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listCreators(status)));
    }

    @PatchMapping("/creators/{id}/suspend")
    public ResponseEntity<Void> suspendCreator(
            @AuthenticationPrincipal CurrentUser currentUser, @PathVariable String id) {
        adminService.suspendCreator(currentUser, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/creators/{id}/unsuspend")
    public ResponseEntity<Void> unsuspendCreator(
            @AuthenticationPrincipal CurrentUser currentUser, @PathVariable String id) {
        adminService.unsuspendCreator(currentUser, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lets an admin edit a creator's public profile (story, images, craft,
     * region) exactly as the creator themself could from their own
     * dashboard — same underlying fields, same {@code CreatorService} calls,
     * just reached via an admin-only path instead of the creator's own JWT.
     */
    @GetMapping("/creators/{id}/profile")
    public ResponseEntity<ApiResponse<CreatorDetail>> getCreatorProfile(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(creatorService.getById(id)));
    }

    @PutMapping("/creators/{id}/profile")
    @CacheEvict(value = {"creators", "creator", "featured"}, allEntries = true)
    public ResponseEntity<ApiResponse<CreatorDetail>> updateCreatorProfile(
            @PathVariable String id,
            @Valid @RequestBody CreatorProfileUpdate req) {
        return ResponseEntity.ok(ApiResponse.ok(creatorService.updateProfile(id, req)));
    }

    // ── Payouts ──────────────────────────────────────────────

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<List<CreatorPayoutDto>>> listPayouts() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listPayouts()));
    }

    @PostMapping("/payouts/{creatorId}/pay")
    public ResponseEntity<ApiResponse<CreatorPayoutDto>> processPayout(
            @PathVariable String creatorId,
            @Valid @RequestBody PayoutRequestDto req) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.processPayout(
                        creatorId, req.accountNumber(), req.accountName()),
                "Payout processed"));
    }

    // ── Reviews ──────────────────────────────────────────────

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<List<ProductReviewDto>>> listReviews() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listReviews()));
    }

    @PatchMapping("/reviews/{id}/status")
    public ResponseEntity<ApiResponse<ProductReviewDto>> updateReviewStatus(
            @PathVariable String id,
            @Valid @RequestBody ReviewStatusUpdate req) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.updateReviewStatus(id, req)));
    }

    // ── Featured slots ───────────────────────────────────────

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<FeaturedSlotDto>>> getFeatured() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getFeaturedSlots()));
    }

    @PutMapping("/featured")
    public ResponseEntity<ApiResponse<List<FeaturedSlotDto>>> updateFeatured(
            @RequestBody List<FeaturedSlotDto> slots) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.updateFeaturedSlots(slots)));
    }

    // ── Revenue dashboard ────────────────────────────────────

    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<UkenaRevenueDto>> getRevenue() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getRevenue()));
    }
}