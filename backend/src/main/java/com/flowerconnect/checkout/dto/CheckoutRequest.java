package com.flowerconnect.checkout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import com.flowerconnect.order.domain.Order.PaymentMethod;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for {@code POST /api/v1/checkout}.
 *
 * <p>Extends the quote request with a client-provided idempotency key
 * ({@code clientOrderId}) and a server-generated {@code quoteHash} that
 * binds the checkout to the exact quote the customer confirmed. The
 * checkout service validates that the request matches the authoritative
 * quote (no stale prices, no stale inventory, no stale slot) before
 * creating orders and reserving stock.
 *
 * <p>The {@code checkoutFingerprint} field has been removed (Task 5.4
 * remediation). The server now computes the fingerprint from canonical,
 * authenticated checkout-defining inputs and stores it in the
 * {@code checkout_idempotency} table for replay verification. This
 * prevents a client from supplying a fabricated fingerprint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequest {

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
    private List<CheckoutItem> items;

    /**
     * Idempotency key provided by the client. A replay of the same
     * checkout with the same {@code clientOrderId} by the same customer
     * returns the original result instead of creating duplicate orders.
     * Must be a valid UUID.
     */
    @NotNull(message = "clientOrderId is required")
    private UUID clientOrderId;

    /**
     * Server-generated fingerprint of the quote being confirmed.
     * Returned by {@code POST /api/v1/checkout/quote} and echoed back
     * here to prove the customer confirmed this exact quote.
     * If the quote has price drift, the hash will not match the current
     * state and checkout will be rejected.
     */
    @NotNull(message = "quoteHash is required")
    @Size(min = 1, message = "quoteHash must not be empty")
    private String quoteHash;

    /**
     * Explicit confirmation of current product prices if they have changed
     * since items were added to the cart. If prices changed and this is false,
     * checkout fails with 409 CONFLICT.
     */
    private boolean confirmPriceChanges;

    /**
     * The payment method: {@code COD} (default) or {@code ONLINE}. Any other
     * value is rejected with 400 {@code VALIDATION_FAILED}. COD preserves the
     * existing Stage 1 behavior; ONLINE creates a payment group and intent.
     */
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CheckoutItem {

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