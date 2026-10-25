package com.flowerconnect.customer.order.dto;

import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full detail of one customer order (plan Phase 6, task 6.3).
 *
 * <p>Includes the order status and payment method/status, the vendor
 * name, the address snapshot, the delivery slot, per-item snapshots
 * (name, price, quantity), the item subtotal and fee breakdown using
 * {@link BigDecimal}, and the chronological status timeline from
 * {@code order_status_history}.
 *
 * <p>Internal fields such as vendor commission, credentials and
 * unrelated user data are deliberately not exposed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrderResponse {

    private Long id;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String vendorName;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal platformFee;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deliveryAddressLine1;
    private String deliveryAddressLine2;
    private BigDecimal deliveryLatitude;
    private BigDecimal deliveryLongitude;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private String note;
    /**
     * Timestamp when the payment was marked refunded ({@code payments.status}
     * moved {@code REFUND_PENDING -> REFUNDED}). Null until then.
     */
    private LocalDateTime refundedAt;
    private List<CustomerOrderItemResponse> items;
    private List<CustomerOrderTimelineEntry> timeline;
}
