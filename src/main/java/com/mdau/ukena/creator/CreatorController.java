package com.mdau.ukena.creator;

import com.mdau.ukena.admin.FeaturedSlot;
import com.mdau.ukena.admin.FeaturedSlotRepository;
import com.mdau.ukena.admin.dto.FeaturedSlotDto;
import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.creator.dto.*;
import com.mdau.ukena.security.CurrentUser;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/creators")
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorService creatorService;
    private final FeaturedSlotRepository featuredSlotRepository;

    @GetMapping
    @RateLimiter(name = "public-api")
    @Cacheable(value = "creators", key = "#craft + '_' + #region")
    public ResponseEntity<ApiResponse<List<CreatorSummary>>> list(
            @RequestParam(required = false) String craft,
            @RequestParam(required = false) String region) {
        return ResponseEntity.ok(ApiResponse.ok(
                creatorService.list(craft, region)));
    }

    @GetMapping("/featured")
    @RateLimiter(name = "public-api")
    @Cacheable(value = "featured")
    public ResponseEntity<ApiResponse<List<FeaturedSlotDto>>> featured() {
        List<FeaturedSlotDto> slots = featuredSlotRepository.findAllByOrderByPositionAsc()
                .stream()
                .filter(s -> s.getCreator() != null)
                .map(s -> new FeaturedSlotDto(
                        s.getPosition(),
                        s.getCreator().getId(),
                        s.getProduct() != null ? s.getProduct().getId() : null))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(slots));
    }

    @GetMapping("/{id}")
    @RateLimiter(name = "public-api")
    @Cacheable(value = "creator", key = "#id")
    public ResponseEntity<ApiResponse<CreatorDetail>> getOne(
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(
                creatorService.getById(id)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<CreatorDetail>> getMe(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                creatorService.getByCreatorId(currentUser.creatorId())));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CREATOR')")
    @CacheEvict(value = {"creators", "creator", "featured"}, allEntries = true)
    public ResponseEntity<ApiResponse<CreatorDetail>> updateMe(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CreatorProfileUpdate req) {
        return ResponseEntity.ok(ApiResponse.ok(
                creatorService.updateProfile(currentUser.creatorId(), req)));
    }
}