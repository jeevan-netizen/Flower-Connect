package com.flowerconnect.geo.controller;

import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.dto.ServiceLocationResponse;
import com.flowerconnect.geo.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "Service locations", description = "Public read of service locations (no authentication)")
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    @Operation(summary = "List or search service locations",
            description = "Returns the hierarchical location list, or a paginated search when pincode/"
                    + "area filters are supplied. Public endpoint; no authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location list"),
            @ApiResponse(responseCode = "400", description = "Invalid pincode/page/size")
    })
    public ResponseEntity<?> getLocations(
            @Parameter(description = "6-digit pincode filter")
            @RequestParam(required = false)
            @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be a 6-digit number")
            String pincode,
            @Parameter(description = "Area name filter")
            @RequestParam(required = false)
            String area,
            @Parameter(description = "Zero-based page index")
            @RequestParam(required = false) @Min(0) Integer page,
            @Parameter(description = "Page size, clamped to 1..100")
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        if (pincode == null && area == null && page == null && size == null) {
            return ResponseEntity.ok(locationService.findAllHierarchical());
        }

        int safePage = page != null ? page : 0;
        int safeSize = size != null ? size : 20;

        return ResponseEntity.ok(locationService.search(pincode, area, safePage, safeSize));
    }
}