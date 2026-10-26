package com.flowerconnect.pos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * POS daily/weekly report response (Phase 11, task 11.5).
 *
 * <p>POS revenue is commission-free and reported separately from marketplace revenue.
 * All amounts are server-side snapshots in DECIMAL(10,2) = BigDecimal, rounded HALF_UP.
 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class PosReportResponse {

    private LocalDate date;
    private String timezone;
    private int transactionCount;
    private BigDecimal totalRevenue;
    private List<PosReportResponse.PosPaymentBreakdown> breakdownByPaymentMethod;
    private List<PosReportResponse.TopProduct> topProducts;

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

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class TopProduct {
        private Long productId;
        private String productName;
        private int quantitySold;
        private BigDecimal revenue;
    }
}