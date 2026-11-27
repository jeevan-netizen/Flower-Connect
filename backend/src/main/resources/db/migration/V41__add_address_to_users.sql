-- FlowerConnect V41__add_address_to_users.sql
-- Farmer portal (Phase 14): add an optional postal address to the users table so
-- the farmer profile can display the signed-in farmer's name, address and mobile
-- number. Additive migration: no existing column, constraint or index is altered.

ALTER TABLE users
    ADD COLUMN address VARCHAR(255) NULL AFTER phone;
