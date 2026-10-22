-- V17__alter_order_status_history_actor_type_to_enum.sql
-- Phase 6 Stage 2: align the committed V16 actor_type column with the
-- OrderStatusHistory.actorType entity mapping (@Enumerated(EnumType.STRING)).
--
-- D-20: enum-typed entity fields require native MySQL ENUM columns; V16 added
-- actor_type as VARCHAR(16), which fails ddl-auto: validate against the entity.
-- Additive migration converts it to ENUM('CUSTOMER','VENDOR','ADMIN','SYSTEM').
-- The column is nullable and currently all-NULL (Phase 5 checkout rows carry no
-- actor_type), so no data conversion is needed.

ALTER TABLE order_status_history
    MODIFY COLUMN actor_type ENUM('CUSTOMER','VENDOR','ADMIN','SYSTEM') NULL
    AFTER actor_user_id;
