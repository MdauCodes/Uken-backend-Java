package com.mdau.ukena.admin.dto;

import java.util.List;

public record UkenaRevenueDto(
        int totalRevenuePence,
        int platformCommissionPence,
        int ukenaProductRevenuePence,
        int totalUkenaIncomePence,
        int creatorPayoutsPence,
        long totalOrderCount,
        long ukenaProductOrderCount,
        List<ProductRevenueLine> topProducts
) {
    public record ProductRevenueLine(
            String productId,
            String productName,
            boolean isUkenaOwned,
            int unitsSold,
            int grossPence
    ) {}
}