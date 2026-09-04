package com.mdau.ukena.pos;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.order.dto.OrderDto;
import com.mdau.ukena.pos.dto.PosOrderRequest;
import com.mdau.ukena.pos.dto.PosPaymentIntentResponse;
import com.mdau.ukena.pos.dto.PosReaderStatus;
import com.mdau.ukena.product.dto.ProductDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Market-stall POS — Stripe Terminal server-driven checkout against a smart
 *  reader (WisePOS E / Stripe Reader S700). Admin or support staff — running the
 *  till doesn't need a full admin login. */
@RestController
@RequestMapping("/pos")
@PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
@RequiredArgsConstructor
public class PosController {

    private final PosService posService;

    /** Default POS browse grid — tap-to-add, no typing required for what's already
     *  in the stall's own catalogue (including market-only pieces). */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductDto>>> products() {
        return ResponseEntity.ok(ApiResponse.ok(posService.browseProducts()));
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderDto>> createOrder(@Valid @RequestBody PosOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(posService.createOrder(req), "POS order created"));
    }

    /** Creates the PaymentIntent and dispatches it to the reader — the reader itself
     *  then prompts the customer to tap/insert. Returns immediately; poll GET
     *  /pos/orders/{displayId} for the outcome once payment_intent.succeeded lands.
     *  Safe to call again on the same order — reuses the live PaymentIntent rather
     *  than risking a double charge. */
    @PostMapping("/orders/{displayId}/charge")
    public ResponseEntity<ApiResponse<PosPaymentIntentResponse>> charge(@PathVariable String displayId) {
        return ResponseEntity.ok(ApiResponse.ok(posService.charge(displayId), "Charge sent to reader"));
    }

    /** Cancels a stuck/unwanted reader prompt (customer walked away, mis-rung sale) —
     *  without this the next charge attempt fails with the reader considering itself
     *  still mid-action. */
    @PostMapping("/orders/{displayId}/cancel-charge")
    public ResponseEntity<ApiResponse<Void>> cancelCharge(@PathVariable String displayId) {
        posService.cancelCharge(displayId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Charge cancelled"));
    }

    /** Polled by the POS screen so an offline/busy reader is visible before a
     *  customer is standing there. */
    @GetMapping("/reader-status")
    public ResponseEntity<ApiResponse<PosReaderStatus>> readerStatus() {
        return ResponseEntity.ok(ApiResponse.ok(posService.readerStatus()));
    }

    @GetMapping("/orders/{displayId}")
    public ResponseEntity<ApiResponse<OrderDto>> getOrder(@PathVariable String displayId) {
        return ResponseEntity.ok(ApiResponse.ok(posService.getOrder(displayId)));
    }
}
