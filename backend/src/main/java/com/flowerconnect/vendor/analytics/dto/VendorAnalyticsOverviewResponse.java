package com.flowerconnect.vendor.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Vendor analytics overview response (plan Phase 12, task 12.5).
 *
 * <p>All marketplace metrics are computed from {@code orders} that reached
 * {@code DELIVERED} within the requested date range, using the delivered
 * timestamp from {@code order_status_history} (not {@code orders.created_at}).
 * POS revenue (Phase 11) is commission-free and reported in its own section;
 * it is never mixed into marketplace order counts, revenue, top products, or
 * repeat-customer rate.
 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class VendorAnalyticsOverviewResponse {

    private int deliveredOrders;
    private BigDecimal marketplaceRevenue;
    private int onlineOrders;
    private BigDecimal onlineRevenue;
    private int codOrders;
    private BigDecimal codRevenue;
    private int cancelledOrders;
    private BigDecimal cancelledRevenue;
    private Double repeatCustomerRate;
    private List<VendorTopProduct> topProductsByUnits;
    private List<VendorTopProduct> topProductsByRevenue;

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class VendorTopProduct {
        private Long productId;
        private String productName;
        private int quantitySold;
        private BigDecimal revenue;
    }

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class PosSection {
        private int transactionCount;
        private BigDecimal totalRevenue;
        private List<PosPaymentBreakdown> breakdownByPaymentMethod;

        @lombok.Getter
        @lombok.Setter
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        @lombok.Builder
        public static class PosPaymentBreakdown {
            private String paymentMethod;
            private int transactionCount;
            private BigDecimal revenue;
        }
    }

    private PosSection pos;
}
