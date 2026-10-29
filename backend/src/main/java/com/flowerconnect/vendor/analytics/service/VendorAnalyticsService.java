package com.flowerconnect.vendor.analytics.service;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.pos.repository.PosTransactionRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.vendor.analytics.dto.CancelledOrderDto;
import com.flowerconnect.vendor.analytics.dto.OrderDeliveryDto;
import com.flowerconnect.vendor.analytics.dto.OrderDeliveryDto.OrderItemLineDto;
import com.flowerconnect.vendor.analytics.dto.PosTransactionDto;
import com.flowerconnect.vendor.analytics.dto.VendorAnalyticsDailyTrend;
import com.flowerconnect.vendor.analytics.dto.VendorAnalyticsOverviewResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vendor analytics service (plan Phase 12, task 12.5).
 *
 * <p>Computes vendor-scoped marketplace and POS analytics for a configurable
 * date range (default 30 days, maximum 365 days). All marketplace metrics use
 * {@code orders} that reached {@code DELIVERED} within the range, with the
 * delivered timestamp taken from {@code order_status_history}
 * ({@code to_status = 'DELIVERED'}), not {@code orders.created_at}. POS
 * revenue (Phase 11) is commission-free and reported in its own section; it is
 * never mixed into marketplace order counts, revenue, top products, or
 * repeat-customer rate.
 *
 * <p>Vendor isolation: the vendor is resolved from the JWT subject (account
 * email) on every call. Suspended vendors may read their historical analytics
 * -- this service deliberately does not check approval status.
 */
@Service
@Transactional(readOnly = true)
public class VendorAnalyticsService {

    public static final int DEFAULT_RANGE_DAYS = 30;
    public static final int MAX_RANGE_DAYS = 365;

    private final VendorProfileRepository vendorProfileRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PosTransactionRepository posTransactionRepository;
    private final Clock clock;

    public VendorAnalyticsService(
            VendorProfileRepository vendorProfileRepository,
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            PosTransactionRepository posTransactionRepository,
            Clock clock) {
        this.vendorProfileRepository = vendorProfileRepository;
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.posTransactionRepository = posTransactionRepository;
        this.clock = clock;
    }

