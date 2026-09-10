package com.mdau.ukena.pos.dto;

/** One product's totals across every completed market-stall sale — see
 *  PosService.productSales. unitsAvailable is the product's CURRENT stock
 *  figure (not a snapshot); null means untracked/unlimited stock. */
public record PosProductSalesDto(
        String productId,
        String productName,
        String heroImage,
        long unitsSold,
        long revenuePence,
        Integer unitsAvailable
) {}
