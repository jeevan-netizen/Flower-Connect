package com.flowerconnect.analytics.controller;

import com.flowerconnect.analytics.dto.AnalyticsDailyTrend;
import com.flowerconnect.analytics.dto.AnalyticsOverviewResponse;
import com.flowerconnect.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin analytics endpoints (plan Phase 12, task 12.4).
 *
 * <p>All endpoints require `ROLE_ADMIN`; `SecurityConfig` matches
 * `/api/v1/admin/analytics/**` with `hasRole("ADMIN")`. POS revenue
 * (Phase 11) is excluded from all marketplace metrics (D-66).
 */
@Tag(name = "Admin analytics", description = "Platform-wide sales and platform-revenue analytics")
@Validated
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Overview metrics for a date range: delivered orders, marketplace sales,
     * platform revenue, active vendors/customers, top vendors, dispute rate.
     *
     * Defaults to the last 30 days when {@code fromDate} and {@code toDate}
     * are omitted. Maximum range is 365 days.
     */
    @Operation(summary = "Analytics overview",
            description = "Returns summary analytics for a date range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Overview metrics"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> overview(
            @Parameter(description = "Inclusive start date (ISO-8601), defaults to 30 days ago")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Inclusive end date (ISO-8601), defaults to today")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return ResponseEntity.ok(analyticsService.overview());
        }
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate must both be supplied");
        }
        return ResponseEntity.ok(analyticsService.overview(fromDate, toDate));
    }

    /**
     * Daily delivered-order trend series for a date range, zero-filled for days
     * with no activity. Buckets are in UTC (platform-wide admin analytics).
     *
     * Defaults to the last 30 days when {@code fromDate} and {@code toDate}
     * are omitted. Maximum range is 365 days.
     */
    @Operation(summary = "Daily trends",
            description = "Returns a daily time series of delivered orders and revenue.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daily trend series"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    @GetMapping("/daily-trends")
    public ResponseEntity<List<AnalyticsDailyTrend>> dailyTrends(
            @Parameter(description = "Inclusive start date (ISO-8601), defaults to 30 days ago")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Inclusive end date (ISO-8601), defaults to today")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(29);
            return ResponseEntity.ok(analyticsService.dailyTrends(from, to));
        }
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate must both be supplied");
        }
        return ResponseEntity.ok(analyticsService.dailyTrends(fromDate, toDate));
    }
}
