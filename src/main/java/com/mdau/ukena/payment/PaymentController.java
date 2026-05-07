package com.mdau.ukena.payment;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> initiate(
            @Valid @RequestBody PaymentInitiateRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {
        PaymentInitResponse result = paymentService.initiate(req.orderId(), currentUser);
        return ResponseEntity.ok(ApiResponse.ok(result, "Payment link generated"));
    }

    @GetMapping("/webhook")
    public ResponseEntity<String> webhookGet(
            @RequestParam(value = "orderTrackingId",        required = false) String trackingId,
            @RequestParam(value = "orderMerchantReference", required = false) String merchantRef,
            @RequestParam(value = "orderNotificationType",  required = false) String notifType) {
        paymentService.handlePesapalIpn(trackingId, merchantRef);
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhookPost(
            HttpServletRequest httpReq,
            @RequestBody(required = false) String rawBody) {
        String signature = httpReq.getHeader("verif-hash");
        paymentService.handleFlutterwaveWebhook(rawBody, signature);
        return ResponseEntity.ok("OK");
    }
}