package com.flowerconnect.catalog.controller;

import com.flowerconnect.catalog.dto.CategoryPageResponse;
import com.flowerconnect.catalog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Public categories", description = "Public read of active top-level categories (no authentication)")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List active top-level categories",
            description = "Returns the active top-level categories ordered by display_order then id. "
                    + "Public endpoint; no authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category list")
    })
    public ResponseEntity<CategoryPageResponse> getCategories() {
        return ResponseEntity.ok(categoryService.listActive());
    }
}