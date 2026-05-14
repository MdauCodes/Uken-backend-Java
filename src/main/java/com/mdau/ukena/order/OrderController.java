package com.mdau.ukena.order;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.order.dto.*;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderDto>> place(
            @Valid @RequestBody CreateOrderRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {

        User buyer = null;

        if (currentUser != null) {
            buyer = userRepository.findById(currentUser.id())
                    .orElseThrow(() -> ApiException.notFound("User not found"));
        } else if (req.guestEmail() != null && req.guestFullName() != null) {
            // guest — buyer stays null, email/name carried in request
        } else {
            throw ApiException.badRequest("Provide guestEmail and guestFullName for guest checkout, or log in");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        orderService.place(buyer, req),
                        "Order placed successfully"));
    }

    @GetMapping("/orders/track")
    public ResponseEntity<ApiResponse<OrderDto>> track(
            @RequestParam String displayId,
            @RequestParam String email) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.trackGuestOrder(displayId, email)));
    }

    @GetMapping("/orders/me")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<List<OrderDto>>> buyerOrders(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getBuyerOrders(currentUser.id())));
    }

    @GetMapping("/creators/me/orders")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<List<OrderDto>>> creatorOrders(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getCreatorOrders(currentUser.creatorId())));
    }

    @PatchMapping("/creators/me/orders/{displayId}/status")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<OrderDto>> updateStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String displayId,
            @Valid @RequestBody UpdateStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.updateStatus(
                        currentUser.creatorId(), displayId, req)));
    }

    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderDto>>> adminOrders() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getAllOrders()));
    }

    @PatchMapping("/admin/orders/{displayId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDto>> adminRefund(
            @PathVariable String displayId) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.adminRefund(displayId)));
    }

    @PatchMapping("/admin/orders/{displayId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDto>> adminUpdateStatus(
            @PathVariable String displayId,
            @Valid @RequestBody UpdateStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.adminUpdateStatus(displayId, req)));
    }

    @PatchMapping("/admin/orders/bulk-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderDto>>> adminBulkUpdateStatus(
            @Valid @RequestBody BulkStatusUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.adminBulkUpdateStatus(req)));
    }
}