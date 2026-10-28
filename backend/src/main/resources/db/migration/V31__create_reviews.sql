-- V31__create_reviews.sql
-- Phase 12 task 12.1: ratings and reviews.
--
-- Design notes:
--   * One review per delivered order ever: UNIQUE(order_id) enforces this at
--     the database level so concurrent submit attempts cannot create duplicates.
--   * rating is bounded 1-5 by a CHECK constraint; comment and vendor_reply are
--     nullable and length-limited (1000 chars each).
--   * hidden flag supports admin moderation; hidden reviews are excluded from
--     public/vendor aggregates and public lists but remain visible to admins,
--     the owning vendor and the review's customer.
--   * vendor_replied_at / hidden_reason / hidden_by / hidden_at timestamp the
--     reply and moderation actions.
--   * All timestamps are UTC DATETIME(6) for consistency with the rest of the
--     schema. created_at/updated_at use Hibernate @CreationTimestamp /
--     @UpdateTimestamp (mirrors disputes, payments).
--   * Additive migration: creates a new table only. No existing table, column,
--     constraint, index or row is altered or dropped (ADR-002, D-9).

CREATE TABLE reviews (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    order_id         BIGINT       NOT NULL,
    customer_id      BIGINT       NOT NULL,
    vendor_id        BIGINT       NOT NULL,
    rating           INT          NOT NULL,
    comment          VARCHAR(1000) NULL,
    vendor_reply     VARCHAR(1000) NULL,
    vendor_replied_at DATETIME(6) NULL,
    hidden           BIT(1)       NOT NULL DEFAULT b'0',
    hidden_reason    VARCHAR(500) NULL,
    hidden_by        BIGINT       NULL,
    hidden_at        DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_reviews PRIMARY KEY (id),
    CONSTRAINT uq_reviews_order UNIQUE (order_id),
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_profiles (id),
    CONSTRAINT ck_reviews_rating CHECK (rating >= 1 AND rating <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_reviews_vendor_created ON reviews (vendor_id, created_at DESC);
CREATE INDEX idx_reviews_customer_created ON reviews (customer_id, created_at DESC);
CREATE INDEX idx_reviews_hidden ON reviews (vendor_id, hidden, created_at DESC);
