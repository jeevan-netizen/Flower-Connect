-- V20__create_payment_intents.sql
-- Phase 7 task 7.4: simulated/test-mode payment intent for ONLINE checkout.
--
-- One row per payment group. An online checkout creates exactly one intent
-- for the combined grand total; every order in the group links its Payment
-- row to the same payment_group_id. The intent expires after a configured
-- TTL and transitions to PAID when the customer resumes/pays it.
--
-- Design notes:
--   * Additive migration: no existing table, column, constraint or index is
--     altered or dropped.
--   * provider_reference is the safe intent reference returned to the frontend
--     (e.g. intent_sim_<uuid>); it is not a secret.
--   * status uses CREATED/PAID/EXPIRED/FAILED; EXPIRED is set by a sweep or
--     on resume when expires_at has passed, PAID on successful resume.
--   * No foreign key to payments: the group id is the logical link, kept in
--     sync by PaymentService within one transaction.

CREATE TABLE payment_intents (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    payment_group_id   VARCHAR(36)  NOT NULL,
    total              DECIMAL(10,2) NOT NULL,
    status             ENUM('CREATED','PAID','EXPIRED','FAILED') NOT NULL DEFAULT 'CREATED',
    provider_reference VARCHAR(128) NOT NULL,
    expires_at         DATETIME(6)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payment_intents PRIMARY KEY (id),
    CONSTRAINT uq_payment_intents_group UNIQUE (payment_group_id),
    CONSTRAINT uq_payment_intents_provider_ref UNIQUE (provider_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_payment_intents_status_expires ON payment_intents (status, expires_at);
