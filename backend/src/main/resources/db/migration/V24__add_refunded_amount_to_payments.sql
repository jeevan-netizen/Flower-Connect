-- V24__add_refunded_amount_to_payments.sql
-- Phase 8 task 8.5: add refunded_amount column to payments for partial refund tracking.
--
-- Design notes:
--   * refunded_amount stores the actual amount refunded for a payment.
--     For full refunds, this equals the payment amount. For partial refunds,
--     it is the partial amount. Null until the payment is refunded.
--   * Precision DECIMAL(10,2) matches payments.amount and orders.total.
--   * Additive migration: adds a new nullable column. No existing data is
--     altered or dropped (ADR-002, D-9).

ALTER TABLE payments
    ADD COLUMN refunded_amount DECIMAL(10,2) NULL;