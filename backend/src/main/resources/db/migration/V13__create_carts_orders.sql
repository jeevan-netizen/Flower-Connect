-- FlowerConnect V13__create_carts_orders.sql
-- Phase 5 (plan v2.2 tasks 5.1-5.5, 5.T): carts, orders, and supporting
-- schema for the cart-and-checkout feature.
--
-- Tables introduced:
--   * service_locations.timezone  — ALTER (decision D-5): the vendor's IANA
--     timezone, used to interpret VendorHours open/close times in local time.
--     Default 'Asia/Kolkata' for the 8 seeded demo rows.
--   * carts          — one row per customer (lock-first, D-5).
--   * cart_items     — one row per (cart, product).
--   * orders         — one row per vendor in a multi-vendor checkout.
--   * order_items    — one row per product in an order.
--   * order_status_history — append-only audit of every status transition.
--
-- Design notes:
--   * The cart is server-side (Redis was removed). One cart per customer,
--     enforced by UNIQUE(user_id) on carts.
--   * cart_items.vendor_id is denormalized from products.vendor_id so the
--     cart view can group by shop without joining through products.
--   * orders.client_order_id is the per-user idempotency key (decision E-1):
--     UNIQUE(user_id, client_order_id) lets a replay return the existing
--     order. NULL is allowed and treated as distinct by MySQL, so orders
--     without a key (POS, Phase 11) coexist.
--   * Address fields on orders are snapshotted columns, not an FK to
--     addresses: if the customer later edits or deletes the address, the
--     order's delivery address is preserved. delivery_service_location_id
--     is kept as an FK to service_locations for lookups.
--   * order_status_history.from_status and to_status use the exact same
--     MySQL ENUM value list and ordering as orders.status (D-20).
--   * payment_method ENUM('COD','ONLINE') — Phase 5 only supports COD (D-1).
--   * Orders are one-per-vendor: a multi-vendor cart produces N order rows,
--     one per vendor. cart_items carry vendor_id so the grouping is possible
--     without a join.
--   * This migration is additive: no existing table, column, constraint
--     or index is altered or dropped, and no existing row is modified
--     except the timezone default on seeded service_locations rows.

-- ---------------------------------------------------------------------------
-- service_locations.timezone (decision D-5)
-- ---------------------------------------------------------------------------
-- The vendor's service-location timezone. Used for all slot math:
-- now-in-TZ = Clock (UTC).withZone(zoneId-of-vendor's-service-location).
-- The server itself stays in UTC; only the interpretation of VendorHours
-- and the lead-time/horizon checks shift to the vendor's local day.
ALTER TABLE service_locations
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata'
    AFTER latitude;

-- The 8 seeded rows get the default value; no UPDATE needed since
-- the DEFAULT clause applies to existing rows on ALTER (MySQL 8+).

-- ---------------------------------------------------------------------------
-- carts
-- ---------------------------------------------------------------------------
CREATE TABLE carts (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT uq_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- cart_items
-- ---------------------------------------------------------------------------
CREATE TABLE cart_items (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    cart_id         BIGINT        NOT NULL,
    product_id      BIGINT        NOT NULL,
    vendor_id       BIGINT        NOT NULL,
    quantity        INT           NOT NULL,
    price_snapshot  DECIMAL(10,2) NOT NULL,
    note            VARCHAR(1000) NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_cart_item_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_items_quantity_positive CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- orders
-- ---------------------------------------------------------------------------
CREATE TABLE orders (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT        NOT NULL,
    vendor_profile_id       BIGINT        NOT NULL,
    client_order_id         VARCHAR(36)   NULL,
    status                  ENUM('PLACED','PENDING_PAYMENT','CONFIRMED','PREPARING',
                                 'OUT_FOR_DELIVERY','DELIVERED','CANCELLED','REJECTED','REFUNDED')
                              NOT NULL DEFAULT 'PLACED',
    payment_method          ENUM('COD','ONLINE') NOT NULL DEFAULT 'COD',
    payment_status          ENUM('PENDING','PAID','COLLECTED','FAILED',
                                 'REFUND_PENDING','REFUNDED')
                              NOT NULL DEFAULT 'PENDING',
    subtotal                DECIMAL(10,2) NOT NULL,
    delivery_fee            DECIMAL(10,2) NOT NULL,
    platform_fee            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total                   DECIMAL(10,2) NOT NULL,
    commission_rate         DECIMAL(5,4)  NULL,
    -- address snapshot
    delivery_address_line1  VARCHAR(255)  NOT NULL,
    delivery_address_line2  VARCHAR(255)  NULL,
    delivery_service_location_id BIGINT   NULL,
    delivery_latitude       DECIMAL(10,8) NOT NULL,
    delivery_longitude      DECIMAL(11,8) NOT NULL,
    -- delivery slot (UTC)
    slot_start              DATETIME(6)   NULL,
    slot_end                DATETIME(6)   NULL,
    note                    VARCHAR(1000) NULL,
    created_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_client_order_id UNIQUE (user_id, client_order_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_vendor FOREIGN KEY (vendor_profile_id) REFERENCES vendor_profiles (id),
    CONSTRAINT fk_orders_address_location FOREIGN KEY (delivery_service_location_id) REFERENCES service_locations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_orders_user ON orders (user_id);
CREATE INDEX idx_orders_vendor_status ON orders (vendor_profile_id, status);
CREATE INDEX idx_orders_slot ON orders (vendor_profile_id, slot_start, slot_end);

-- ---------------------------------------------------------------------------
-- order_items
-- ---------------------------------------------------------------------------
CREATE TABLE order_items (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_id        BIGINT        NOT NULL,
    product_id      BIGINT        NOT NULL,
    vendor_id       BIGINT        NOT NULL,
    quantity        INT           NOT NULL,
    price_snapshot  DECIMAL(10,2) NOT NULL,
    note            VARCHAR(1000) NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_order_items_order ON order_items (order_id);

-- ---------------------------------------------------------------------------
-- order_status_history (D-20: same ENUM list as orders.status)
-- ---------------------------------------------------------------------------
CREATE TABLE order_status_history (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_id        BIGINT        NOT NULL,
    from_status     ENUM('PLACED','PENDING_PAYMENT','CONFIRMED','PREPARING',
                         'OUT_FOR_DELIVERY','DELIVERED','CANCELLED','REJECTED','REFUNDED') NULL,
    to_status       ENUM('PLACED','PENDING_PAYMENT','CONFIRMED','PREPARING',
                         'OUT_FOR_DELIVERY','DELIVERED','CANCELLED','REJECTED','REFUNDED') NOT NULL,
    reason          VARCHAR(500)  NULL,
    actor_user_id   BIGINT        NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_osh_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_osh_order ON order_status_history (order_id, created_at);