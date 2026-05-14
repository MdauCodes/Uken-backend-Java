package com.mdau.ukena.payment;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> initiate(
            @Valid @RequestBody PaymentInitiateRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {

        CurrentUser caller;

        if (currentUser != null) {
            caller = currentUser;
        } else if (req.guestEmail() != null && !req.guestEmail().isBlank()) {
            // Guest buyer — no account required; identity verified via order's buyerEmail
            caller = new CurrentUser(null, req.guestEmail().toLowerCase().trim(), null, null);
        } else {
            throw ApiException.forbidden("Provide guestEmail or log in");
        }

        PaymentInitResponse result = paymentService.initiate(req.orderId(), caller);
        return ResponseEntity.ok(ApiResponse.ok(result, "Payment link generated"));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            HttpServletRequest httpReq,
            @RequestBody(required = false) String rawBody) {
        String stripeSignature   = httpReq.getHeader("Stripe-Signature");
        String paystackSignature = httpReq.getHeader("x-paystack-signature");
        String signature = stripeSignature != null ? stripeSignature : paystackSignature;
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok("OK");
    }
}