-- FlowerConnect V6__audit_log_and_vendor_checks.sql
-- Phase 2c (plan v2.2 tasks 2.5 and 2.6).
--
-- 1. audit_log: append-only record of privileged actions. Required by task 2.6
--    ("Every action writes an audit_log row").
-- 2. CHECK constraints for the vendor invariants that Phase 2b deliberately
--    deferred to this phase. These back the API/service validation so the
--    invariants hold even for non-HTTP writers. They are additive: no existing
--    column, constraint or index is altered or dropped, and V5 seeds no vendor
--    rows, so no existing data can violate them.

CREATE TABLE audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT       NOT NULL,
    action_type   VARCHAR(64)  NOT NULL,
    entity_type   VARCHAR(64)  NOT NULL,
    entity_id     BIGINT       NOT NULL,
    reason        VARCHAR(500) NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_user_id, created_at);

-- Vendor profile invariants (task 2.3 / 2.5 validation rules).
ALTER TABLE vendor_profiles
    ADD CONSTRAINT ck_vendor_profiles_review_count CHECK (review_count >= 0),
    ADD CONSTRAINT ck_vendor_profiles_delivery_radius CHECK (delivery_radius_km > 0),
    ADD CONSTRAINT ck_vendor_profiles_money_non_negative CHECK (
        min_order_amount >= 0
        AND base_delivery_fee >= 0
        AND per_km_fee >= 0
        AND (free_delivery_above IS NULL OR free_delivery_above >= 0)
    ),
    ADD CONSTRAINT ck_vendor_profiles_prep_time CHECK (prep_time_minutes > 0),
    ADD CONSTRAINT ck_vendor_profiles_slot_duration CHECK (slot_duration_minutes > 0),
    ADD CONSTRAINT ck_vendor_profiles_max_orders CHECK (max_orders_per_slot > 0),
    ADD CONSTRAINT ck_vendor_profiles_commission_rate CHECK (
        commission_rate IS NULL OR (commission_rate >= 0 AND commission_rate <= 1)
    ),
    ADD CONSTRAINT ck_vendor_profiles_avg_rating CHECK (
        avg_rating IS NULL OR (avg_rating >= 0 AND avg_rating <= 5)
    );

-- Operating-hours invariants (task 2.4 / 2.5 validation rules):
--   closed  -> open_time and close_time must both be NULL
--   open    -> both must be present and close_time must be later than open_time
ALTER TABLE vendor_hours
    ADD CONSTRAINT ck_vendor_hours_times CHECK (
        (closed = b'1' AND open_time IS NULL AND close_time IS NULL)
        OR
        (closed = b'0' AND open_time IS NOT NULL AND close_time IS NOT NULL
            AND close_time > open_time)
    );
