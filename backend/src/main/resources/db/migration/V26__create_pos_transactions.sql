-- V26__create_pos_transactions.sql
-- Phase 11 task 11.1: walk-in point-of-sale transactions.
--
-- Design notes:
--   * pos_transactions holds one row per POS sale: vendor, idempotency key,
--     request fingerprint, total, payment-method label (CASH / CARD / UPI),
--     and an UTC timestamp. No order, order-item, payment, commission or
--     ledger row is created (POS is commission-free, outside the ledger -
--     plan section 6.4).
--   * idempotency_key is client-generated (canonical UUID). The pair
--     (vendor_id, idempotency_key) is unique so a replayed sale cannot be
--     recorded twice; concurrent identical requests serialize on this
--     constraint and exactly one sale is committed.
--   * request_fingerprint is the SHA-256 hex of the canonical request body,
--     used to reject a same-key/different-body replay with 409.
--   * pos_items snapshots product name and unit price from the server at
--     sale time; the client supplies product IDs and quantities only.
--   * Money columns are DECIMAL(10,2); timestamps are UTC DATETIME(6).
--   * Additive migration: creates new tables only (ADR-002, D-9).

CREATE TABLE pos_transactions (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    vendor_id           BIGINT       NOT NULL,
    idempotency_key     VARCHAR(64)  NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    total               DECIMAL(10,2) NOT NULL,
    payment_method      VARCHAR(16)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at        DATETIME(6)  NULL,
    CONSTRAINT pk_pos_transactions PRIMARY KEY (id),
    CONSTRAINT fk_pos_transactions_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_profiles (id) ON DELETE CASCADE,
    CONSTRAINT uq_pos_transactions_vendor_idempotency UNIQUE (vendor_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pos_items (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_id       BIGINT       NOT NULL,
    product_id           BIGINT       NOT NULL,
    product_name_snapshot VARCHAR(160) NOT NULL,
    unit_price_snapshot  DECIMAL(10,2) NOT NULL,
    quantity             INT          NOT NULL,
    line_total           DECIMAL(10,2) NOT NULL,
    CONSTRAINT pk_pos_items PRIMARY KEY (id),
    CONSTRAINT fk_pos_items_transaction FOREIGN KEY (transaction_id) REFERENCES pos_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_pos_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_pos_transactions_vendor_created ON pos_transactions (vendor_id, created_at DESC);
CREATE INDEX idx_pos_items_transaction ON pos_items (transaction_id);
