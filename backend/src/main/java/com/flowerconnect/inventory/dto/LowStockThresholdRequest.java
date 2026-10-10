package com.flowerconnect.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Body of {@code PUT /api/v1/vendors/products/{productId}/inventory/low-stock-threshold}
 * (plan task 3.6).
 *
 * <p>The threshold is a full replacement, not a patch: the field is not
 * nullable, so omitting it is a validation error rather than a silent
 * "leave it alone". That matches the task-3.5 rule for
 * {@code ProductRequest.status} — an absent optional field must not be
 * ambiguous between "unchanged" and "cleared" — and it means the vendor
 * always knows exactly what the number is after a save.
 *
 * <p>Changing the threshold is <em>not</em> a stock change and writes
 * no {@code stock_movements} row: the log records changes to the
 * quantity, and the threshold is an alert setting.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockThresholdRequest {

    @NotNull(message = "Low stock threshold is required")
    @Min(value = 0, message = "Low stock threshold must not be negative")
    private Integer lowStockThreshold;
}