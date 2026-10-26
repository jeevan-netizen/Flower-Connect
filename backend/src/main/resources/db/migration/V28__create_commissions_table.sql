-- V28__create_commissions_table.sql
-- Phase 10 Task 10.2: commission records table.
-- Exactly one commission row per delivered marketplace order.
-- The applied commission rate percentage is snapshotted at delivery time.

CREATE TABLE commissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    commission_rate DECIMAL(5,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    delivery_fee DECIMAL(12,2) NOT NULL,
    platform_fee DECIMAL(12,2) NOT NULL,
    commission_amount DECIMAL(12,2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_commissions PRIMARY KEY (id),
    CONSTRAINT uq_commissions_order UNIQUE (order_id),
    CONSTRAINT fk_commissions_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_commissions_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_commissions_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_commissions_rate CHECK (commission_rate >= 0 AND commission_rate <= 100),
    CONSTRAINT ck_commissions_money CHECK (
        subtotal >= 0 AND delivery_fee >= 0 AND platform_fee >= 0 AND commission_amount >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_commissions_vendor_created_at ON commissions (vendor_id, created_at);
