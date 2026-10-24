package com.flowerconnect.dispute.domain;

/**
 * Dispute lifecycle status (plan Phase 8, task 8.4).
 *
 * <p>Values are stored as native MySQL ENUM (see {@link Dispute} entity
 * {@code @Enumerated(EnumType.STRING)}). Adding a new value requires an
 * additive migration to extend the column's ENUM list.
 */
public enum DisputeStatus {
    /**
     * Dispute has been raised by the customer; vendor has not yet responded.
     */
    OPEN,

    /**
     * Vendor has responded; dispute is under admin review.
     */
    UNDER_REVIEW,

    /**
     * Admin resolved in the customer's favour: full refund issued.
     */
    RESOLVED_REFUND,

    /**
     * Admin resolved with a partial refund.
     */
    RESOLVED_PARTIAL,

    /**
     * Admin rejected the dispute; no refund.
     */
    RESOLVED_REJECTED
}