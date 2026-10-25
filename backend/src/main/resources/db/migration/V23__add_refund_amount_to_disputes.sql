-- V23__add_refund_amount_to_disputes.sql
-- Phase 8 task 8.5: add refund_amount column to disputes for partial/full refund tracking.
--
-- Design notes:
--   * refund_amount stores the refund amount for RESOLVED_REFUND and
--     RESOLVED_PARTIAL outcomes. It is null for RESOLVED_REJECTED.
--   * Precision DECIMAL(10,2) matches orders.total and payments.amount.
--   * Additive migration: adds a new nullable column. No existing data is
--     altered or dropped (ADR-002, D-9).

ALTER TABLE disputes
    ADD COLUMN refund_amount DECIMAL(10,2) NULL;