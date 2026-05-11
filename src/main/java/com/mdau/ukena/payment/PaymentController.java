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

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            HttpServletRequest httpReq,
            @RequestBody(required = false) String rawBody) {
        // Support both Stripe and Paystack webhook signatures
        String stripeSignature   = httpReq.getHeader("Stripe-Signature");
        String paystackSignature = httpReq.getHeader("x-paystack-signature");
        String signature = stripeSignature != null ? stripeSignature : paystackSignature;
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok("OK");
    }
}