package com.mdau.ukena.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.audit.AuditLogService;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.delivery.DeliveryZone;
import com.mdau.ukena.delivery.DeliveryZoneService;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.dto.*;
import com.mdau.ukena.payment.EarningsLedger;
import com.mdau.ukena.payment.EarningsLedgerRepository;
import com.mdau.ukena.payment.LedgerStatus;
import com.mdau.ukena.payment.PaymentGateway;
import com.mdau.ukena.payment.PayoutUpdateService;
import com.mdau.ukena.payment.RefundRequest;
import com.mdau.ukena.payment.RefundResult;
import com.mdau.ukena.pos.MarketDay;
import com.mdau.ukena.pos.MarketDayCatalogueItem;
import com.mdau.ukena.pos.MarketDayCatalogueItemRepository;
import com.mdau.ukena.pos.MarketDayRepository;
import com.mdau.ukena.product.Product;
import com.mdau.ukena.product.ProductRepository;
import com.mdau.ukena.product.ProductService;
import com.mdau.ukena.product.ProductStatus;
import com.mdau.ukena.product.dto.ProductCategoryDto;
import com.mdau.ukena.promo.PromoCode;
import com.mdau.ukena.promo.PromoCodeService;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.shipping.ShippingSettings;
import com.mdau.ukena.shipping.ShippingSettingsService;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** Sentinel buyerEmail for an anonymous POS sale (no customer email given) — Order.buyerEmail
     *  is NOT NULL, so this fills the column without implying a real address. Never send mail here. */
    public static final String WALK_IN_EMAIL = "walk-in@ukena.co.uk";

    private final OrderRepository        orderRepository;
    private final ProductRepository      productRepository;
    private final UserRepository         userRepository;
    private final DeliveryZoneService    deliveryZoneService;
    private final ShippingSettingsService shippingSettingsService;
    private final PromoCodeService       promoCodeService;
    private final ObjectMapper           objectMapper;
    private final EmailService           emailService;
    private final PaymentGateway         paymentGateway;
    private final EarningsLedgerRepository ledgerRepository;
    private final PayoutUpdateService    payoutUpdateService;
    private final ProductService         productService;
    private final AuditLogService        auditLogService;
    private final MarketDayRepository    marketDayRepository;
    private final MarketDayCatalogueItemRepository marketDayCatalogueItemRepository;

    @Transactional
    public OrderDto place(User buyer, CreateOrderRequest req) {
        String buyerEmail    = buyer != null ? buyer.getEmail()    : req.guestEmail().toLowerCase().trim();
        String buyerFullName = buyer != null ? buyer.getFullName() : req.guestFullName().trim();

        // Zone still picks/validates the delivery country — it no longer prices shipping.
        DeliveryZone zone = deliveryZoneService.getActiveById(req.deliveryZoneId());
        ShippingSettings shippingSettings = shippingSettingsService.getOrCreate();

        List<OrderItem> items = req.items().stream().map(itemReq -> {
            Product product = productRepository.findActiveById(itemReq.productId())
                    .orElseThrow(() -> ApiException.notFound(
                            "Product not found: " + itemReq.productId()));
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw ApiException.badRequest(
                        product.getStatus() == ProductStatus.OUT_OF_STOCK
                                ? product.getName() + " is currently out of stock"
                                : product.getName() + " is not available for purchase");
            }
            if (product.getUnitsAvailable() != null && itemReq.quantity() > product.getUnitsAvailable()) {
                throw ApiException.badRequest(
                        "Only " + product.getUnitsAvailable() + " of " + product.getName() + " left in stock");
            }
            Integer productWeight = product.getWeightGrams();
            int unitWeightGrams = (productWeight != null && productWeight > 0)
                    ? productWeight : shippingSettings.getFallbackWeightGrams();
            return OrderItem.builder()
                    .product(product)
                    .creator(product.getCreator())
                    .productName(product.getName())
                    .quantity(itemReq.quantity())
                    .pricePence(product.getPricePence())
                    .weightGrams(unitWeightGrams)
                    .image(product.getHeroImage())
                    .creatorFullName(product.getCreator().getFullName())
                    .creatorRegion(product.getCreator().getRegion())
                    .build();
        }).toList();

        int productsTotalPence = items.stream()
                .mapToInt(i -> i.getPricePence() * i.getQuantity()).sum();
        int totalWeightGrams = items.stream()
                .mapToInt(i -> i.getWeightGrams() * i.getQuantity()).sum();
        int shippingPence = (int) Math.round(
                totalWeightGrams / 1000.0 * shippingSettings.getRatePencePerKg());

        // Promo code — validated and priced server-side; the client never dictates the
        // discount amount, only which code to try.
        PromoCode promo = null;
        int discountPence = 0;
        if (req.promoCode() != null && !req.promoCode().isBlank()) {
            promo = promoCodeService.validate(req.promoCode());
            discountPence = (int) Math.round(productsTotalPence * (promo.getPercentOff() / 100.0));
        }

        int totalPence = productsTotalPence - discountPence + shippingPence;

        Order order = Order.builder()
                .displayId(generateDisplayId())
                .buyer(buyer)
                .buyerFullName(buyerFullName)
                .buyerEmail(buyerEmail)
                .shippingPence(shippingPence)
                .deliveryZoneId(zone.getId())
                .promoCode(promo != null ? promo.getCode() : null)
                .discountPence(discountPence)
                .totalPence(totalPence)
                .delivery(toJson(req.delivery()))
                .status(OrderStatus.PENDING)
                .channel(OrderChannel.ONLINE)
                .build();

        items.forEach(item -> item.setOrder(order));
        order.getItems().addAll(items);
        Order saved = orderRepository.save(order);

        if (promo != null) {
            promoCodeService.redeem(promo);
        }

        // Only send buyer acknowledgement — creator notification fires after payment is confirmed
