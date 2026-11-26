package com.flowerconnect.analytics.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin analytics overview response (plan Phase 12, task 12.4).
 *
 * <p>All monetary figures use {@code BigDecimal} and are HALF_UP to 2 decimals
 * (INR). The dispute rate is {@code null} when the denominator (delivered
 * orders) is zero.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsOverviewResponse {

    /** Number of delivered marketplace orders in the range. */
    private Long totalDeliveredOrders;

    /** Sum of {@code orders.total} for delivered orders in the range. */
    private BigDecimal marketplaceSales;

    /** Sum of commission + platform fees for delivered orders in the range. */
    private BigDecimal platformRevenue;

    /** Vendors with status APPROVED and accepting_orders = true. */
    private Long activeVendors;

    /** Active customers (status ACTIVE) with at least one delivered order in the range. */
    private Long activeCustomers;

    /** Top vendors by delivered marketplace sales, newest/revenue first. */
    private List<AnalyticsTopVendor> topVendors;

    /** Distinct disputed delivered orders / delivered orders, or null when zero orders. */
    private BigDecimal disputeRate;
}
