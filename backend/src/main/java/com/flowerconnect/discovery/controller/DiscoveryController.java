package com.flowerconnect.discovery.controller;

import com.flowerconnect.discovery.dto.DiscoveryResponse;
import com.flowerconnect.discovery.service.DiscoveryService;
import com.flowerconnect.geo.dto.PageResponse;
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

@Validated
@Tag(name = "Discovery", description = "Public vendor discovery for a delivery location (no authentication)")
@RestController
@RequestMapping("/api/v1/discover")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @GetMapping
    @Operation(summary = "Discover eligible vendors",
            description = "Returns approved vendors accepting orders within their delivery radius "
                    + "of the given location, with distance and estimated delivery fee. Public "
                    + "endpoint; no authentication required. locationId is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discovery page"),
            @ApiResponse(responseCode = "400", description = "locationId is required or unknown")
    })
    public ResponseEntity<PageResponse<DiscoveryResponse>> discover(
            @Parameter(description = "Service location id (required)")
            @RequestParam @NotNull(message = "locationId is required") Long locationId,
            @Parameter(description = "Zero-based page index")
            @RequestParam(required = false) @Min(0) Integer page,
            @Parameter(description = "Page size, clamped to 1..100")
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        PageResponse<DiscoveryResponse> response = discoveryService.discover(locationId, page, size);
        return ResponseEntity.ok(response);
    }
}