//        emailService.sendApplicationReceived(
//                saved.getBuyerEmail(), saved.getBuyerFullName(), saved.getDisplayId());

        return toDto(saved);
    }

    /** Market-stall sale — a walk-out handover, not a shipment. Deliberately skips
     *  the delivery zone / shipping calc that place() requires: no delivery zone,
     *  no shipping fee, a synthetic delivery marker (Order.delivery is NOT NULL but
     *  nothing here needs a real address). Stock/status validation mirrors place(). */
    @Transactional
    public OrderDto placePos(List<OrderItemRequest> itemRequests, String customerEmail, String customerFullName) {
        if (itemRequests == null || itemRequests.isEmpty())
            throw ApiException.badRequest("Cart is empty");

        // Today's assigned MarketDayCatalogue (if any) reprices only the products it
        // actually lists — anything sold outside it (search, "New item") still prices
        // at the product's normal live rate. See MarketDay's own class comment.
        Map<String, Integer> catalogueOverridePrices = todaysCatalogueOverridePrices();

        List<OrderItem> items = itemRequests.stream().map(itemReq -> {
            Product product = productRepository.findActiveById(itemReq.productId())
                    .orElseThrow(() -> ApiException.notFound("Product not found: " + itemReq.productId()));
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw ApiException.badRequest(
                        product.getStatus() == ProductStatus.OUT_OF_STOCK
                                ? product.getName() + " is currently out of stock"
                                : product.getName() + " is not available for purchase");
            }
            // Seeded demo/placeholder listings have no real fulfillment behind them — the
            // storefront already blocks add-to-cart on these; POS must too, in case one is
            // ever accidentally flipped ACTIVE by an admin.
            if (product.isDemoProduct()) {
                throw ApiException.badRequest(product.getName() + " is a preview listing and can't be sold");
            }
            if (product.getUnitsAvailable() != null && itemReq.quantity() > product.getUnitsAvailable()) {
                throw ApiException.badRequest(
                        "Only " + product.getUnitsAvailable() + " of " + product.getName() + " left in stock");
            }
            int pricePence = catalogueOverridePrices.getOrDefault(product.getId(), product.getPricePence());
            return OrderItem.builder()
                    .product(product)
                    .creator(product.getCreator())
                    .productName(product.getName())
                    .quantity(itemReq.quantity())
                    .pricePence(pricePence)
                    .weightGrams(product.getWeightGrams())
                    .image(product.getHeroImage())
                    .creatorFullName(product.getCreator().getFullName())
                    .creatorRegion(product.getCreator().getRegion())
                    .build();
        }).toList();

        int totalPence = items.stream().mapToInt(i -> i.getPricePence() * i.getQuantity()).sum();

        Order order = Order.builder()
                .displayId(generateDisplayId())
                .buyer(null)
                .buyerFullName(customerFullName != null && !customerFullName.isBlank()
                        ? customerFullName.trim() : "Walk-in customer")
                .buyerEmail(customerEmail != null && !customerEmail.isBlank()
                        ? customerEmail.toLowerCase().trim() : WALK_IN_EMAIL)
                .shippingPence(0)
                .deliveryZoneId(null)
                .totalPence(totalPence)
                .delivery(toJson(Map.of("channel", "POS")))
                .status(OrderStatus.PENDING)
                .channel(OrderChannel.POS)
                .build();

        items.forEach(item -> item.setOrder(order));
        order.getItems().addAll(items);
        Order saved = orderRepository.save(order);
        return toDto(saved);
    }

    /** productId -> this catalogue's own price, for whichever MarketDayCatalogue is
     *  assigned to today (Europe/London) — empty when there's no market day today or
     *  it has no catalogue assigned, in which case every item just prices normally. */
    private Map<String, Integer> todaysCatalogueOverridePrices() {
        return marketDayRepository.findById(LocalDate.now(ZoneId.of("Europe/London")))
                .map(MarketDay::getCatalogue)
                .map(catalogue -> marketDayCatalogueItemRepository
                        .findByCatalogue_IdOrderByProductNameAsc(catalogue.getId()).stream()
                        .collect(Collectors.toMap(i -> i.getProduct().getId(), MarketDayCatalogueItem::getPricePence)))
                .orElse(Map.of());
    }

    @Transactional(readOnly = true)
    public OrderDto getByDisplayId(String displayId) {
        return orderRepository.findByDisplayId(displayId)
                .map(this::toDto)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + displayId));
    }

    @Transactional(readOnly = true)
    public OrderDto trackGuestOrder(String displayId, String email) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getBuyerEmail().equalsIgnoreCase(email.trim()))
            throw ApiException.notFound("Order not found");
        return toDto(order);
    }

    @Transactional
    public void linkGuestOrders(String email, User user) {
        List<Order> guestOrders = orderRepository
                .findByBuyerEmailIgnoreCaseAndBuyerIsNull(email);
        if (guestOrders.isEmpty()) return;
        guestOrders.forEach(o -> o.setBuyer(user));
        orderRepository.saveAll(guestOrders);
        log.info("Linked {} guest orders to new user {}", guestOrders.size(), user.getId());
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getBuyerOrders(UUID buyerId) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto getBuyerOrderByDisplayId(UUID buyerId, String displayId) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyerId))
            throw ApiException.notFound("Order not found");
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getCreatorOrders(String creatorId) {
        return orderRepository.findByCreatorId(creatorId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getAllOrders() {
        return orderRepository.findAllOrderByCreatedAtDesc()
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public OrderDto updateStatus(String creatorId, String displayId,
                                 UpdateStatusRequest req) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        boolean owns = order.getItems().stream()
                .anyMatch(i -> i.getCreator() != null
                        && i.getCreator().getId().equals(creatorId));
        if (!owns) throw ApiException.forbidden("You do not have access to this order");
        OrderStatus next = parseStatus(req.status());
        validateTransition(order.getStatus(), next);
        order.setStatus(next);
        return toDto(orderRepository.save(order));
    }

    /**
     * Real refund: actually reverses the Stripe/Paystack charge, restores tracked
     * stock, and reverses each item's ledger entry — not just a local status flip.
     * amountPence null = full refund of whatever hasn't already been refunded.
     *
     * If the gateway call fails, nothing else happens — never mark an order
     * refunded (or touch stock/ledger) without money actually moving.
     */
    @Transactional
    public OrderDto adminRefund(String displayId, Integer amountPence, CurrentUser actor) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));

        if (order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.PREPARING
                && order.getStatus() != OrderStatus.SHIPPED
                && order.getStatus() != OrderStatus.DELIVERED) {
            throw ApiException.badRequest(
                    "Only a paid order can be refunded (this one is " + order.getStatus() + ")");
        }
        int alreadyRefunded = order.getRefundedPence() != null ? order.getRefundedPence() : 0;
        if (alreadyRefunded >= order.getTotalPence()) {
            throw ApiException.badRequest("This order has already been fully refunded");
        }
        int refundAmount = amountPence != null ? amountPence : (order.getTotalPence() - alreadyRefunded);
        if (refundAmount <= 0 || refundAmount > order.getTotalPence() - alreadyRefunded) {
            throw ApiException.badRequest("Invalid refund amount");
        }

        // POS/Terminal orders carry their own PaymentIntent; online orders resolve
        // through the Checkout Session gatewayRef — StripeGateway.refund() handles both.
        String ref = order.getPaymentIntentId() != null ? order.getPaymentIntentId() : order.getGatewayRef();
        if (ref == null || ref.isBlank())
            throw ApiException.badRequest("This order has no payment reference to refund");

        RefundResult result = paymentGateway.refund(new RefundRequest(displayId, ref, refundAmount));
        if (!result.success())
            throw ApiException.badRequest("Refund failed: " + result.message());

        boolean isFullRefund = refundAmount == order.getTotalPence() - alreadyRefunded;
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null) continue;
            // Partial refunds don't try to guess which item(s) — stock/ledger reversal
            // only happens on a full refund. A partial (e.g. "one of these three is
            // faulty") needs the operator to separately note which item, out of scope here.
            if (!isFullRefund) continue;
            productService.restock(item.getProduct().getId(), item.getQuantity());
            reverseLedgerEntry(item, order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setRefundedAt(Instant.now());
        order.setRefundedPence(alreadyRefunded + refundAmount);
        orderRepository.save(order);

        auditLogService.record(actor, "ORDER_REFUNDED", "Order", displayId, displayId,
                "Refunded " + refundAmount + "p (" + (isFullRefund ? "full" : "partial") + ") via " + result.gatewayRefundRef());

        if (!WALK_IN_EMAIL.equalsIgnoreCase(order.getBuyerEmail())) {
            emailService.sendOrderStatusUpdate(
                    order.getBuyerEmail(), order.getBuyerFullName(), displayId, "REFUNDED");
        }

        log.info("Order {} refunded: {}p via {}", displayId, refundAmount, result.gatewayRefundRef());
        return toDto(order);
    }

    /** Reverses one item's ledger entry, but only when the money hasn't actually left
     *  the platform yet. A real creator entry already PAID means an external payout
     *  already happened — auto-clawback isn't attempted here (needs a human, this is
     *  flagged loudly); a still-PENDING entry (creator or Uken's own immediate-PAID
     *  revenue recognition, which never leaves the platform externally) reverses safely. */
    private void reverseLedgerEntry(OrderItem item, Order order) {
        if (item.getCreator() == null) return;
        boolean isUkenItem = ProductService.UKENA_CREATOR_ID.equals(item.getCreator().getId());

        List<EarningsLedger> entries = ledgerRepository.findByOrderId(order.getId()).stream()
                .filter(e -> e.getOrderItemId().equals(item.getId()))
                .toList();

        for (EarningsLedger entry : entries) {
            if (entry.getStatus() == LedgerStatus.REVERSED) continue;
            if (!isUkenItem && entry.getStatus() == LedgerStatus.PAID) {
                log.warn("Order {} refunded but creator={} was already paid out {}p for this item — "
                                + "needs manual clawback, not reversed automatically",
                        order.getDisplayId(), item.getCreator().getId(), entry.getNetPence());
                continue;
            }
            boolean wasPending = entry.getStatus() == LedgerStatus.PENDING
                    || entry.getStatus() == LedgerStatus.INCLUDED_IN_PAYOUT;
            entry.setStatus(LedgerStatus.REVERSED);
            ledgerRepository.save(entry);
            if (!isUkenItem && wasPending) {
                payoutUpdateService.reversePendingPayout(
                        item.getCreator().getId(), entry.getNetPence(), order.getDisplayId());
            }
        }
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PAID      -> next == OrderStatus.PREPARING;
            case PREPARING -> next == OrderStatus.SHIPPED;
            case SHIPPED   -> next == OrderStatus.DELIVERED;
            default        -> false;
        };
        if (!valid) throw ApiException.badRequest(
                "Invalid status transition from " + current + " to " + next);
    }

    private OrderStatus parseStatus(String status) {
        try { return OrderStatus.valueOf(status.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid status: " + status); }
    }

    private String generateDisplayId() {
        String month = DateTimeFormatter.ofPattern("yyyyMM")
                .format(LocalDateTime.now());
        int rand = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "UKN-" + month + "-" + rand;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private DeliveryDto parseDelivery(String json) {
        try { return objectMapper.readValue(json, DeliveryDto.class); }
        catch (Exception e) { return null; }
    }

    OrderDto toDto(Order o) {
        int productsTotalPence = o.getItems().stream()
                .mapToInt(i -> i.getPricePence() * i.getQuantity()).sum();
        List<OrderItemDto> items = o.getItems().stream().map(i ->
                new OrderItemDto(
                        i.getProduct() != null ? i.getProduct().getId() : null,
                        i.getProductName(), i.getQuantity(), i.getPricePence(),
                        i.getWeightGrams(),
                        i.getImage(),
                        new OrderItemCreatorDto(
                                i.getCreator() != null ? i.getCreator().getId() : null,
                                i.getCreatorFullName(), i.getCreatorRegion()),
                        i.getProduct() != null && i.getProduct().getCategory() != null
                                ? new ProductCategoryDto(
                                        i.getProduct().getCategory().getId(),
                                        i.getProduct().getCategory().getName(),
                                        i.getProduct().getCategory().getColorToken())
                                : null)
        ).toList();
        return new OrderDto(
                o.getDisplayId(), o.getCreatedAt(), o.getStatus().name(),
                o.getChannel() != null ? o.getChannel().name() : OrderChannel.ONLINE.name(),
                productsTotalPence, o.getShippingPence(),
                o.getPromoCode(), o.getDiscountPence() != null ? o.getDiscountPence() : 0,
                o.getTotalPence(),
                new OrderBuyerDto(o.getBuyerFullName(), o.getBuyerEmail()),
                items, parseDelivery(o.getDelivery()),
                o.getLastPaymentError(),
                o.getRefundedPence() != null ? o.getRefundedPence() : 0);
    }


    @Transactional
    public OrderDto adminUpdateStatus(String displayId, UpdateStatusRequest req) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        OrderStatus next = parseStatus(req.status());
        validateAdminTransition(order.getStatus(), next);
        order.setStatus(next);
        Order saved = orderRepository.save(order);
        if (!WALK_IN_EMAIL.equalsIgnoreCase(saved.getBuyerEmail())) {
            emailService.sendOrderStatusUpdate(
                    saved.getBuyerEmail(), saved.getBuyerFullName(),
                    saved.getDisplayId(), next.name());
        }
        return toDto(saved);
    }

    @Transactional
    public List<OrderDto> adminBulkUpdateStatus(BulkStatusUpdateRequest req) {
        OrderStatus next = parseStatus(req.status());
        List<OrderDto> results = new ArrayList<>();
        for (String displayId : req.displayIds()) {
            Order order = orderRepository.findByDisplayId(displayId)
                    .orElseThrow(() -> ApiException.notFound("Order not found: " + displayId));
            validateAdminTransition(order.getStatus(), next);
            order.setStatus(next);
            Order saved = orderRepository.save(order);
            emailService.sendOrderStatusUpdate(
                    saved.getBuyerEmail(), saved.getBuyerFullName(),
                    saved.getDisplayId(), next.name());
            results.add(toDto(saved));
        }
        return results;
    }

    private void validateAdminTransition(OrderStatus current, OrderStatus next) {
        if (current == OrderStatus.CANCELLED)
            throw ApiException.badRequest("Cannot update a cancelled order");
        if (next == OrderStatus.PENDING)
            throw ApiException.badRequest("Cannot revert to PENDING");
        if (current == OrderStatus.DELIVERED && next != OrderStatus.CANCELLED)
            throw ApiException.badRequest("Order already delivered");
    }
}