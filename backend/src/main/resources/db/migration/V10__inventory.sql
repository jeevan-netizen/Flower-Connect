-- FlowerConnect V10__inventory.sql
-- Phase 3b (plan v2.2 task 3.3): per-product inventory.
--
-- `inventory` holds stock levels for a single product. Exactly one row per
-- product, created automatically at quantity 0 when the product is created.
-- The inventory is the single source of truth for stock; all changes are
-- recorded in `stock_movements`.
--
-- Design notes:
--   * `product_id` is a unique FK to `products` (one-to-one). Cascade delete
--     so inventory is removed when the product is deleted.
--   * `quantity` = physical units on hand (non-negative).
--   * `reserved_quantity` = units reserved for pending orders (non-negative,
--     cannot exceed quantity).
--   * `available` = quantity - reserved_quantity (computed, not stored).
--   * `low_stock_threshold` = threshold for low-stock alerts (non-negative).
--   * `expiry_date` = optional expiry date for perishable products. When
--     reached, the expiry scheduler writes off remaining stock as WASTE and
--     delists the product.
--   * CHECK constraints enforce the invariants at the database level:
--     quantity >= 0, reserved_quantity >= 0, low_stock_threshold >= 0,
--     reserved_quantity <= quantity.
--   * The inventory row is created in the same transaction as the product
--     (see ProductService.create).
--
-- This migration is additive: no existing table, column, constraint or index
-- is altered or dropped, and no existing row is touched.

CREATE TABLE inventory (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    product_id            BIGINT         NOT NULL,
    quantity              INT            NOT NULL DEFAULT 0,
    reserved_quantity     INT            NOT NULL DEFAULT 0,
    low_stock_threshold   INT            NOT NULL DEFAULT 0,
    expiry_date           DATE           NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory PRIMARY KEY (id),
    CONSTRAINT uq_inventory_product UNIQUE (product_id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_inventory_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventory_threshold CHECK (low_stock_threshold >= 0),
    CONSTRAINT ck_inventory_reserved_le_quantity CHECK (reserved_quantity <= quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_inventory_product ON inventory (product_id);
CREATE INDEX idx_inventory_expiry ON inventory (expiry_date);