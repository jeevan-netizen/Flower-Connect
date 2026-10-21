package com.flowerconnect.storefront.controller;

import com.flowerconnect.storefront.dto.StorefrontResponse;
import com.flowerconnect.storefront.service.StorefrontService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public vendor storefront (plan task 4.5).
 *
 * <p>Mounted under {@code /api/v1/vendors/{id}/storefront} with a public GET
 * exception in {@code SecurityConfig}; all other vendor routes remain
 * {@code hasRole("FLORIST")}.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/vendors")
public class StorefrontController {

    private final StorefrontService storefrontService;

    @GetMapping("/{id}/storefront")
    public ResponseEntity<StorefrontResponse> storefront(
            @PathVariable Long id,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        StorefrontResponse response = storefrontService.storefront(id, page, size);
        return ResponseEntity.ok(response);
    }
}
