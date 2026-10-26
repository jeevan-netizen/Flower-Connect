-- V27__billing_settings_and_audit_columns.sql
-- Phase 10:
-- 1. Additive audit columns for rate-change audit rows (before/after values).
-- 2. Seed platform_config.commission_rate_default = 10.00 (percentage, meaning 10%).
--    This is the global default resolved when no per-vendor override is set.

ALTER TABLE audit_log
    ADD COLUMN previous_value VARCHAR(500) NULL AFTER reason,
    ADD COLUMN new_value VARCHAR(500) NULL AFTER previous_value;

INSERT INTO platform_config (config_key, config_value, description)
VALUES ('commission_rate_default', '10.00',
        'Global default commission rate percentage (0-100). 10.00 = 10%.')
ON DUPLICATE KEY UPDATE config_value = '10.00';
