package com.mdau.ukena.shipment.dto;

import java.util.List;

public record ShipmentDto(
        String id,
        String forwarder,
        String route,
        String awb,
        String departsOn,
        String arrivesOn,
        String status,
        int weightGrams,
        int freightPence,
        int clearancePence,
        String customsRef,
        List<String> orderIds) {}
