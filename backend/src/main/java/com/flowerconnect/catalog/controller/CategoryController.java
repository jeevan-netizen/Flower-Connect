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
 * <p>Returns a flat list of active categories ordered by display_order then
 * name. The parent is flattened to its id and name so the client can
 * reconstruct a tree if needed. This mirrors the {@code /api/v1/locations}
 * public read pattern.
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