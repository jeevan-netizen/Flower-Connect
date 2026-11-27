-- FlowerConnect V37__create_farmer_payments.sql
-- Stage 3 of the farmer module: collection requests and vendor decisions.
-- Additive migration: no existing column, constraint or index is altered.

CREATE TABLE farmer_payments (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    link_id       BIGINT       NOT NULL,
    amount        DECIMAL(12,2) NOT NULL,
    mode          ENUM('CASH','UPI') NOT NULL,
    note          VARCHAR(500) NULL,
    status        ENUM('PENDING','CONFIRMED','REJECTED') NOT NULL DEFAULT 'PENDING',
    -- 1 for a PENDING row, NULL otherwise. A unique index on (link_id, is_pending)
    -- therefore enforces at most one PENDING collection per link at the database
    -- level. It depends on `status`, not on the FK column `link_id`, so it can
    -- coexist with the foreign key on `link_id` (see docs/decisions.md D-21).
    is_pending    TINYINT(1)   GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN 1 END) VIRTUAL,
    requested_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    decided_at    DATETIME(6)  NULL,
    CONSTRAINT pk_farmer_payments PRIMARY KEY (id),
    CONSTRAINT fk_farmer_payments_link FOREIGN KEY (link_id) REFERENCES farmer_vendor_links (id),
    CONSTRAINT uq_one_pending_payment_per_link UNIQUE (link_id, is_pending)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_farmer_payments_link_status ON farmer_payments (link_id, status);
