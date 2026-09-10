package com.mdau.ukena.pos;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderItemRepository;
import com.mdau.ukena.order.OrderItemRepository.PosProductSalesProjection;
import com.mdau.ukena.order.OrderRepository;
import com.mdau.ukena.order.OrderRepository.PosSalesDayProjection;
import com.mdau.ukena.order.OrderService;
import com.mdau.ukena.order.OrderStatus;
import com.mdau.ukena.order.dto.OrderDto;
import com.mdau.ukena.payment.PaymentService;
import com.mdau.ukena.pos.dto.AddCatalogueItemRequest;
import com.mdau.ukena.pos.dto.CreateCatalogueRequest;
import com.mdau.ukena.pos.dto.MarkMarketDayRequest;
import com.mdau.ukena.pos.dto.MarketDayCatalogueDto;
import com.mdau.ukena.pos.dto.MarketDayCatalogueItemDto;
import com.mdau.ukena.pos.dto.MarketDayCatalogueSummaryDto;
import com.mdau.ukena.pos.dto.PosBrowseItemDto;
import com.mdau.ukena.pos.dto.PosOrderRequest;
import com.mdau.ukena.pos.dto.PosPaymentIntentResponse;
import com.mdau.ukena.pos.dto.PosProductSalesDto;
import com.mdau.ukena.pos.dto.PosReaderStatus;
import com.mdau.ukena.pos.dto.PosSalesDayDto;
import com.mdau.ukena.pos.dto.UpdateCatalogueItemRequest;
import com.mdau.ukena.product.Product;
import com.mdau.ukena.product.ProductRepository;
import com.mdau.ukena.product.ProductService;
import com.mdau.ukena.product.dto.ProductSummaryDto;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosService {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final StripeTerminalService stripeTerminalService;
    private final PaymentService paymentService;
    private final MarketDayRepository marketDayRepository;
    private final MarketDayCatalogueRepository marketDayCatalogueRepository;
    private final MarketDayCatalogueItemRepository marketDayCatalogueItemRepository;

    /** Default POS browse grid. Normally Uken's own catalogue (including market-only
     *  pieces hidden from the public shop) — but when today has a MarketDay with a
     *  catalogue assigned, shows ONLY that catalogue's products at that catalogue's
     *  prices instead. Search and "New item" stay unrestricted either way; see
     *  MarketDay's class comment for why. */
    @Transactional(readOnly = true)
    public List<PosBrowseItemDto> browseProducts() {
        Optional<MarketDayCatalogue> todaysCatalogue = marketDayRepository.findById(today())
                .map(MarketDay::getCatalogue);
        if (todaysCatalogue.isPresent()) {
            return marketDayCatalogueItemRepository
                    .findByCatalogue_IdOrderByProductNameAsc(todaysCatalogue.get().getId()).stream()
                    .map(i -> new PosBrowseItemDto(i.getProduct().getId(), i.getProductName(), i.getPricePence(), i.getHeroImage()))
                    .toList();
        }
        return productService.browseForPos().stream()
                .map(p -> new PosBrowseItemDto(p.id(), p.name(), p.pricePence(), p.heroImage()))
                .toList();
    }

    /** POS search — reaches beyond Uken's own catalogue, demo/preview listings
     *  excluded (see ProductRepository.searchForPos). Deliberately not restricted
     *  by a market day's catalogue — this is the operator's own explicit escape
     *  hatch for something outside the plan. */
    public List<ProductSummaryDto> searchProducts(String q, int size) {
        return productService.searchForPos(q, size);
    }

    public OrderDto createOrder(PosOrderRequest req) {
        return orderService.placePos(req.items(), req.customerEmail(), req.customerFullName());
    }

    /**
     * Creates (or safely reuses) the PaymentIntent and dispatches it to the reader.
     * Idempotent by design — Stripe's own guidance for a server-driven integration is
     * explicit that recreating a PaymentIntent on retry risks a double charge if the
     * first attempt actually went through; re-dispatching the SAME intent is required
     * instead. The order stays PENDING until the payment_intent.succeeded webhook
     * lands (see PaymentService) — the POS page polls getOrder() to find out.
     *
     * Deliberately NOT @Transactional. dispatchToReader() throws on the single most
     * common failure (reader offline/busy) — with a surrounding transaction, that
     * exception would roll back the order.setPaymentIntentId() save a few lines
     * above, silently undoing the "persist before dispatch" guarantee exactly when
     * it matters (a webhook or the next retry would find paymentIntentId still
     * null). Each repository call below is transactional on its own by default
     * with no enclosing transaction, so the persist genuinely commits before
     * dispatch is attempted, regardless of how dispatch turns out.
     */
    public PosPaymentIntentResponse charge(String displayId) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + displayId));
        if (order.getStatus() != OrderStatus.PENDING)
            throw ApiException.badRequest("This order is not awaiting payment (status: " + order.getStatus() + ")");

        String paymentIntentId = order.getPaymentIntentId();
        String clientSecret = null;

        if (paymentIntentId != null && !paymentIntentId.isBlank()) {
            PaymentIntent existing = stripeTerminalService.retrievePaymentIntent(paymentIntentId);
            switch (existing.getStatus()) {
                case "succeeded" -> {
                    // A webhook may simply not have landed yet — don't create a second
                    // charge for money that's already moved. Let the poll/webhook settle it.
                    return new PosPaymentIntentResponse(existing.getId(), existing.getClientSecret());
                }
                case "requires_payment_method" -> clientSecret = existing.getClientSecret(); // safe to re-dispatch as-is
                default -> {
                    // canceled, or any other non-retryable state — start clean.
                    stripeTerminalService.cancelPaymentIntent(paymentIntentId);
                    paymentIntentId = null;
                }
            }
        }

        if (paymentIntentId == null) {
            PosPaymentIntentResponse created = stripeTerminalService.createPaymentIntent(
                    displayId, order.getTotalPence(), receiptEmailFor(order));
            paymentIntentId = created.paymentIntentId();
            clientSecret = created.clientSecret();
        }

        // Persist BEFORE dispatch — a webhook that beats this HTTP response back must
        // still be able to resolve to this order (see PaymentService.resolveOrderForTerminalEvent).
        order.setPaymentIntentId(paymentIntentId);
        order.setLastPaymentError(null);
        orderRepository.save(order);

        stripeTerminalService.dispatchToReader(paymentIntentId);
        return new PosPaymentIntentResponse(paymentIntentId, clientSecret);
    }

    /** The operator's escape hatch for a stuck reader prompt (customer walked away,
     *  mis-rung sale) — cancels both the reader-side action and the PaymentIntent so
     *  the next charge attempt starts clean instead of failing with terminal_reader_busy.
     *  No-ops the local cleanup if the order already succeeded (a slow webhook could
     *  race with an operator tapping cancel) — never null out paymentIntentId on a
     *  completed order, since that's a real reference a later refund still needs. */
    @Transactional
    public void cancelCharge(String displayId) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + displayId));
        stripeTerminalService.cancelReaderAction();
        if (order.getStatus() == OrderStatus.PENDING && order.getPaymentIntentId() != null) {
            stripeTerminalService.cancelPaymentIntent(order.getPaymentIntentId());
            order.setPaymentIntentId(null);
            orderRepository.save(order);
        }
    }

    /** The customer email collected (optionally) in the POS cart, if a real one was
     *  given — used as the PaymentIntent's receiptEmail so Stripe sends its own
     *  compliant card-present receipt. Never the walk-in sentinel. */
    private String receiptEmailFor(Order order) {
        String email = order.getBuyerEmail();
        return OrderService.WALK_IN_EMAIL.equalsIgnoreCase(email) ? null : email;
    }

    public PosReaderStatus readerStatus() {
        return stripeTerminalService.readerStatus();
    }

    public OrderDto getOrder(String displayId) {
        return orderService.getByDisplayId(displayId);
    }

    /** Checks Stripe directly for this order's true payment status and settles it if
     *  Stripe already says "succeeded" — see PaymentService.reconcilePosOrder. */
    public OrderDto reconcile(String displayId) {
        paymentService.reconcilePosOrder(displayId);
        return orderService.getByDisplayId(displayId);
    }

    /** More than this many completed sales in one calendar day counts as a genuine
     *  Market Day rather than a one-off in-person sale or two. */
    private static final int MARKET_DAY_THRESHOLD = 3;

    /** Completed market-stall sales grouped by the calendar day they happened on
     *  (Europe/London, where the stalls trade), newest first — the sales-history view
     *  the admin dashboard was missing entirely. A day past the threshold is flagged
     *  as a Market Day; an admin can also flag/plan one by hand (see markMarketDay)
     *  for a day the threshold missed, or ahead of a day that hasn't happened yet — a
     *  manually-flagged day with no completed sales at all still shows up here, with
     *  a zero count. Every qualifying day gets a stable sequence number (oldest
     *  qualifying day = 1), so "Market Day 7" reads the same way even after more days
     *  are added later. */
    @Transactional(readOnly = true)
    public List<PosSalesDayDto> salesByDate() {
        List<PosSalesDayProjection> aggregated = orderRepository.aggregatePosSalesByDate();
        Map<LocalDate, PosSalesDayProjection> byDate = aggregated.stream()
                .collect(Collectors.toMap(PosSalesDayProjection::getSaleDate, d -> d));
        Map<LocalDate, MarketDay> manualByDate = marketDayRepository.findAll().stream()
                .collect(Collectors.toMap(MarketDay::getDate, d -> d));

        Set<LocalDate> allDates = new TreeSet<>(Comparator.reverseOrder()); // newest first
        allDates.addAll(byDate.keySet());
        allDates.addAll(manualByDate.keySet());

        List<LocalDate> chronological = new ArrayList<>(allDates);
        Collections.reverse(chronological); // oldest first, so numbering is stable
        Map<LocalDate, Integer> marketDayNumbers = new HashMap<>();
        int number = 0;
        for (LocalDate date : chronological) {
            long orderCount = orderCountFor(byDate, date);
            if (orderCount > MARKET_DAY_THRESHOLD || manualByDate.containsKey(date)) {
                marketDayNumbers.put(date, ++number);
            }
        }

        return allDates.stream()
                .map(date -> {
                    PosSalesDayProjection row = byDate.get(date);
                    long orderCount = row != null ? row.getOrderCount() : 0;
                    long totalPence = row != null ? row.getTotalPence() : 0;
                    MarketDay manual = manualByDate.get(date);
                    boolean isMarketDay = orderCount > MARKET_DAY_THRESHOLD || manual != null;
                    MarketDayCatalogue catalogue = manual != null ? manual.getCatalogue() : null;
                    return new PosSalesDayDto(
                            date, orderCount, totalPence, isMarketDay,
                            isMarketDay ? marketDayNumbers.get(date) : null,
                            manual != null,
                            manual != null ? manual.getName() : null,
                            catalogue != null ? catalogue.getId() : null,
                            catalogue != null ? catalogue.getName() : null);
                })
                .toList();
    }

    private long orderCountFor(Map<LocalDate, PosSalesDayProjection> byDate, LocalDate date) {
        PosSalesDayProjection row = byDate.get(date);
        return row != null ? row.getOrderCount() : 0;
    }

    /** Per-product totals across every completed market-stall sale, best-sellers
     *  first — units sold, revenue, and current stock so an admin can see units
     *  stocked vs. sold at a glance. */
    public List<PosProductSalesDto> productSales() {
        return orderItemRepository.aggregatePosSalesByProduct().stream()
                .map(row -> new PosProductSalesDto(
                        row.getProductId(),
                        row.getProductName(),
                        row.getHeroImage(),
                        row.getUnitsSold(),
                        row.getRevenuePence(),
                        row.getUnitsAvailable()))
                .toList();
    }

    /** Creates or updates the MarketDay row for a date — the original bare "just flag
     *  this date" behaviour when req is null/empty, or plans it with a name and/or
     *  assigns a catalogue (restricting the till on that date — see MarketDay).
     *  Idempotent; safe on a past OR future date. */
    @Transactional
    public void markMarketDay(LocalDate date, MarkMarketDayRequest req) {
        MarketDay day = marketDayRepository.findById(date).orElseGet(() -> {
            MarketDay d = new MarketDay();
            d.setDate(date);
            return d;
        });
        if (req != null) {
            if (req.name() != null) {
                day.setName(req.name().isBlank() ? null : req.name().trim());
            }
            if (req.catalogueId() != null) {
                day.setCatalogue(requireCatalogue(req.catalogueId()));
            }
        }
        marketDayRepository.save(day);
    }

    /** Removes a manual/planned Market Day entirely (flag, name, and catalogue
     *  assignment together). A day that separately qualifies via the sales
     *  threshold stays flagged, since that part isn't a decision to undo. */
    @Transactional
    public void unmarkMarketDay(LocalDate date) {
        marketDayRepository.deleteById(date);
    }

    /** Detaches just the catalogue from a market day — the till stops being
     *  restricted on that date, but the day keeps its flag/name. */
    @Transactional
    public void unassignCatalogue(LocalDate date) {
        marketDayRepository.findById(date).ifPresent(day -> {
            day.setCatalogue(null);
            marketDayRepository.save(day);
        });
    }

    /* ------------------------------------------------------------------ *
     * Market Day catalogues
     * ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public List<MarketDayCatalogueSummaryDto> listCatalogues() {
        return marketDayCatalogueRepository.findAllNewestFirst().stream()
                .map(c -> new MarketDayCatalogueSummaryDto(
                        c.getId(), c.getName(), c.getCreatedAt(),
                        marketDayCatalogueItemRepository.countByCatalogue_Id(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketDayCatalogueDto getCatalogue(UUID catalogueId) {
        MarketDayCatalogue catalogue = requireCatalogue(catalogueId);
        return toCatalogueDto(catalogue);
    }

    /** Starts a new, independent catalogue — empty, or pre-seeded with a fresh COPY
     *  of one or more existing catalogues' items (merged by product, last source
     *  wins on a shared one) when cloneFromCatalogueIds is given. The copy is real:
     *  editing this catalogue's products/prices afterwards never touches whatever
     *  it was cloned from. */
    @Transactional
    public MarketDayCatalogueDto createCatalogue(CreateCatalogueRequest req) {
        MarketDayCatalogue catalogue = new MarketDayCatalogue();
        catalogue.setName(req.name().trim());
        catalogue = marketDayCatalogueRepository.save(catalogue);

        if (req.cloneFromCatalogueIds() != null && !req.cloneFromCatalogueIds().isEmpty()) {
            Map<String, MarketDayCatalogueItem> merged = new java.util.LinkedHashMap<>();
            for (UUID sourceId : req.cloneFromCatalogueIds()) {
                for (MarketDayCatalogueItem item :
                        marketDayCatalogueItemRepository.findByCatalogue_IdOrderByProductNameAsc(sourceId)) {
                    merged.put(item.getProduct().getId(), item);
                }
            }
            MarketDayCatalogue target = catalogue;
            List<MarketDayCatalogueItem> copies = merged.values().stream().map(src -> {
                MarketDayCatalogueItem copy = new MarketDayCatalogueItem();
                copy.setCatalogue(target);
                copy.setProduct(src.getProduct());
                copy.setProductName(src.getProductName());
                copy.setHeroImage(src.getHeroImage());
                copy.setPricePence(src.getPricePence());
                return copy;
            }).toList();
            marketDayCatalogueItemRepository.saveAll(copies);
        }

        return getCatalogue(catalogue.getId());
    }

    /** Adds (or updates, if the product's already in this catalogue) one product —
     *  name/image/price are snapshotted from the live product now and become this
     *  catalogue's own copy from this point on. Omit pricePence to snapshot the
     *  product's current live price. */
    @Transactional
    public MarketDayCatalogueDto addCatalogueItem(UUID catalogueId, AddCatalogueItemRequest req) {
        MarketDayCatalogue catalogue = requireCatalogue(catalogueId);
        Product product = productRepository.findActiveById(req.productId())
                .orElseThrow(() -> ApiException.notFound("Product not found: " + req.productId()));

        MarketDayCatalogueItem item = marketDayCatalogueItemRepository
                .findByCatalogue_IdAndProduct_Id(catalogueId, product.getId())
                .orElseGet(MarketDayCatalogueItem::new);
        item.setCatalogue(catalogue);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setHeroImage(product.getHeroImage());
        item.setPricePence(req.pricePence() != null ? req.pricePence() : product.getPricePence());
        marketDayCatalogueItemRepository.save(item);

        return getCatalogue(catalogueId);
    }

    @Transactional
    public MarketDayCatalogueDto updateCatalogueItem(UUID catalogueId, UUID itemId, UpdateCatalogueItemRequest req) {
        MarketDayCatalogueItem item = marketDayCatalogueItemRepository.findByIdAndCatalogue_Id(itemId, catalogueId)
                .orElseThrow(() -> ApiException.notFound("Catalogue item not found"));
        item.setPricePence(req.pricePence());
        marketDayCatalogueItemRepository.save(item);
        return getCatalogue(catalogueId);
    }

    @Transactional
    public MarketDayCatalogueDto removeCatalogueItem(UUID catalogueId, UUID itemId) {
        MarketDayCatalogueItem item = marketDayCatalogueItemRepository.findByIdAndCatalogue_Id(itemId, catalogueId)
                .orElseThrow(() -> ApiException.notFound("Catalogue item not found"));
        marketDayCatalogueItemRepository.delete(item);
        return getCatalogue(catalogueId);
    }

    /** Refuses to delete a catalogue that's still assigned to a market day — that
     *  would silently un-restrict the till on whatever date was using it. Unassign
     *  it first (unassignCatalogue). */
    @Transactional
    public void deleteCatalogue(UUID catalogueId) {
        requireCatalogue(catalogueId);
        if (marketDayRepository.existsByCatalogue_Id(catalogueId)) {
            throw ApiException.badRequest("This catalogue is assigned to a market day — unassign it there first");
        }
        marketDayCatalogueRepository.deleteById(catalogueId);
    }

    private MarketDayCatalogue requireCatalogue(UUID catalogueId) {
        return marketDayCatalogueRepository.findById(catalogueId)
                .orElseThrow(() -> ApiException.notFound("Catalogue not found: " + catalogueId));
    }

    private MarketDayCatalogueDto toCatalogueDto(MarketDayCatalogue catalogue) {
        List<MarketDayCatalogueItemDto> items = marketDayCatalogueItemRepository
                .findByCatalogue_IdOrderByProductNameAsc(catalogue.getId()).stream()
                .map(i -> new MarketDayCatalogueItemDto(
                        i.getId(), i.getProduct().getId(), i.getProductName(), i.getHeroImage(), i.getPricePence()))
                .toList();
        return new MarketDayCatalogueDto(catalogue.getId(), catalogue.getName(), catalogue.getCreatedAt(), items);
    }

    private static LocalDate today() {
        return LocalDate.now(LONDON);
    }
}
