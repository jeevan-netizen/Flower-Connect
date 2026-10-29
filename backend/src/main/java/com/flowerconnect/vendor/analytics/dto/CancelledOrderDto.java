package com.flowerconnect.vendor.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projection DTO for a cancelled order with its cancelled timestamp and total.
 * Used by {@code OrderRepository.findCancelledWithTimestampBetween} via a
 * constructor expression in JPQL.
 */
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PACKAGE)
@lombok.AllArgsConstructor
public class CancelledOrderDto {

    private Long orderId;
    private BigDecimal total;
    private LocalDateTime cancelledAt;
}
