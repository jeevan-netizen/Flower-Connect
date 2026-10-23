package com.flowerconnect.order.domain;

/**
 * The actor that initiated an order status transition.
 *
 * <p>Mirrors the plan's actor vocabulary for section 6.1: {@code CUSTOMER},
 * {@code VENDOR}, {@code ADMIN}, and {@code SYSTEM} (checkout, auto-reject,
 * payment confirmation, expiry-driven actions). {@code SYSTEM} transitions
 * have no {@code actor_user_id} on the history row.
 */
public enum ActorType {
    CUSTOMER,
    VENDOR,
    ADMIN,
    SYSTEM
}
