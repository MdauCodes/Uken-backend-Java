package com.mdau.ukena.promo;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.promo.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    /** Public — checkout calls this to check a code and preview the discount before placing the order. */
    @GetMapping("/promo-codes/validate")
    public ResponseEntity<ApiResponse<PromoCodeValidateResponse>> validate(@RequestParam String code) {
        PromoCode promo = promoCodeService.validate(code);
        return ResponseEntity.ok(ApiResponse.ok(new PromoCodeValidateResponse(promo.getCode(), promo.getPercentOff())));
    }

    /** Public — the guest email-capture banner on the storefront. Always returns 200 so we
     *  never reveal to a visitor whether the welcome offer happens to be configured. */
    @PostMapping("/promo-codes/welcome-capture")
    public ResponseEntity<ApiResponse<Void>> welcomeCapture(@Valid @RequestBody WelcomeCaptureRequest req) {
        promoCodeService.captureWelcomeEmail(req.email());
        return ResponseEntity.ok(ApiResponse.ok(null, "If eligible, your code is on its way to your inbox."));
    }

    /** Public — lets the storefront fade promo/coupon UI until the admin has
     *  configured something usable, without exposing any actual codes. */
    @GetMapping("/promo-codes/status")
    public ResponseEntity<ApiResponse<PromoCodeStatusResponse>> status() {
        return ResponseEntity.ok(ApiResponse.ok(promoCodeService.status()));
    }

    @GetMapping("/admin/promo-codes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PromoCodeDto>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(promoCodeService.listAll()));
    }

    @PostMapping("/admin/promo-codes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromoCodeDto>> create(@Valid @RequestBody PromoCodeCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(promoCodeService.create(req), "Promo code created"));
    }

    @PutMapping("/admin/promo-codes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromoCodeDto>> update(
            @PathVariable UUID id, @Valid @RequestBody PromoCodeUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(promoCodeService.update(id, req), "Promo code updated"));
    }

    @DeleteMapping("/admin/promo-codes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        promoCodeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
