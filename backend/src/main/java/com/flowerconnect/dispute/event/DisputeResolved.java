package com.flowerconnect.dispute.event;

import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published after a dispute is successfully resolved (plan Phase 8, task 8.5).
 * Scalar-only payload so a rollback never sends a notification.
 * Contains Phase 10 hook data for settlement/ledger integration.
 */
public class DisputeResolved extends ApplicationEvent {

    private final Long disputeId;
    private final Long orderId;
    private final Long vendorId;
    private final Long customerId;
    private final DisputeStatus status;
    private final BigDecimal refundAmount;
    private final PaymentMethod paymentMethod;
    private final Long resolverId;
    private final Instant resolvedAt;

    public DisputeResolved(Object source, Long disputeId, Long orderId, Long vendorId,
                           Long customerId, DisputeStatus status, BigDecimal refundAmount,
                           PaymentMethod paymentMethod, Long resolverId, Instant resolvedAt) {
        super(source);
        this.disputeId = disputeId;
        this.orderId = orderId;
        this.vendorId = vendorId;
        this.customerId = customerId;
        this.status = status;
        this.refundAmount = refundAmount;
        this.paymentMethod = paymentMethod;
        this.resolverId = resolverId;
        this.resolvedAt = resolvedAt;
    }

    public Long getDisputeId() { return disputeId; }
    public Long getOrderId() { return orderId; }
    public Long getVendorId() { return vendorId; }
    public Long getCustomerId() { return customerId; }
    public DisputeStatus getStatus() { return status; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public Long getResolverId() { return resolverId; }
    public Instant getResolvedAt() { return resolvedAt; }
}