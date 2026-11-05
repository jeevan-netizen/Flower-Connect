package com.flowerconnect.catalog.controller;

import com.flowerconnect.catalog.dto.CategoryPageResponse;
import com.flowerconnect.catalog.dto.CategoryRequest;
import com.flowerconnect.catalog.dto.CategoryResponse;
import com.flowerconnect.catalog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@Tag(name = "Admin categories", description = "Admin-only category CRUD")
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List all categories",
            description = "Returns every category (roots and children, active and inactive) "
                    + "paginated. Admin-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    public ResponseEntity<CategoryPageResponse> listCategories(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        int safePage = page != null ? page : 0;
        int safeSize = size != null ? size : 20;

        return ResponseEntity.ok(categoryService.list(parentId, safePage, safeSize));
    }

    @PostMapping
    @Operation(summary = "Create a category",
            description = "Creates a category with an auto-generated slug. Admin-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "409", description = "Slug collision")
    })
    public ResponseEntity<CategoryResponse> createCategory(
            Authentication authentication,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.create(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category",
            description = "Updates a category; changing the name regenerates the slug. Admin-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "409", description = "Slug collision")
    })
    public ResponseEntity<CategoryResponse> updateCategory(
            Authentication authentication,
            @PathVariable @Positive(message = "Category id must be positive") Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category",
            description = "Hard-deletes a category. Refused with 409 if it has children. Admin-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Category deleted"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "409", description = "Category has children")
    })
    public ResponseEntity<Void> deleteCategory(
            Authentication authentication,
            @PathVariable @Positive(message = "Category id must be positive") Long id) {
        categoryService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one category",
            description = "Returns one category by id. Admin-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category detail"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    public ResponseEntity<CategoryResponse> getCategory(
            @PathVariable @Positive(message = "Category id must be positive") Long id) {
        return ResponseEntity.ok(categoryService.getById(id));
    }
}