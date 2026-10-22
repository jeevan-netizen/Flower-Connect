package com.flowerconnect.vendor.dto;

import com.flowerconnect.order.domain.Order.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Today's order summary for a vendor (plan Phase 6, task 6.4).
 *
 * <p>All monetary values use {@link BigDecimal}. {@code todayTotalValue}
 * excludes cancelled and rejected orders; {@code expectedCodCash} is the
 * cash the vendor should have collected on today's delivered COD orders.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorOrderSummaryResponse {

    private LocalDate today;
    private Map<OrderStatus, Long> ordersByStatus;
    private long todayOrderCount;
    private BigDecimal todayTotalValue;
    private BigDecimal expectedCodCash;

    /**
     * Builds an empty summary for a day with no orders.
     */
    public static VendorOrderSummaryResponse empty(LocalDate today) {
        VendorOrderSummaryResponse summary = new VendorOrderSummaryResponse();
        summary.today = today;
        summary.ordersByStatus = new LinkedHashMap<>();
        summary.todayOrderCount = 0;
        summary.todayTotalValue = BigDecimal.ZERO;
        summary.expectedCodCash = BigDecimal.ZERO;
        return summary;
    }
}
