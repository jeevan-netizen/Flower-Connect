package com.flowerconnect.storefront.controller;

import com.flowerconnect.storefront.dto.StorefrontResponse;
import com.flowerconnect.storefront.service.StorefrontService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public vendor storefront API (plan task 4.5).
 *
 * <p>The one route under {@code /api/v1/vendors/**} that is deliberately
 * <b>not</b> behind {@code hasRole("FLORIST")}: it is anonymous reference
 * data, like {@code /api/v1/locations}, {@code /api/v1/discover} and
 * {@code /api/v1/search}. {@code SecurityConfig} carries the exception matcher
 * for it above the vendor namespace rule for exactly that reason.
 *
 * <p>Because the route is public it has no role to check and no approval
 * annotation to carry — the approval rule lives in {@code StorefrontService},
 * which returns a 404 for anything that is not an APPROVED vendor. It is also
 * the reason no {@code Authentication} parameter appears here: a request is
 * never tied to the caller, only to the path variable.
 *
 * <p>No {@code locationId} and no distance or fee estimate are part of this
 * contract. A storefront is browsed without a search origin; the vendor's own
 * {@code deliveryRadiusKm} is reported as-is, and the caller-facing distance
 * work belongs to discovery and search, which take an origin.
 */
@Validated
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class StorefrontController {

    private final StorefrontService storefrontService;

    @GetMapping("/{vendorId}/storefront")
    public ResponseEntity<StorefrontResponse> storefront(
            @PathVariable @Positive(message = "Vendor id must be positive") Long vendorId,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        StorefrontResponse response = storefrontService.storefront(vendorId, page, size);
        return ResponseEntity.ok(response);
    }
}
