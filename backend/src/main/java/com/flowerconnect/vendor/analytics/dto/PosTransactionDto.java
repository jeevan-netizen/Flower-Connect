package com.flowerconnect.vendor.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projection DTO for a POS transaction within a date range.
 * Used by {@code PosTransactionRepository.findByVendorIdAndCreatedAtBetween}
 * via a constructor expression in JPQL.
 */
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PACKAGE)
@lombok.AllArgsConstructor
public class PosTransactionDto {

    private Long transactionId;
    private String paymentMethod;
    private BigDecimal total;
    private LocalDateTime createdAt;
}
