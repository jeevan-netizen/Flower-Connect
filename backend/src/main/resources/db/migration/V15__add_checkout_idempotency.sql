-- V15__add_checkout_idempotency.sql
-- Dedicated idempotency table for safe concurrent checkout handling
-- This table provides a lockable row for first-time requests and stores
-- the checkout fingerprint for payload verification on replay.

CREATE TABLE checkout_idempotency (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    client_order_id     VARCHAR(36)   NOT NULL,
    checkout_fingerprint VARCHAR(128)  NOT NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at        DATETIME(6)   NULL,
    CONSTRAINT pk_checkout_idempotency PRIMARY KEY (id),
    CONSTRAINT uq_checkout_idempotency_user_client UNIQUE (user_id, client_order_id),
    CONSTRAINT fk_checkout_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Index for cleanup of abandoned incomplete checkouts (optional scheduled job)
CREATE INDEX idx_checkout_idempotency_created ON checkout_idempotency (created_at);