    /**
     * Returns the vendor's analytics overview for the given date range. When
     * either bound is {@code null}, defaults to the trailing 30 days ending on
     * the vendor-local "today". The inclusive range width is capped at
     * {@link #MAX_RANGE_DAYS} days; exceeding it throws
     * {@code IllegalArgumentException}.
     */
    public VendorAnalyticsOverviewResponse overview(
            String vendorEmail, LocalDate fromDate, LocalDate toDate) {

        VendorProfile vendor = requireVendor(vendorEmail);
        ZoneId zone = resolveZoneId(vendor);
        LocalDate today = LocalDate.now(clock.withZone(zone));

        LocalDateRange range = normalizeRange(fromDate, toDate, today);

        // Bucket bounds in the vendor's timezone, converted to UTC wall time
        // for direct comparison against stored DATETIME(6) timestamps.
        LocalDateTime fromUtc = range.from().atStartOfDay(zone).toInstant().atZone(clock.getZone()).toLocalDateTime();
        LocalDateTime toUtcExclusive =
                range.to().plusDays(1).atStartOfDay(zone).toInstant().atZone(clock.getZone()).toLocalDateTime();

        Long vendorId = vendor.getId();

        // Delivered orders within range, using delivered timestamp from
        // order_status_history (to_status = DELIVERED).
        List<OrderDeliveryDto> deliveries = orderRepository.findDeliveredWithTimestampBetween(vendorId, fromUtc, toUtcExclusive);

        BigDecimal marketplaceRevenue = BigDecimal.ZERO;
        BigDecimal onlineRevenue = BigDecimal.ZERO;
        BigDecimal codRevenue = BigDecimal.ZERO;
        BigDecimal cancelledRevenue = BigDecimal.ZERO;
        int deliveredCount = 0;
        int onlineCount = 0;
        int codCount = 0;
        int cancelledCount = 0;

        Map<Long, Integer> customerOrderCount = new HashMap<>();
        Map<Long, ProductAggregate> productAggregates = new HashMap<>();

        for (OrderDeliveryDto d : deliveries) {
            deliveredCount++;
            marketplaceRevenue = marketplaceRevenue.add(d.getTotal());

            if ("ONLINE".equals(d.getPaymentMethod())) {
                onlineCount++;
                onlineRevenue = onlineRevenue.add(d.getTotal());
            } else {
                codCount++;
                codRevenue = codRevenue.add(d.getTotal());
            }

            customerOrderCount.merge(d.getCustomerId(), 1, Integer::sum);

            List<OrderItemLineDto> items = d.getItems();
            if (items != null) {
                for (OrderItemLineDto item : items) {
                    productAggregates.computeIfAbsent(item.getProductId(), pid -> new ProductAggregate(pid, item.getProductName()))
                            .add(item.getQuantity(), item.getLineTotal());
                }
            }
        }

        // Cancelled orders within range (cancel/reject transitions).
        List<CancelledOrderDto> cancelled = orderRepository.findCancelledWithTimestampBetween(vendorId, fromUtc, toUtcExclusive);
        for (CancelledOrderDto d : cancelled) {
            cancelledCount++;
            cancelledRevenue = cancelledRevenue.add(d.getTotal());
        }

        // Repeat-customer rate: customers with more than one delivered order
        // divided by total distinct delivered-order customers. Null-safe when
        // there are no delivered orders.
        Double repeatCustomerRate = null;
        int totalCustomers = customerOrderCount.size();
        if (totalCustomers > 0) {
            long repeaters = customerOrderCount.values().stream().filter(c -> c > 1).count();
            repeatCustomerRate = BigDecimal.valueOf(repeaters)
                    .divide(BigDecimal.valueOf(totalCustomers), 4, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // Top products by units and by revenue.
        List<VendorAnalyticsOverviewResponse.VendorTopProduct> byUnits = productAggregates.values().stream()
                .sorted(Comparator.comparingInt((ProductAggregate a) -> a.quantitySold).reversed()
                        .thenComparingLong(a -> a.productId))
                .limit(10)
                .map(a -> new VendorAnalyticsOverviewResponse.VendorTopProduct(
                        a.productId, a.productName, a.quantitySold, a.revenue))
                .collect(Collectors.toList());

        List<VendorAnalyticsOverviewResponse.VendorTopProduct> byRevenue = productAggregates.values().stream()
                .sorted(Comparator.comparing((ProductAggregate a) -> a.revenue).reversed()
                        .thenComparingLong(a -> a.productId))
                .limit(10)
                .map(a -> new VendorAnalyticsOverviewResponse.VendorTopProduct(
                        a.productId, a.productName, a.quantitySold, a.revenue))
                .collect(Collectors.toList());

        // POS section -- commission-free, never mixed into marketplace metrics.
        VendorAnalyticsOverviewResponse.PosSection pos = posSection(vendorId, range.from(), range.to(), zone);

        return VendorAnalyticsOverviewResponse.builder()
                .deliveredOrders(deliveredCount)
                .marketplaceRevenue(marketplaceRevenue.setScale(2, RoundingMode.HALF_UP))
                .onlineOrders(onlineCount)
                .onlineRevenue(onlineRevenue.setScale(2, RoundingMode.HALF_UP))
                .codOrders(codCount)
                .codRevenue(codRevenue.setScale(2, RoundingMode.HALF_UP))
                .cancelledOrders(cancelledCount)
                .cancelledRevenue(cancelledRevenue.setScale(2, RoundingMode.HALF_UP))
                .repeatCustomerRate(repeatCustomerRate)
                .topProductsByUnits(byUnits)
                .topProductsByRevenue(byRevenue)
                .pos(pos)
                .build();
    }

    /**
     * Returns the daily trend series for the vendor's delivered orders within
     * the range. Buckets are computed in the vendor's timezone; days with no
     * activity are zero-filled. The series is inclusive of both bounds.
     */
    public List<VendorAnalyticsDailyTrend> dailyTrends(
            String vendorEmail, LocalDate fromDate, LocalDate toDate) {

        VendorProfile vendor = requireVendor(vendorEmail);
        ZoneId zone = resolveZoneId(vendor);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDateRange range = normalizeRange(fromDate, toDate, today);

        LocalDateTime fromUtc = range.from().atStartOfDay(zone).toInstant().atZone(clock.getZone()).toLocalDateTime();
        LocalDateTime toUtcExclusive =
                range.to().plusDays(1).atStartOfDay(zone).toInstant().atZone(clock.getZone()).toLocalDateTime();

        Long vendorId = vendor.getId();
        List<OrderDeliveryDto> deliveries = orderRepository.findDeliveredWithTimestampBetween(vendorId, fromUtc, toUtcExclusive);

        // Aggregate delivered orders per local date.
        Map<LocalDate, BigDecimal> revenueByDate = new HashMap<>();
        Map<LocalDate, Integer> countByDate = new HashMap<>();
        for (OrderDeliveryDto d : deliveries) {
            LocalDate localDate = d.getDeliveredAt().atZone(zone).toLocalDate();
            revenueByDate.merge(localDate, d.getTotal(), BigDecimal::add);
            countByDate.merge(localDate, 1, Integer::sum);
        }

        // Zero-fill every day in the range.
        List<VendorAnalyticsDailyTrend> series = new ArrayList<>();
        LocalDate cursor = range.from();
        while (!cursor.isAfter(range.to())) {
            series.add(new VendorAnalyticsDailyTrend(
                    cursor,
                    countByDate.getOrDefault(cursor, 0),
                    revenueByDate.getOrDefault(cursor, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)));
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    /* ---------------------------------------------------------------- */
    // Private helpers

    private VendorProfile requireVendor(String vendorEmail) {
        return vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for " + vendorEmail));
    }

    private ZoneId resolveZoneId(VendorProfile vendor) {
        String tz = vendor.getServiceLocation() != null
                ? vendor.getServiceLocation().getTimezone()
                : null;
        return tz != null ? ZoneId.of(tz) : ZoneId.of("UTC");
    }

    private LocalDateRange normalizeRange(LocalDate fromDate, LocalDate toDate, LocalDate today) {
        LocalDate to = toDate != null ? toDate : today;
        LocalDate from = fromDate != null ? fromDate : to.minusDays(DEFAULT_RANGE_DAYS - 1);

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range may not exceed " + MAX_RANGE_DAYS + " days");
        }
        return new LocalDateRange(from, to);
    }

    private VendorAnalyticsOverviewResponse.PosSection posSection(
            Long vendorId, LocalDate fromDate, LocalDate toDate, ZoneId zone) {

        LocalDateTime fromUtc = fromDate.atStartOfDay(zone).toInstant().atZone(clock.getZone()).toLocalDateTime();
        LocalDateTime toUtcExclusive =
                toDate.plusDays(1).atStartOfDay(zone).toInstant().atZone(clock.getZone()).toLocalDateTime();

        List<PosTransactionDto> rows = posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(vendorId, fromUtc, toUtcExclusive);

        int transactionCount = rows.size();
        BigDecimal totalRevenue = rows.stream()
                .map(PosTransactionDto::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, PosPaymentBreakdownAccum> byMethod = new HashMap<>();
        for (PosTransactionDto r : rows) {
            byMethod.computeIfAbsent(r.getPaymentMethod(), m -> new PosPaymentBreakdownAccum(m))
                    .add(r.getTotal());
        }

        List<VendorAnalyticsOverviewResponse.PosSection.PosPaymentBreakdown> breakdown =
                byMethod.values().stream()
                        .map(a -> new VendorAnalyticsOverviewResponse.PosSection.PosPaymentBreakdown(
                                a.method, a.count, a.total.setScale(2, RoundingMode.HALF_UP)))
                        .sorted(Comparator.comparing(
                                            (VendorAnalyticsOverviewResponse.PosSection.PosPaymentBreakdown b) -> b.getRevenue())
                                        .reversed())
                        .collect(Collectors.toList());

        return new VendorAnalyticsOverviewResponse.PosSection(transactionCount, totalRevenue, breakdown);
    }

    /* ---------------------------------------------------------------- */
    // Value types

    private static final class ProductAggregate {
        private final Long productId;
        private final String productName;
        private int quantitySold;
        private BigDecimal revenue;

        ProductAggregate(Long productId, String productName) {
            this.productId = productId;
            this.productName = productName;
            this.quantitySold = 0;
            this.revenue = BigDecimal.ZERO;
        }

        void add(int quantity, BigDecimal lineTotal) {
            this.quantitySold += quantity;
            this.revenue = this.revenue.add(lineTotal);
        }
    }

    private static final class PosPaymentBreakdownAccum {
        private final String method;
        private int count;
        private BigDecimal total;

        PosPaymentBreakdownAccum(String method) {
            this.method = method;
            this.count = 0;
            this.total = BigDecimal.ZERO;
        }

        void add(BigDecimal amount) {
            this.count++;
            this.total = this.total.add(amount);
        }
    }

    private record LocalDateRange(LocalDate from, LocalDate to) {
    }
}
