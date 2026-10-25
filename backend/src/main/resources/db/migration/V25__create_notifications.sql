-- V25__create_notifications.sql
-- Phase 9 task 9.1: in-app notifications table.
--
-- Design notes:
--   * One row per notification, owned by a user (user_id -> users.id).
--   * type is the notification category (order_placed, order_confirmed,
--     order_preparing, order_out_for_delivery, order_delivered,
--     order_rejected, order_cancelled_by_customer, order_auto_rejected,
--     order_payment_timeout, refund_pending, refunded, dispute_opened,
--     dispute_resolved, vendor_status_approved, vendor_status_rejected,
--     vendor_status_suspended, vendor_status_reinstated, order_new).
--   * target_type / target_id provide a nullable deep-link target (ORDER,
--     DISPUTE, VENDOR_PROFILE, PAYMENT).
--   * read_at is nullable; null means unread.
--   * dedupe_key is nullable with a unique index: only rows that carry a
--     dedupe key participate in idempotent redelivery; NULL dedupe_key rows
--     are never deduplicated (multiple NULLs allowed by MySQL unique index).
--   * created_at uses the injected Clock via @CreationTimestamp; stored in
--     UTC DATETIME(6) like the rest of the schema.
--   * Additive migration: creates a new table only. No existing table,
--     column, constraint, index or row is altered or dropped (ADR-002, D-9).

CREATE TABLE notifications (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    type         VARCHAR(64)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    message      VARCHAR(1000) NOT NULL,
    target_type  VARCHAR(64)  NULL,
    target_id    BIGINT       NULL,
    read_at      DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    dedupe_key   VARCHAR(200) NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_notifications_dedupe UNIQUE (dedupe_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, read_at, created_at DESC);
