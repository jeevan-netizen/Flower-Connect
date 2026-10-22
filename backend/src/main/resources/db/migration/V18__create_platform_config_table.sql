-- V18__create_platform_config_table.sql
-- Phase 6 task 6.5: platform configuration key/value store.
--
-- The plan (section 9, `platform_config` row) places this table in phases
-- 6-10 and assigns it "Default commission, platform fee, auto-reject
-- minutes, timeouts". Phase 6 uses only the auto-reject minutes row; Phase
-- 10 extends the same table with commission and platform-fee settings.
-- The key/value shape is deliberately generic so Phase 10 can add rows
-- without another migration.
--
-- Additive migration: creates a new table, seeds the auto-reject-minutes
-- row with a documented default. No existing table, column, constraint,
-- index or row is altered or dropped (ADR-002, D-9).
CREATE TABLE platform_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    config_key      VARCHAR(64)  NOT NULL,
    config_value    VARCHAR(255) NOT NULL,
    description     VARCHAR(512) NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_platform_config PRIMARY KEY (id),
    CONSTRAINT uq_platform_config_key UNIQUE (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Auto-reject threshold: unaccepted PLACED orders older than this many
-- minutes are auto-cancelled by the scheduled auto-reject job (task 6.5).
-- 60 minutes is a sensible default for a same-day flower marketplace;
-- override with a platform_config row or, in Phase 10, the admin settings
-- API. The value is read as an integer; non-numeric values fail fast at
-- service startup rather than silently disabling auto-reject.
INSERT INTO platform_config (config_key, config_value, description)
VALUES ('auto_reject_minutes', '60',
        'Minutes after which an unaccepted PLACED order is auto-cancelled (task 6.5)');
