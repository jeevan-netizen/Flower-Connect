package com.flowerconnect.product.controller;

import com.flowerconnect.product.service.PublicProductService;
import com.flowerconnect.search.dto.PublicProductResponse;
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
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class PublicProductController {

    private final PublicProductService publicProductService;

    @GetMapping("/{id}")
    public ResponseEntity<PublicProductResponse> detail(
            @PathVariable Long id,
            @RequestParam @NotNull(message = "locationId is required") Long locationId) {

        PublicProductResponse response = publicProductService.productDetail(id, locationId);
        return ResponseEntity.ok(response);
    }
}
