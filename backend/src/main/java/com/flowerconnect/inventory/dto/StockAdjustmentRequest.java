package com.flowerconnect.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body of {@code POST /api/v1/vendors/products/{productId}/inventory/adjustments}
 * (plan task 3.6: "adjustment with reason").
 *
 * <p>{@code quantity} is a <em>signed</em> correction rather than an
 * absolute level: positive raises the recorded quantity, negative
 * lowers it. That is what {@code stock_movements.quantity_delta} means
 * for an {@code ADJUSTMENT} row (see {@code V11__stock_movements.sql}),
 * so a correction is recorded in the log with the same sign convention
 * as every other movement.
 *
 * <p>Zero is rejected. It is a valid-looking value that would write a
 * movement row recording no change, and the log records changes only —
 * the same reasoning that stops {@code ProductService.create} writing a
 * movement for an initial quantity of zero. Bean Validation cannot
 * express "non-zero" for a signed integer, so the rule is enforced in
 * {@code InventoryService} as well (D-12).
 *
 * <p>{@code reason} is mandatory and is persisted into the movement
 * row: a manual correction that is not explained is indistinguishable
 * from a bug, and the log is the only place the correction is visible.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustmentRequest {

    /** Signed correction to apply to {@code quantity}. Must not be zero. */
    @NotNull(message = "Quantity is required")
    private Integer quantity;

    @NotBlank(message = "A reason is required for a stock adjustment")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}