-- FlowerConnect V36__create_farmer_deposits.sql
-- Stage 2 of the farmer module: vendor-entered flower deposits.
-- Additive migration: no existing column, constraint or index is altered.

CREATE TABLE farmer_deposits (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    link_id       BIGINT       NOT NULL,
    flower_type   VARCHAR(160) NOT NULL,
    gross_kg      DECIMAL(10,2) NOT NULL,
    deduction_kg  DECIMAL(10,2) NOT NULL,
    net_kg        DECIMAL(10,2) NOT NULL,
    price_per_kg  DECIMAL(10,2) NOT NULL,
    amount        DECIMAL(12,2) NOT NULL,
    deposited_at  DATETIME(6)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_farmer_deposits PRIMARY KEY (id),
    CONSTRAINT fk_farmer_deposits_link FOREIGN KEY (link_id) REFERENCES farmer_vendor_links (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_farmer_deposits_link_deposited_at ON farmer_deposits (link_id, deposited_at);
