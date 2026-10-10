package com.flowerconnect.discovery.controller;

import com.flowerconnect.discovery.dto.DiscoveryResponse;
import com.flowerconnect.discovery.service.DiscoveryService;
import com.flowerconnect.geo.dto.PageResponse;
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
@RestController
@RequestMapping("/api/v1/discover")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @GetMapping
    public ResponseEntity<PageResponse<DiscoveryResponse>> discover(
            @RequestParam @NotNull(message = "locationId is required") Long locationId,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        PageResponse<DiscoveryResponse> response = discoveryService.discover(locationId, page, size);
        return ResponseEntity.ok(response);
    }
}