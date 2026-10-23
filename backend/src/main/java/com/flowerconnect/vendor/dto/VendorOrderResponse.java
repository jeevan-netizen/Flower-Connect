package com.flowerconnect.vendor.dto;

import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single order in the vendor's order listing (plan Phase 6, task 6.4).
 *
 * <p>Internal fields such as {@code commissionRate} are deliberately not
 * exposed to the vendor through this endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorOrderResponse {

    private Long id;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private String deliveryAddressLine1;
    private String deliveryAddressLine2;
    private String note;
}
