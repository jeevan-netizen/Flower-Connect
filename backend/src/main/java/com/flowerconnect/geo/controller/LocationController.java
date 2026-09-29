package com.flowerconnect.geo.controller;

import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.dto.ServiceLocationResponse;
import com.flowerconnect.geo.service.LocationService;
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
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public ResponseEntity<?> getLocations(
            @RequestParam(required = false)
            @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be a 6-digit number")
            String pincode,
            @RequestParam(required = false)
            String area,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        if (pincode == null && area == null && page == null && size == null) {
            return ResponseEntity.ok(locationService.findAllHierarchical());
        }

        int safePage = page != null ? page : 0;
        int safeSize = size != null ? size : 20;

        return ResponseEntity.ok(locationService.search(pincode, area, safePage, safeSize));
    }
}