package com.mdau.ukena.pos.dto;

/** The default POS browse-grid tile — deliberately lighter than the storefront's
 *  ProductDto (POS never needed creator/category/rating fields). Sourced either
 *  from the live Uken catalogue, or from today's MarketDayCatalogue when one is
 *  assigned — see PosService.browseProducts. */
public record PosBrowseItemDto(
        String id,
        String name,
        int pricePence,
        String heroImage
) {}
