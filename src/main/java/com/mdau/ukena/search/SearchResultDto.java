package com.mdau.ukena.search;

import com.mdau.ukena.creator.dto.CreatorSummary;
import com.mdau.ukena.product.dto.ProductSummaryDto;
import java.util.List;

public record SearchResultDto(
        List<CreatorSummary>    creators,
        List<ProductSummaryDto> products,
        int                     total
) {}