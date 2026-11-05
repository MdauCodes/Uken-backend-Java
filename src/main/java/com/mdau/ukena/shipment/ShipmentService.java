package com.mdau.ukena.shipment;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.order.*;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.shipment.dto.CreateShipmentRequest;
import com.mdau.ukena.shipment.dto.ShipmentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Weekly flights from the Nairobi hub. Packed orders are booked onto a flight;
 * when it departs every order on it moves to IN_AIR, and when it lands and
 * clears customs at the Nottingham base every order moves to AT_UK_BASE.
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderFlowService orderFlowService;

    @Value("${ukena.freight.per-kg-pence:460}")
    private int freightPerKgPence;

    @Value("${ukena.freight.clearance-pence:4500}")
    private int clearancePence;

    @Value("${ukena.freight.forwarder:Nairobi air cargo forwarder}")
    private String defaultForwarder;

    private static final String ROUTE = "Nairobi (NBO) → London Heathrow (LHR) → Nottingham";

    @Transactional(readOnly = true)
    public List<ShipmentDto> list() {
        return shipmentRepository.findAllByOrderByDepartsOnDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public ShipmentDto book(CreateShipmentRequest req, CurrentUser actor) {
        if (req.arrivesOn().isBefore(req.departsOn()))
            throw ApiException.badRequest("The flight can't land before it leaves");
        List<Order> orders = req.orderIds().stream()
                .map(id -> orderRepository.findByDisplayId(id)
                        .orElseThrow(() -> ApiException.notFound("Order not found: " + id)))
                .toList();
        for (Order o : orders) {
            if (o.effectiveStage() != FulfilmentStage.PACKED)
                throw ApiException.badRequest(o.getDisplayId() + " isn't packed yet");
            if (o.getShipmentId() != null)
                throw ApiException.badRequest(o.getDisplayId() + " is already booked on " + o.getShipmentId());
        }
        int weight = orders.stream()
                .mapToInt(o -> o.getPackedWeightGrams() != null ? o.getPackedWeightGrams() : 500).sum();
        Shipment shipment = Shipment.builder()
                .id(nextId())
                .forwarder(req.forwarder() != null && !req.forwarder().isBlank() ? req.forwarder().trim() : defaultForwarder)
                .route(ROUTE)
                .awb(req.awb().trim())
                .departsOn(req.departsOn())
                .arrivesOn(req.arrivesOn())
                .weightGrams(weight)
                .freightPence(req.freightPence() != null ? req.freightPence()
                        : (int) Math.round(weight / 1000.0 * freightPerKgPence))
                .clearancePence(clearancePence)
                .build();
        shipmentRepository.save(shipment);
        for (Order o : orders) {
            o.setShipmentId(shipment.getId());
            orderRepository.save(o);
        }
        return toDto(shipment);
    }

    @Transactional
    public ShipmentDto depart(String id, CurrentUser actor) {
        Shipment s = load(id);
        if (s.getStatus() != ShipmentStatus.BOOKED) throw ApiException.badRequest("This flight has already left");
        s.setStatus(ShipmentStatus.IN_AIR);
        shipmentRepository.save(s);
        for (Order o : orderRepository.findByShipmentId(id)) {
            if (o.effectiveStage() == FulfilmentStage.PACKED)
                orderFlowService.moveOnFlight(o, FulfilmentStage.IN_AIR, "Flight " + id + ", NBO → LHR");
        }
        return toDto(s);
    }

    @Transactional
    public ShipmentDto clear(String id, String customsRef, CurrentUser actor) {
        Shipment s = load(id);
        if (s.getStatus() != ShipmentStatus.IN_AIR) throw ApiException.badRequest("Mark the flight as departed first");
        if (customsRef == null || customsRef.isBlank()) throw ApiException.badRequest("Add the customs entry reference");
        s.setStatus(ShipmentStatus.CLEARED);
        s.setCustomsRef(customsRef.trim());
        shipmentRepository.save(s);
        for (Order o : orderRepository.findByShipmentId(id)) {
            if (o.effectiveStage() == FulfilmentStage.IN_AIR)
                orderFlowService.moveOnFlight(o, FulfilmentStage.AT_UK_BASE, "Cleared UK customs");
        }
        return toDto(s);
    }

    private Shipment load(String id) {
        return shipmentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Flight not found"));
    }

    private String nextId() {
        int max = shipmentRepository.findAll().stream()
                .map(Shipment::getId)
                .filter(i -> i.startsWith("AIR-"))
                .mapToInt(i -> {
                    try { return Integer.parseInt(i.substring(4)); } catch (Exception e) { return 0; }
                })
                .max().orElse(0);
        return String.format("AIR-%04d", max + 1);
    }

    private ShipmentDto toDto(Shipment s) {
        List<String> orderIds = orderRepository.findByShipmentId(s.getId()).stream().map(Order::getDisplayId).toList();
        return new ShipmentDto(s.getId(), s.getForwarder(), s.getRoute(), s.getAwb(),
                s.getDepartsOn().toString(), s.getArrivesOn().toString(), s.getStatus().name(),
                s.getWeightGrams(), s.getFreightPence(), s.getClearancePence(), s.getCustomsRef(), orderIds);
    }
}
