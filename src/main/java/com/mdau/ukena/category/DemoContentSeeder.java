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
            ),
            // Second batch (2026-08-26) — one more maker per thin category,
            // plus two for the new Jewelry & Accessories category, at the
            // client's request to make the catalogue feel more populated
            // ahead of real makers onboarding into each. Same rules as
            // above: SUSPENDED_BY_ADMIN until an admin reviews and activates
            // from the Products screen, real Pexels photography (each URL
            // verified to actually resolve before being added here).
            new DemoCreator(
                    "zawadi-textiles-demo", "Zawadi", "Zawadi Weaving House", "Textile Weaving", "Machakos, Kenya",
                    "Handwoven rugs and mats made on a traditional floor loom, using wool and cotton sourced regionally.",
                    "https://images.pexels.com/photos/22942812/pexels-photo-22942812.jpeg",
                    "textiles",
                    List.of(
                            new DemoProduct("p-demo-woven-rug", "Handwoven Wool Rug", 6500,
                                    "https://images.pexels.com/photos/34506224/pexels-photo-34506224.jpeg",
                                    "A floor rug hand-woven on a traditional loom, in a pattern passed down through the family workshop.",
                                    List.of("Wool", "Cotton warp"), "120cm x 80cm", "Shake out and air; spot clean only.")
                    )
            ),
            new DemoCreator(
                    "baraka-ceramics-demo", "Baraka", "Baraka Clayworks", "Pottery and Ceramics", "Kisumu, Kenya",
                    "Hand-thrown stoneware fired in a wood-burning kiln, finished with food-safe glazes.",
                    "https://images.pexels.com/photos/38428349/pexels-photo-38428349.jpeg",
                    "pottery-ceramics",
                    List.of(
                            new DemoProduct("p-demo-ceramic-bowls", "Set of Ceramic Serving Bowls", 4200,
                                    "https://images.pexels.com/photos/29312155/pexels-photo-29312155.jpeg",
                                    "A set of hand-thrown serving bowls, each individually shaped, in a clean matte glaze.",
                                    List.of("Stoneware clay"), "Set of 4, 14cm diameter", "Hand wash only.")
                    )
            ),
            new DemoCreator(
                    "oduya-woodwork-demo", "Peter", "Oduya Woodwork", "Home Decor Craftsmanship", "Kakamega, Kenya",
                    "Small furniture and serving pieces carved from reclaimed hardwood, finished by hand.",
                    "https://images.pexels.com/photos/6790933/pexels-photo-6790933.jpeg",
                    "home-decor",
                    List.of(
                            new DemoProduct("p-demo-serving-tray", "Handcrafted Wooden Serving Tray", 3400,
                                    "https://images.pexels.com/photos/10545937/pexels-photo-10545937.jpeg",
                                    "A serving tray carved with an inlaid pattern, sanded smooth and finished with food-safe oil.",
                                    List.of("Mahogany"), "45cm x 30cm", "Wipe with a dry cloth; oil occasionally.")
                    )
            ),
            new DemoCreator(
                    "wanjiru-digital-demo", "Grace", "Wanjiru Digital Press", "Digital Publishing", "Nairobi, Kenya",
                    "Independently written and published digital guides on East African travel and culture.",
                    "https://images.pexels.com/photos/9895992/pexels-photo-9895992.jpeg",
                    "ebooks",
                    List.of(
                            new DemoProduct("p-demo-ebook-travel-guide", "Kenya Travel & Culture Guide — Digital Edition", 799,
                                    "https://images.pexels.com/photos/5137809/pexels-photo-5137809.jpeg",
                                    "A self-published digital guide to Kenya's regions, culture, and markets. Delivered as a download.",
                                    List.of("PDF download"), "Digital download", "N/A", 1)
                    )
            ),
            new DemoCreator(
                    "naisula-beadwork-demo", "Naisula", "Naisula Beadwork", "Beaded Jewelry", "Kajiado, Kenya",
                    "Traditional Maasai beaded jewelry, strung and finished by hand using glass beads and cotton thread.",
                    "https://images.pexels.com/photos/31151819/pexels-photo-31151819.jpeg",
                    "jewelry-accessories",
                    List.of(
                            new DemoProduct("p-demo-beaded-necklace", "Maasai Beaded Necklace", 2400,
                                    "https://images.pexels.com/photos/29831467/pexels-photo-29831467.jpeg",
                                    "A hand-strung beaded necklace in a traditional pattern, made using glass beads on cotton thread.",
                                    List.of("Glass beads", "Cotton thread"), "45cm length", "Wipe clean; avoid water.")
                    )
            ),
            new DemoCreator(
                    "bakari-leather-demo", "Hassan", "Bakari Leatherworks", "Leatherwork", "Malindi, Kenya",
                    "Handmade leather sandals and small goods, cut and stitched by hand using traditional tools.",
                    "https://images.pexels.com/photos/38226090/pexels-photo-38226090.jpeg",
                    "jewelry-accessories",
                    List.of(
                            new DemoProduct("p-demo-leather-sandals", "Handmade Leather Sandals", 3800,
                                    "https://images.pexels.com/photos/37136138/pexels-photo-37136138.jpeg",
                                    "A pair of hand-cut and hand-stitched leather sandals, made to order and finished with natural oils.",
                                    List.of("Genuine leather"), "Made to order, sizes 36–45", "Condition with leather oil periodically.")
                    )
            ),
            // Third batch (2026-08-26) — 12 more makers / 50 more products,
            // topping up every thin category and populating the two new
            // Coffee & Tea and Beauty & Wellness categories from scratch, at
            // the client's request. Same rules as every batch above: every
            // product SUSPENDED_BY_ADMIN + demo=true until an admin reviews
            // and activates from the Products screen, every image a real
            // Pexels URL verified to resolve (HTTP 200) before being added
            // here — none invented.
            new DemoCreator(
                    "ochieng-woodcraft-demo", "Ochieng", "Ochieng Wood Studio", "Wood Carving", "Kisii, Kenya",
                    "Hand-carved wooden masks and figurines, carved and finished using traditional Kisii techniques.",
                    "https://images.pexels.com/photos/37584311/pexels-photo-37584311.jpeg",
                    "crafts",
                    List.of(
                            new DemoProduct("p-demo-wooden-mask", "Hand-Carved Tribal Wall Mask", 3200,
                                    "https://images.pexels.com/photos/38030784/pexels-photo-38030784.jpeg",
                                    "A wall mask carved from a single piece of hardwood and finished with a natural wax polish.",
                                    List.of("Hardwood"), "35cm x 22cm", "Wipe with a dry cloth; keep away from direct sun."),
                            new DemoProduct("p-demo-wooden-elephant", "Carved Wooden Elephant Figurine", 1800,
                                    "https://images.pexels.com/photos/11450666/pexels-photo-11450666.jpeg",
                                    "A hand-carved elephant figurine, sanded smooth and finished with natural oil.",
                                    List.of("Hardwood"), "18cm tall", "Wipe with a dry cloth."),
                            new DemoProduct("p-demo-tribal-mask-large", "Large Tribal Ceremonial Mask", 4200,
                                    "https://images.pexels.com/photos/37604060/pexels-photo-37604060.jpeg",
                                    "A larger ceremonial-style mask, hand-carved and detailed with traditional patterning.",
                                    List.of("Hardwood"), "50cm x 28cm", "Wipe with a dry cloth; keep away from direct sun.")
                    )
            ),
            new DemoCreator(
                    "taveta-sisal-demo", "Mwikali", "Taveta Sisal Works", "Sisal Weaving", "Taita-Taveta, Kenya",
                    "Baskets and woven pieces hand-plaited from locally-grown sisal fibre.",
                    "https://images.pexels.com/photos/18686058/pexels-photo-18686058.jpeg",
                    "crafts",
                    List.of(
                            new DemoProduct("p-demo-sisal-wall-baskets", "Set of 3 Decorative Wall Baskets", 2900,
                                    "https://images.pexels.com/photos/30480965/pexels-photo-30480965.jpeg",
                                    "A trio of flat woven baskets, hand-plaited from sisal fibre for wall display or storage.",
                                    List.of("Sisal fibre"), "Set of 3, 20-30cm diameter", "Wipe with a dry cloth."),
                            new DemoProduct("p-demo-sisal-round-basket", "Round Sisal Storage Basket", 2400,
                                    "https://images.pexels.com/photos/32839144/pexels-photo-32839144.jpeg",
                                    "A sturdy round basket, hand-plaited from sisal fibre sourced from Taita-Taveta.",
                                    List.of("Sisal fibre"), "28cm diameter", "Wipe with a dry cloth."),
                            new DemoProduct("p-demo-sisal-coasters", "Hand-Plaited Sisal Coaster Set", 1200,
                                    "https://images.pexels.com/photos/36319624/pexels-photo-36319624.jpeg",
                                    "A set of coasters hand-plaited from natural sisal fibre.",
                                    List.of("Sisal fibre"), "Set of 6, 10cm diameter", "Wipe with a dry cloth.")
                    )
            ),
            new DemoCreator(
                    "nyeri-farm-demo", "Wanjiku", "Nyeri Highland Farms", "Horticulture and AgriBusiness", "Nyeri, Kenya",
                    "Fresh fruit and vegetables grown on the slopes of Mount Kenya.",
                    "https://images.pexels.com/photos/33569520/pexels-photo-33569520.jpeg",
                    "farm-produce",
                    List.of(
                            new DemoProduct("p-demo-carrots", "Fresh Carrots (5kg box)", 450,
                                    "https://images.pexels.com/photos/27934885/pexels-photo-27934885.jpeg",
                                    "Freshly harvested carrots, sorted and packed for delivery.",
                                    List.of("Harvested", "sorted and packed"), "Per 5kg box", "Store in a cool, dry place."),
                            new DemoProduct("p-demo-bell-peppers", "Yellow & Green Bell Peppers (3kg box)", 600,
                                    "https://images.pexels.com/photos/10847032/pexels-photo-10847032.jpeg",
                                    "Fresh bell peppers harvested and packed for delivery.",
                                    List.of("Harvested", "sorted and packed"), "Per 3kg box", "Store in a cool, dry place."),
                            new DemoProduct("p-demo-bananas", "Fresh Banana Bunch", 350,
                                    "https://images.pexels.com/photos/37124826/pexels-photo-37124826.jpeg",
                                    "A full bunch of fresh bananas, harvested and ready for delivery.",
                                    List.of("Harvested"), "Per bunch, approx 12-15 bananas", "Store at room temperature."),
                            new DemoProduct("p-demo-sweet-potatoes", "Purple Sweet Potatoes (5kg box)", 500,
                                    "https://images.pexels.com/photos/9956725/pexels-photo-9956725.jpeg",
                                    "Purple sweet potatoes, freshly harvested and packed for delivery.",
                                    List.of("Harvested", "sorted and packed"), "Per 5kg box", "Store in a cool, dry place.")
                    )
            ),
            new DemoCreator(
                    "mombasa-kitenge-demo", "Fatuma", "Mombasa Kitenge House", "Textile Weaving", "Mombasa, Kenya",
                    "Vibrant kitenge and kanga fabrics, sewn into home textiles and accessories.",
                    "https://images.pexels.com/photos/30247274/pexels-photo-30247274.jpeg",
                    "textiles",
                    List.of(
                            new DemoProduct("p-demo-kitenge-runner", "Kitenge Table Runner", 1800,
                                    "https://images.pexels.com/photos/37439166/pexels-photo-37439166.jpeg",
                                    "A table runner hand-sewn from vibrant kitenge fabric.",
                                    List.of("Cotton kitenge fabric"), "180cm x 35cm", "Hand wash cold, line dry."),
                            new DemoProduct("p-demo-kanga-wrap", "Kanga Wrap Cloth", 1500,
                                    "https://images.pexels.com/photos/38487457/pexels-photo-38487457.jpeg",
                                    "A traditional kanga wrap, block-printed in a bold pattern.",
                                    List.of("Cotton"), "150cm x 100cm", "Hand wash cold, line dry."),
                            new DemoProduct("p-demo-kitenge-cushion", "Kitenge Cushion Cover Set", 2200,
                                    "https://images.pexels.com/photos/35633192/pexels-photo-35633192.jpeg",
                                    "A set of cushion covers hand-sewn from vibrant kitenge fabric.",
                                    List.of("Cotton kitenge fabric"), "Set of 2, 45cm x 45cm", "Hand wash cold, line dry."),
                            new DemoProduct("p-demo-kitenge-tote", "Kitenge Tote Bag", 1600,
                                    "https://images.pexels.com/photos/38487458/pexels-photo-38487458.jpeg",
                                    "A sturdy tote bag hand-sewn from kitenge fabric with a cotton lining.",
                                    List.of("Cotton kitenge fabric", "Cotton lining"), "35cm x 40cm", "Hand wash cold, line dry."),
                            new DemoProduct("p-demo-kitenge-headwrap", "Kitenge Head Wrap", 900,
                                    "https://images.pexels.com/photos/18330807/pexels-photo-18330807.jpeg",
                                    "A head wrap hand-cut and hemmed from vibrant kitenge fabric.",
                                    List.of("Cotton kitenge fabric"), "110cm x 55cm", "Hand wash cold, line dry.")
                    )
            ),
            new DemoCreator(
                    "machakos-clay-demo", "Muthoni", "Machakos Clay Collective", "Pottery and Ceramics", "Machakos, Kenya",
                    "Traditional hand-built clay pots, fired the traditional way and finished with a natural clay slip.",
                    "https://images.pexels.com/photos/9541097/pexels-photo-9541097.jpeg",
                    "pottery-ceramics",
                    List.of(
                            new DemoProduct("p-demo-clay-water-pot", "Traditional Clay Water Pot", 3200,
                                    "https://images.pexels.com/photos/29776425/pexels-photo-29776425.jpeg",
                                    "A hand-built water pot, shaped and fired using traditional methods.",
                                    List.of("Terracotta clay"), "35cm tall", "Hand wash only; season before first use."),
                            new DemoProduct("p-demo-clay-cooking-pot", "Hand-Built Clay Cooking Pot", 2800,
                                    "https://images.pexels.com/photos/34004100/pexels-photo-34004100.jpeg",
                                    "A traditional cooking pot, hand-built and fired for even heat distribution.",
                                    List.of("Terracotta clay"), "25cm diameter", "Hand wash only; season before first use."),
                            new DemoProduct("p-demo-clay-flower-pots", "Set of Small Clay Flower Pots", 1600,
                                    "https://images.pexels.com/photos/17962521/pexels-photo-17962521.jpeg",
                                    "A set of small hand-thrown flower pots, unglazed for a natural finish.",
                                    List.of("Terracotta clay"), "Set of 4, 10cm diameter", "Wipe with a dry cloth."),
                            new DemoProduct("p-demo-clay-serving-tray", "Glazed Clay Serving Tray", 2600,
                                    "https://images.pexels.com/photos/20208730/pexels-photo-20208730.jpeg",
                                    "A hand-built serving tray finished with a food-safe glaze.",
                                    List.of("Stoneware clay"), "38cm x 26cm", "Hand wash only."),
                            new DemoProduct("p-demo-clay-decorative-vase", "Decorative Clay Vase", 2400,
                                    "https://images.pexels.com/photos/33878988/pexels-photo-33878988.jpeg",
                                    "A decorative vase, hand-built and finished with a natural clay slip.",
                                    List.of("Terracotta clay"), "22cm tall", "Wipe with a dry cloth.")
                    )
            ),
            new DemoCreator(
                    "eldoret-decor-demo", "Kiplangat", "Eldoret Home Craft", "Home Decor Craftsmanship", "Eldoret, Kenya",
                    "Handmade wooden and woven pieces for the home, finished by hand in a small family workshop.",
                    "https://images.pexels.com/photos/30201839/pexels-photo-30201839.jpeg",
                    "home-decor",
                    List.of(
                            new DemoProduct("p-demo-wooden-mirror-frame", "Carved Wooden Mirror Frame", 3600,
                                    "https://images.pexels.com/photos/12814969/pexels-photo-12814969.jpeg",
                                    "A mirror framed in hand-carved hardwood, sanded and sealed.",
                                    List.of("Hardwood", "Mirror glass"), "50cm x 70cm", "Wipe frame with a dry cloth; clean glass with a soft cloth."),
                            new DemoProduct("p-demo-wooden-ladder-shelf", "Wooden Ladder Shelf", 4200,
                                    "https://images.pexels.com/photos/1125131/pexels-photo-1125131.jpeg",
                                    "A decorative ladder shelf, hand-built from reclaimed hardwood.",
                                    List.of("Reclaimed hardwood"), "150cm tall", "Wipe with a dry cloth."),
                            new DemoProduct("p-demo-wooden-vase-spiral", "Spiral-Carved Wooden Vase", 2200,
                                    "https://images.pexels.com/photos/12148041/pexels-photo-12148041.jpeg",
                                    "A vase hand-carved from a single piece of hardwood in a spiral pattern.",
                                    List.of("Hardwood"), "24cm tall", "Wipe with a dry cloth."),
                            new DemoProduct("p-demo-magazine-holder", "Handwoven Magazine Holder", 1900,
                                    "https://images.pexels.com/photos/12771213/pexels-photo-12771213.jpeg",
                                    "A magazine holder woven from natural fibre over a wooden frame.",
                                    List.of("Hardwood", "Natural fibre"), "35cm x 28cm", "Wipe with a dry cloth."),
                            new DemoProduct("p-demo-room-divider", "Carved Wooden Room Divider Panel", 8500,
                                    "https://images.pexels.com/photos/2062432/pexels-photo-2062432.jpeg",
                                    "A hand-carved wooden panel, made to order for use as a room divider or wall feature.",
                                    List.of("Hardwood"), "180cm x 60cm", "Wipe with a dry cloth.")
                    )
            ),
            new DemoCreator(
                    "kisumu-press-demo", "Achieng", "Kisumu Digital Press", "Digital Publishing", "Kisumu, Kenya",
                    "Independently written and published digital guides and short story collections.",
                    "https://images.pexels.com/photos/36322504/pexels-photo-36322504.jpeg",
                    "ebooks",
                    List.of(
                            new DemoProduct("p-demo-ebook-folktales", "Lake Victoria Folktales — Digital Collection", 699,
                                    "https://images.pexels.com/photos/76942/pexels-photo-76942.jpeg",
                                    "A digital collection of traditional folktales from the Lake Victoria region, illustrated and self-published.",
                                    List.of("PDF download"), "Digital download", "N/A", 1),
                            new DemoProduct("p-demo-ebook-business-guide", "Small Business Starter Guide — Digital Edition", 899,
                                    "https://images.pexels.com/photos/8546475/pexels-photo-8546475.jpeg",
                                    "A practical guide for starting a small business in Kenya, self-published and delivered as a download.",
                                    List.of("PDF download"), "Digital download", "N/A", 1),
                            new DemoProduct("p-demo-ebook-poetry", "Poems from the Rift — Digital Poetry Collection", 599,
                                    "https://images.pexels.com/photos/3060654/pexels-photo-3060654.jpeg",
                                    "A collection of original poetry inspired by the Rift Valley, self-published and delivered as a download.",
                                    List.of("PDF download"), "Digital download", "N/A", 1),
                            new DemoProduct("p-demo-ebook-language", "Learn Swahili — Digital Phrasebook", 499,
                                    "https://images.pexels.com/photos/28182660/pexels-photo-28182660.jpeg",
                                    "A beginner's Swahili phrasebook, self-published and delivered as a download.",
                                    List.of("PDF download"), "Digital download", "N/A", 1)
                    )
            ),
            new DemoCreator(
                    "kajiado-beads-demo", "Nasirian", "Kajiado Beadworks", "Beaded Jewelry", "Kajiado, Kenya",
                    "Beaded jewelry and accessories, hand-strung using glass beads in traditional Maasai patterns.",
                    "https://images.pexels.com/photos/35846958/pexels-photo-35846958.jpeg",
                    "jewelry-accessories",
                    List.of(
                            new DemoProduct("p-demo-beaded-bracelet-set", "Maasai Beaded Bracelet Set", 1400,
                                    "https://images.pexels.com/photos/28521274/pexels-photo-28521274.jpeg",
                                    "A set of hand-strung beaded bracelets in traditional Maasai patterns.",
                                    List.of("Glass beads", "Elastic cord"), "Set of 3, adjustable", "Wipe clean; avoid water."),
                            new DemoProduct("p-demo-beaded-anklet", "Beaded Ankle Bracelet", 900,
                                    "https://images.pexels.com/photos/33610292/pexels-photo-33610292.jpeg",
                                    "A hand-strung beaded ankle bracelet, made using glass beads on elastic cord.",
                                    List.of("Glass beads", "Elastic cord"), "One size, adjustable", "Wipe clean; avoid water."),
                            new DemoProduct("p-demo-beaded-cuff", "Wide Beaded Cuff Bracelet", 1800,
                                    "https://images.pexels.com/photos/34158322/pexels-photo-34158322.jpeg",
                                    "A wide cuff bracelet, hand-beaded in a traditional geometric pattern.",
                                    List.of("Glass beads", "Leather backing"), "One size, adjustable", "Wipe clean; avoid water."),
                            new DemoProduct("p-demo-beaded-belt", "Beaded Waist Belt", 2600,
                                    "https://images.pexels.com/photos/8628442/pexels-photo-8628442.jpeg",
                                    "A hand-beaded waist belt, made using glass beads in a traditional Maasai pattern.",
                                    List.of("Glass beads", "Leather backing"), "One size, adjustable", "Wipe clean; avoid water."),
                            new DemoProduct("p-demo-beaded-hair-clip", "Beaded Hair Clip Set", 700,
                                    "https://images.pexels.com/photos/33337874/pexels-photo-33337874.jpeg",
                                    "A set of hair clips finished with hand-strung glass beads.",
                                    List.of("Glass beads", "Metal clip"), "Set of 3", "Wipe clean; avoid water.")
                    )
            ),
            new DemoCreator(
                    "kirinyaga-coffee-demo", "Muriuki", "Kirinyaga Coffee Roasters", "Coffee Roasting", "Kirinyaga, Kenya",
                    "Small-batch coffee, grown on the slopes of Mount Kenya and roasted by hand.",
                    "https://images.pexels.com/photos/29892493/pexels-photo-29892493.jpeg",
                    "coffee-tea",
                    List.of(
                            new DemoProduct("p-demo-coffee-whole-beans", "Kirinyaga AA Whole Bean Coffee", 1200,
                                    "https://images.pexels.com/photos/33015766/pexels-photo-33015766.jpeg",
                                    "Single-origin AA grade coffee beans, hand-sorted and small-batch roasted.",
                                    List.of("Roasted"), "250g bag", "Store airtight, away from light."),
                            new DemoProduct("p-demo-coffee-ground", "Kirinyaga Ground Coffee", 1100,
                                    "https://images.pexels.com/photos/25547393/pexels-photo-25547393.jpeg",
                                    "Medium-roast ground coffee, milled fresh to order from single-origin beans.",
                                    List.of("Roasted", "Ground"), "250g bag", "Store airtight, away from light."),
                            new DemoProduct("p-demo-coffee-cherries-dried", "Sun-Dried Coffee Cherries", 800,
                                    "https://images.pexels.com/photos/14498531/pexels-photo-14498531.jpeg",
                                    "Whole sun-dried coffee cherries, a traditional natural-process coffee.",
                                    List.of("Sun-dried"), "300g bag", "Store airtight, away from light."),
                            new DemoProduct("p-demo-coffee-gift-set", "Coffee Lover's Gift Set", 2600,
                                    "https://images.pexels.com/photos/28580602/pexels-photo-28580602.jpeg",
                                    "A gift set of three single-origin coffees, hand-roasted in small batches.",
                                    List.of("Roasted"), "3 x 150g bags", "Store airtight, away from light.")
                    )
            ),
            new DemoCreator(
                    "limuru-tea-demo", "Wangari", "Limuru Tea Gardens", "Tea Growing", "Limuru, Kenya",
                    "Hand-plucked tea leaves, grown and processed on a family-run tea garden.",
                    "https://images.pexels.com/photos/30541302/pexels-photo-30541302.jpeg",
                    "coffee-tea",
                    List.of(
                            new DemoProduct("p-demo-tea-black-loose", "Limuru Black Loose Leaf Tea", 650,
                                    "https://images.pexels.com/photos/9025660/pexels-photo-9025660.jpeg",
                                    "Hand-plucked black tea leaves, withered and rolled using traditional methods.",
                                    List.of("Dried tea leaves"), "200g bag", "Store airtight, away from light."),
                            new DemoProduct("p-demo-tea-purple", "Kenyan Purple Tea Leaves", 750,
                                    "https://images.pexels.com/photos/7074106/pexels-photo-7074106.jpeg",
                                    "A rare purple tea varietal, hand-plucked and processed on a small family garden.",
                                    List.of("Dried tea leaves"), "150g bag", "Store airtight, away from light."),
                            new DemoProduct("p-demo-tea-green", "Limuru Green Tea Leaves", 700,
                                    "https://images.pexels.com/photos/33117970/pexels-photo-33117970.jpeg",
                                    "Lightly oxidised green tea leaves, hand-plucked and pan-fired.",
                                    List.of("Dried tea leaves"), "150g bag", "Store airtight, away from light."),
                            new DemoProduct("p-demo-tea-herbal-blend", "Herbal Tea Blend Gift Tin", 900,
                                    "https://images.pexels.com/photos/20535021/pexels-photo-20535021.jpeg",
                                    "A blend of tea leaves and dried herbs from the garden, packed in a gift tin.",
                                    List.of("Dried tea leaves", "Dried herbs"), "180g tin", "Store airtight, away from light.")
                    )
            ),
            new DemoCreator(
                    "nakuru-skincare-demo", "Njoki", "Nakuru Natural Skincare", "Natural Skincare", "Nakuru, Kenya",
                    "Shea butter and natural oils, hand-blended into skincare using traditional recipes.",
                    "https://images.pexels.com/photos/11284698/pexels-photo-11284698.jpeg",
                    "beauty-wellness",
                    List.of(
                            new DemoProduct("p-demo-shea-butter-raw", "Raw Shea Butter (250g)", 850,
                                    "https://images.pexels.com/photos/30754235/pexels-photo-30754235.jpeg",
                                    "Unrefined raw shea butter, hand-processed from shea nuts using traditional methods.",
                                    List.of("Shea butter"), "250g jar", "Store in a cool, dry place."),
                            new DemoProduct("p-demo-body-butter-rose", "Rose & Geranium Body Butter", 950,
                                    "https://images.pexels.com/photos/17596984/pexels-photo-17596984.jpeg",
                                    "A whipped body butter blended with rose and geranium essential oils.",
                                    List.of("Shea butter", "Rose oil", "Geranium oil"), "200g jar", "Store in a cool, dry place."),
                            new DemoProduct("p-demo-body-butter-citrus", "Lavender & Orange Body Butter", 950,
                                    "https://images.pexels.com/photos/17596986/pexels-photo-17596986.jpeg",
                                    "A whipped body butter blended with lavender and sweet orange essential oils.",
                                    List.of("Shea butter", "Lavender oil", "Orange oil"), "200g jar", "Store in a cool, dry place."),
                            new DemoProduct("p-demo-hand-cream", "Natural Hand Cream", 550,
                                    "https://images.pexels.com/photos/6925484/pexels-photo-6925484.jpeg",
                                    "A rich hand cream made with shea butter and natural oils.",
                                    List.of("Shea butter", "Natural oils"), "100ml tube", "Store in a cool, dry place.")
                    )
            ),
            new DemoCreator(
                    "athi-soap-demo", "Chebet", "Athi River Soap Co.", "Soap Making", "Athi River, Kenya",
                    "Cold-process natural soaps, hand-cut and cured using plant oils and botanicals.",
                    "https://images.pexels.com/photos/7500231/pexels-photo-7500231.jpeg",
                    "beauty-wellness",
                    List.of(
                            new DemoProduct("p-demo-soap-oatmeal", "Oatmeal & Honey Soap Bar", 450,
                                    "https://images.pexels.com/photos/6621470/pexels-photo-6621470.jpeg",
                                    "A cold-process soap bar made with oatmeal and honey for gentle exfoliation.",
                                    List.of("Plant oils", "Oatmeal", "Honey"), "100g bar", "Keep dry between uses."),
                            new DemoProduct("p-demo-soap-charcoal", "Activated Charcoal Soap Bar", 450,
                                    "https://images.pexels.com/photos/6930879/pexels-photo-6930879.jpeg",
                                    "A cold-process soap bar with activated charcoal for a deep clean.",
                                    List.of("Plant oils", "Activated charcoal"), "100g bar", "Keep dry between uses."),
                            new DemoProduct("p-demo-soap-rose-gift", "Rose & Botanicals Soap Gift Set", 1400,
                                    "https://images.pexels.com/photos/7055158/pexels-photo-7055158.jpeg",
                                    "A gift set of three botanical soap bars, hand-cut and finished with dried petals.",
                                    List.of("Plant oils", "Dried botanicals"), "Set of 3, 100g each", "Keep dry between uses."),
                            new DemoProduct("p-demo-soap-eucalyptus", "Eucalyptus & Mint Soap Bar", 450,
                                    "https://images.pexels.com/photos/10155373/pexels-photo-10155373.jpeg",
                                    "A cold-process soap bar scented with eucalyptus and mint essential oils.",
                                    List.of("Plant oils", "Eucalyptus oil", "Mint oil"), "100g bar", "Keep dry between uses.")
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
                        .demo(true)
                        .build();
                productRepository.save(product);
            }

            log.info("Seeded demo creator: {} with {} product(s) (hidden — SUSPENDED_BY_ADMIN)",
                    dc.fullName(), dc.products().size());
        }

        backfillDemoFlag();
    }

    /** The create-only loop above skips a product that already exists — so
     *  the very first demo batch (created before the demo flag existed) never
     *  got it set. Every id in DEMO_CREATORS is a demo product by definition,
     *  so this just ensures the flag is true on all of them, regardless of
     *  when they were created; never touches any field an admin might have
     *  edited since (name, price, status, etc.). */
    private void backfillDemoFlag() {
        for (DemoCreator dc : DEMO_CREATORS) {
            for (DemoProduct dp : dc.products()) {
                productRepository.findById(dp.id()).ifPresent(product -> {
                    if (!product.isDemoProduct()) {
                        product.setDemo(true);
                        productRepository.save(product);
                    }
                });
            }
        }
    }

    private String toJson(List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (Exception e) { return "[]"; }
    }
}
