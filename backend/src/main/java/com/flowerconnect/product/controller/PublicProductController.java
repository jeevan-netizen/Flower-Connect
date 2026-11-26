package com.flowerconnect.product.controller;

import com.flowerconnect.product.service.PublicProductService;
import com.flowerconnect.search.dto.PublicProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public product detail (plan task 4.9 backend).
 *
 * <p>{@code locationId} is required; missing or unknown values return
 * {@code 400 VALIDATION_FAILED}. Ineligible products return a non-disclosing
 * {@code 404}.
 */
@Validated
@Tag(name = "Public product", description = "Public product detail (read-only, no authentication)")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class PublicProductController {

    private final PublicProductService publicProductService;

    @GetMapping("/{id}")
    @Operation(summary = "Get a product's public detail",
            description = "Returns a product's public detail for a chosen delivery location. "
                    + "Public endpoint; no authentication required. Ineligible products (unknown, "
                    + "inactive, sold out, unapproved vendor, outside radius) return a non-disclosing 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product detail"),
            @ApiResponse(responseCode = "400", description = "locationId is required"),
            @ApiResponse(responseCode = "404", description = "Product not found or not deliverable to the location")
    })
    public ResponseEntity<PublicProductResponse> detail(
            @PathVariable Long id,
            @RequestParam @NotNull(message = "locationId is required") Long locationId) {

        PublicProductResponse response = publicProductService.productDetail(id, locationId);
        return ResponseEntity.ok(response);
    }
}
