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
 * Idempotent per category id — re-running never overwrites an admin's
 * edits to an existing category's name/color/craftValues. The backfill
 * pass runs every boot (cheap: only touches products with category IS
 * NULL), so a product created before its matching category existed still
 * gets picked up on the next restart, and widening a category's
 * craftValues list picks up newly-matching products too.
 */
@Slf4j
@Component
@Order(11)
@RequiredArgsConstructor
public class CategorySeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    private record Seed(String id, String name, String colorToken, int sortOrder, List<String> craftValues) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("crafts", "Crafts", "clay", 0, List.of("Beadwork")),
            new Seed("farm-produce", "Farm & Produce", "sage", 1, List.of(
                    "Horticulture and AgriBusiness", "HASS FARMING", "Tree nursery", "Farming", "Avocado grower"))
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Seed seed : SEEDS) {
            if (categoryRepository.existsById(seed.id())) continue;
            categoryRepository.save(Category.builder()
                    .id(seed.id())
                    .name(seed.name())
                    .colorToken(seed.colorToken())
                    .sortOrder(seed.sortOrder())
                    .craftValues(toJson(seed.craftValues()))
                    .build());
            log.info("Seeded category: {}", seed.name());
        }

        backfillUncategorized();
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
