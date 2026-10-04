package com.flowerconnect.inventory.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * One row of the vendor's low-stock list (plan task 3.6).
 *
 * <p>Carries the product id and name alongside the inventory numbers,
 * because the list is a work list: a vendor opening it needs to know
 * <em>what</em> to restock, not just that some row crossed a number.
 * A plain {@link InventorySummary} would force a second request per
 * row to resolve the product.
 *
 * <p>{@code lowStock} is always {@code true} here — the row exists
 * precisely because the condition holds — and is included so the
 * shape matches the {@code inventory} block of {@code ProductResponse}
 * and a client can reuse its rendering. The rule is
 * {@code available <= lowStockThreshold}, computed, never stored.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockProductResponse {

    private Long productId;
    private String productName;
    private int quantity;
    private int reservedQuantity;
    private int available;
    private int lowStockThreshold;
    private LocalDate expiryDate;
    private boolean lowStock;
}