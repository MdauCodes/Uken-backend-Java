package com.mdau.ukena.order;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.order.access.OrderAccessService;
import com.mdau.ukena.order.access.TrackCodeRequest;
import com.mdau.ukena.order.access.TrackVerifyRequest;
import com.mdau.ukena.order.access.TrackVerifyResponse;
import com.mdau.ukena.order.dto.*;
import com.mdau.ukena.security.CurrentUser;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Hub order flow endpoints — staff moving orders along, makers marking their
 * part dropped at the hub, and buyers (signed in, or guests with an emailed
 * code) tracking, confirming and reporting problems.
 *
 * Handlers that load an order and then build its DTO run in one transaction,
 * so the order's lazy collections (items) are still readable. verify() is the
 * exception: its failed-attempt counter must commit even when the code is wrong.
 */
@RestController
@RequiredArgsConstructor
public class OrderFlowController {

    private static final String ACCESS_HEADER = "X-Order-Access";

    private final OrderFlowService flowService;
    private final OrderAccessService accessService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;

    // ── Staff ───────────────────────────────────────────────────────────────

    @GetMapping("/admin/orders/{displayId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<OrderDto>> adminGet(@PathVariable String displayId) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> com.mdau.ukena.common.ApiException.notFound("Order not found"));
        return ResponseEntity.ok(ApiResponse.ok(orderService.toStaffDto(order)));
    }

    @PostMapping("/admin/orders/{displayId}/advance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDto>> advance(@PathVariable String displayId,
            @Valid @RequestBody AdvanceOrderRequest req, @AuthenticationPrincipal CurrentUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(flowService.advance(displayId, req, actor)));
    }

    @PostMapping("/admin/orders/{displayId}/override")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDto>> override(@PathVariable String displayId,
            @Valid @RequestBody OverrideStageRequest req, @AuthenticationPrincipal CurrentUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(flowService.override(displayId, req, actor)));
    }

    @PatchMapping("/admin/orders/{displayId}/maker-ready")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDto>> makerReady(@PathVariable String displayId,
            @Valid @RequestBody MakerReadyRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(flowService.setMakerReady(displayId, req.creatorId(), req.ready())));
    }

    @PatchMapping("/admin/orders/{displayId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDto>> assign(@PathVariable String displayId,
            @Valid @RequestBody AssignOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(flowService.assign(displayId, req.assignedTo())));
    }

    @PostMapping("/admin/orders/{displayId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDto>> resolve(@PathVariable String displayId,
            @Valid @RequestBody ResolveIssueRequest req, @AuthenticationPrincipal CurrentUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(flowService.resolveIssue(displayId, req, actor)));
    }

    // ── Makers ──────────────────────────────────────────────────────────────

    @PostMapping("/creators/me/orders/{displayId}/at-hub")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<OrderDto>> makerAtHub(@PathVariable String displayId,
            @AuthenticationPrincipal CurrentUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(
                flowService.makerDroppedAtHub(displayId, actor.creatorId()), "Marked as dropped at the hub"));
    }

    // ── Buyers and guests ───────────────────────────────────────────────────

    /** Emails a 6-digit code if the order and email match. Always answers the same way. */
    @PostMapping("/orders/track/code")
    @RateLimiter(name = "auth-api")
    public ResponseEntity<ApiResponse<Void>> sendCode(@Valid @RequestBody TrackCodeRequest req) {
        accessService.sendCode(req);
        return ResponseEntity.ok(ApiResponse.ok(null, "If that order matches, we've emailed a code"));
    }

    @PostMapping("/orders/track/verify")
    @RateLimiter(name = "auth-api")
    public ResponseEntity<ApiResponse<TrackVerifyResponse>> verify(@Valid @RequestBody TrackVerifyRequest req) {
        String token = accessService.verify(req);
        return ResponseEntity.ok(ApiResponse.ok(
                new TrackVerifyResponse(token, orderService.toDtoByDisplayId(req.displayId().trim()))));
    }

    /** The order for its signed-in owner, or a guest holding a valid access token. */
    @GetMapping("/orders/access/{displayId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<OrderDto>> view(@PathVariable String displayId,
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader(value = ACCESS_HEADER, required = false) String token) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.toDto(accessService.resolve(displayId, user, token))));
    }

    @PostMapping("/orders/access/{displayId}/confirm")
    @Transactional
    public ResponseEntity<ApiResponse<OrderDto>> confirm(@PathVariable String displayId,
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader(value = ACCESS_HEADER, required = false) String token) {
        Order order = accessService.resolve(displayId, user, token);
        return ResponseEntity.ok(ApiResponse.ok(
                flowService.confirmReceived(order, order.getBuyerFullName()), "Thanks for confirming"));
    }

    @PostMapping("/orders/access/{displayId}/report")
    @Transactional
    public ResponseEntity<ApiResponse<OrderDto>> report(@PathVariable String displayId,
            @Valid @RequestBody ReportProblemRequest req,
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader(value = ACCESS_HEADER, required = false) String token) {
        Order order = accessService.resolve(displayId, user, token);
        return ResponseEntity.ok(ApiResponse.ok(
                flowService.reportProblem(order, order.getBuyerFullName(), req), "We've got it"));
    }
}
