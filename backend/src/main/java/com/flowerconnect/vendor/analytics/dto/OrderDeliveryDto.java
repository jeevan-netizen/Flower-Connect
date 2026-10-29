package com.flowerconnect.vendor.analytics.dto;

import com.flowerconnect.order.domain.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Projection DTO for a delivered order with its delivered timestamp and items.
 * Used by {@code OrderRepository.findDeliveredWithTimestampBetween} via a
 * constructor expression in JPQL.
 */
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PACKAGE)
@lombok.AllArgsConstructor
public class OrderDeliveryDto {

    private Long orderId;
    private Long customerId;
    private String paymentMethod;
    private BigDecimal total;
    private LocalDateTime deliveredAt;
    private List<OrderItemLineDto> items;

    /**
     * Constructor used by the JPQL projection query; {@code items} is left
     * {@code null} because JPQL cannot easily construct a nested list.
     */
    public OrderDeliveryDto(Long orderId, Long customerId, Order.PaymentMethod paymentMethod,
                            BigDecimal total, LocalDateTime deliveredAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.paymentMethod = paymentMethod.name();
        this.total = total;
        this.deliveredAt = deliveredAt;
        this.items = null;
    }

    @lombok.Getter
    @lombok.NoArgsConstructor(access = lombok.AccessLevel.PACKAGE)
    @lombok.AllArgsConstructor
    public static class OrderItemLineDto {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal lineTotal;
    }
}
