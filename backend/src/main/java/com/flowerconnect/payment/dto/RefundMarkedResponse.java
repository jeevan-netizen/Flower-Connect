package com.flowerconnect.payment.dto;

import com.flowerconnect.order.domain.Order.PaymentStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Response body for {@code POST /api/v1/admin/refunds/{orderId}/mark}.
 * Mirrors the post-transition payment state.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundMarkedResponse {

    private Long orderId;
    private PaymentStatus paymentStatus;
    private LocalDateTime refundedAt;
    private String referenceNote;
}
