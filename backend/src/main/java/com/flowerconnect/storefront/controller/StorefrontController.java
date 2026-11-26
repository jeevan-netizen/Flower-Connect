package com.flowerconnect.storefront.controller;

import com.flowerconnect.storefront.dto.StorefrontResponse;
import com.flowerconnect.storefront.service.StorefrontService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Vendor storefront", description = "Public vendor storefront (no authentication)")
@RestController
@RequestMapping("/api/v1/vendors")
public class StorefrontController {

    private final StorefrontService storefrontService;

    @GetMapping("/{id}/storefront")
    @Operation(summary = "Get a vendor's storefront",
            description = "Public. Returns the vendor's public profile, weekly hours and a "
                    + "paginated product list. No delivery-radius filter is applied.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Storefront"),
            @ApiResponse(responseCode = "404", description = "Vendor not found, not approved, or not accepting orders")
    })
    public ResponseEntity<StorefrontResponse> storefront(
            @PathVariable Long id,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        StorefrontResponse response = storefrontService.storefront(id, page, size);
        return ResponseEntity.ok(response);
    }
}
