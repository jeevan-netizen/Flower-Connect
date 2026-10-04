package com.flowerconnect.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body of {@code POST /api/v1/vendors/products/{productId}/inventory/stock-out}
 * (plan task 3.6).
 *
 * <p>A deliberate removal the vendor can explain at the time of the
 * call — units moved to another shop, samples taken, stock counted out
 * for a walk-in order that never completed. A loss that needs no
 * explanation is {@link StockWriteOffRequest}; a correction of the
 * recorded number is {@link StockAdjustmentRequest}.
 *
 * <p>Refused with 409 when the resulting quantity would fall below the
 * reserved quantity, i.e. when it would consume stock already promised
 * to pending orders (plan section 6.2).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockOutRequest {

    /** Units removed. Always positive; the movement delta is its negation. */
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private Integer quantity;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}