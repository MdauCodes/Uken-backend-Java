package com.mdau.ukena.shipment;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.shipment.dto.ClearShipmentRequest;
import com.mdau.ukena.shipment.dto.CreateShipmentRequest;
import com.mdau.ukena.shipment.dto.ShipmentDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/shipments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentDto>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(shipmentService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentDto>> book(
            @Valid @RequestBody CreateShipmentRequest req, @AuthenticationPrincipal CurrentUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(shipmentService.book(req, actor), "Flight booked"));
    }

    @PostMapping("/{id}/depart")
    public ResponseEntity<ApiResponse<ShipmentDto>> depart(@PathVariable String id, @AuthenticationPrincipal CurrentUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(shipmentService.depart(id, actor), "Flight departed"));
    }

    @PostMapping("/{id}/clear")
    public ResponseEntity<ApiResponse<ShipmentDto>> clear(
            @PathVariable String id, @Valid @RequestBody ClearShipmentRequest req, @AuthenticationPrincipal CurrentUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(shipmentService.clear(id, req.customsRef(), actor), "Flight landed and cleared"));
    }
}
