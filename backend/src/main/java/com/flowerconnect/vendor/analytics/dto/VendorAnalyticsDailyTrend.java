package com.flowerconnect.vendor.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One daily trend data point for vendor analytics (plan Phase 12, task 12.5).
 *
 * <p>Buckets are computed in the vendor's service-location timezone (UTC
 * fallback), using delivered order timestamps. Days with no activity are
 * zero-filled.
 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class VendorAnalyticsDailyTrend {

    private LocalDate date;
    private int orderCount;
    private BigDecimal revenue;
}
