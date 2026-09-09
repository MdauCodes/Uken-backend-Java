package com.mdau.ukena.pos;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.order.dto.OrderDto;
import com.mdau.ukena.pos.dto.PosOrderRequest;
import com.mdau.ukena.pos.dto.PosPaymentIntentResponse;
import com.mdau.ukena.pos.dto.PosReaderStatus;
import com.mdau.ukena.pos.dto.PosSalesDayDto;
import com.mdau.ukena.product.dto.ProductDto;
import com.mdau.ukena.product.dto.ProductSummaryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    /** Reaches beyond the stall's own catalogue into other creators' real work —
     *  demo/preview listings excluded, unlike the public /search endpoint. */
    @GetMapping("/products/search")
    public ResponseEntity<ApiResponse<List<ProductSummaryDto>>> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok(posService.searchProducts(q, size)));
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

    /** Sales history — completed market-stall sales grouped by day, with days past the
     *  Market Day threshold flagged. Backs the admin "Market Days" report. */
    @GetMapping("/sales-by-date")
    public ResponseEntity<ApiResponse<List<PosSalesDayDto>>> salesByDate() {
        return ResponseEntity.ok(ApiResponse.ok(posService.salesByDate()));
    }

    /** Admin override — calls a date a Market Day regardless of its actual sales
     *  count. Admin-only (unlike the rest of this controller): this is a business
     *  call about the stall's history, not something till staff decide mid-sale. */
    @PutMapping("/market-days/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PosSalesDayDto>>> markMarketDay(@PathVariable LocalDate date) {
        posService.markMarketDay(date);
        return ResponseEntity.ok(ApiResponse.ok(posService.salesByDate(), "Marked as a Market Day"));
    }

    /** Removes a manual Market Day flag — a day that separately qualifies via the
     *  sales threshold stays flagged regardless. */
    @DeleteMapping("/market-days/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PosSalesDayDto>>> unmarkMarketDay(@PathVariable LocalDate date) {
        posService.unmarkMarketDay(date);
        return ResponseEntity.ok(ApiResponse.ok(posService.salesByDate(), "Market Day override removed"));
    }

    /** Fallback for when the payment_intent.succeeded webhook is slow or never lands —
     *  checks Stripe directly and settles the order if Stripe already says the charge
     *  succeeded. Safe to call repeatedly; a no-op once the order is already resolved. */
    @PostMapping("/orders/{displayId}/reconcile")
    public ResponseEntity<ApiResponse<OrderDto>> reconcile(@PathVariable String displayId) {
        return ResponseEntity.ok(ApiResponse.ok(posService.reconcile(displayId), "Checked with Stripe directly"));
    }
}
