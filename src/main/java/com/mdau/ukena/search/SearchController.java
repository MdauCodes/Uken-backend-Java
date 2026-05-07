package com.mdau.ukena.search;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.creator.dto.CreatorSummary;
import com.mdau.ukena.product.Product;
import com.mdau.ukena.product.ProductRepository;
import com.mdau.ukena.product.dto.ProductSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final CreatorRepository creatorRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResultDto>> search(
            @RequestParam(value = "q",    defaultValue = "")    String q,
            @RequestParam(value = "type", defaultValue = "all") String type,
            @RequestParam(value = "page", defaultValue = "0")   int page,
            @RequestParam(value = "size", defaultValue = "12")  int size) {

        if (q.isBlank()) {
            throw ApiException.badRequest("Search query must not be empty");
        }
        if (size < 1 || size > 50) {
            throw ApiException.badRequest("Page size must be between 1 and 50");
        }

        Pageable pageable = PageRequest.of(page, size);
        String   term     = q.trim();

        List<CreatorSummary>    creators = Collections.emptyList();
        List<ProductSummaryDto> products = Collections.emptyList();

        boolean includeCreators = type.equalsIgnoreCase("creators")
                               || type.equalsIgnoreCase("all");
        boolean includeProducts = type.equalsIgnoreCase("products")
                               || type.equalsIgnoreCase("all");

        if (includeCreators) {
            creators = creatorRepository.search(term, pageable)
                    .getContent().stream()
                    .map(this::toCreatorSummary)
                    .toList();
        }

        if (includeProducts) {
            products = productRepository.search(term, pageable)
                    .getContent().stream()
                    .map(this::toProductSummary)
                    .toList();
        }

        int total = creators.size() + products.size();
        return ResponseEntity.ok(ApiResponse.ok(
                new SearchResultDto(creators, products, total)));
    }

    private CreatorSummary toCreatorSummary(Creator c) {
        return new CreatorSummary(
                c.getId(), c.getFirstName(), c.getFullName(),
                c.getCraft(), c.getRegion(), c.getHook(), c.getImage());
    }

    private ProductSummaryDto toProductSummary(Product p) {
        return new ProductSummaryDto(
                p.getId(),
                p.getName(),
                p.getPricePence(),
                p.getHeroImage(),
                p.getCreator() != null ? p.getCreator().getId()       : null,
                p.getCreator() != null ? p.getCreator().getFullName() : null,
                p.getCreator() != null ? p.getCreator().getCraft()    : null);
    }
}