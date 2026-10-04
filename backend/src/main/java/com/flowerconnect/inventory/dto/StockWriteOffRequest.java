package com.flowerconnect.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body of {@code POST /api/v1/vendors/products/{productId}/inventory/write-offs}
 * (plan task 3.6).
 *
 * <p>A write-off is stock that physically exists in the books and
 * physically does not exist any more: spoiled stems, a crushed box,
 * a damaged cooler. It is recorded as a {@code WASTE} movement — the
 * same vocabulary the expiry scheduler uses for the same loss
 * (plan section 6.2) — and it carries the same mandatory reason,
 * because the discrepancy is the thing the vendor will later need to
 * explain to an accountant.
 *
 * <p>The distinction from {@link StockAdjustmentRequest} is intent,
 * not arithmetic: an adjustment corrects a number, a write-off
 * documents a loss. Both are refused with 409 when they would drive
 * {@code quantity} below {@code reserved_quantity}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockWriteOffRequest {

    /** Units lost. Always positive; the movement delta is its negation. */
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private Integer quantity;

    @NotBlank(message = "A reason is required for a write-off")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}