package com.flowerconnect.customer.order.dto;

import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One order in the customer's order listing (plan Phase 6, task 6.3).
 *
 * <p>A deliberately light read model: the list shows enough to pick an
 * order and open its detail, but not the per-item snapshots or the full
 * status timeline, which are reserved for
 * {@code GET /api/v1/orders/{id}}.
 *
 * <p>Internal fields such as vendor commission are never exposed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrderListItem {

    private Long id;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String vendorName;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
}
