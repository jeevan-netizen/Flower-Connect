-- FlowerConnect V32__add_audit_log_actor_type.sql
-- Phase 13 Stage 6 (task 13.5): audit-log coverage -- typed actor model.
--
-- Adds an explicit actor_type column to audit_log so every audit row records
-- whether it was initiated by an authenticated application user (USER) or by
-- a system/scheduled process (SYSTEM), and makes the user foreign key nullable
-- only for SYSTEM entries. A CHECK constraint enforces the invariant:
--   actor_type = 'USER'  -> actor_user_id NOT NULL (real user FK)
--   actor_type = 'SYSTEM' -> actor_user_id IS NULL   (no fake/login-capable user)
--
-- Existing rows keep actor_user_id and get actor_type = 'USER' via the column
-- default, so no data migration is required and no existing audit row changes
-- classification.

ALTER TABLE audit_log
    ADD COLUMN actor_type ENUM('USER', 'SYSTEM') NOT NULL DEFAULT 'USER' AFTER actor_user_id,
    MODIFY COLUMN actor_user_id BIGINT NULL,
    ADD CONSTRAINT ck_audit_log_actor_user_consistency
        CHECK (
            (actor_type = 'USER' AND actor_user_id IS NOT NULL)
            OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        );
