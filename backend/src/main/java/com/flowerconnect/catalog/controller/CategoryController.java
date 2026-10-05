package com.flowerconnect.catalog.controller;

import com.flowerconnect.catalog.dto.CategoryPageResponse;
import com.flowerconnect.catalog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read endpoint for categories (plan task 3.1).
 *
 * <p>Returns the active top-level categories ordered by display_order then id
 * (id breaks ties). Inactive categories are excluded, so a deactivated
 * category disappears from the storefront chips and the vendor product form
 * while its row remains available to the admin listing. This mirrors the
 * {@code /api/v1/locations} public read pattern.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<CategoryPageResponse> getCategories() {
        return ResponseEntity.ok(categoryService.listActive());
    }
}