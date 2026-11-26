package com.flowerconnect.search.controller;

import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.search.dto.PublicProductResponse;
import com.flowerconnect.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Public product search (plan task 4.4).
 *
 * <p>{@code locationId} is required; missing or unknown values return
 * {@code 400 VALIDATION_FAILED}. Results are limited to active, in-stock
 * products from approved, accepting vendors within their delivery radius.
 */
@Validated
@Tag(name = "Product search", description = "Public product search for a delivery location (no authentication)")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "Search products",
            description = "Public. Returns active, in-stock products from approved, "
                    + "accepting vendors within their delivery radius of the given location. "
                    + "locationId is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results page"),
            @ApiResponse(responseCode = "400", description = "locationId is required or unknown")
    })
    public ResponseEntity<PageResponse<PublicProductResponse>> search(
            @RequestParam @NotNull(message = "locationId is required") Long locationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        PageResponse<PublicProductResponse> response = searchService.search(
                locationId, q, category, priceMin, priceMax, sort, vendorId, page, size);
        return ResponseEntity.ok(response);
    }
}
