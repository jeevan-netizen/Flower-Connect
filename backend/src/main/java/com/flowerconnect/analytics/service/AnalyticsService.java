package com.flowerconnect.analytics.service;

import com.flowerconnect.analytics.dto.AnalyticsDailyTrend;
import com.flowerconnect.analytics.dto.AnalyticsOverviewResponse;
import com.flowerconnect.analytics.dto.AnalyticsTopVendor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin analytics service (plan Phase 12, task 12.4).
 *
 * <p>Metric definitions (approved for Phase 12):
 * <ul>
 *   <li><b>Delivered marketplace sales</b>: sum of {@code orders.total} for
 *       delivered orders, filtered by the delivered timestamp from
 *       {@code order_status_history} ({@code to_status = 'DELIVERED'}).</li>
 *   <li><b>Platform revenue</b>: sum of {@code commissions.commission_amount}
 *       plus sum of {@code orders.platform_fee} for delivered orders in range
 *       (Phase 10 commission data).</li>
 *   <li><b>Active vendor</b>: {@code vendor_profiles.status = APPROVED} and
 *       {@code accepting_orders = true}.</li>
 *   <li><b>Active customer</b>: {@code users.status = ACTIVE} with at least
 *       one delivered order in the selected range.</li>
 *   <li><b>Dispute rate</b>: distinct delivered orders with a dispute divided
 *       by delivered orders in range; {@code null} when the denominator is
 *       zero.</li>
 *   <li><b>Daily buckets</b>: UTC for platform-wide admin analytics (no
 *       global business timezone is configured in v1).</li>
 *   <li><b>Date range</b>: last 30 days by default, maximum 365 days, with
 *       zero-filled daily series.</li>
 * </ul>
 *
 * <p>POS revenue (Phase 11) is commission-free and outside the marketplace
 * ledger (D-66): it is excluded from all marketplace counts, sales, and
 * repeat-customer metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 365;
    private static final int TOP_VENDOR_LIMIT = 10;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Default range: last 30 days.
     */
    public AnalyticsOverviewResponse overview() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(DEFAULT_RANGE_DAYS - 1);
        return overview(from, to);
    }

    /**
     * Overview metrics for an inclusive date range [fromDate, toDate].
     */
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(LocalDate fromDate, LocalDate toDate) {
        validateRange(fromDate, toDate);

        String fromSql = fromDate.toString();
        String toSql = toDate.plusDays(1).toString(); // exclusive upper bound

        // Delivered orders in range, using the delivered timestamp from history.
        String deliveredBase =
                "FROM orders o " +
                        "JOIN order_status_history osh ON osh.order_id = o.id " +
                        "AND osh.to_status = 'DELIVERED' " +
                        "WHERE o.status = 'DELIVERED' " +
                        "AND osh.created_at >= ? " +
                        "AND osh.created_at < ?";

        Long totalDeliveredOrders =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) " + deliveredBase,
                        Long.class, fromSql, toSql);

        BigDecimal marketplaceSales =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(o.total), 0) " + deliveredBase,
                        BigDecimal.class, fromSql, toSql);

        // Platform revenue: commission + platform fees for delivered orders.
        BigDecimal platformRevenue =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(c.commission_amount), 0) + " +
                                "COALESCE(SUM(o.platform_fee), 0) " +
                                "FROM orders o " +
                                "JOIN order_status_history osh ON osh.order_id = o.id " +
                                "AND osh.to_status = 'DELIVERED' " +
                                "LEFT JOIN commissions c ON c.order_id = o.id " +
                                "WHERE o.status = 'DELIVERED' " +
                                "AND osh.created_at >= ? " +
                                "AND osh.created_at < ?",
                        BigDecimal.class, fromSql, toSql);

        // Active vendors: APPROVED and accepting orders.
        Long activeVendors =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM vendor_profiles " +
                                "WHERE status = 'APPROVED' AND accepting_orders = b'1'",
                        Long.class);

        // Active customers: ACTIVE users with at least one delivered order in range.
        Long activeCustomers =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(DISTINCT o.user_id) " + deliveredBase +
                                " JOIN users u ON u.id = o.user_id AND u.status = 'ACTIVE'",
                        Long.class, fromSql, toSql);

        // Top vendors by delivered marketplace sales.
        List<AnalyticsTopVendor> topVendors =
                jdbcTemplate.query(
                        "SELECT o.vendor_id, vp.business_name, " +
                                "COALESCE(SUM(o.total), 0) AS sales, COUNT(*) AS order_count " +
                                deliveredBase +
                                " JOIN vendor_profiles vp ON vp.id = o.vendor_id " +
                                "GROUP BY o.vendor_id, vp.business_name " +
                                "ORDER BY sales DESC " +
                                "LIMIT ?",
                        (rs, rowNum) -> AnalyticsTopVendor.builder()
                                .vendorId(rs.getLong("vendor_id"))
                                .businessName(rs.getString("business_name"))
                                .marketplaceSales(rs.getBigDecimal("sales"))
                                .deliveredOrders(rs.getLong("order_count"))
                                .build(),
                        fromSql, toSql, TOP_VENDOR_LIMIT);

        // Dispute rate: distinct disputed delivered orders / delivered orders.
        BigDecimal disputeRate = null;
        if (totalDeliveredOrders != null && totalDeliveredOrders > 0) {
            Long disputedOrders =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(DISTINCT d.order_id) " +
                                    "FROM disputes d " +
                                    "JOIN orders o ON o.id = d.order_id " +
                                    "JOIN order_status_history osh ON osh.order_id = o.id " +
                                    "AND osh.to_status = 'DELIVERED' " +
                                    "WHERE o.status = 'DELIVERED' " +
                                    "AND osh.created_at >= ? " +
                                    "AND osh.created_at < ?",
                            Long.class, fromSql, toSql);
            disputeRate = BigDecimal.valueOf(disputedOrders)
                    .divide(BigDecimal.valueOf(totalDeliveredOrders), 4, RoundingMode.HALF_UP);
        }

        return AnalyticsOverviewResponse.builder()
                .totalDeliveredOrders(totalDeliveredOrders != null ? totalDeliveredOrders : 0L)
                .marketplaceSales(marketplaceSales != null ? marketplaceSales : BigDecimal.ZERO)
                .platformRevenue(platformRevenue != null ? platformRevenue : BigDecimal.ZERO)
                .activeVendors(activeVendors != null ? activeVendors : 0L)
                .activeCustomers(activeCustomers != null ? activeCustomers : 0L)
                .topVendors(topVendors != null ? topVendors : List.of())
                .disputeRate(disputeRate)
                .build();
    }

    /**
     * Daily delivered-order trends for an inclusive date range, zero-filled for
     * days with no activity. Bucketed in UTC (platform-wide admin analytics).
     */
    @Transactional(readOnly = true)
    public List<AnalyticsDailyTrend> dailyTrends(LocalDate fromDate, LocalDate toDate) {
        validateRange(fromDate, toDate);

        String fromSql = fromDate.toString();
        String toSql = toDate.plusDays(1).toString();

        Map<LocalDate, AnalyticsDailyTrend> byDate = new LinkedHashMap<>();
        LocalDate day = fromDate;
        while (!day.isAfter(toDate)) {
            byDate.put(day, AnalyticsDailyTrend.builder()
                    .date(day)
                    .orderCount(0L)
                    .revenue(BigDecimal.ZERO)
                    .build());
            day = day.plusDays(1);
        }

        List<AnalyticsDailyTrend> rows =
                jdbcTemplate.query(
                        "SELECT DATE(osh.created_at) AS trend_date, " +
                                "COUNT(*) AS order_count, " +
                                "COALESCE(SUM(o.total), 0) AS revenue " +
                                "FROM orders o " +
                                "JOIN order_status_history osh ON osh.order_id = o.id " +
                                "AND osh.to_status = 'DELIVERED' " +
                                "WHERE o.status = 'DELIVERED' " +
                                "AND osh.created_at >= ? " +
                                "AND osh.created_at < ? " +
                                "GROUP BY DATE(osh.created_at) " +
                                "ORDER BY trend_date",
                        (rs, rowNum) -> AnalyticsDailyTrend.builder()
                                .date(rs.getObject("trend_date", LocalDate.class))
                                .orderCount(rs.getLong("order_count"))
                                .revenue(rs.getBigDecimal("revenue"))
                                .build(),
                        fromSql, toSql);

        for (AnalyticsDailyTrend row : rows) {
            byDate.put(row.getDate(), row);
        }

        return new ArrayList<>(byDate.values());
    }

    private void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Date range exceeds " + MAX_RANGE_DAYS + " days");
        }
    }
}
