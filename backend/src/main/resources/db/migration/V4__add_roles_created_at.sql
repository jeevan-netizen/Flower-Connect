-- V4: Add created_at column to roles table (was missing from V1 baseline)
-- The Role entity maps created_at with @CreationTimestamp, but V1__baseline.sql
-- only created id + name columns. This additive migration adds the missing column.
ALTER TABLE roles ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
