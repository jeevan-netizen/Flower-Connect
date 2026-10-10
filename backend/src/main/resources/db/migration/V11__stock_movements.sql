-- FlowerConnect V11__stock_movements.sql
-- Phase 3b (plan v2.2 task 3.4): stock movement log.
--
-- `stock_movements` is the append-only audit trail of every change to
-- a product's stock. The `inventory` table holds the current levels;
-- this table records how they got there. Rows are never updated or
-- deleted — the log is the history.
--
-- Design notes:
--   * `quantity_delta` is the signed change: positive for stock added
--     (STOCK_IN, RESTOCK), negative for stock removed (STOCK_OUT,
--     SALE_*, WASTE), and either sign for ADJUSTMENT. RESERVE and
--     RELEASE move units between `quantity` and `reserved_quantity`
--     and are recorded so reservations are auditable.
--   * `reason` is free text supplied by the caller (required by the
--     inventory API for adjustments and write-offs, plan task 3.6).
--   * `reference_id` / `reference_type` point at the document that
--     caused the movement (order id, purchase order id, adjustment
--     id, ...). Nullable because the reference type is defined by the
--     caller that writes the row.
--   * `actor_user_id` is the user who caused the movement. Nullable
--     because system-initiated movements (the expiry scheduler's
--     WASTE write-off, plan task 3.7) have no human actor.
--   * `created_at` is the only timestamp: the log is append-only, so
--     there is no `updated_at`.
--   * The movement types mirror plan section 6.2 exactly. The
--     column is a native MySQL ENUM, so the type system itself
--     is the authority on the vocabulary — no writer can invent
--     a type, and no CHECK constraint is needed to enforce it.
--
-- This migration is additive: no existing table, column, constraint or
-- index is altered or dropped, and no existing row is touched.

CREATE TABLE stock_movements (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    product_id     BIGINT         NOT NULL,
    movement_type  ENUM('STOCK_IN', 'STOCK_OUT', 'ADJUSTMENT', 'RESERVE', 'RELEASE',
                       'SALE_ONLINE', 'SALE_POS', 'RESTOCK', 'WASTE') NOT NULL,
    quantity_delta INT            NOT NULL,
    reason         VARCHAR(500)   NULL,
    reference_id   BIGINT         NULL,
    reference_type VARCHAR(64)    NULL,
    actor_user_id  BIGINT         NULL,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movements_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The hot path is "movements for one product, newest first" (inventory
-- history UI), so the leading index column is product_id.
CREATE INDEX idx_stock_movements_product_time ON stock_movements (product_id, created_at);
CREATE INDEX idx_stock_movements_actor_time ON stock_movements (actor_user_id, created_at);
CREATE INDEX idx_stock_movements_type ON stock_movements (movement_type);
