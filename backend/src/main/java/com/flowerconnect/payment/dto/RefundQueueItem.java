package com.flowerconnect.payment.dto;

import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the admin refund queue ({@code GET /api/v1/admin/refunds}).
 * Only fields needed to review a pending refund are exposed; unrelated
 * customer or internal data is deliberately omitted.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundQueueItem {

    private Long orderId;
    private String vendorName;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private OrderStatus orderStatus;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    private String customerEmail;
}
