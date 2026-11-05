package com.mdau.ukena.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.dto.*;
import com.mdau.ukena.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.mdau.ukena.order.FulfilmentStage.*;

/**
 * Moves online orders through the hub journey. Modelled on Moments Packaging's
 * per-mode order flow: each stage has exactly one next step a person can take,
 * the move is validated here (never trusted from the client), every move is
 * recorded as an {@link OrderEvent}, and the legacy {@link OrderStatus} is kept
 * in step so reviews, reports and earnings keep working.
 *
 * Moving PACKED → IN_AIR → AT_UK_BASE happens for a whole flight at once
 * (see ShipmentService), not order by order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFlowService {

    private final OrderRepository orderRepository;
    private final OrderEventRepository eventRepository;
    private final OrderService orderService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    /** Stages a buyer is emailed about. */
    private static final Set<FulfilmentStage> BUYER_EMAIL_STAGES =
            EnumSet.of(IN_AIR, OUT_FOR_DELIVERY, READY_TO_COLLECT, DELIVERED);

    /** The one next stage a person can move this order to, or null if it moves some other way. */
    public static FulfilmentStage nextStage(String fulfilment, FulfilmentStage from) {
        boolean pickup = "PICKUP".equals(fulfilment);
        return switch (from) {
            case PAID -> IN_PRODUCTION;
            case IN_PRODUCTION -> AT_HUB;
            case AT_HUB -> PACKED;
            case AT_UK_BASE -> pickup ? READY_TO_COLLECT : OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> DELIVERED;
            case DELIVERED, READY_TO_COLLECT -> COMPLETED;
            default -> null; // PACKED/IN_AIR move with their flight; ISSUE/COMPLETED/CANCELLED are settled elsewhere
        };
    }

    @Transactional
    public OrderDto advance(String displayId, AdvanceOrderRequest req, CurrentUser actor) {
        Order order = load(displayId);
        FulfilmentStage to = parse(req.to());
        FulfilmentStage expected = nextStage(order.effectiveFulfilment(), order.effectiveStage());
        if (expected == null || expected != to) {
            throw ApiException.badRequest("This order can't move from " + order.effectiveStage()
                    + " to " + to + (expected != null ? " — next step is " + expected : ""));
        }
        String note = req.note();
        if (to == PACKED) {
            if (req.packedWeightGrams() == null) throw ApiException.badRequest("Weigh the packed parcel first");
            order.setPackedWeightGrams(req.packedWeightGrams());
            note = "All items checked, packed " + String.format("%.1f kg", req.packedWeightGrams() / 1000.0);
        }
        if (to == OUT_FOR_DELIVERY) {
            if (isBlank(req.courier()) || isBlank(req.trackingNumber()))
                throw ApiException.badRequest("Add the Royal Mail service and tracking number");
            order.setCourier(req.courier().trim());
            order.setTrackingNumber(req.trackingNumber().trim());
            note = req.courier().trim() + " " + req.trackingNumber().trim();
        }
        moveTo(order, to, staffName(actor), note);
        return orderService.toStaffDto(order);
    }

    /** Admin fix for a mistake — any stage, with a reason that goes into the history. No buyer email. */
    @Transactional
    public OrderDto override(String displayId, OverrideStageRequest req, CurrentUser actor) {
        Order order = load(displayId);
        moveTo(order, parse(req.stage()), staffName(actor), "Manual override: " + req.reason().trim(), false);
        return orderService.toStaffDto(order);
    }

    @Transactional
    public OrderDto setMakerReady(String displayId, String creatorId, boolean ready) {
        Order order = load(displayId);
        boolean hasMaker = order.getItems().stream()
                .anyMatch(i -> i.getCreator() != null && i.getCreator().getId().equals(creatorId));
        if (!hasMaker) throw ApiException.badRequest("That maker isn't on this order");
        Map<String, Boolean> map = orderService.readMakerReady(order);
        map.put(creatorId, ready);
        order.setMakerReady(write(map));
        orderRepository.save(order);
        return orderService.toStaffDto(order);
    }

    /** The maker says their part of the order has been dropped at the hub. */
    @Transactional
    public OrderDto makerDroppedAtHub(String displayId, String creatorId) {
        Order order = load(displayId);
        if (order.effectiveStage() != IN_PRODUCTION)
            throw ApiException.badRequest("This order isn't waiting for makers right now");
        setMakerReady(displayId, creatorId, true);
        return orderService.toDto(order);
    }

    @Transactional
    public OrderDto assign(String displayId, String assignedTo) {
        Order order = load(displayId);
        order.setAssignedTo(isBlank(assignedTo) ? null : assignedTo.trim());
        orderRepository.save(order);
        return orderService.toStaffDto(order);
    }

    /** Buyer confirms it arrived. */
    @Transactional
    public OrderDto confirmReceived(Order order, String buyerName) {
        FulfilmentStage stage = order.effectiveStage();
        if (stage != DELIVERED && stage != OUT_FOR_DELIVERY)
            throw ApiException.badRequest("You can confirm once your parcel has been delivered");
        order.setBuyerConfirmedAt(Instant.now());
        moveTo(order, COMPLETED, buyerName, "Buyer confirmed it arrived");
        return orderService.toDto(order);
    }

    /** Buyer reports a problem (or asks to cancel). Staff see it under "Needs attention". */
    @Transactional
    public OrderDto reportProblem(Order order, String buyerName, ReportProblemRequest req) {
        FulfilmentStage stage = order.effectiveStage();
        boolean cancelRequest = "Cancellation request".equals(req.kind());
        boolean afterDispatch = EnumSet.of(OUT_FOR_DELIVERY, DELIVERED, READY_TO_COLLECT, COMPLETED).contains(stage);
        boolean beforeFlight = EnumSet.of(PAID, IN_PRODUCTION, AT_HUB, PACKED).contains(stage);
        if (!(afterDispatch || (cancelRequest && beforeFlight)))
            throw ApiException.badRequest("You can report a problem once your parcel is on its way to you");
        order.setIssueKind(req.kind().trim());
        order.setIssueNote(isBlank(req.note()) ? null : req.note().trim());
        moveTo(order, ISSUE, buyerName, req.kind().trim(), false);
        return orderService.toDto(order);
    }

    /** Staff settle a reported problem. A refund goes through the real gateway refund. */
    @Transactional
    public OrderDto resolveIssue(String displayId, ResolveIssueRequest req, CurrentUser actor) {
        Order order = load(displayId);
        if (order.effectiveStage() != ISSUE) throw ApiException.badRequest("There's no open problem on this order");
        switch (req.action()) {
            case "REFUND" -> {
                return orderService.adminRefund(displayId, null, actor);
            }
            case "RESEND" -> moveTo(order, IN_PRODUCTION, staffName(actor),
                    "Replacement requested from the maker" + (isBlank(req.note()) ? "" : ": " + req.note().trim()), false);
            default -> moveTo(order, COMPLETED, staffName(actor),
                    "Resolved with the buyer" + (isBlank(req.note()) ? "" : ": " + req.note().trim()), false);
        }
        order.setIssueKind(null);
        order.setIssueNote(null);
        orderRepository.save(order);
        return orderService.toStaffDto(order);
    }

    /** Used by ShipmentService when a whole flight departs or clears customs. */
    public void moveOnFlight(Order order, FulfilmentStage to, String note) {
        moveTo(order, to, to == IN_AIR ? "Nairobi hub" : "Nottingham base", note);
    }

    // ── shared ──────────────────────────────────────────────────────────────

    void moveTo(Order order, FulfilmentStage to, String actor, String note) {
        moveTo(order, to, actor, note, true);
    }

    void moveTo(Order order, FulfilmentStage to, String actor, String note, boolean emailBuyer) {
        order.setStage(to);
        order.setStageChangedAt(Instant.now());
        if (to.legacyStatus() != null) order.setStatus(to.legacyStatus());
        orderRepository.save(order);
        eventRepository.save(OrderEvent.builder()
                .orderId(order.getId()).stage(to).actor(actor).note(note).build());
        if (emailBuyer && BUYER_EMAIL_STAGES.contains(to)
                && !OrderService.WALK_IN_EMAIL.equalsIgnoreCase(order.getBuyerEmail())) {
            emailService.sendOrderStatusUpdate(order.getBuyerEmail(), order.getBuyerFullName(),
                    order.getDisplayId(), to.name());
        }
        log.info("Order {} → {} by {}", order.getDisplayId(), to, actor);
    }

    private Order load(String displayId) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getChannel() == OrderChannel.POS)
            throw ApiException.badRequest("Market-stall sales don't go through the hub");
        if (order.getStatus() == OrderStatus.PENDING)
            throw ApiException.badRequest("This order hasn't been paid yet");
        return order;
    }

    private static FulfilmentStage parse(String s) {
        try {
            return FulfilmentStage.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            throw ApiException.badRequest("Unknown stage: " + s);
        }
    }

    private static String staffName(CurrentUser actor) {
        return actor != null && actor.email() != null ? actor.email() : "UKEN";
    }

    private String write(Map<String, Boolean> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
