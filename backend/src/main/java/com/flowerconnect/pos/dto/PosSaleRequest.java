package com.flowerconnect.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of {@code POST /api/v1/pos/sales} (plan Phase 11, task 11.2).
 *
 * <p>The client supplies product IDs and quantities only. Prices and totals
 * are computed server-side from {@code products.base_price}; the client is
 * never trusted with a price or total.
 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class PosSaleRequest {

    @NotNull(message = "Items are required")
    @NotEmpty(message = "Items must not be empty")
    @Valid
    private List<PosSaleLineRequest> items;

    @NotNull(message = "Payment method is required")
    private PosPaymentMethod paymentMethod;

    /** One line of a POS sale: product ID and quantity. */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class PosSaleLineRequest {
        @NotNull(message = "Product id is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @jakarta.validation.constraints.Positive(message = "Quantity must be greater than zero")
        private Integer quantity;
    }

    /** Payment method label for a walk-in sale. No gateway is involved; CASH, CARD and UPI are labels only. */
    public enum PosPaymentMethod {
        CASH, CARD, UPI
    }
}
