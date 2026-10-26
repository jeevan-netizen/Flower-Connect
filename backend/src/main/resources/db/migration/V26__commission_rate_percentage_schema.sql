-- V26__commission_rate_percentage_schema.sql
-- Phase 10: commission rates are percentage 0-100 with 2 dp.
--
-- 1. vendor_profiles: change commission_rate from DECIMAL(5,4) 0-1 multiplier to
--    DECIMAL(5,2) 0-100 percentage; replace the old 0-1 CHECK with a 0-100 CHECK.
-- 2. orders: change commission_rate from DECIMAL(5,4) 0-1 multiplier to
--    DECIMAL(5,2) 0-100 percentage and add the 0-100 CHECK (orders had no CHECK).
--
-- Additive: no existing column, constraint or index is dropped or altered other
-- than the vendor commission_rate type/CHECK rename.

ALTER TABLE vendor_profiles
    MODIFY commission_rate DECIMAL(5,2) NULL;

ALTER TABLE vendor_profiles
    DROP CONSTRAINT ck_vendor_profiles_commission_rate;

ALTER TABLE vendor_profiles
    ADD CONSTRAINT ck_vendor_profiles_commission_rate CHECK (
        commission_rate IS NULL OR (commission_rate >= 0 AND commission_rate <= 100)
    );

ALTER TABLE orders
    MODIFY commission_rate DECIMAL(5,2) NULL,
    ADD CONSTRAINT ck_orders_commission_rate CHECK (
        commission_rate IS NULL OR (commission_rate >= 0 AND commission_rate <= 100)
    );
