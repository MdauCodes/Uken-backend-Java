package com.mdau.ukena.delivery;

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
@RequiredArgsConstructor
public class DeliveryZoneController {

    private final DeliveryZoneService deliveryZoneService;

    // Public — buyer fetches zones for checkout dropdown
    @GetMapping("/delivery-zones")
    public ResponseEntity<ApiResponse<List<DeliveryZoneDto>>> listActive() {
        return ResponseEntity.ok(ApiResponse.ok(deliveryZoneService.listActive()));
    }

    // Admin — full list including inactive
    @GetMapping("/admin/delivery-zones")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DeliveryZoneDto>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(deliveryZoneService.listAll()));
    }

    @PostMapping("/admin/delivery-zones")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DeliveryZoneDto>> create(
            @Valid @RequestBody DeliveryZoneRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        deliveryZoneService.create(req), "Delivery zone created"));
    }

    @PutMapping("/admin/delivery-zones/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DeliveryZoneDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody DeliveryZoneRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                deliveryZoneService.update(id, req), "Delivery zone updated"));
    }

    @DeleteMapping("/admin/delivery-zones/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deliveryZoneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}