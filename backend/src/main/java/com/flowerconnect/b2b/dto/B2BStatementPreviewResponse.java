package com.flowerconnect.b2b.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of previewing the orders eligible for a statement: the orders plus
 * their combined total.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BStatementPreviewResponse {

    private List<B2BStatementOrderSummary> orders;
    private BigDecimal totalAmount;
}
