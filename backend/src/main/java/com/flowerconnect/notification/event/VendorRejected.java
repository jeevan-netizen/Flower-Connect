package com.flowerconnect.notification.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Published when a vendor profile is rejected
 * ({@code PENDING_APPROVAL -> REJECTED}), after the transaction commits.
 * Scalar-only payload.
 */
public class VendorRejected extends ApplicationEvent {

    private final Long profileId;
    private final Long vendorUserId;
    private final String prevStatus;
    private final String newStatus;
    private final Long adminId;
    private final String reason;
    private final Instant occurredAt;

    public VendorRejected(Object source, Long profileId, Long vendorUserId,
                          String prevStatus, String newStatus, Long adminId,
                          String reason, Instant occurredAt) {
        super(source);
        this.profileId = profileId;
        this.vendorUserId = vendorUserId;
        this.prevStatus = prevStatus;
        this.newStatus = newStatus;
        this.adminId = adminId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getProfileId() { return profileId; }
    public Long getVendorUserId() { return vendorUserId; }
    public String getPrevStatus() { return prevStatus; }
    public String getNewStatus() { return newStatus; }
    public Long getAdminId() { return adminId; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
