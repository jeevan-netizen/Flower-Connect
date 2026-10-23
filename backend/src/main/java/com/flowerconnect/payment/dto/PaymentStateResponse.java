package com.flowerconnect.payment.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The current payment state for a single order, returned by
 * {@code GET /api/v1/payments/{orderId}}.
 *
 * <p>Only safe, customer-visible fields are exposed: no provider secrets,
 * no raw credentials, and no internal keys beyond the server-generated
 * payment group / intent reference (which the frontend needs to resume).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStateResponse {

    /** The order id. */
    private Long orderId;

    /** The payment group shared by all orders in this checkout (ONLINE only). */
    private String paymentGroupId;

    /** The simulated payment intent reference (ONLINE only). */
    private String intentReference;

    /** The payment method ({@code COD} or {@code ONLINE}). */
    private String method;

    /** The payment status ({@code PENDING}, {@code PAID}, {@code COLLECTED}, ...). */
    private String paymentStatus;

    /** The order status ({@code PLACED}, {@code PENDING_PAYMENT}, ...). */
    private String orderStatus;

    /** The order total. */
    private BigDecimal amount;

    /** When the payment intent expires (ONLINE only). */
    private String expiresAt;

    /** When the payment record was created. */
    private String createdAt;
}
