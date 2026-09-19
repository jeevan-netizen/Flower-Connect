-- FlowerConnect V5__add_phone_unique_constraint.sql
-- Adds a unique constraint on users(phone) so duplicate phone numbers
-- cannot be registered. Phone is nullable; MySQL unique constraints
-- allow multiple NULL values, so registrations without a phone are unaffected.
ALTER TABLE users ADD CONSTRAINT uq_users_phone UNIQUE (phone);
