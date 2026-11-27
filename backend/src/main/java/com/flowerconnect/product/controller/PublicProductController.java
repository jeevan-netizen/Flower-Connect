package com.flowerconnect.product.controller;

import com.flowerconnect.product.service.PublicProductService;
import com.flowerconnect.search.dto.PublicProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

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

    @GetMapping("/{id}/images/{imageId}")
    @Operation(summary = "Serve a product image",
            description = "Public. Returns the stored image bytes with the image's recorded MIME type. "
                    + "The same visibility policy as product detail applies: only images attached to "
                    + "products that are active, in stock, sold by an approved and accepting vendor, "
                    + "and within that vendor's delivery radius of the given location are served. "
                    + "A missing image or an ineligible product is a non-disclosing 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image bytes"),
            @ApiResponse(responseCode = "400", description = "locationId is required"),
            @ApiResponse(responseCode = "404", description = "Image not found, or product not deliverable to the location")
    })
    public ResponseEntity<byte[]> image(
            @PathVariable @Positive(message = "Product id must be positive") Long id,
            @PathVariable @Positive(message = "Image id must be positive") Long imageId,
            @RequestParam @NotNull(message = "locationId is required") Long locationId) {

        PublicProductService.ImageData data = publicProductService.productImage(id, imageId, locationId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(data.content());
    }
}
