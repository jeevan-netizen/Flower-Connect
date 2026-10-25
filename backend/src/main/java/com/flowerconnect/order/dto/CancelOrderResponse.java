package com.flowerconnect.order.dto;

import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import java.math.BigDecimal;
import lombok.*;

/**
 * Response body for {@code POST /api/v1/orders/{id}/cancel}. Mirrors the
 * order's post-transition status and payment state, plus a
 * {@code refundPending} indicator that tells the client whether a paid
 * online order was moved to {@code REFUND_PENDING} awaiting an admin
 * refund (Phase 8 task 8.3).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelOrderResponse {

    private Long id;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private BigDecimal total;
    private boolean refundPending;
}
