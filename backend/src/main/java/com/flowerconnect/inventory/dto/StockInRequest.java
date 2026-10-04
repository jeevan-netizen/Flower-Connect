package com.flowerconnect.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body of {@code POST /api/v1/vendors/products/{productId}/inventory/stock-in}
 * (plan task 3.6).
 *
 * <p>{@code reason} is optional here: receiving a delivery is a routine
 * positive event whose cause the vendor has already recorded on the
 * purchase. It is <em>not</em> optional for {@link StockAdjustmentRequest}
 * or {@link StockWriteOffRequest}, where the reason is the only
 * explanation of a discrepancy that will ever exist.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockInRequest {

    /** Units received. Always positive; the movement delta is derived from it. */
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private Integer quantity;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}