package com.flowerconnect.notification.event;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published when a paid online order's payment moves to {@code REFUNDED}
 * (admin marks refund complete), after the transaction commits.
 * Scalar-only payload.
 */
public class PaymentRefunded extends ApplicationEvent {

    private final Long orderId;
    private final Long customerId;
    private final BigDecimal amount;
    private final Long adminId;
    private final Instant occurredAt;

    public PaymentRefunded(Object source, Long orderId, Long customerId,
                           BigDecimal amount, Long adminId, Instant occurredAt) {
        super(source);
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.adminId = adminId;
        this.occurredAt = occurredAt;
    }

    public Long getOrderId() { return orderId; }
    public Long getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public Long getAdminId() { return adminId; }
    public Instant getOccurredAt() { return occurredAt; }
}
