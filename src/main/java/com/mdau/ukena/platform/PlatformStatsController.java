package com.mdau.ukena.platform;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlatformStatsController {

    private final CreatorRepository creatorRepository;
    private final ProductRepository productRepository;

    /** Public — powers the storefront's trust-strip messaging with real, current counts. */
    @GetMapping("/platform/stats")
    public ResponseEntity<ApiResponse<PlatformStatsDto>> stats() {
        PlatformStatsDto dto = new PlatformStatsDto(
                creatorRepository.countActiveMakers(),
                productRepository.countActive(),
                creatorRepository.countDistinctRegions());
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }
}
