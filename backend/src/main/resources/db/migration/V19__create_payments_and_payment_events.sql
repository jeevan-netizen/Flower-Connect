-- V19__create_payments_and_payment_events.sql
-- Phase 7 task 7.1-7.2: payments model for COD (and later ONLINE) checkout.
--
-- Tables introduced:
--   * payments        — one row per order (1:1). Holds the payment method,
--     provider, amount, status and the nullable collected_at timestamp.
--     A nullable payment_group_id is reserved for the later shared online
--     payment intent (Phase 7 online work).
--   * payment_events  — append-only log of provider events. provider_event_id
--     is unique for idempotency; received_at records when the event arrived.
--
-- Design notes:
--   * DECIMAL(10,2) for all monetary amounts (INR, HALF_UP at the application
--     layer). UTC DATETIME(6) timestamps.
--   * payments.status reuses the existing Order.PaymentStatus ENUM value set
--     (PENDING, PAID, COLLECTED, FAILED, REFUND_PENDING, REFUNDED) including
--     COLLECTED, so the backfill can preserve existing order payment state
--     without a type change.
--   * Additive migration: no existing table, column, constraint or index is
--     altered or dropped. Existing orders are backfilled into payments.
--   * Collected COD orders are backfilled as COLLECTED with collected_at
--     derived from the DELIVERED order_status_history timestamp where
--     available, otherwise the order's updated_at is used as the fallback.
--   * Non-collected orders keep their existing payment_status (PENDING etc.)
--     with a NULL collected_at.
-- ---------------------------------------------------------------------------

CREATE TABLE payments (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    order_id          BIGINT        NOT NULL,
    payment_group_id  VARCHAR(36)   NULL,
    method            ENUM('COD','ONLINE') NOT NULL,
    provider          VARCHAR(64)   NOT NULL,
    provider_reference VARCHAR(128) NULL,
    amount            DECIMAL(10,2) NOT NULL,
    status            ENUM('PENDING','PAID','COLLECTED','FAILED',
                           'REFUND_PENDING','REFUNDED') NOT NULL DEFAULT 'PENDING',
    collected_at      DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_order UNIQUE (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_group ON payments (payment_group_id);

-- Index supporting the auto-reject age query: measure age from entry into
-- PLACED (order_status_history.to_status = PLACED) rather than
-- orders.created_at (Phase 7 task 7.2 readiness fix).
CREATE INDEX idx_osh_to_status_created_at ON order_status_history (to_status, created_at);

-- ---------------------------------------------------------------------------
-- payment_events
-- ---------------------------------------------------------------------------
CREATE TABLE payment_events (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id        BIGINT       NOT NULL,
    payment_group_id  VARCHAR(36)  NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type        VARCHAR(64)  NOT NULL,
    outcome           VARCHAR(32)  NOT NULL,
    amount            DECIMAL(10,2) NOT NULL,
    received_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payment_events PRIMARY KEY (id),
    CONSTRAINT uq_payment_events_provider_event UNIQUE (provider_event_id),
    CONSTRAINT fk_payment_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_payment_events_payment ON payment_events (payment_id, received_at);
CREATE INDEX idx_payment_events_group ON payment_events (payment_group_id, received_at);

-- ---------------------------------------------------------------------------
-- Backfill: one payment row per existing order.
-- ---------------------------------------------------------------------------
INSERT INTO payments (
    order_id, payment_group_id, method, provider, provider_reference,
    amount, status, collected_at, created_at, updated_at
)
SELECT
    o.id,
    NULL,
    o.payment_method,
    'cod',
    NULL,
    o.total,
    o.payment_status,
    CASE
        WHEN o.payment_status = 'COLLECTED' THEN (
            SELECT MAX(osh.created_at)
            FROM order_status_history osh
            WHERE osh.order_id = o.id
              AND osh.to_status = 'DELIVERED'
        )
        ELSE NULL
    END,
    o.created_at,
    o.updated_at
FROM orders o;
