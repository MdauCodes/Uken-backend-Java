package com.mdau.ukena.review;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<Void>> submit(
            @Valid @RequestBody ReviewSubmitRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {
        reviewService.submitReview(req, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(null, "Review submitted and pending moderation"));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<PublicReviewDto>>> getByProduct(
            @PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.getPublishedReviews(productId)));
    }
}