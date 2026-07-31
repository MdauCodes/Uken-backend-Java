package com.mdau.ukena.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.admin.ReviewRepository;
import com.mdau.ukena.cloudinary.CloudinaryService;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.OrderItemRepository;
import com.mdau.ukena.product.dto.*;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    /** Sentinel creator ID reserved for Uken-owned catalogue products. */
    public static final String UKENA_CREATOR_ID = "ukena";

    private final ProductRepository      productRepository;
    private final ProductImageRepository imageRepository;
    private final CreatorRepository      creatorRepository;
    private final CloudinaryService      cloudinaryService;
    private final ObjectMapper           objectMapper;
    private final UserRepository         userRepository;
    private final EmailService           emailService;
    private final ReviewRepository       reviewRepository;
    private final OrderItemRepository    orderItemRepository;

    // ── Public browse ────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProductDto> browse(String creatorId, Integer minPrice,
                                   Integer maxPrice, String sort,
                                   int page, int size) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        return productRepository.browse(creatorId, minPrice, maxPrice, pageable)
                .map(this::toDto);
    }

    /** Ukena-owned catalogue only — used by GET /catalogue. */
    @Transactional(readOnly = true)
    public Page<ProductDto> browseUkenaCatalogue(Integer minPrice, Integer maxPrice,
                                                  String sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        return productRepository.browse(UKENA_CREATOR_ID, minPrice, maxPrice, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProductDto getById(String id) {
        return productRepository.findActiveById(id)
                .filter(p -> p.getStatus() != ProductStatus.SUSPENDED_BY_ADMIN
                          && p.getStatus() != ProductStatus.SUSPENDED_BY_CREATOR)
                .map(this::toDto)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    /** Admin moderation queue — every product regardless of status or soft-delete. */
    @Transactional(readOnly = true)
    public List<ProductDto> listAllForAdmin() {
        return productRepository.findAllForAdmin().stream().map(this::toDto).toList();
    }

    /**
     * "Frequently bought together" — ranked by real order co-occurrence, not a
     * fabricated signal. Falls back to same-craft products (excluding this one)
     * when there isn't enough order history yet, so the section is never empty
     * on a young platform.
     */
    @Transactional(readOnly = true)
    public List<ProductDto> frequentlyBoughtWith(String productId, int limit) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));

        List<Object[]> rows = orderItemRepository.frequentlyBoughtWith(productId, PageRequest.of(0, limit));
        List<Product> results = rows.stream()
                .map(row -> productRepository.findActiveById((String) row[0]))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .toList();

        if (results.size() >= limit) {
            return results.stream().limit(limit).map(this::toDto).toList();
        }

        // Fill the remainder with same-craft products, excluding anything already included.
        Set<String> seen = new HashSet<>();
        seen.add(productId);
        results.forEach(p -> seen.add(p.getId()));

        String craft = product.getCreator().getCraft();
        List<Product> filler = productRepository.browse(null, null, null, PageRequest.of(0, limit + results.size() + 10))
                .stream()
                .filter(p -> p.getCreator().getCraft().equalsIgnoreCase(craft) && !seen.contains(p.getId()))
                .limit((long) limit - results.size())
                .toList();

        return Stream.concat(results.stream(), filler.stream())
                .map(this::toDto)
                .toList();
    }

    // ── Creator CRUD ─────────────────────────────────────────

    @Transactional
    public ProductDto create(String creatorId, ProductCreateRequest req) {
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.forbidden("No active creator profile found"));
        validateCompareAtPrice(req.compareAtPricePence(), req.pricePence());
        String slug = generateSlug(req.name());
        Product product = Product.builder()
                .id(slug).creator(creator).name(req.name())
                .pricePence(req.pricePence()).heroImage(req.heroImage())
                .pieceStory(req.pieceStory())
                .materials(toJson(req.materials()))
                .dimensions(req.dimensions()).care(req.care())
                .weightGrams(req.weightGrams())
                .compareAtPricePence(req.compareAtPricePence())
                .status(ProductStatus.ACTIVE)
                .isUkenaOwned(false)
                .build();
        return toDto(productRepository.save(product));
    }

    /** A "was" price only makes sense — and is only legal to display — if it's genuinely
     *  higher than the real selling price. Rejects anything that wouldn't be. */
    private void validateCompareAtPrice(Integer compareAtPricePence, int pricePence) {
        if (compareAtPricePence != null && compareAtPricePence > 0 && compareAtPricePence <= pricePence) {
            throw ApiException.badRequest(
                    "The 'was' price must be higher than the current price — it should be a real price this piece has genuinely sold at.");
        }
    }

    @Transactional
    public ProductDto update(String creatorId, String productId,
                             ProductUpdateRequest req) {
        Product product = getOwnedProduct(creatorId, productId);
        applyFields(product, req.name(), req.pricePence(), req.heroImage(),
                req.pieceStory(), req.materials(), req.dimensions(), req.care(), req.weightGrams(),
                req.compareAtPricePence());
        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto updateStatusByCreator(String creatorId, String productId,
                                            ProductStatusUpdateRequest req) {
        Product product = getOwnedProduct(creatorId, productId);
        ProductStatus next = parseStatus(req.status());
        if (next == ProductStatus.SUSPENDED_BY_ADMIN)
            throw ApiException.forbidden("Only admins can set SUSPENDED_BY_ADMIN");
        product.setStatus(next);
        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto updateStatusByAdmin(String productId,
                                          ProductStatusUpdateRequest req) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        product.setStatus(parseStatus(req.status()));
        return toDto(productRepository.save(product));
    }

    @Transactional
    public void delete(String creatorId, String productId) {
        Product product = getOwnedProduct(creatorId, productId);
        purgeImages(product);
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
        log.info("Product {} soft-deleted by creator {}", productId, creatorId);
    }

    @Transactional
    public void adminDelete(String productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        purgeImages(product);
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
        log.info("Product {} soft-deleted by admin", productId);
    }

    // ── Uken catalogue (admin-managed) ───────────────────────

    @Transactional
    public ProductDto createForUkena(AdminProductCreateRequest req) {
        Creator ukena = creatorRepository.findById(UKENA_CREATOR_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Uken sentinel creator not seeded — check DataSeeder"));
        validateCompareAtPrice(req.compareAtPricePence(), req.pricePence());
        String slug = generateSlug(req.name());
        Product product = Product.builder()
                .id(slug).creator(ukena).name(req.name())
                .pricePence(req.pricePence()).heroImage(req.heroImage())
                .pieceStory(req.pieceStory())
                .materials(toJson(req.materials()))
                .dimensions(req.dimensions()).care(req.care())
                .weightGrams(req.weightGrams())
                .compareAtPricePence(req.compareAtPricePence())
                .status(ProductStatus.ACTIVE)
                .isUkenaOwned(true)
                .build();
        ProductDto dto = toDto(productRepository.save(product));
        log.info("Uken catalogue product created: {}", slug);
        return dto;
    }

    @Transactional
    public ProductDto updateForUkena(String productId, AdminProductCreateRequest req) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!product.isUkenaOwned())
            throw ApiException.forbidden("Product is not a Uken catalogue item");
        applyFields(product, req.name(), req.pricePence(), req.heroImage(),
                req.pieceStory(), req.materials(), req.dimensions(), req.care(), req.weightGrams(),
                req.compareAtPricePence());
        return toDto(productRepository.save(product));
    }

    // ── Images ───────────────────────────────────────────────

    @Transactional
    public ProductDto addImage(String creatorId, String productId,
                               AddImageRequest req) {
        return addImageToProduct(getOwnedProduct(creatorId, productId), req);
    }

    @Transactional
    public ProductDto addImageForUkena(String productId, AddImageRequest req) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!product.isUkenaOwned())
            throw ApiException.forbidden("Product is not a Uken catalogue item");
        return addImageToProduct(product, req);
    }

    @Transactional
    public void deleteImage(String creatorId, String productId, Long imageId) {
        getOwnedProduct(creatorId, productId);
        removeImage(productId, imageId);
    }

    @Transactional
    public void deleteImageForUkena(String productId, Long imageId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!product.isUkenaOwned())
            throw ApiException.forbidden("Product is not a Uken catalogue item");
        removeImage(productId, imageId);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getByCreator(String creatorId) {
        return productRepository.findByCreatorIdNotDeleted(creatorId)
                .stream().map(this::toDto).toList();
    }

    /** Emails every creator with at least one active product missing a weight,
     *  listing which of their pieces need it. Returns the number of creators emailed. */
    @Transactional(readOnly = true)
    public int sendWeightReminders() {
        Map<String, List<Product>> byCreator = productRepository.findActiveMissingWeight()
                .stream().collect(Collectors.groupingBy(p -> p.getCreator().getId()));
        int sent = 0;
        for (Map.Entry<String, List<Product>> entry : byCreator.entrySet()) {
            User user = userRepository.findByCreatorId(entry.getKey()).orElse(null);
            if (user == null) {
                log.warn("No user account found for creator {} — skipping weight reminder", entry.getKey());
                continue;
            }
            List<String> names = entry.getValue().stream().map(Product::getName).toList();
            emailService.sendWeightReminder(user.getEmail(), user.getFullName(), names);
            sent++;
        }
        log.info("Weight reminder emails sent to {} creators", sent);
        return sent;
    }

    // ── Internal helpers ─────────────────────────────────────

    private ProductDto addImageToProduct(Product product, AddImageRequest req) {
        if (req.isPrimary())
            product.getImages().forEach(img -> img.setPrimary(false));
        product.getImages().add(ProductImage.builder()
                .product(product).url(req.url())
                .cloudinaryId(req.cloudinaryId())
                .isPrimary(req.isPrimary())
                .displayOrder(req.displayOrder())
                .altText(req.altText())
                .build());
        return toDto(productRepository.save(product));
    }

    private void removeImage(String productId, Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> ApiException.notFound("Image not found"));
        if (!image.getProduct().getId().equals(productId))
            throw ApiException.forbidden("Image does not belong to this product");
        if (image.getCloudinaryId() != null)
            cloudinaryService.deleteImage(image.getCloudinaryId());
        imageRepository.delete(image);
    }

    private void applyFields(Product p, String name, int pricePence,
                              String heroImage, String pieceStory,
                              List<String> materials, String dimensions, String care,
                              Integer weightGrams, Integer compareAtPricePence) {
        p.setName(name);
        p.setPricePence(pricePence);
        if (heroImage != null) {
            String old = p.getHeroImage();
            p.setHeroImage(heroImage);
            if (old != null && !old.equals(heroImage)) {
                String pid = cloudinaryService.extractPublicId(old);
                if (pid != null) cloudinaryService.deleteImage(pid);
            }
        }
        if (pieceStory != null) p.setPieceStory(pieceStory);
        if (materials  != null) p.setMaterials(toJson(materials));
        if (dimensions != null) p.setDimensions(dimensions);
        if (care       != null) p.setCare(care);
        if (weightGrams != null) p.setWeightGrams(weightGrams);
        if (compareAtPricePence != null) {
            validateCompareAtPrice(compareAtPricePence, pricePence);
            // 0 explicitly clears/ends the markdown; a positive value sets/replaces it.
            p.setCompareAtPricePence(compareAtPricePence == 0 ? null : compareAtPricePence);
        }
    }

    private void purgeImages(Product product) {
        List<String> ids = product.getImages().stream()
                .map(ProductImage::getCloudinaryId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (!ids.isEmpty()) cloudinaryService.deleteImages(ids);
        if (product.getHeroImage() != null && !product.getHeroImage().isBlank()) {
            String pid = cloudinaryService.extractPublicId(product.getHeroImage());
            if (pid != null) cloudinaryService.deleteImage(pid);
        }
    }

    private Product getOwnedProduct(String creatorId, String productId) {
        Product p = productRepository.findActiveById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!p.getCreator().getId().equals(creatorId))
            throw ApiException.forbidden("You do not own this product");
        return p;
    }

    private Sort resolveSort(String sort) {
        return switch (sort != null ? sort : "newest") {
            case "price_asc"  -> Sort.by("pricePence").ascending();
            case "price_desc" -> Sort.by("pricePence").descending();
            default           -> Sort.by("createdAt").descending();
        };
    }

    private ProductStatus parseStatus(String status) {
        try { return ProductStatus.valueOf(status.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid product status: " + status); }
    }

    private String generateSlug(String name) {
        String base = "p-" + Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "").toLowerCase()
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String slug = base;
        int count = 1;
        while (productRepository.existsById(slug)) slug = base + "-" + count++;
        return slug;
    }

    ProductDto toDto(Product p) {
        List<ProductImageDto> images = p.getImages().stream()
                .map(img -> new ProductImageDto(img.getId(), img.getUrl(),
                        img.isPrimary(), img.getDisplayOrder(), img.getAltText()))
                .toList();
        String hero = p.getHeroImage() != null ? p.getHeroImage()
                : images.stream().filter(ProductImageDto::isPrimary)
                        .findFirst().map(ProductImageDto::url).orElse(null);
        Creator c = p.getCreator();

        List<Object[]> ratingRows = reviewRepository.ratingSummary(p.getId());
        Object[] ratingRow = ratingRows.isEmpty() ? null : ratingRows.get(0);
        Double avgRating = ratingRow != null && ratingRow[0] != null ? (Double) ratingRow[0] : null;
        long reviewCount = ratingRow != null && ratingRow[1] != null ? (Long) ratingRow[1] : 0L;
        long unitsSold = orderItemRepository.unitsSold(p.getId());

        return new ProductDto(
                p.getId(), p.getName(), p.getPricePence(), p.getCompareAtPricePence(), hero, images,
                p.getPieceStory(),
                parseList(p.getMaterials(), new TypeReference<>() {}),
                p.getDimensions(), p.getCare(), p.getWeightGrams(), p.getStatus().name(),
                new ProductCreatorDto(c.getId(), c.getFirstName(), c.getFullName(),
                        c.getRegion(), c.getCraft(), c.getPortraitImage()),
                p.isUkenaOwned(),
                avgRating, (int) reviewCount, unitsSold, p.getDeletedAt() != null);
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> ref) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, ref); }
        catch (Exception e) { return List.of(); }
    }

    private String toJson(Object obj) {
        if (obj == null) return "[]";
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "[]"; }
    }
}