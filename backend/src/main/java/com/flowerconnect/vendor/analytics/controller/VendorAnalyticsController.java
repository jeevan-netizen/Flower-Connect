package com.flowerconnect.vendor.analytics.controller;

import com.flowerconnect.security.jwt.JwtService;
import com.flowerconnect.vendor.analytics.dto.VendorAnalyticsDailyTrend;
import com.flowerconnect.vendor.analytics.dto.VendorAnalyticsOverviewResponse;
import com.flowerconnect.vendor.analytics.service.VendorAnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vendor analytics controller (plan Phase 12, task 12.5).
 *
 * <p>Endpoints live under `/api/v1/vendor/analytics/**`, which `SecurityConfig`
 * matches with `hasRole("FLORIST")`. The vendor is resolved from the JWT
 * subject (account email); no vendor id is accepted from the request, so a
 * vendor can only read their own analytics. Suspended vendors may read their
 * historical analytics -- this controller deliberately does not gate on
 * approval status (D-6).
 */
@RestController
@RequestMapping("/api/v1/vendor/analytics")
public class VendorAnalyticsController {

    public static final int DEFAULT_RANGE_DAYS = 30;
    public static final int MAX_RANGE_DAYS = 365;

    private final VendorAnalyticsService analyticsService;
    private final JwtService jwtService;

    public VendorAnalyticsController(
            VendorAnalyticsService analyticsService,
            JwtService jwtService) {
        this.analyticsService = analyticsService;
        this.jwtService = jwtService;
    }

    /**
     * Vendor analytics overview for a date range.
     *
     * <p>Default range: trailing 30 days ending on vendor-local today.
     * Maximum range: 365 days. `fromDate` must not be after `toDate`.
     *
     * Marketplace metrics use delivered orders (timestamp from
     * {@code order_status_history}, {@code to_status = 'DELIVERED'}). POS
     * revenue (Phase 11) is in a separate section and never mixed into
     * marketplace counts or revenue.
     */
    @GetMapping("/overview")
    public ResponseEntity<VendorAnalyticsOverviewResponse> overview(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        String email = authentication.getName();
        VendorAnalyticsOverviewResponse result = analyticsService.overview(email, fromDate, toDate);
        return ResponseEntity.ok(result);
    }

    /**
     * Vendor daily trend series for a date range. Buckets are computed in the
     * vendor's service-location timezone; days with no activity are
     * zero-filled. Same range rules as {@link #overview}.
     */
    @GetMapping("/daily-trends")
    public ResponseEntity<List<VendorAnalyticsDailyTrend>> dailyTrends(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        String email = authentication.getName();
        List<VendorAnalyticsDailyTrend> result = analyticsService.dailyTrends(email, fromDate, toDate);
        return ResponseEntity.ok(result);
    }
}
