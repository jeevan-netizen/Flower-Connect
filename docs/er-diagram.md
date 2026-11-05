# Entity-Relationship Diagram

Derived from Flyway migrations `V1__baseline.sql` … `V32__add_audit_log_actor_type.sql`.
Column types and constraints reflect the applied schema (`ddl-auto: validate`). Relationships
are inferred from foreign-key declarations in the migrations, not from entity names alone.

## Tables

### Core (Phase 0/1)

**roles** (`V1`, `V3`)
- `id` BIGINT PK AUTO_INCREMENT
- `name` VARCHAR(64) NOT NULL UNIQUE (`CUSTOMER`, `FLORIST`, `ADMIN`)

**users** (`V1`)
- `id` BIGINT PK AUTO_INCREMENT
- `role_id` BIGINT NOT NULL FK → roles(id)
- `email` VARCHAR(255) NOT NULL UNIQUE
- `password_hash` VARCHAR(255) NOT NULL
- `full_name` VARCHAR(128) NOT NULL
- `phone` VARCHAR(32) NULL UNIQUE
- `status` ENUM('ACTIVE','SUSPENDED','DISABLED') NOT NULL DEFAULT 'ACTIVE'
- `created_at` DATETIME(6) NOT NULL
- `updated_at` DATETIME(6) NOT NULL

**refresh_tokens** (`V2`)
- `id` BIGINT PK AUTO_INCREMENT
- `user_id` BIGINT NOT NULL FK → users(id)
- `token_hash` VARCHAR(255) NOT NULL UNIQUE
- `family_id` VARCHAR(255) NOT NULL
- `revoked_at` DATETIME(6) NULL
- `replaced_by_id` BIGINT NULL FK → refresh_tokens(id)
- `expires_at` DATETIME(6) NOT NULL

**password_reset_tokens** (`V2`)
- `id` BIGINT PK AUTO_INCREMENT
- `user_id` BIGINT NOT NULL FK → users(id)
- `token_hash` VARCHAR(255) NOT NULL UNIQUE
- `expires_at` DATETIME(6) NOT NULL
- `used_at` DATETIME(6) NULL

### Service locations (Phase 2a)

**service_locations** (`V4`, timezone added `V13`)
- `id` BIGINT PK AUTO_INCREMENT
- `city` VARCHAR(128) NOT NULL
- `area` VARCHAR(128) NOT NULL
- `pincode` VARCHAR(10) NOT NULL
- `latitude` DECIMAL(10,8) NOT NULL
- `longitude` DECIMAL(11,8) NOT NULL
- `timezone` VARCHAR(64) NOT NULL (`V13`)
- `created_at` DATETIME(6) NOT NULL
- UNIQUE (city, area)

### Vendor profiles (Phase 2b)

