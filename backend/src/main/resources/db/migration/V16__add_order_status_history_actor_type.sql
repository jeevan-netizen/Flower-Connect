-- V16__add_order_status_history_actor_type.sql
-- Phase 6 task 6.1: record the actor type on every order status transition.
--
-- Additive migration: appends a nullable {@code actor_type} column to
-- {@code order_status_history}. No existing column, constraint, index or row
-- is altered or dropped. The column is populated by {@code OrderStateMachine}
-- for every transition it writes; the initial Phase 5 checkout row
-- ({@code from_status = null}, {@code to_status = PLACED}) is left with a
-- null {@code actor_type}, which is compatible with the new model.

ALTER TABLE order_status_history
    ADD COLUMN actor_type VARCHAR(16) NULL
    AFTER actor_user_id;
