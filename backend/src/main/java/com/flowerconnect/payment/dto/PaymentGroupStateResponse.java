package com.flowerconnect.payment.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The current state of a payment group (one simulated intent + all linked
 * orders), returned by {@code GET /api/v1/payments/group/{paymentGroupId}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentGroupStateResponse {

    /** The payment group id. */
    private String paymentGroupId;

    /** The simulated payment intent reference. */
    private String intentReference;

    /** The intent status ({@code CREATED}, {@code PAID}, {@code EXPIRED}, {@code FAILED}). */
    private String status;

    /** The combined grand total of all orders in the group. */
    private BigDecimal total;

    /** When the intent expires. */
    private String expiresAt;

    /** One entry per order in the group. */
    private List<OrderEntry> orders;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderEntry {
        private Long orderId;
        private String paymentStatus;
        private String orderStatus;
        private String method;
        private BigDecimal amount;
    }
}
