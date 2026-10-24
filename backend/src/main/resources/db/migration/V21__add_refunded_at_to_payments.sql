-- Phase 8 task 8.3: refunded_at column for the payments table.
-- Tracks when a payment was marked REFUNDED (stamped from the injected Clock).
ALTER TABLE payments ADD COLUMN refunded_at DATETIME(6) NULL;
