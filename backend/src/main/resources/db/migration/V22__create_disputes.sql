-- V22__create_disputes.sql
-- Phase 8 task 8.4: disputes table for post-delivery customer complaints.
--
-- Design notes:
--   * One dispute per order ever: UNIQUE(order_id) enforces this at the
--     database level so concurrent raise attempts cannot create duplicates.
--   * raised_by references users(id) — the customer who opened the dispute.
--   * reason_code is a fixed vocabulary (not free text) so the admin queue
--     can be filtered/grouped. The allowed codes are defined in the
--     application layer (DisputeService) rather than a CHECK constraint,
--     because the vocabulary may evolve before Phase 8 resolution work.
--   * status uses the exact ENUM values from the plan section 8.4:
--     OPEN, UNDER_REVIEW, RESOLVED_REFUND, RESOLVED_PARTIAL, RESOLVED_REJECTED.
--   * resolved_by references users(id) — the admin who resolved the dispute.
--   * All timestamps are UTC DATETIME(6) for consistency with the rest of
--     the schema. created_at uses the injected Clock; resolved_at is set
--     by the resolution service (Phase 8 Stage 5).
--   * The dispute window (days after DELIVERED during which a customer may
--     raise a dispute) is read from platform_config at runtime; the
--     documented default is 7 days (seeded here). The window is measured
--     from the order's entry into DELIVERED (order_status_history.to_status
--     = DELIVERED), not orders.updated_at, so a delayed delivery scan does
--     not shrink the customer's window.
--   * Additive migration: creates a new table, seeds the dispute_window_days
--     row. No existing table, column, constraint, index or row is altered
--     or dropped (ADR-002, D-9).

CREATE TABLE disputes (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_id        BIGINT       NOT NULL,
    raised_by       BIGINT       NOT NULL,
    reason_code     VARCHAR(32)  NOT NULL,
    description     VARCHAR(2000) NOT NULL,
    status          ENUM('OPEN','UNDER_REVIEW','RESOLVED_REFUND','RESOLVED_PARTIAL','RESOLVED_REJECTED')
                      NOT NULL DEFAULT 'OPEN',
    resolution_note VARCHAR(500) NULL,
    resolved_by     BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    resolved_at     DATETIME(6)  NULL,
    CONSTRAINT pk_disputes PRIMARY KEY (id),
    CONSTRAINT uq_disputes_order UNIQUE (order_id),
    CONSTRAINT fk_disputes_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_disputes_raised_by FOREIGN KEY (raised_by) REFERENCES users (id),
    CONSTRAINT fk_disputes_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_disputes_status ON disputes (status);
CREATE INDEX idx_disputes_raised_by ON disputes (raised_by);
CREATE INDEX idx_disputes_resolved_by ON disputes (resolved_by);

-- Dispute window: a customer may raise a dispute within this many days
-- after the order enters DELIVERED (per order_status_history). 7 days
-- is a sensible default for a perishable-goods marketplace; override via
-- the admin settings API (Phase 10) or by inserting a new platform_config
-- row. The value is read as an integer; non-numeric values fail fast at
-- service startup rather than silently disabling the window.
INSERT INTO platform_config (config_key, config_value, description)
VALUES ('dispute_window_days', '7',
        'Days after DELIVERED during which a customer may raise a dispute (task 8.4)');