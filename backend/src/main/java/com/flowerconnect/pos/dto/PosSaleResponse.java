package com.flowerconnect.pos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Receipt response of {@code POST /api/v1/pos/sales} and
 * {@code GET /api/v1/pos/sales/{id}} (plan Phase 11, task 11.2).
 *
 * <p>All monetary amounts are server-side snapshots, formatted as
 * two-decimal strings to avoid floating-point ambiguity on the wire.
 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class PosSaleResponse {

    private Long transactionId;
    private String vendorName;
    private LocalDateTime createdAt;
    private String paymentMethod;
    private List<PosSaleLineResponse> lines;
    private BigDecimal total;

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class PosSaleLineResponse {
        private Long productId;
        private String productNameSnapshot;
        private BigDecimal unitPriceSnapshot;
        private Integer quantity;
        private BigDecimal lineTotal;
    }
}
