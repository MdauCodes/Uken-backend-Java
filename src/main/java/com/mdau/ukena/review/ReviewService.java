package com.mdau.ukena.review;

import com.mdau.ukena.admin.ReviewRecord;
import com.mdau.ukena.admin.ReviewRepository;
import com.mdau.ukena.admin.ReviewStatus;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderItem;
import com.mdau.ukena.order.OrderRepository;
import com.mdau.ukena.order.OrderStatus;
import com.mdau.ukena.product.Product;
import com.mdau.ukena.product.ProductRepository;
import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository  reviewRepository;
    private final OrderRepository   orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository    userRepository;

    @Transactional
    public void submitReview(ReviewSubmitRequest req, CurrentUser currentUser) {

        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> ApiException.notFound("Product not found"));

        User buyer = userRepository.findById(currentUser.id())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        // Verify buyer has a DELIVERED order containing this product
        boolean hasDeliveredOrder = orderRepository
                .findByBuyerIdOrderByCreatedAtDesc(currentUser.id())
                .stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .flatMap(o -> o.getItems().stream())
                .anyMatch(i -> i.getProduct() != null
                        && i.getProduct().getId().equals(req.productId()));

        if (!hasDeliveredOrder) {
            throw ApiException.badRequest(
                    "You can only review products from a delivered order");
        }

        // Prevent duplicate reviews for same product by same buyer
        boolean alreadyReviewed = reviewRepository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .anyMatch(r -> r.getBuyer() != null
                        && r.getBuyer().getId().equals(currentUser.id())
                        && r.getProduct() != null
                        && r.getProduct().getId().equals(req.productId()));

        if (alreadyReviewed) {
            throw ApiException.conflict("You have already reviewed this product");
        }

        ReviewRecord review = ReviewRecord.builder()
                .id(generateReviewId())
                .product(product)
                .buyer(buyer)
                .buyerName(buyer.getFullName())
                .rating(req.rating())
                .body(req.body())
                .status(ReviewStatus.PENDING)
                .build();

        reviewRepository.save(review);
        log.info("Review submitted by buyer {} for product {}", currentUser.id(), req.productId());
    }

    @Transactional(readOnly = true)
    public List<PublicReviewDto> getPublishedReviews(String productId) {
        return reviewRepository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .filter(r -> r.getProduct() != null
                        && r.getProduct().getId().equals(productId)
                        && r.getStatus() == ReviewStatus.PUBLISHED)
                .map(r -> new PublicReviewDto(
                        r.getId(),
                        r.getBuyerName(),
                        r.getRating(),
                        r.getBody(),
                        r.getSubmittedAt()))
                .toList();
    }

    private String generateReviewId() {
        String ts  = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
                .format(LocalDateTime.now());
        int    rnd = ThreadLocalRandom.current().nextInt(100, 999);
        return "RV-" + ts + "-" + rnd;
    }
}