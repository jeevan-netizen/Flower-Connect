-- FlowerConnect V40__create_b2b_statements.sql
-- Stage 3 of the B2B module: B2B statements and payment recording.
-- Additive migration: creates b2b_statements and b2b_statement_orders, and
-- adds PAID to the b2b_orders.payment_status ENUM so delivered orders can be
-- marked PAID when their statement is paid. No existing column, constraint or
-- index is altered except the payment_status ENUM value list (additive).

CREATE TABLE b2b_statements (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    statement_no      VARCHAR(64)  NOT NULL,
    seller_id         BIGINT       NOT NULL,
    buyer_id          BIGINT       NOT NULL,
    period_start      DATETIME(6)  NOT NULL,
    period_end        DATETIME(6)  NOT NULL,
    total_amount      DECIMAL(12,2) NOT NULL,
    status            ENUM('OPEN', 'PAID') NOT NULL DEFAULT 'OPEN',
    paid_at           DATETIME(6)  NULL,
    payment_mode      ENUM('CASH', 'UPI', 'BANK_TRANSFER') NULL,
    payment_note      VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_b2b_statements PRIMARY KEY (id),
    CONSTRAINT uq_b2b_statements_statement_no UNIQUE (statement_no),
    CONSTRAINT fk_b2b_statements_seller FOREIGN KEY (seller_id) REFERENCES vendor_profiles (id),
    CONSTRAINT fk_b2b_statements_buyer FOREIGN KEY (buyer_id) REFERENCES vendor_profiles (id),
    CONSTRAINT ck_b2b_statements_period CHECK (period_end >= period_start),
    CONSTRAINT ck_b2b_statements_buyer_not_seller CHECK (buyer_id <> seller_id),
    CONSTRAINT ck_b2b_statements_total CHECK (total_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE b2b_statement_orders (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    statement_id   BIGINT       NOT NULL,
    order_id       BIGINT       NOT NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_b2b_statement_orders PRIMARY KEY (id),
    CONSTRAINT uq_b2b_statement_orders_order UNIQUE (order_id),
    CONSTRAINT fk_b2b_statement_orders_statement FOREIGN KEY (statement_id) REFERENCES b2b_statements (id) ON DELETE CASCADE,
    CONSTRAINT fk_b2b_statement_orders_order FOREIGN KEY (order_id) REFERENCES b2b_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_b2b_statements_seller_status ON b2b_statements (seller_id, status);
CREATE INDEX idx_b2b_statements_buyer_status ON b2b_statements (buyer_id, status);
CREATE INDEX idx_b2b_statement_orders_statement ON b2b_statement_orders (statement_id);

ALTER TABLE b2b_orders
    MODIFY COLUMN payment_status ENUM('UNPAID', 'PAID') NOT NULL DEFAULT 'UNPAID';
