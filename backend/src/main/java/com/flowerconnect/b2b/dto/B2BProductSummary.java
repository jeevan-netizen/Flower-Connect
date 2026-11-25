package com.flowerconnect.b2b.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Summary of a seller's orderable product: catalog price and currently
 * available stock (quantity minus reserved quantity).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BProductSummary {

    private Long productId;
    private String productName;
    private BigDecimal unitPrice;
    private String unit;
    private int availableStock;
}
