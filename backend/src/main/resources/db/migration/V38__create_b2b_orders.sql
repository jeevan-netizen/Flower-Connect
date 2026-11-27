-- FlowerConnect V38__create_b2b_orders.sql
-- Stage 1 of the B2B module: B2B orders between approved vendors.
-- Additive migration: no existing column, constraint or index is altered.

CREATE TABLE b2b_orders (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    order_no          VARCHAR(64)  NOT NULL,
    buyer_id          BIGINT       NOT NULL,
    seller_id         BIGINT       NOT NULL,
    status            ENUM('PLACED', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'SHIPPED', 'DELIVERED') NOT NULL DEFAULT 'PLACED',
    payment_status    ENUM('UNPAID') NOT NULL DEFAULT 'UNPAID',
    total             DECIMAL(12,2) NOT NULL,
    order_note        VARCHAR(1000) NULL,
    shipment_reference VARCHAR(255) NULL,
    shipped_at        DATETIME(6)  NULL,
    delivered_at      DATETIME(6)  NULL,
    bill_number       VARCHAR(100) NULL,
    placed_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_b2b_orders PRIMARY KEY (id),
    CONSTRAINT uq_b2b_orders_order_no UNIQUE (order_no),
    CONSTRAINT fk_b2b_orders_buyer FOREIGN KEY (buyer_id) REFERENCES vendor_profiles (id),
    CONSTRAINT fk_b2b_orders_seller FOREIGN KEY (seller_id) REFERENCES vendor_profiles (id),
    CONSTRAINT ck_b2b_orders_buyer_not_seller CHECK (buyer_id <> seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_b2b_orders_buyer_status ON b2b_orders (buyer_id, status);
CREATE INDEX idx_b2b_orders_seller_status ON b2b_orders (seller_id, status);
CREATE INDEX idx_b2b_orders_seller_buyer_delivered ON b2b_orders (seller_id, buyer_id, delivered_at);

CREATE TABLE b2b_order_items (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    order_id       BIGINT       NOT NULL,
    product_id     BIGINT       NOT NULL,
    product_name   VARCHAR(160) NOT NULL,
    unit           VARCHAR(50)  NOT NULL,
    quantity       INT          NOT NULL,
    unit_price     DECIMAL(10,2) NOT NULL,
    line_total     DECIMAL(12,2) NOT NULL,
    CONSTRAINT pk_b2b_order_items PRIMARY KEY (id),
    CONSTRAINT fk_b2b_order_items_order FOREIGN KEY (order_id) REFERENCES b2b_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_b2b_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_b2b_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_b2b_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_b2b_order_items_line_total CHECK (line_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_b2b_order_items_order ON b2b_order_items (order_id);
CREATE INDEX idx_b2b_order_items_product ON b2b_order_items (product_id);
