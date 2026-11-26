package com.flowerconnect.pos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Today's POS summary for a vendor (plan Phase 11, task 11.5).
 *
 * <p>POS revenue is commission-free and reported separately from
 * marketplace revenue. Figures are labelled "commission-free" by the
 * consumer; this response carries only the raw POS data.
 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class PosSummaryResponse {

    private LocalDate date;
    private String timezone;
    private int transactionCount;
    private BigDecimal totalRevenue;
    private List<PosPaymentMethodBreakdown> breakdownByPaymentMethod;

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class PosPaymentMethodBreakdown {
        private String paymentMethod;
        private int transactionCount;
        private BigDecimal revenue;
    }
}
