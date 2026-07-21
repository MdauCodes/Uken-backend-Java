package com.mdau.ukena.shipping;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.shipping.dto.ShippingSettingsDto;
import com.mdau.ukena.shipping.dto.ShippingSettingsUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ShippingSettingsController {

    private final ShippingSettingsService shippingSettingsService;

    /** Public — the checkout page reads this to preview shipping cost from cart weight before submitting the order. */
    @GetMapping("/shipping-settings")
    public ResponseEntity<ApiResponse<ShippingSettingsDto>> get() {
        return ResponseEntity.ok(ApiResponse.ok(shippingSettingsService.get()));
    }

    @PutMapping("/admin/shipping-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShippingSettingsDto>> update(
            @Valid @RequestBody ShippingSettingsUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                shippingSettingsService.update(req), "Shipping settings updated"));
    }
}