**vendor_profiles** (`V5`, commission_rate % `V26`)
- `id` BIGINT PK AUTO_INCREMENT
- `user_id` BIGINT NOT NULL UNIQUE FK → users(id)
- `business_name` VARCHAR(160) NOT NULL
- `description` VARCHAR(1000) NULL
- `address_line1` VARCHAR(255) NOT NULL
- `address_line2` VARCHAR(255) NULL
- `service_location_id` BIGINT NOT NULL FK → service_locations(id)
- `latitude` DECIMAL(10,8) NOT NULL
- `longitude` DECIMAL(11,8) NOT NULL
- `delivery_radius_km` DECIMAL(5,2) NOT NULL DEFAULT 5.00
- `logo_url` VARCHAR(512) NULL
- `status` ENUM('PENDING_APPROVAL','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING_APPROVAL'
- `commission_rate` DECIMAL(5,2) NULL CHECK (0 ≤ rate ≤ 100) (`V26`)
- `avg_rating` DECIMAL(3,2) NULL
- `review_count` INT NOT NULL DEFAULT 0
- `min_order_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00
- `base_delivery_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00
- `per_km_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00
- `free_delivery_above` DECIMAL(10,2) NULL
- `prep_time_minutes` INT NOT NULL DEFAULT 30
- `slot_duration_minutes` INT NOT NULL DEFAULT 60
- `max_orders_per_slot` INT NOT NULL DEFAULT 10
- `accepting_orders` BIT(1) NOT NULL DEFAULT b'1'
- `created_at` / `updated_at` DATETIME(6)

**vendor_hours** (`V5`)
- `id` BIGINT PK AUTO_INCREMENT
- `vendor_profile_id` BIGINT NOT NULL FK → vendor_profiles(id) ON DELETE CASCADE
- `weekday` ENUM('MONDAY'…'SUNDAY') NOT NULL
- `open_time` TIME NULL
- `close_time` TIME NULL
- `closed` BIT(1) NOT NULL DEFAULT b'0'
- `created_at` / `updated_at` DATETIME(6)
- UNIQUE (vendor_profile_id, weekday)
- CHECK: closed ⇒ both times NULL; open ⇒ both present, close > open

### Audit log (Phase 2c)

**audit_log** (`V6`, actor_type `V32`)
- `id` BIGINT PK AUTO_INCREMENT
- `actor_user_id` BIGINT NULL FK → users(id)
- `actor_type` ENUM('USER','SYSTEM') NOT NULL DEFAULT 'USER' (`V32`)
- `action_type` VARCHAR(64) NOT NULL
- `entity_type` VARCHAR(64) NOT NULL
- `entity_id` BIGINT NOT NULL
- `reason` VARCHAR(500) NULL
- `previous_value` VARCHAR(500) NULL (`V27`)
- `new_value` VARCHAR(500) NULL (`V27`)
- `created_at` DATETIME(6) NOT NULL
- CHECK: actor_type='USER' → actor_user_id NOT NULL; actor_type='SYSTEM' → actor_user_id IS NULL

### Catalog (Phase 3)

**categories** (`V7`)
- `id` BIGINT PK AUTO_INCREMENT
- `parent_id` BIGINT NULL FK → categories(id)
- `name` VARCHAR(128) NOT NULL
- `slug` VARCHAR(128) NOT NULL UNIQUE
- `display_order` INT NOT NULL
- `active` BIT(1) NOT NULL
- `created_at` / `updated_at` DATETIME(6)

**products** (`V8`, commission_rate % `V26`)
- `id` BIGINT PK AUTO_INCREMENT
- `vendor_id` BIGINT NOT NULL FK → vendor_profiles(id)
- `category_id` BIGINT NOT NULL FK → categories(id)
- `name` VARCHAR(160) NOT NULL
- `slug` VARCHAR(160) NOT NULL UNIQUE
- `description` VARCHAR(1000) NULL
- `base_price` DECIMAL(10,2) NOT NULL CHECK (> 0)
- `status` ENUM('DRAFT','ACTIVE','INACTIVE','ARCHIVED') NOT NULL
- `commission_rate` DECIMAL(5,2) NULL CHECK (0 ≤ rate ≤ 100) (`V26`)
- `created_at` / `updated_at` DATETIME(6)

**product_images** (`V9`)
- `id` BIGINT PK AUTO_INCREMENT
- `product_id` BIGINT NOT NULL FK → products(id)
- `storage_key` VARCHAR(512) NOT NULL
- `original_filename` VARCHAR(255) NULL
- `mime_type` VARCHAR(64) NOT NULL
- `file_size` BIGINT NOT NULL
- `sort_order` INT NOT NULL
- `primary` BIT(1) NOT NULL DEFAULT b'0'
- `created_at` DATETIME(6) NOT NULL

### Inventory (Phase 3b/3d/3e)

**inventory** (`V10`)
- `id` BIGINT PK AUTO_INCREMENT
- `product_id` BIGINT NOT NULL UNIQUE FK → products(id)
- `quantity` DECIMAL(10,2) NOT NULL CHECK (≥ 0)
- `reserved_quantity` DECIMAL(10,2) NOT NULL CHECK (≥ 0, ≤ quantity)
- `low_stock_threshold` INT NOT NULL DEFAULT 0
- `expiry_date` DATE NULL
- `created_at` / `updated_at` DATETIME(6)

**stock_movements** (`V11`)
- `id` BIGINT PK AUTO_INCREMENT
- `product_id` BIGINT NOT NULL FK → products(id)
- `movement_type` ENUM(9 types) NOT NULL
- `quantity_delta` DECIMAL(10,2) NOT NULL
- `reason` VARCHAR(500) NULL
- `reference_id` BIGINT NULL
- `reference_type` VARCHAR(64) NULL
- `actor_user_id` BIGINT NULL FK → users(id)
- `created_at` DATETIME(6) NOT NULL

### Cart / Orders (Phase 5, V13)

**carts** (`V13`)
- `id` BIGINT PK AUTO_INCREMENT
- `user_id` BIGINT NOT NULL UNIQUE FK → users(id)
- `created_at` / `updated_at` DATETIME(6)

**cart_items** (`V13`)
- `id` BIGINT PK AUTO_INCREMENT
- `cart_id` BIGINT NOT NULL FK → carts(id) ON DELETE CASCADE
- `product_id` BIGINT NOT NULL FK → products(id)
- `quantity` INT NOT NULL CHECK (> 0)
- `price_snapshot` DECIMAL(10,2) NOT NULL
- `note` VARCHAR(1000) NULL
- `created_at` / `updated_at` DATETIME(6)

**orders** (`V13`)
- `id` BIGINT PK AUTO_INCREMENT
- `user_id` BIGINT NOT NULL FK → users(id)
- `vendor_id` BIGINT NOT NULL FK → vendor_profiles(id)
- `address_id` BIGINT NOT NULL FK → addresses(id)
- `status` ENUM(…) NOT NULL
- `payment_method` ENUM('COD','ONLINE') NOT NULL
- `payment_status` ENUM('PENDING','PAID','COLLECTED','REFUND_PENDING','REFUNDED') NOT NULL
- `client_order_id` VARCHAR(36) NULL UNIQUE
- `subtotal` / `delivery_fee` / `platform_fee` / `total` DECIMAL(10,2) NOT NULL
- `commission_rate` DECIMAL(5,2) NULL (`V26`)
- `slot_start` DATETIME(6) NOT NULL
- `created_at` / `updated_at` DATETIME(6)

**order_items** (`V13`)
- `id` BIGINT PK AUTO_INCREMENT
- `order_id` BIGINT NOT NULL FK → orders(id)
- `product_id` BIGINT NOT NULL FK → products(id)
- `product_name` VARCHAR(160) NOT NULL
- `quantity` INT NOT NULL
- `price_snapshot` DECIMAL(10,2) NOT NULL
- `item_subtotal` DECIMAL(10,2) NOT NULL

**order_status_history** (`V13`, actor_type `V16`/`V17`)
- `id` BIGINT PK AUTO_INCREMENT
- `order_id` BIGINT NOT NULL FK → orders(id)
- `from_status` ENUM NULL
- `to_status` ENUM NOT NULL
- `actor_type` ENUM('CUSTOMER','VENDOR','ADMIN','SYSTEM') (`V17`)
- `actor_user_id` BIGINT NULL FK → users(id)
- `reason` VARCHAR(500) NULL
- `created_at` DATETIME(6) NOT NULL

**checkout_idempotency** (`V15`)
- `user_id` BIGINT NOT NULL FK → users(id)
- `client_order_id` VARCHAR(36) NOT NULL
- `checkout_fingerprint` VARCHAR(64) NOT NULL
- `created_at` / `completed_at` DATETIME(6)
- UNIQUE (user_id, client_order_id)

### Addresses (Phase 4, V12)

**addresses** (`V12`)
- `id` BIGINT PK AUTO_INCREMENT
- `user_id` BIGINT NOT NULL FK → users(id) ON DELETE CASCADE
- `service_location_id` BIGINT NOT NULL FK → service_locations(id)
- `label` VARCHAR(64) NOT NULL
- `line1` VARCHAR(255) NOT NULL
- `line2` VARCHAR(255) NULL
- `latitude` / `longitude` DECIMAL(...) NOT NULL
- `is_default` BIT(1) NOT NULL
- `created_at` / `updated_at` DATETIME(6)

### Payments (Phase 7, V19/V20/V21/V24)

**payments** (`V19`, refunded_at `V21`, refunded_amount `V24`)
- `id` BIGINT PK AUTO_INCREMENT
- `order_id` BIGINT NOT NULL UNIQUE FK → orders(id)
- `method` ENUM('COD','ONLINE') NOT NULL
- `provider` VARCHAR(64) NOT NULL
- `amount` DECIMAL(10,2) NOT NULL
- `status` ENUM('PENDING','PAID','COLLECTED','REFUND_PENDING','REFUNDED') NOT NULL
- `payment_group_id` VARCHAR(36) NULL
- `collected_at` / `refunded_at` DATETIME(6) NULL
- `refunded_amount` DECIMAL(10,2) NULL (`V24`)
- `created_at` / `updated_at` DATETIME(6)

**payment_events** (`V19`)
- `id` BIGINT PK AUTO_INCREMENT
- `payment_id` BIGINT NOT NULL FK → payments(id)
- `event_id` VARCHAR(64) NOT NULL UNIQUE
- `type` VARCHAR(64) NOT NULL
- `created_at` DATETIME(6) NOT NULL

**payment_intents** (`V20`)
- `id` BIGINT PK AUTO_INCREMENT
- `payment_group_id` VARCHAR(36) NOT NULL UNIQUE
- `intent_reference` VARCHAR(64) NOT NULL UNIQUE
- `status` ENUM('CREATED','PAID','EXPIRED','FAILED') NOT NULL
- `expires_at` DATETIME(6) NOT NULL
- `created_at` / `updated_at` DATETIME(6)

### Platform config (Phase 6/10, V18/V27)

**platform_config** (`V18`)
- `config_key` VARCHAR(128) PK
- `config_value` VARCHAR(500) NOT NULL
- `description` VARCHAR(500) NULL

### Disputes (Phase 8, V22/V23)

**disputes** (`V22`, refund_amount `V23`)
- `id` BIGINT PK AUTO_INCREMENT
- `order_id` BIGINT NOT NULL UNIQUE FK → orders(id)
- `vendor_id` BIGINT NOT NULL FK → vendor_profiles(id)
- `customer_id` BIGINT NOT NULL FK → users(id)
- `reason_code` VARCHAR(64) NOT NULL
- `description` VARCHAR(2000) NOT NULL
- `status` ENUM('OPEN','UNDER_REVIEW','RESOLVED_REFUND','RESOLVED_PARTIAL','RESOLVED_REJECTED') NOT NULL
- `vendor_response` VARCHAR(1000) NULL
- `vendor_responded_at` DATETIME(6) NULL
- `refund_amount` DECIMAL(10,2) NULL (`V23`)
- `resolved_at` DATETIME(6) NULL
- `resolver_id` BIGINT NULL FK → users(id)
- `created_at` / `updated_at` DATETIME(6)

### Reviews (Phase 12, V31)

**reviews** (`V31`)
- `id` BIGINT PK AUTO_INCREMENT
- `order_id` BIGINT NOT NULL UNIQUE FK → orders(id)
- `vendor_id` BIGINT NOT NULL FK → vendor_profiles(id)
- `customer_id` BIGINT NOT NULL FK → users(id)
- `rating` INT NOT NULL CHECK (1 ≤ rating ≤ 5)
- `comment` VARCHAR(2000) NULL
- `vendor_reply` VARCHAR(1000) NULL
- `vendor_replied_at` DATETIME(6) NULL
- `hidden` BIT(1) NOT NULL DEFAULT b'0'
- `hidden_reason` VARCHAR(500) NULL
- `hidden_by` BIGINT NULL FK → users(id)
- `hidden_at` DATETIME(6) NULL
- `created_at` / `updated_at` DATETIME(6)

### POS (Phase 11, V30)

**pos_transactions** (`V30`)
- `id` BIGINT PK AUTO_INCREMENT
- `vendor_id` BIGINT NOT NULL FK → vendor_profiles(id)
- `transaction_reference` VARCHAR(64) NOT NULL UNIQUE
- `idempotency_key` VARCHAR(64) NOT NULL UNIQUE
- `payment_method` ENUM('CASH','CARD','UPI') NOT NULL
- `total_amount` DECIMAL(10,2) NOT NULL
- `created_at` DATETIME(6) NOT NULL

**pos_items** (`V30`)
- `id` BIGINT PK AUTO_INCREMENT
- `transaction_id` BIGINT NOT NULL FK → pos_transactions(id)
- `product_id` BIGINT NOT NULL FK → products(id)
- `quantity` INT NOT NULL
- `unit_price` DECIMAL(10,2) NOT NULL
- `item_subtotal` DECIMAL(10,2) NOT NULL

### Commissions / Settlements (Phase 10, V26/V27/V28/V29)

**commissions** (`V28`)
- `id` BIGINT PK AUTO_INCREMENT
- `order_id` BIGINT NOT NULL UNIQUE FK → orders(id)
- `vendor_id` BIGINT NOT NULL FK → vendor_profiles(id)
- `customer_id` BIGINT NOT NULL FK → users(id)
- `commission_rate` DECIMAL(5,2) NOT NULL
- `subtotal` / `delivery_fee` / `platform_fee` / `commission_amount` DECIMAL(10,2) NOT NULL
- `created_at` DATETIME(6) NOT NULL

**settlements** (`V29`)
- `id` BIGINT PK AUTO_INCREMENT
- `vendor_id` BIGINT NOT NULL FK → vendor_profiles(id)
- `period_start` / `period_end` DATE NOT NULL
- `total_credits` / `total_dues` / `net_amount` DECIMAL(12,2) NOT NULL
- `direction` ENUM('PLATFORM_PAYS_VENDOR','VENDOR_PAYS_PLATFORM','ZERO') NOT NULL
- `status` ENUM('PENDING','SETTLED') NOT NULL
- `admin_reference_note` VARCHAR(500) NULL
- `settled_by_user_id` / `settled_by_email` / `settled_at` NULL
- `created_at` DATETIME(6) NOT NULL
- UNIQUE (vendor_id, period_start)

**settlement_entries** (`V29`)
- `id` BIGINT PK AUTO_INCREMENT
- `settlement_id` BIGINT NULL FK → settlements(id)
- `order_id` BIGINT NULL UNIQUE FK → orders(id)
- `dispute_id` BIGINT NULL UNIQUE FK → disputes(id)
- `vendor_id` BIGINT NOT NULL FK → vendor_profiles(id)
- `type` ENUM('ONLINE_ORDER_CREDIT','COD_DUES','DISPUTE_ADJUSTMENT','SETTLEMENT_PAYMENT') NOT NULL
- `amount` DECIMAL(12,2) NOT NULL
- `created_at` DATETIME(6) NOT NULL

## Relationships

```
roles 1 ──< users
users 1 ──1 refresh_tokens (user_id)
users 1 ──< password_reset_tokens
users 1 ──< addresses
users 1 ──< vendor_profiles (user_id UNIQUE)
users 1 ──< orders
users 1 ──< disputes (customer_id)
users 1 ──< reviews (customer_id)
users 1 ──< audit_log (actor_user_id, nullable)
users ──< order_status_history (actor_user_id, nullable)

service_locations 1 ──< vendor_profiles
service_locations 1 ──< addresses

vendor_profiles 1 ──< vendor_hours (cascade delete)
vendor_profiles 1 ──< products
vendor_profiles 1 ──< orders
vendor_profiles 1 ──< disputes (vendor_id)
vendor_profiles 1 ──< reviews (vendor_id)
vendor_profiles 1 ──< commissions
vendor_profiles 1 ──< settlements
vendor_profiles 1 ──< settlement_entries
vendor_profiles 1 ──< pos_transactions

categories 1 ──< products (parent_id self-ref for hierarchy)
products 1 ──< product_images
products 1 ──< inventory
products 1 ──< stock_movements
products 1 ──< order_items
products 1 ──< reviews (indirect via orders)
products 1 ──< pos_items

carts 1 ──< cart_items (cascade delete)
orders 1 ──< order_items
orders 1 ──< order_status_history
orders 1 ──< payments (order_id UNIQUE)
orders 1 ──< commissions (order_id UNIQUE)
orders 1 ──< settlement_entries (order_id UNIQUE)
orders 1 ──< disputes (order_id UNIQUE)
orders 1 ──< reviews (order_id UNIQUE)

payments 1 ──< payment_events
payment_intents 1 ──< payments (payment_group_id)

settlements 1 ──< settlement_entries
disputes 1 ──< settlement_entries (dispute_id UNIQUE)

pos_transactions 1 ──< pos_items
```

## Notes

- No Redis in v1 (removed in Phase 0 Stage 6); rate limiting uses per-instance Caffeine buckets.
- `audit_log` is append-only; `actor_type` distinguishes USER- vs SYSTEM-initiated rows (`V32`).
- POS (`pos_transactions`/`pos_items`) is commission-free and outside the marketplace ledger (D-66).
- `checkout_idempotency` supports replay-safe checkout (Phase 5 task 5.4).