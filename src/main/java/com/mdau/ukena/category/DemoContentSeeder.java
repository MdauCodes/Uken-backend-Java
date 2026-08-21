package com.mdau.ukena.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.product.Product;
import com.mdau.ukena.product.ProductRepository;
import com.mdau.ukena.product.ProductStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time seed of representative creators/products for the new Textiles,
 * Pottery & Ceramics, Home & Decor and E-books categories (2026-08-21) — so
 * the category-first homepage sections and per-category color accents can
 * be evaluated against something real-looking before real makers onboard.
 *
 * Every seeded product is created {@code SUSPENDED_BY_ADMIN} — the same
 * status a real admin uses to hide any listing — so nothing here appears in
 * any public listing, search, or even a direct product-page URL
 * ({@code ProductService.getById} already excludes suspended products)
 * until an admin explicitly reactivates it from the existing admin Products
 * screen. No separate "demo" flag; this reuses the real moderation state
 * that already exists, per the 2026-08-21 decision.
 *
 * Images are royalty-free Pexels photography (free for commercial use, no
 * attribution required — pexels.com/license), not Pinterest, per the same
 * decision. Idempotent per creator id — re-running never overwrites an
 * admin's edits to a creator/product that already exists.
 *
 * Known gap, not solved here: the E-book product has no real weightGrams
 * (it's a digital download, not a physical shipment) — set to a nominal 1g
 * so it doesn't inherit an absurd physical-shipping charge if ever
 * activated, but the platform genuinely has no "digital delivery" order
 * path yet. Worth a real fix before E-books actually sells anything.
 */
@Slf4j
@Component
@Order(12)
@RequiredArgsConstructor
public class DemoContentSeeder implements ApplicationRunner {

    private final CreatorRepository creatorRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    private record DemoProduct(
            String id, String name, int pricePence, String heroImage, String pieceStory,
            List<String> materials, String dimensions, String care, Integer weightGrams) {
        DemoProduct(String id, String name, int pricePence, String heroImage, String pieceStory,
                    List<String> materials, String dimensions, String care) {
            this(id, name, pricePence, heroImage, pieceStory, materials, dimensions, care, null);
        }
    }

    private record DemoCreator(
            String id, String firstName, String fullName, String craft, String region,
            String hook, String portraitImage, String categoryId, List<DemoProduct> products) {}

    private static final List<DemoCreator> DEMO_CREATORS = List.of(
            new DemoCreator(
                    "amara-textiles-demo", "Amara", "Amara Textiles Collective", "Textile Weaving", "Kisumu, Kenya",
                    "Handwoven textiles made on traditional looms, using natural dyes and patterns passed through generations.",
                    "https://images.pexels.com/photos/18789516/pexels-photo-18789516.jpeg",
                    "textiles",
                    List.of(
                            new DemoProduct("p-demo-textile-wall-art", "Handwoven Tribal Wall Hanging", 4500,
                                    "https://images.pexels.com/photos/35692211/pexels-photo-35692211.jpeg",
                                    "A hand-loomed wall piece in a traditional pattern, woven over several days on a wooden frame loom.",
                                    List.of("Cotton", "Natural dye"), "60cm x 90cm", "Spot clean, avoid direct sunlight."),
                            new DemoProduct("p-demo-woven-basket", "Handwoven Storage Basket", 3200,
                                    "https://images.pexels.com/photos/33476890/pexels-photo-33476890.jpeg",
                                    "A sturdy, tightly-woven basket for storage or display, made from locally-sourced natural fibre.",
                                    List.of("Sisal fibre"), "30cm diameter", "Wipe with a dry cloth.")
                    )
            ),
            new DemoCreator(
                    "kito-ceramics-demo", "Kito", "Kito Ceramics Studio", "Pottery and Ceramics", "Nakuru, Kenya",
                    "Wheel-thrown and hand-built pottery, fired using clay sourced from the Rift Valley.",
                    "https://images.pexels.com/photos/31633685/pexels-photo-31633685.jpeg",
                    "pottery-ceramics",
                    List.of(
                            new DemoProduct("p-demo-clay-vase", "Hand-thrown Clay Vase", 3800,
                                    "https://images.pexels.com/photos/11975310/pexels-photo-11975310.jpeg",
                                    "A wheel-thrown vase — each one uniquely shaped and finished with a natural clay glaze.",
                                    List.of("Stoneware clay"), "18cm tall", "Hand wash only."),
                            new DemoProduct("p-demo-clay-jars", "Set of 3 Storage Jars", 5200,
                                    "https://images.pexels.com/photos/3692053/pexels-photo-3692053.jpeg",
                                    "A set of hand-built clay storage jars with fitted lids, finished in a matte glaze.",
                                    List.of("Stoneware clay"), "Set of 3, various sizes", "Hand wash only.")
                    )
            ),
            new DemoCreator(
                    "juma-decor-demo", "Juma", "Juma Home Craft", "Home Decor Craftsmanship", "Mombasa, Kenya",
                    "Handmade wooden home pieces — signage, small furniture and decor — carved and finished by hand.",
                    "https://images.pexels.com/photos/33203798/pexels-photo-33203798.jpeg",
                    "home-decor",
                    List.of(
                            new DemoProduct("p-demo-wooden-sign", "Carved Wooden Welcome Sign", 2800,
                                    "https://images.pexels.com/photos/163046/welcome-to-our-home-welcome-tablet-an-array-of-163046.jpeg",
                                    "A hand-carved wooden sign, sanded and sealed for indoor or covered outdoor use.",
                                    List.of("Reclaimed hardwood"), "40cm x 20cm", "Wipe with a dry cloth.")
                    )
            ),
            new DemoCreator(
                    "amina-ebooks-demo", "Amina", "Amina Recipes & Stories", "Digital Publishing", "Nairobi, Kenya",
                    "Digital collections of East African recipes and craft stories, written and self-published.",
                    "https://images.pexels.com/photos/19039168/pexels-photo-19039168.jpeg",
                    "ebooks",
                    List.of(
                            new DemoProduct("p-demo-ebook-recipes", "Traditional Kenyan Recipes — Digital Cookbook", 999,
                                    "https://images.pexels.com/photos/20092850/pexels-photo-20092850.jpeg",
                                    "A digital cookbook of traditional recipes, collected and written by the author. Delivered as a download.",
                                    List.of("PDF download"), "Digital download", "N/A", 1)
                    )
            )
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (DemoCreator dc : DEMO_CREATORS) {
            if (creatorRepository.existsById(dc.id())) continue;

            Creator creator = Creator.builder()
                    .id(dc.id())
                    .firstName(dc.firstName())
                    .fullName(dc.fullName())
                    .craft(dc.craft())
                    .region(dc.region())
                    .hook(dc.hook())
                    .image(dc.portraitImage())
                    .portraitImage(dc.portraitImage())
                    .headerImage(dc.portraitImage())
                    .build();
            creatorRepository.save(creator);

            Category category = categoryRepository.findById(dc.categoryId()).orElse(null);

            for (DemoProduct dp : dc.products()) {
                if (productRepository.existsById(dp.id())) continue;
                Product product = Product.builder()
                        .id(dp.id())
                        .creator(creator)
                        .name(dp.name())
                        .pricePence(dp.pricePence())
                        .heroImage(dp.heroImage())
                        .pieceStory(dp.pieceStory())
                        .materials(toJson(dp.materials()))
                        .dimensions(dp.dimensions())
                        .care(dp.care())
                        .weightGrams(dp.weightGrams())
                        .status(ProductStatus.SUSPENDED_BY_ADMIN)
                        .category(category)
                        .build();
                productRepository.save(product);
            }

            log.info("Seeded demo creator: {} with {} product(s) (hidden — SUSPENDED_BY_ADMIN)",
                    dc.fullName(), dc.products().size());
        }
    }

    private String toJson(List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (Exception e) { return "[]"; }
    }
}
