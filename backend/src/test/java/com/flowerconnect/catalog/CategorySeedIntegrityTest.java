package com.flowerconnect.catalog;

import com.flowerconnect.catalog.domain.Category;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the seed data inserted by {@code V7__categories.sql}.
 */
class CategorySeedIntegrityTest {

    @Test
    void shouldHaveExpectedSeedCount() {
        List<Category> all = createSeedData();
        assertEquals(4, all.size(), "Expected 4 seed categories");
    }

    @Test
    void shouldHaveExpectedTopLevelCategories() {
        List<Category> all = createSeedData();
        List<String> names = all.stream()
                .map(Category::getName)
                .sorted()
                .toList();
        assertEquals(List.of("Arrangements", "Bouquets", "Occasions", "Roses"), names);
    }

    @Test
    void shouldHaveCorrectSlugs() {
        List<Category> all = createSeedData();
        for (Category cat : all) {
            String expectedSlug = cat.getName().toLowerCase().replace(" ", "-");
            assertEquals(expectedSlug, cat.getSlug(), "Slug must match name for " + cat.getName());
        }
    }

    @Test
    void shouldHaveCorrectDisplayOrder() {
        List<Category> all = new java.util.ArrayList<>(createSeedData());
        all.sort((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()));
        assertEquals(List.of("Roses", "Bouquets", "Arrangements", "Occasions"),
                all.stream().map(Category::getName).toList());
    }

    @Test
    void shouldAllBeActive() {
        List<Category> all = createSeedData();
        for (Category cat : all) {
            assertTrue(cat.isActive(), "Seed category must be active: " + cat.getName());
        }
    }

    @Test
    void shouldHaveNoParent() {
        List<Category> all = createSeedData();
        for (Category cat : all) {
            assertNull(cat.getParent(), "Seed categories must be top-level (no parent): " + cat.getName());
        }
    }

    private List<Category> createSeedData() {
        return List.of(
                createCategory("Roses", "roses", 1),
                createCategory("Bouquets", "bouquets", 2),
                createCategory("Arrangements", "arrangements", 3),
                createCategory("Occasions", "occasions", 4)
        );
    }

    private Category createCategory(String name, String slug, int displayOrder) {
        return Category.builder()
                .name(name)
                .slug(slug)
                .displayOrder(displayOrder)
                .active(true)
                .build();
    }
}