package com.mdau.ukena.pos;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.order.dto.OrderDto;
import com.mdau.ukena.pos.dto.AddCatalogueItemRequest;
import com.mdau.ukena.pos.dto.CreateCatalogueRequest;
import com.mdau.ukena.pos.dto.MarkMarketDayRequest;
import com.mdau.ukena.pos.dto.MarketDayCatalogueDto;
import com.mdau.ukena.pos.dto.MarketDayCatalogueSummaryDto;
import com.mdau.ukena.pos.dto.PosBrowseItemDto;
import com.mdau.ukena.pos.dto.PosOrderRequest;
import com.mdau.ukena.pos.dto.PosPaymentIntentResponse;
import com.mdau.ukena.pos.dto.PosProductSalesDto;
import com.mdau.ukena.pos.dto.PosReaderStatus;
import com.mdau.ukena.pos.dto.PosSalesDayDto;
import com.mdau.ukena.pos.dto.UpdateCatalogueItemRequest;
import com.mdau.ukena.product.dto.ProductSummaryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Market-stall POS — Stripe Terminal server-driven checkout against a smart
 *  reader (WisePOS E / Stripe Reader S700). Admin or support staff — running the
 *  till doesn't need a full admin login. */
@RestController
@RequestMapping("/pos")
@PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
@RequiredArgsConstructor
public class PosController {

    private final PosService posService;

    /** Default POS browse grid — tap-to-add, no typing required. Normally the
     *  stall's own catalogue (including market-only pieces); on a day with a
     *  MarketDayCatalogue assigned, only that catalogue's products. */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<PosBrowseItemDto>>> products() {
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

    /** Per-product sales totals across every completed market-stall sale, best
     *  sellers first — backs the "best selling units" / stock-vs-sold section of
     *  the Market Days report. */
    @GetMapping("/product-sales")
    public ResponseEntity<ApiResponse<List<PosProductSalesDto>>> productSales() {
        return ResponseEntity.ok(ApiResponse.ok(posService.productSales()));
    }

    /** Creates/updates the Market Day for a date — plain flag when the body is
     *  omitted, or set a name and/or assign a catalogue (which then restricts the
     *  till on that date — see MarketDay). Works on a past OR future date, so a
     *  market day can be planned ahead. Admin-only (unlike the rest of this
     *  controller): this is a business call, not something till staff decide
     *  mid-sale. */
    @PutMapping("/market-days/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PosSalesDayDto>>> markMarketDay(
            @PathVariable LocalDate date,
            @RequestBody(required = false) MarkMarketDayRequest req) {
        posService.markMarketDay(date, req);
        return ResponseEntity.ok(ApiResponse.ok(posService.salesByDate(), "Market Day saved"));
    }

    /** Removes a manual/planned Market Day entirely (flag, name, and catalogue
     *  together) — a day that separately qualifies via the sales threshold stays
     *  flagged regardless. */
    @DeleteMapping("/market-days/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PosSalesDayDto>>> unmarkMarketDay(@PathVariable LocalDate date) {
        posService.unmarkMarketDay(date);
        return ResponseEntity.ok(ApiResponse.ok(posService.salesByDate(), "Market Day removed"));
    }

    /** Detaches just the catalogue from a market day — the till stops being
     *  restricted that date, but the day keeps its flag/name. */
    @DeleteMapping("/market-days/{date}/catalogue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PosSalesDayDto>>> unassignCatalogue(@PathVariable LocalDate date) {
        posService.unassignCatalogue(date);
        return ResponseEntity.ok(ApiResponse.ok(posService.salesByDate(), "Catalogue unassigned"));
    }

    /** Fallback for when the payment_intent.succeeded webhook is slow or never lands —
     *  checks Stripe directly and settles the order if Stripe already says the charge
     *  succeeded. Safe to call repeatedly; a no-op once the order is already resolved. */
    @PostMapping("/orders/{displayId}/reconcile")
    public ResponseEntity<ApiResponse<OrderDto>> reconcile(@PathVariable String displayId) {
        return ResponseEntity.ok(ApiResponse.ok(posService.reconcile(displayId), "Checked with Stripe directly"));
    }

    /* ------------------------------------------------------------------ *
     * Market Day catalogues — admin-only, same reasoning as market-days above.
     * ------------------------------------------------------------------ */

    @GetMapping("/market-day-catalogues")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<MarketDayCatalogueSummaryDto>>> listCatalogues() {
        return ResponseEntity.ok(ApiResponse.ok(posService.listCatalogues()));
    }

    @GetMapping("/market-day-catalogues/{catalogueId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketDayCatalogueDto>> getCatalogue(@PathVariable UUID catalogueId) {
        return ResponseEntity.ok(ApiResponse.ok(posService.getCatalogue(catalogueId)));
    }

    /** Starts a new catalogue — empty, or pre-seeded with an independent copy of
     *  one or more existing catalogues' items when cloneFromCatalogueIds is given. */
    @PostMapping("/market-day-catalogues")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketDayCatalogueDto>> createCatalogue(
            @Valid @RequestBody CreateCatalogueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(posService.createCatalogue(req), "Catalogue created"));
    }

    @PostMapping("/market-day-catalogues/{catalogueId}/items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketDayCatalogueDto>> addCatalogueItem(
            @PathVariable UUID catalogueId, @Valid @RequestBody AddCatalogueItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(posService.addCatalogueItem(catalogueId, req), "Product added"));
    }

    @PatchMapping("/market-day-catalogues/{catalogueId}/items/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketDayCatalogueDto>> updateCatalogueItem(
            @PathVariable UUID catalogueId, @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCatalogueItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(posService.updateCatalogueItem(catalogueId, itemId, req), "Price updated"));
    }

    @DeleteMapping("/market-day-catalogues/{catalogueId}/items/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketDayCatalogueDto>> removeCatalogueItem(
            @PathVariable UUID catalogueId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok(posService.removeCatalogueItem(catalogueId, itemId), "Product removed"));
    }

    @DeleteMapping("/market-day-catalogues/{catalogueId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCatalogue(@PathVariable UUID catalogueId) {
        posService.deleteCatalogue(catalogueId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Catalogue deleted"));
    }
}
