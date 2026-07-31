package com.mdau.ukena.product;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.product.dto.*;
import com.mdau.ukena.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ── Public ───────────────────────────────────────────────

    /** All active products — creator + Uken catalogue mixed. */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Page<ProductDto>>> browse(
            @RequestParam(required = false) String creatorId,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.browse(creatorId, minPrice, maxPrice, sort, page, size)));
    }

    /** Uken-owned catalogue only — separate section. */
    @GetMapping("/catalogue")
    public ResponseEntity<ApiResponse<Page<ProductDto>>> catalogue(
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.browseUkenaCatalogue(minPrice, maxPrice, sort, page, size)));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getOne(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getById(id)));
    }

    /** "Frequently bought together" — ranked by real order co-occurrence (see ProductService). */
    @GetMapping("/products/{id}/frequently-bought-with")
    public ResponseEntity<ApiResponse<List<ProductDto>>> frequentlyBoughtWith(
            @PathVariable String id,
            @RequestParam(defaultValue = "4") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(productService.frequentlyBoughtWith(id, limit)));
    }

    // ── Creator CRUD ─────────────────────────────────────────

    @GetMapping("/creators/me/products")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<List<ProductDto>>> myProducts(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getByCreator(currentUser.creatorId())));
    }

    @PostMapping("/creators/me/products")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<ProductDto>> create(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody ProductCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        productService.create(currentUser.creatorId(), req),
                        "Product created"));
    }

    @PutMapping("/creators/me/products/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<ProductDto>> update(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String id,
            @Valid @RequestBody ProductUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.update(currentUser.creatorId(), id, req)));
    }

    @PatchMapping("/creators/me/products/{id}/status")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<ProductDto>> updateStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String id,
            @Valid @RequestBody ProductStatusUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.updateStatusByCreator(currentUser.creatorId(), id, req)));
    }

    @DeleteMapping("/creators/me/products/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String id) {
        productService.delete(currentUser.creatorId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/creators/me/products/{id}/images")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<ProductDto>> addImage(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String id,
            @Valid @RequestBody AddImageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.addImage(currentUser.creatorId(), id, req)));
    }

    @DeleteMapping("/creators/me/products/{id}/images/{imageId}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> deleteImage(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String id,
            @PathVariable Long imageId) {
        productService.deleteImage(currentUser.creatorId(), id, imageId);
        return ResponseEntity.noContent().build();
    }

    // ── Admin: creator product moderation ────────────────────

    /** Every product regardless of status or soft-delete — the public /products
     *  listing only ever shows ACTIVE ones, which hides suspended/paused/deleted
     *  items from admins who'd need to find and restore them. */
    @GetMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ProductDto>>> adminListAll() {
        return ResponseEntity.ok(ApiResponse.ok(productService.listAllForAdmin()));
    }

    @PatchMapping("/admin/products/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> adminUpdateStatus(
            @PathVariable String id,
            @Valid @RequestBody ProductStatusUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.updateStatusByAdmin(id, req)));
    }

    @DeleteMapping("/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> adminDelete(@PathVariable String id) {
        productService.adminDelete(id);
        return ResponseEntity.noContent().build();
    }

    /** Emails every creator with a product still missing a weight, prompting them to fill it in. */
    @PostMapping("/admin/products/weight-reminders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> sendWeightReminders() {
        int sent = productService.sendWeightReminders();
        return ResponseEntity.ok(ApiResponse.ok(sent,
                sent + " creator" + (sent == 1 ? "" : "s") + " emailed"));
    }

    // ── Admin: Uken catalogue management ─────────────────────

    @PostMapping("/admin/catalogue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> catalogueCreate(
            @Valid @RequestBody AdminProductCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        productService.createForUkena(req), "Catalogue product created"));
    }

    @PutMapping("/admin/catalogue/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> catalogueUpdate(
            @PathVariable String id,
            @Valid @RequestBody AdminProductCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.updateForUkena(id, req)));
    }

    @PatchMapping("/admin/catalogue/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> catalogueUpdateStatus(
            @PathVariable String id,
            @Valid @RequestBody ProductStatusUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.updateStatusByAdmin(id, req)));
    }

    @DeleteMapping("/admin/catalogue/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> catalogueDelete(@PathVariable String id) {
        productService.adminDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/catalogue/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> catalogueAddImage(
            @PathVariable String id,
            @Valid @RequestBody AddImageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.addImageForUkena(id, req)));
    }

    @DeleteMapping("/admin/catalogue/{id}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> catalogueDeleteImage(
            @PathVariable String id,
            @PathVariable Long imageId) {
        productService.deleteImageForUkena(id, imageId);
        return ResponseEntity.noContent().build();
    }
}