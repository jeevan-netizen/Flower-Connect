package com.flowerconnect.notification.event;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published when a paid online order's payment moves to
 * {@code REFUND_PENDING} (after cancellation/rejection), after the
 * transaction commits. Scalar-only payload.
 */
public class PaymentRefundPending extends ApplicationEvent {

    private final Long orderId;
    private final Long customerId;
    private final BigDecimal amount;
    private final Instant occurredAt;

    public PaymentRefundPending(Object source, Long orderId, Long customerId,
                                BigDecimal amount, Instant occurredAt) {
        super(source);
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.occurredAt = occurredAt;
    }

    public Long getOrderId() { return orderId; }
    public Long getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getOccurredAt() { return occurredAt; }
}
