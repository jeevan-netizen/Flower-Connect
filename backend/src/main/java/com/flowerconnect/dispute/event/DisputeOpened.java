package com.flowerconnect.dispute.event;

import com.flowerconnect.dispute.domain.DisputeStatus;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Published after a dispute is successfully raised (plan Phase 8, task 8.4).
 * Scalar-only payload so a rollback never sends a notification.
 */
public class DisputeOpened extends ApplicationEvent {

    private final Long disputeId;
    private final Long orderId;
    private final Long customerId;
    private final String reasonCode;
    private final DisputeStatus status;
    private final Instant createdAt;

    public DisputeOpened(Object source, Long disputeId, Long orderId, Long customerId,
                         String reasonCode, DisputeStatus status, Instant createdAt) {
        super(source);
        this.disputeId = disputeId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.reasonCode = reasonCode;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getDisputeId() { return disputeId; }
    public Long getOrderId() { return orderId; }
    public Long getCustomerId() { return customerId; }
    public String getReasonCode() { return reasonCode; }
    public DisputeStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}