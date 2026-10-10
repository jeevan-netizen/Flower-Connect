package com.flowerconnect.search.controller;

import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.search.dto.SearchResponse;
import com.flowerconnect.search.service.SearchService;
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

@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<PageResponse<SearchResponse>> search(
            @RequestParam @NotNull(message = "locationId is required") Long locationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        PageResponse<SearchResponse> response = searchService.search(
                locationId, q, category, priceMin, priceMax, sort, vendorId, page, size);
        return ResponseEntity.ok(response);
    }
}