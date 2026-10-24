package com.flowerconnect.checkout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;

import com.flowerconnect.order.domain.Order.PaymentMethod;

/**
 * Request payload for {@code POST /api/v1/checkout/quote}.
 *
 * <p>The customer provides the delivery address and the per-vendor slot
 * selection. The quote validates eligibility, stock, distance, minimum
 * order, and slot availability, then returns a per-shop breakdown with
 * totals and any warnings (e.g., price drift).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutQuoteRequest {

    /**
     * The payment method: {@code COD} (default) or {@code ONLINE}. Any other
     * value is rejected with 400 {@code VALIDATION_FAILED}. The quote itself
     * is price-based and payment-method agnostic; this field is carried
     * through so checkout can bind to the same method.
     */
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    /**
     * The customer's saved address id (must belong to the caller).
     */
    @NotNull(message = "addressId is required")
    @Positive(message = "addressId must be positive")
    private Long addressId;

    /**
     * Per-vendor slot selection. One entry per vendor in the cart.
     */
    @Valid
    @NotNull(message = "items is required")
    private List<QuoteItem> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuoteItem {

        /**
         * The vendor id this slot applies to (must match a vendor in the cart).
         */
        @NotNull(message = "vendorId is required")
        @Positive(message = "vendorId must be positive")
        private Long vendorId;

        /**
         * The selected slot start time in UTC (ISO-8601).
         * The slot end is derived from the vendor's {@code slot_duration_minutes}.
         */
        @NotNull(message = "slotStart is required")
        private String slotStart;
    }
}