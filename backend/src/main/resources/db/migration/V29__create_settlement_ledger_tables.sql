-- V29__create_settlement_ledger_tables.sql
-- Phase 10 Task 10.3 and 10.4: weekly settlements and immutable settlement ledger entries.

CREATE TABLE settlements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    vendor_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_credits DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_dues DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    net_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    direction ENUM('PLATFORM_PAYS_VENDOR', 'VENDOR_PAYS_PLATFORM', 'ZERO') NOT NULL,
    status ENUM('PENDING', 'SETTLED') NOT NULL DEFAULT 'PENDING',
    admin_reference_note VARCHAR(500) NULL,
    settled_by_user_id BIGINT NULL,
    settled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_settlements PRIMARY KEY (id),
    CONSTRAINT uq_settlements_vendor_period UNIQUE (vendor_id, period_start, period_end),
    CONSTRAINT fk_settlements_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlements_settled_by FOREIGN KEY (settled_by_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_settlements_vendor_created_at ON settlements (vendor_id, created_at);
CREATE INDEX idx_settlements_status ON settlements (status);

CREATE TABLE settlement_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    vendor_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    dispute_id BIGINT NULL,
    settlement_id BIGINT NULL,
    type ENUM('ONLINE_ORDER_CREDIT', 'COD_DUES', 'DISPUTE_ADJUSTMENT', 'SETTLEMENT_PAYMENT') NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    reference VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_settlement_entries PRIMARY KEY (id),
    CONSTRAINT uq_settlement_entries_order UNIQUE (order_id),
    CONSTRAINT uq_settlement_entries_dispute UNIQUE (dispute_id),
    CONSTRAINT fk_settlement_entries_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlement_entries_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlement_entries_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlement_entries_settlement FOREIGN KEY (settlement_id) REFERENCES settlements (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_settlement_entries_vendor_created ON settlement_entries (vendor_id, created_at);
CREATE INDEX idx_settlement_entries_vendor_settlement ON settlement_entries (vendor_id, settlement_id, created_at);
CREATE INDEX idx_settlement_entries_settlement_id ON settlement_entries (settlement_id);
