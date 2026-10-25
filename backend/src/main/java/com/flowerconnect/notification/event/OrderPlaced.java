package com.flowerconnect.notification.event;

import com.flowerconnect.order.domain.Order;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published when an order is first placed ({@code PLACED} initial state),
 * after the checkout transaction commits. Scalar-only payload.
 *
 * <p>Published once per order row. For COD orders, published by
 * {@code CheckoutService} at order creation. For online orders, published by
 * {@code OrderStateMachine} when payment confirmation moves
 * {@code PENDING_PAYMENT -> PLACED}. Never published for {@code PENDING_PAYMENT}.
 */
public class OrderPlaced extends ApplicationEvent {

    private final Long orderId;
    private final Long customerId;
    private final Long vendorUserId;
    private final Order.PaymentMethod paymentMethod;
    private final BigDecimal total;
    private final Instant occurredAt;

    public OrderPlaced(Object source, Long orderId, Long customerId, Long vendorUserId,
                       Order.PaymentMethod paymentMethod, BigDecimal total, Instant occurredAt) {
        super(source);
        this.orderId = orderId;
        this.customerId = customerId;
        this.vendorUserId = vendorUserId;
        this.paymentMethod = paymentMethod;
        this.total = total;
        this.occurredAt = occurredAt;
    }

    public Long getOrderId() { return orderId; }
    public Long getCustomerId() { return customerId; }
    public Long getVendorUserId() { return vendorUserId; }
    public Order.PaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getTotal() { return total; }
    public Instant getOccurredAt() { return occurredAt; }
}
