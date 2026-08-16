package com.mdau.ukena.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.delivery.DeliveryZone;
import com.mdau.ukena.delivery.DeliveryZoneService;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.dto.*;
import com.mdau.ukena.product.Product;
import com.mdau.ukena.product.ProductRepository;
import com.mdau.ukena.product.ProductStatus;
import com.mdau.ukena.promo.PromoCode;
import com.mdau.ukena.promo.PromoCodeService;
import com.mdau.ukena.shipping.ShippingSettings;
import com.mdau.ukena.shipping.ShippingSettingsService;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    private final OrderRepository        orderRepository;
    private final ProductRepository      productRepository;
    private final UserRepository         userRepository;
    private final DeliveryZoneService    deliveryZoneService;
    private final ShippingSettingsService shippingSettingsService;
    private final PromoCodeService       promoCodeService;
    private final ObjectMapper           objectMapper;
    private final EmailService           emailService;

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

        List<OrderItem> items = itemRequests.stream().map(itemReq -> {
            Product product = productRepository.findActiveById(itemReq.productId())
                    .orElseThrow(() -> ApiException.notFound("Product not found: " + itemReq.productId()));
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
            return OrderItem.builder()
                    .product(product)
                    .creator(product.getCreator())
                    .productName(product.getName())
                    .quantity(itemReq.quantity())
                    .pricePence(product.getPricePence())
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
                        ? customerEmail.toLowerCase().trim() : "walk-in@ukena.co.uk")
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

    @Transactional
    public OrderDto adminRefund(String displayId) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        order.setStatus(OrderStatus.CANCELLED);
        log.info("Order {} cancelled by admin", displayId);
        return toDto(orderRepository.save(order));
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
                                i.getCreatorFullName(), i.getCreatorRegion()))
        ).toList();
        return new OrderDto(
                o.getDisplayId(), o.getCreatedAt(), o.getStatus().name(),
                o.getChannel() != null ? o.getChannel().name() : OrderChannel.ONLINE.name(),
                productsTotalPence, o.getShippingPence(),
                o.getPromoCode(), o.getDiscountPence() != null ? o.getDiscountPence() : 0,
                o.getTotalPence(),
                new OrderBuyerDto(o.getBuyerFullName(), o.getBuyerEmail()),
                items, parseDelivery(o.getDelivery()));
    }


    @Transactional
    public OrderDto adminUpdateStatus(String displayId, UpdateStatusRequest req) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        OrderStatus next = parseStatus(req.status());
        validateAdminTransition(order.getStatus(), next);
        order.setStatus(next);
        Order saved = orderRepository.save(order);
        emailService.sendOrderStatusUpdate(
                saved.getBuyerEmail(), saved.getBuyerFullName(),
                saved.getDisplayId(), next.name());
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

    @Transactional(readOnly = true)
    public List<OrderDto> trackByEmail(String email) {
        return orderRepository.findByBuyerEmailIgnoreCaseOrderByCreatedAtDesc(email.trim())
                .stream().map(this::toDto).toList();
    }
}