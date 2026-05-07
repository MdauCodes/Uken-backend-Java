package com.mdau.ukena.admin;

import com.mdau.ukena.admin.dto.*;
import com.mdau.ukena.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // -- Staff --------------------------------------------------------------

    @PostMapping("/staff")
    public ResponseEntity<ApiResponse<StaffDto>> createStaff(
            @Valid @RequestBody CreateStaffRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(adminService.createStaff(req), "Staff account created"));
    }

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<StaffDto>>> listStaff() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listStaff()));
    }

    // -- Buyers -------------------------------------------------------------

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

    // -- Creators -----------------------------------------------------------

    @DeleteMapping("/creators/{id}")
    public ResponseEntity<Void> deleteCreator(@PathVariable String id) {
        adminService.softDeleteCreator(id);
        return ResponseEntity.noContent().build();
    }

    // -- Payouts ------------------------------------------------------------

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<List<CreatorPayoutDto>>> listPayouts() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listPayouts()));
    }

    @PostMapping("/payouts/{creatorId}/pay")
    public ResponseEntity<ApiResponse<CreatorPayoutDto>> processPayout(
            @PathVariable String creatorId,
            @Valid @RequestBody PayoutRequestDto req) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.processPayout(creatorId, req.accountNumber(), req.accountName()),
                "Payout processed"));
    }

    // -- Reviews ------------------------------------------------------------

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

    // -- Featured slots -----------------------------------------------------

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
}