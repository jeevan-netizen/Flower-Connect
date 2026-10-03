package com.flowerconnect.catalog.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * Embedded stock summary of a product (plan task 3.3).
 *
 * <p>{@code available} is {@code quantity − reservedQuantity}:
 * what a customer can still buy. {@code lowStock} is true when
 * the available quantity has fallen to or below the vendor's
 * threshold, which is what the low-stock badges and the
 * low-stock list (plan task 3.6) are driven by.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySummary {

    private Long productId;
    private int quantity;
    private int reservedQuantity;
    private int available;
    private int lowStockThreshold;
    private LocalDate expiryDate;
    private boolean lowStock;
}
