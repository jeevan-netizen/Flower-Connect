-- FlowerConnect V39__add_unique_bill_number_to_b2b_orders.sql
-- Stage 2 of the B2B module: B2B e-bills.
-- Additive migration: adds a unique constraint on bill_number so each delivered
-- order has exactly one, stable bill number. NULL bill numbers (orders not yet
-- delivered) are allowed to repeat under MySQL unique-index semantics.
ALTER TABLE b2b_orders
    ADD CONSTRAINT uq_b2b_orders_bill_number UNIQUE (bill_number);
