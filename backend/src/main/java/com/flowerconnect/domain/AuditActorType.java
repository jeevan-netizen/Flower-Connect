package com.flowerconnect.domain;

/**
 * The actor that initiated an audited action.
 *
 * <p>{@code USER} is an authenticated application user whose {@code users.id}
 * is recorded as {@code audit_log.actor_user_id}. {@code SYSTEM} is a
 * system/scheduled process (for example the weekly settlement scheduler) that
 * has no associated user and therefore records a NULL {@code actor_user_id}.
 * No fake or login-capable SYSTEM user is created.
 */
public enum AuditActorType {
    USER,
    SYSTEM
}
