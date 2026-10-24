package com.flowerconnect.checkout.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response payload for {@code POST /api/v1/checkout}.
 *
 * <p>Returns the created orders with their details, or an idempotent
 * result if the checkout was already processed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponse {

    /**
     * The idempotency key used for this checkout.
     */
    private UUID clientOrderId;

    /**
     * Whether this response is from an idempotent replay (true) or a
     * newly created checkout (false).
     */
    private boolean idempotentReplay;

    /**
     * The created orders (one per vendor).
     */
    private List<OrderSummary> orders;

    /**
     * Grand total across all orders.
     */
    private BigDecimal total;

    /**
     * The payment method used for this checkout ({@code COD} or {@code ONLINE}).
     */
    private String paymentMethod;

    /**
     * The payment group id shared by all orders in this checkout ({@code ONLINE}
     * only; {@code null} for COD). Safe to return to the frontend; it is a
     * server-generated reference, not a secret.
     */
    private String paymentGroupId;

    /**
     * The payment intent reference ({@code ONLINE} only; {@code null} for COD).
     * Safe to return to the frontend; used to resume/pay the pending order.
     */
    private String intentReference;

    /**
     * When the payment intent expires ({@code ONLINE} only; {@code null} for
     * COD). After expiry the order can no longer be resumed via payment.
     */
    private String intentExpiresAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderSummary {

        /**
         * The order id.
         */
        private Long orderId;

        /**
         * The vendor id.
         */
        private Long vendorId;

        /**
         * The vendor's business name.
         */
        private String vendorName;

        /**
         * The order status (always PLACED on creation).
         */
        private String status;

        /**
         * The payment status (always PENDING on creation).
         */
        private String paymentStatus;

        /**
         * The payment method (always COD).
         */
        private String paymentMethod;

        /**
         * Sum of priceSnapshot x quantity for this order's items.
         */
        private BigDecimal subtotal;

        /**
         * Delivery fee for this order.
         */
        private BigDecimal deliveryFee;

        /**
         * Platform fee for this order.
         */
        private BigDecimal platformFee;

        /**
         * Total for this order.
         */
        private BigDecimal total;

        /**
         * The selected delivery slot start time.
         */
        private String slotStart;

        /**
         * The selected delivery slot end time.
         */
        private String slotEnd;

        /**
         * Items in this order.
         */
        private List<OrderItemSummary> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemSummary {

        /**
         * The product id.
         */
        private Long productId;

        /**
         * The product name.
         */
        private String productName;

        /**
         * The quantity ordered.
         */
        private int quantity;

        /**
         * The price snapshot used for this order item.
         */
        private BigDecimal priceSnapshot;

        /**
         * Line subtotal: priceSnapshot x quantity.
         */
        private BigDecimal subtotal;
    }
}