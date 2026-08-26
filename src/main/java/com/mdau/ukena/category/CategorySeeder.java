package com.mdau.ukena.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.product.Product;
import com.mdau.ukena.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Seeds the starter category taxonomy and auto-assigns any uncategorized
 * product to the category whose {@code craftValues} matches its creator's
 * real `craft` string — the same grouping the frontend used to do with a
 * hardcoded list (see the storefront redesign plan's craftGroups.ts), now
 * a real, admin-editable backend source of truth instead.
 *
 * Seeding is create-only: every field (name, color, craft values, thumbnail,
 * initial active state) is set once, on first creation, and never touched
 * again — categories are fully admin-editable from the Categories screen,
 * and those edits must survive every future deploy. The backfill pass runs
 * every boot (cheap: only touches products with category IS NULL), so a
 * product created before its matching category existed still gets picked
 * up on the next restart, and widening a category's craftValues list picks
 * up newly-matching products too.
 */
@Slf4j
@Component
@Order(11)
@RequiredArgsConstructor
public class CategorySeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    private record Seed(
            String id, String name, String colorToken, int sortOrder,
            List<String> craftValues, String thumbnailImage) {}

    // Thumbnail sources: royalty-free Pexels photography (free for commercial
    // use, no attribution required — see pexels.com/license), not Pinterest,
    // per the 2026-08-21 decision on image sourcing.
    private static final List<Seed> SEEDS = List.of(
            new Seed("crafts", "Crafts", "terracotta", 0, List.of("Beadwork"),
                    "https://images.pexels.com/photos/32405948/pexels-photo-32405948.jpeg"),
            new Seed("farm-produce", "Farm & Produce", "sage", 1, List.of(
                    "Horticulture and AgriBusiness", "HASS FARMING", "Tree nursery", "Farming", "Avocado grower"),
                    "https://images.pexels.com/photos/37875706/pexels-photo-37875706.jpeg"),
            // New categories (2026-08-21) — seeded inactive; an admin activates
            // each from the Categories screen once it's ready to go live.
            new Seed("textiles", "Textiles", "indigo", 2,
                    List.of("Textile Weaving", "Handloom Weaving", "Textile and Weaving"),
                    "https://images.pexels.com/photos/35692211/pexels-photo-35692211.jpeg"),
            new Seed("pottery-ceramics", "Pottery & Ceramics", "rust", 3,
                    List.of("Pottery and Ceramics", "Ceramic Art", "Pottery"),
                    "https://images.pexels.com/photos/18373966/pexels-photo-18373966.jpeg"),
            new Seed("home-decor", "Home & Decor", "gold", 4,
                    List.of("Home Decor Craftsmanship", "Woodwork and Home Decor", "Interior Craft"),
                    "https://images.pexels.com/photos/12715584/pexels-photo-12715584.jpeg"),
            new Seed("ebooks", "E-books", "slate", 5,
                    List.of("Digital Publishing", "Author and Publisher"),
                    "https://images.pexels.com/photos/20092850/pexels-photo-20092850.jpeg"),
            // "clay" is the last unused color token in categoryColor.ts's
            // palette (frontend) — adding a further category beyond this one
            // needs a new token defined there first, not just here.
            new Seed("jewelry-accessories", "Jewelry & Accessories", "clay", 6,
                    List.of("Beaded Jewelry", "Jewelry Making", "Leatherwork", "Leather Craft"),
                    "https://images.pexels.com/photos/10562316/pexels-photo-10562316.jpeg")
    );

    /** New (2026-08-21) categories seeded inactive by default — no live
     *  products/creators yet, so they shouldn't appear on the public site
     *  (GET /categories only returns active=true) until an admin flips them
     *  on. Crafts/Farm & Produce are pre-existing and already real. */
    private static final List<String> STARTS_INACTIVE =
            List.of("textiles", "pottery-ceramics", "home-decor", "ebooks", "jewelry-accessories");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Create-only: categories are now fully admin-editable (name, color,
        // craft values, thumbnail) from the Categories screen, and those edits
        // must stick across deploys — so seeding only ever fills in a category
        // that doesn't exist yet, never overwrites one that does.
        for (Seed seed : SEEDS) {
            if (categoryRepository.existsById(seed.id())) continue;
            Category category = Category.builder()
                    .id(seed.id())
                    .name(seed.name())
                    .colorToken(seed.colorToken())
                    .sortOrder(seed.sortOrder())
                    .craftValues(toJson(seed.craftValues()))
                    .thumbnailImage(seed.thumbnailImage())
                    .active(!STARTS_INACTIVE.contains(seed.id()))
                    .build();
            categoryRepository.save(category);
            log.info("Seeded category: {} (active={})", seed.name(), category.isActive());
        }

        backfillMissingThumbnails();
        backfillUncategorized();
        activateCategoriesWithRealProducts();
    }

    /** The create-only guard above protects deliberate admin edits (name,
     *  color, craft values) from ever being overwritten — but it also means
     *  a category row created before this seed data had thumbnails never
     *  got one backfilled. A null thumbnail is never a deliberate admin
     *  choice (the card is designed to always show one), so this fills it
     *  in from the seed once, without touching any field an admin might
     *  have actually edited. */
    private void backfillMissingThumbnails() {
        for (Seed seed : SEEDS) {
            if (seed.thumbnailImage() == null) continue;
            categoryRepository.findById(seed.id()).ifPresent(category -> {
                if (category.getThumbnailImage() == null || category.getThumbnailImage().isBlank()) {
                    category.setThumbnailImage(seed.thumbnailImage());
                    categoryRepository.save(category);
                    log.info("Backfilled thumbnail for category: {}", category.getName());
                }
            });
        }
    }

    /** New categories seed inactive since they start with no real products —
     *  once real products actually land in one (via the craft-matching
     *  backfill above, across restarts), it's ready to go live without
     *  needing a manual admin toggle. Never deactivates a category an admin
     *  turned on, or one an admin deliberately turned back off — only ever
     *  flips inactive -> active, and only when it's genuinely no longer empty. */
    private void activateCategoriesWithRealProducts() {
        List<Category> inactive = categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(c -> !c.isActive())
                .toList();
        for (Category category : inactive) {
            long liveProducts = productRepository.countByCategory_IdAndDeletedAtIsNullAndStatus(
                    category.getId(), com.mdau.ukena.product.ProductStatus.ACTIVE);
            if (liveProducts > 0) {
                category.setActive(true);
                categoryRepository.save(category);
                log.info("Activated category {} — {} real product(s) found", category.getName(), liveProducts);
            }
        }
    }

    private void backfillUncategorized() {
        List<Product> uncategorized = productRepository.findUncategorizedNotDeleted();
        if (uncategorized.isEmpty()) return;

        List<Category> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();
        Map<Category, List<String>> craftValuesByCategory = categories.stream()
                .collect(java.util.stream.Collectors.toMap(c -> c, c -> parseList(c.getCraftValues())));

        int assigned = 0;
        for (Product p : uncategorized) {
            String craft = p.getCreator() != null ? p.getCreator().getCraft() : null;
            if (craft == null) continue;
            String normalizedCraft = craft.trim().toLowerCase(Locale.ROOT);

            for (Category category : categories) {
                boolean matches = craftValuesByCategory.get(category).stream()
                        .anyMatch(v -> v.trim().toLowerCase(Locale.ROOT).equals(normalizedCraft));
                if (matches) {
                    p.setCategory(category);
                    productRepository.save(p);
                    assigned++;
                    break;
                }
            }
        }
        if (assigned > 0) log.info("Category backfill: assigned {} product(s)", assigned);
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private String toJson(List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (Exception e) { return "[]"; }
    }
}
