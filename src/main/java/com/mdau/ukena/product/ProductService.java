package com.mdau.ukena.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.cloudinary.CloudinaryService;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.product.dto.*;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CreatorRepository creatorRepository;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ProductDto> browse(String creatorId, Integer minPrice,
                                   Integer maxPrice, String sort,
                                   int page, int size) {
        Sort sorting = switch (sort != null ? sort : "newest") {
            case "price_asc"  -> Sort.by("pricePence").ascending();
            case "price_desc" -> Sort.by("pricePence").descending();
            default           -> Sort.by("createdAt").descending();
        };
        Pageable pageable = PageRequest.of(page, size, sorting);
        return productRepository.browse(creatorId, minPrice, maxPrice, pageable)
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

    @Transactional
    public ProductDto create(String creatorId, ProductCreateRequest req) {
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.forbidden("No active creator profile found"));
        String slug = generateSlug(req.name());
        Product product = Product.builder()
                .id(slug).creator(creator).name(req.name())
                .pricePence(req.pricePence()).heroImage(req.heroImage())
                .pieceStory(req.pieceStory())
                .materials(toJson(req.materials()))
                .dimensions(req.dimensions()).care(req.care())
                .status(ProductStatus.ACTIVE)
                .build();
        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto update(String creatorId, String productId,
                             ProductUpdateRequest req) {
        Product product = getOwnedProduct(creatorId, productId);
        product.setName(req.name());
        product.setPricePence(req.pricePence());
        if (req.heroImage()  != null) product.setHeroImage(req.heroImage());
        if (req.pieceStory() != null) product.setPieceStory(req.pieceStory());
        if (req.materials()  != null) product.setMaterials(toJson(req.materials()));
        if (req.dimensions() != null) product.setDimensions(req.dimensions());
        if (req.care()       != null) product.setCare(req.care());
        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto updateStatusByCreator(String creatorId, String productId,
                                            ProductStatusUpdateRequest req) {
        Product product = getOwnedProduct(creatorId, productId);
        ProductStatus newStatus = parseStatus(req.status());
        if (newStatus == ProductStatus.SUSPENDED_BY_ADMIN)
            throw ApiException.forbidden("Only admins can set SUSPENDED_BY_ADMIN");
        product.setStatus(newStatus);
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
        deleteProductImages(product);
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
        log.info("Product {} soft-deleted", productId);
    }

    @Transactional
    public void adminDelete(String productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        deleteProductImages(product);
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
        log.info("Product {} soft-deleted by admin", productId);
    }

    @Transactional
    public ProductDto addImage(String creatorId, String productId,
                               AddImageRequest req) {
        Product product = getOwnedProduct(creatorId, productId);
        if (req.isPrimary()) {
            product.getImages().forEach(img -> img.setPrimary(false));
        }
        ProductImage image = ProductImage.builder()
                .product(product).url(req.url())
                .cloudinaryId(req.cloudinaryId())
                .isPrimary(req.isPrimary())
                .displayOrder(req.displayOrder())
                .altText(req.altText())
                .build();
        product.getImages().add(image);
        return toDto(productRepository.save(product));
    }

    @Transactional
    public void deleteImage(String creatorId, String productId, Long imageId) {
        getOwnedProduct(creatorId, productId);
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> ApiException.notFound("Image not found"));
        if (!image.getProduct().getId().equals(productId))
            throw ApiException.forbidden("Image does not belong to this product");
        // Delete from Cloudinary
        if (image.getCloudinaryId() != null) {
            cloudinaryService.deleteImage(image.getCloudinaryId());
        }
        imageRepository.delete(image);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getByCreator(String creatorId) {
        return productRepository.findByCreatorIdNotDeleted(creatorId)
                .stream().map(this::toDto).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void deleteProductImages(Product product) {
        List<String> publicIds = product.getImages().stream()
                .map(ProductImage::getCloudinaryId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (!publicIds.isEmpty()) {
            cloudinaryService.deleteImages(publicIds);
        }
    }

    private Product getOwnedProduct(String creatorId, String productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!product.getCreator().getId().equals(creatorId))
            throw ApiException.forbidden("You do not own this product");
        return product;
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
        String heroImage = p.getHeroImage() != null ? p.getHeroImage()
                : images.stream().filter(ProductImageDto::isPrimary)
                        .findFirst().map(ProductImageDto::url).orElse(null);
        Creator c = p.getCreator();
        return new ProductDto(
                p.getId(), p.getName(), p.getPricePence(), heroImage, images,
                p.getPieceStory(),
                parseList(p.getMaterials(), new TypeReference<>() {}),
                p.getDimensions(), p.getCare(), p.getStatus().name(),
                new ProductCreatorDto(c.getId(), c.getFirstName(), c.getFullName(),
                        c.getRegion(), c.getCraft(), c.getPortraitImage()));
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