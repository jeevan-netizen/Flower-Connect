# Phase 5 Stage 0 — Design Note (Cart and Checkout)

**Status:** APPROVED — Stage 1 gated on branch creation and explicit go-ahead
**Date:** 2026-10-13
**Plan:** `docs/FlowerConnect_Implementation_Plan_v2.2.md` §7 (tasks 5.1–5.T), §6 (domain rules 6.1–6.4)
**Latest migration inspected:** `V12__create_addresses.sql` → proposed Stage 1 migration: **`V13__create_carts_orders.sql`**
**Branch:** `phase-5-cart-checkout` (created from `main` @ `76b3048`)

> **Repository-state verification (resolved):** Preflight confirmed `phase-5-cart-checkout` does **not** exist locally or on `origin`. `main` is clean and synchronized with `origin/main` (0/0 divergence, both at `76b3048`). Branch creation from `main @ 76b3048` is explicitly approved by the user (approval item 1). The branch has been created; no commits, pushes, merges, or rebases are performed in this stage.

This note records what code inspection found and the design decisions Stage 1–5
will implement. Nothing below is implemented yet. All blocking decisions (B–E,
order_status_history, stock-check HTTP code, frontend structure, multi-vendor
cart) have been approved by the user.

---

## A. Preflight evidence

| Check | Result |
|---|---|
| Branch | `main` (HEAD `76b3048`), up to date with `origin/main` (0 behind, 0 ahead) |
| `phase-5-cart-checkout` | **Does not exist** locally or on `origin` (see mismatch above) |
| Working tree | Clean |
| Backend unit-test baseline | **537 tests, 0 failures, 0 errors, 0 skipped**, `./mvnw test` → `BUILD SUCCESS` (user-verified; JAVA_HOME set to `C:\Program Files\Java\jdk-17` in the current PowerShell session only) |
| Backend integration tests | Not re-run in this session; user baseline was 390 passing (issue 018 if Docker is unavailable) |
| Latest Flyway migration | `V12__create_addresses.sql` (Phase 4 task 4.1) — confirmed latest |
| No existing cart/order/checkout code | Confirmed via grep: `carts`, `orders`, `order_items`, `checkout`, `cart_items` have **zero** matches in `backend/src/main/java` or `frontend/src` |

---

## B. Foundational facts established by inspection

These are existing code realities the design builds on and must not contradict.

### B.1 Inventory reservation pattern (InventoryService, D-24)

`InventoryService` (lines 89–432) owns the lock-validate-write-log template:

1. **Ownership check** (unlocked): resolve the product, compare `product.vendor.id` against the caller's `VendorProfile` (resolved from JWT email). Foreign → 403, missing → 404.
2. **Lock** the inventory row: `InventoryRepository.findByProductIdForUpdate(productId)` issues `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`).
3. **Validate** against the locked quantities: `requireQuantityNotBelowReserved` refuses if `newQuantity < reservedQuantity` with `409 INSUFFICIENT_STOCK`.
4. **Write** the new level (`inventoryRepository.saveAndFlush`).
5. **Append one movement** (`stockMovementRepository.save`) with the signed delta, reason, and the actor resolved from the JWT subject.

`StockMovement.MovementType` (lines 75–78) already includes `RESERVE` and `RELEASE`:

```java
public enum MovementType {
    STOCK_IN, STOCK_OUT, ADJUSTMENT, RESERVE, RELEASE,
    SALE_ONLINE, SALE_POS, RESTOCK, WASTE
}
```

`Inventory.getAvailable()` (line 76) computes `quantity - reservedQuantity` — never stored.

**Key implication for Phase 5:** checkout can call the existing `applyStockChange`-style pattern with `RESERVE`/`RELEASE` movement types. The lock ordering discipline ("ascending product id") from D-24 applies to the multi-product checkout lock. No new movement type is needed.

### B.2 Discovery math (DiscoveryService, lines 44–197)

`DiscoveryService` already computes exactly what checkout needs for delivery-fee validation:

- **Haversine** (`haversineKm`, lines 177–196) between two lat/lng centroids, `EARTH_RADIUS_KM = 6371`, `BigDecimal` result, `HALF_UP` scale 2.
- **Fee formula** (`mapToResponse`, lines 143–168): `estimatedFee = baseFee + (perKmFee × distanceKm)`, `setScale(2, HALF_UP)`. Also exposes `freeDeliveryAbove` (informational only).
- **Distance** is between the `ServiceLocation` centroid (origin) and the `VendorProfile` centroid (copied from its service location).

**Key implication:** checkout reuses this same Haversine + fee math. A `PricingService` that wraps the distance and fee computation is recommended (task 5.3/5.4), so discovery and checkout do not diverge.

### B.3 Vendor delivery settings (VendorProfile, lines 69–91)

```java
@Column(name = "base_delivery_fee",   precision = 10, scale = 2, nullable = false)
private BigDecimal baseDeliveryFee;
@Column(name = "per_km_fee",          precision = 10, scale = 2, nullable = false)
private BigDecimal perKmFee;
@Column(name = "free_delivery_above",precision = 10, scale = 2)
private BigDecimal freeDeliveryAbove;
@Column(name = "min_order_amount",   precision = 10, scale = 2, nullable = false)
private BigDecimal minOrderAmount;
@Column(name = "delivery_radius_km", precision = 5, scale = 2, nullable = false)
private BigDecimal deliveryRadiusKm;
@Column(name = "prep_time_minutes",  nullable = false) private Integer prepTimeMinutes;
@Column(name = "slot_duration_minutes", nullable = false) private Integer slotDurationMinutes;
@Column(name = "max_orders_per_slot", nullable = false) private Integer maxOrdersPerSlot;
@Column(name = "accepting_orders",   nullable = false) private boolean acceptingOrders;
```

All delivery-relevant settings already exist on `VendorProfile`. No new vendor columns are needed for Phase 5.

### B.4 VendorHours has no timezone column (the D-6 gap)

`VendorHours` (lines 29–40) stores `DayOfWeek weekday`, `LocalTime openTime`/`closeTime`, and `boolean closed`. There is **no timezone column** on `vendor_hours` or on `service_locations`. The server runs in UTC (`TZ=UTC` in docker-compose, `Clock.systemUTC()` in `ClockConfig`).

**Key implication:** slot computation must decide a timezone source — see decision D.

### B.5 Address book locking pattern (AddressService, lines 134–225)

`AddressService` (Phase 4 task 4.1) establishes the lock-first pattern that checkout can mirror:

- Every write method is one `@Transactional`.
- The **first read** locks the owning user row: `UserRepository.findByEmailForUpdate(email)` (`@Lock(PESSIMISTIC_WRITE)`). This is intentionally the *first* read (inverting D-24) because the locked row is always the caller's own `users` row, resolved from the JWT subject — no caller can name another user's row.
- Ownership is inferred by scoping queries to `user_id`; a foreign id and a missing id are both 404.
- Centroid coordinates are copied from `ServiceLocation` at write time (the D-4 precedent).

**Key implication:** the customer's checkout address is resolved from the JWT principal; the address's stored `latitude`/`longitude` centroid is already copied from its `service_locations` row, so checkout can compute distance without an extra join.

### B.6 Security namespaces (SecurityConfig, lines 79–103)

```java
.requestMatchers("/api/v1/auth/**").permitAll()
.requestMatchers("/api/v1/locations/**").permitAll()
.requestMatchers("/api/v1/discover/**").permitAll()
.requestMatchers(HttpMethod.GET, "/api/v1/categories").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/vendors/register").permitAll()
.requestMatchers("/api/v1/addresses/**").hasRole("CUSTOMER")
.requestMatchers("/api/v1/vendors/**").hasRole("FLORIST")
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
.requestMatchers("/actuator/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

- `GET|PUT /api/v1/vendors/profile` requires `FLORIST` at the namespace, and `@RequiresApprovedVendor` on specific handlers for the approval gate (D-13).
- `VendorApprovalGuard` reads the JWT subject's email, finds the `VendorProfile`, and raises `VendorNotApprovedException` (403 `VENDOR_NOT_APPROVED`) unless `status == APPROVED`.

**Key implication:** cart and checkout endpoints go under `/api/v1/cart/**` and `/api/v1/checkout/**`, mapped to `hasRole("CUSTOMER")`. Customer must be `ACTIVE` (auth is already enforced per 1.3). No approval gate needed (customers are not approved/suspended the way vendors are).

### B.7 Error handling conventions (ErrorCode, BusinessException)

`ErrorCode` (lines 3–35) and `BusinessException` (lines 1–82) provide factory methods:

- `badRequest(message)` → `VALIDATION_FAILED` (400)
- `forbidden(message)` → `FORBIDDEN` (403)
- `notFound(message)` → `NOT_FOUND` (404)
- `conflict(message)` → `CONFLICT` (409)
- `insufficientStock(message)` → `INSUFFICIENT_STOCK` (409)
- `unsupportedMediaType`, `payloadTooLarge`, `rateLimited`, `accountSuspended`, `vendorNotApproved`

**Key implication:** checkout reuses `insufficientStock` for stock shortages (not a generic `CONFLICT`). Two new codes are proposed: `SLOT_FULL` (409) for capacity exhaustion, and `MINIMUM_ORDER` (400) for sub-minimum subtotals. See §7.

### B.8 Configuration convention (AppProperties, lines 7–77)

`AppProperties` is `@Configuration @ConfigurationProperties(prefix = "app")`. Properties are camelCase in YAML (`app.expiry-sweep-cron`) and overridable via `APP_*` env vars (`APP_EXPIRY_SWEEP_CRON`). The test profile disables the expiry sweep cron (`expiry-sweep-cron: "-"`).

**Key implication:** Phase 5 config (platform fee default, min lead time, max booking horizon) should extend `AppProperties` under the `app.*` namespace with `APP_*` env overrides — not a separate `platform_config` table. See decision B.

### B.9 Test infrastructure (AbstractIntegrationTest, lines 17–59)

`AbstractIntegrationTest` uses:
- `@Testcontainers` + `@ActiveProfiles("test")` + `@Tag("integration")`
- Singleton MySQL container started in a `static {}` block (NOT `@Container` — issue 017)
- `@DynamicPropertySource` wires `spring.datasource.*` from the container
- `@Import(TestClockConfig.class)` for an injectable `Clock`

`RoleBoundaryTester` (lines 23–76) runs the same request as `CUSTOMER`/`FLORIST`/`ADMIN` via `@WithMockUser`-style `user(...)` post-processors. It is the reference RBAC sweep utility.

**Key implication:** Phase 5 tests extend `AbstractIntegrationTest`, mint real JWTs via the login endpoint (per the AddressApiIntegrationTest precedent), and use `RoleBoundaryTester` for RBAC cells. Concurrency tests use `CountDownLatch`-released threads (the `InventoryConcurrencyIntegrationTest` precedent).

### B.10 Frontend patterns

- **API:** `shared/lib/api.ts` is an Axios instance with `withCredentials: true`, a Bearer-token request interceptor (skipped for public endpoints), and a single-flight refresh-on-401 response interceptor. The access token lives in the Zustand `auth-store` (issue 022: persisted to `localStorage` — flagged, not fixed in Phase 5).
- **Feature slices:** `features/location/`, `features/vendor/`, `features/admin/` each follow the same structure: `api.ts` (typed fetches), `queries.ts` ({useQuery}-style hooks with named query keys), `types.ts` (DTOs), `components/`, `pages/`.
- **Router:** `router.tsx` currently has `cart` and `orders` as `<Placeholder title="Cart" />` / `<Placeholder title="Orders" />` behind `<RequireAuth>` — ready for Phase 5 to fill in.
- **ProtectedRoute:** role-gated routing; three outcomes (loader / redirect-to-login / access-denied panel).

---

## 1. Cart entities and persistence

### 1.1 Design: per-customer cart, multi-vendor items

The cart is a **server-side** MySQL table (Redis was removed in Stage 6; the plan §6.2 is explicit: "stock is reserved at checkout under ordered pessimistic locks"). One cart per customer, many items, each item carrying its vendor id so the cart view can group by shop.

### 1.2 Proposed schema (new V13 migration)

```sql
-- carts: one row per customer. The UNIQUE on user_id enforces "one cart per customer".
CREATE TABLE carts (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT uq_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- cart_items: one row per (cart, product). The UNIQUE on (cart_id, product_id)
-- enforces "one line per product" — adding the same product again increments
-- quantity rather than duplicating the row.
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
    CONSTRAINT uq_cart_item_cart_product UNIQUE (cart_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- `vendor_id` is a denormalized copy of `products.vendor_id`. This lets the cart group by shop and resolve each vendor's delivery settings without joining through `products` on every cart read.
- `price_snapshot` is the product's `base_price` at the moment the item was added. It is the authority for the cart total until checkout, at which point it is copied to `order_items` (decision C).
- CHECK `(quantity > 0)` prevents zero/negative quantities at the row level (D-12: service + database).
- `ON DELETE CASCADE` on `fk_carts_user` means a deleted user loses their cart. This matches the `addresses` precedent.

### 1.3 Cart lifecycle

- A cart is **created lazily** on the first `POST /api/v1/cart/items` — the cart service creates the `carts` row if the customer does not yet have one.
- The cart is **cleared** (items deleted) on successful checkout. The `carts` row itself is retained (empty) so the next add-to-cart is a simple insert, not a create-or-update.
- The cart is **server-side**, so it survives browser reloads and device switches. The Zustand auth store (issue 022 notwithstanding) is the only persistent client state; the cart lives on the server.

---

## 2. Cart API and validation

### 2.1 Endpoints

| Method | Path | Behaviour |
|---|---|---|
| GET | `/api/v1/cart` | View the caller's cart, grouped by vendor |
| POST | `/api/v1/cart/items` | Add an item (product id + quantity + optional note) |
| PATCH | `/api/v1/cart/items/{id}` | Update quantity |
| DELETE | `/api/v1/cart/items/{id}` | Remove an item |
| DELETE | `/api/v1/cart` | Clear all items |

All under `/api/v1/cart/**` → `hasRole("CUSTOMER")` in `SecurityConfig`.

### 2.2 Ownership

The cart is resolved from the **JWT subject's email**, never from a request body. The service calls `UserRepository.findByEmail(email)` → `user.id`, then `carts.findByUserId(user.id)`. A customer can only access their own cart; a foreign cart id is 404 (inferred by scoping the query to `user_id`).

### 2.3 Validation on add-to-cart

1. **Product exists and is ACTIVE**: `product.status == ACTIVE`. A `DRAFT`, `INACTIVE`, or `ARCHIVED` product → 404 (not visible to the customer; the customer should never see these in discovery/search, and a direct id reference is "not found").
2. **Vendor is APPROVED**: `product.vendor.status == APPROVED`. A `PENDING_APPROVAL` or `SUSPENDED` vendor → 403 `FORBIDDEN` (the customer can see the product in discovery, but cannot order from an unapproved vendor). This reuses the `VendorApprovalGuard` rule (D-13) at the cart level.
3. **Vendor is accepting orders**: `vendor.acceptingOrders == true`. If false → 409 `CONFLICT` ("vendor is not accepting orders").
4. **Sufficient available stock**: `inventory.getAvailable() >= requested quantity`. If not → `409 INSUFFICIENT_STOCK` (reusing the existing `BusinessException.insufficientStock` factory and `INSUFFICIENT_STOCK` `ErrorCode`).

   > **Note on the 400/409 tension:** Plan task 5.2 says "stock checked on add (400 if insufficient)." The existing codebase uses `409 INSUFFICIENT_STOCK` (D-24, `InventoryService`). This design note recommends **409** for cart-add stock checks, for consistency with the catalog and checkout paths. A stock shortage is "the vendor changed the numbers" — not a malformed request — so 409 is the semantically correct code. If the plan's 400 is a hard requirement, it is an **[APPROVAL]** item.

5. **Quantity is positive**: `@Min(1)` on the request DTO; the database CHECK backs it up.

### 2.4 Add vs. update behaviour

- Adding a product already in the cart **increments quantity** (the `UNIQUE(cart_id, product_id)` constraint enforces one line per product; the service does `quantity += delta`).
- Setting quantity to 0 via PATCH **deletes** the line item.

### 2.5 Stale-cart behavior

If a product becomes `INACTIVE` or its stock drops to 0 *after* being added to the cart but *before* checkout, the **cart view** (`GET /api/v1/cart`) shows the item normally (the snapshot price and quantity are still valid), but:
- The **quote** endpoint flags the item with a `STALE` warning and recalculates availability.
- The **checkout** endpoint re-validates everything under the inventory lock and refuses with `409 INSUFFICIENT_STOCK` or `400 VALIDATION_FAILED` if the product is no longer purchasable.

This matches the plan's "checkout re-checks stock under the lock" rule (6.2) and the D-24 precedent.

---

## 3. Delivery-slot discovery and capacity

### 3.1 Slot model

A delivery slot is a `(vendor_id, slot_start, slot_end)` window. The slot is chosen by the customer during checkout (plan task 5.5: "slot chosen per shop"). `slot_start`/`slot_end` are stored as UTC `DATETIME(6)` on the `orders` table (see §5).

### 3.2 Slot endpoints

| Method | Path | Behaviour |
|---|---|---|
| GET | `/api/v1/vendors/{vendorId}/slots?addressId={addressId}&date={date}` | Returns available slot windows for the vendor on the given date at the given address |

- `vendorId` must reference an `APPROVED` vendor with `accepting_orders = true` (D-13). A non-approved or non-accepting vendor → 403/409.
- `addressId` must belong to the caller (ownership scoped by `user_id`; foreign → 404). The address's stored centroid is used for the distance check.
- Unknown `vendorId` → 404; unknown `addressId` → 404.
- `date` defaults to today. A past date → 400 `VALIDATION_FAILED`.

### 3.3 Slot computation algorithm

For the vendor's service-location timezone (decision D), on the requested `date`:

1. **Look up vendor hours** for that `DayOfWeek`. If `closed` → no slots for that day.
2. **Determine the effective open window:** `[max(now_in_tz, open_time) + prepTime, close_time]`. If the window cannot fit at least one `slot_duration_minutes` slot (i.e., `window_start + slot_duration > close_time`) → no slots today; advance to the next open day.
3. **Generate candidate slots:** starting at `window_start`, step by `slot_duration_minutes` until `slot_end ≤ close_time`. Each slot is `[slot_start, slot_start + slot_duration]`.
4. **Apply the horizon cap:** slots beyond `app.checkout.max-booking-horizon-days` (proposed default 7) from "now in tz" are excluded.
5. **Apply the lead-time floor:** slots starting within `app.checkout.min-lead-time-minutes` (proposed default 120) of "now in tz" are excluded — the customer cannot book a slot that starts before the vendor can possibly fulfill it.
6. **Apply capacity:** for each candidate slot, count existing `orders` where `vendor_profile_id = vendor`, `slot_start = candidate_start`, and `status IN (PLACED, CONFIRMED)`. If the count ≥ `maxOrdersPerSlot` → the slot is omitted.

### 3.4 Overnight hours and DST

`VendorHours` stores `open_time`/`close_time` as `LocalTime`. If `close_time` is earlier than `open_time` (e.g., 22:00 → 02:00), the vendor is open overnight. The slot generator handles this by allowing slot end times to cross midnight. `ZoneId` from decision D governs the DST boundary — if the vendor's timezone observes DST, a slot scheduled for a weekend in summer may shift by an hour. Indian timezones do not observe DST, so the demo region is unaffected; the column supports expansion.

### 3.5 Concurrency on slot capacity

The capacity check (count orders per slot) is a read. Under concurrent checkouts, two customers could both see capacity for the same slot and both book it. The guard is the `max_orders_per_slot` business rule, which is enforced at checkout by counting existing `PLACED`/`CONFIRMED` orders for that vendor+slot **under the order creation lock**. If the count exceeds the cap at that point, the checkout fails with `409 SLOT_FULL`. This is the same "lock-and-recheck" discipline used for stock reservations (D-24). See §7 for the concurrency test plan.

---

## 4. Checkout quote and price calculations

### 4.1 Quote endpoint

`POST /api/v1/checkout/quote` — validates the cart against address/slot constraints and returns a per-shop breakdown. **No side effects.** The customer calls this before `POST /api/v1/checkout` to see the total.

**Request:**
```json
{
  "addressId": 123,
  "items": [{ "vendorId": 1, "slotStart": "2026-10-15T09:00:00Z" }],
  "platformFee": 0.00  /* optional override; see decision B */
}
```

**Response (per shop):**
```json
{
  "shops": [
    {
      "vendorId": 1,
      "businessName": "Rose & Bud",
      "items": [
        { "productId": 10, "name": "Red Roses Bouquet", "quantity": 2,
          "priceSnapshot": 450.00, "note": "Include pink paper" }
      ],
      "subtotal": 900.00,
      "deliveryFee": 45.00,
      "platformFee": 0.00,
      "minOrder": 0.00,
      "total": 945.00,
      "warnings": []  /* e.g. "price changed since add-to-cart" */
    }
  ],
  "total": 945.00
}
```

### 4.2 Price-calculation rules (reusing DiscoveryService, B.2)

For each shop (vendor):

| Component | Formula | Source |
|---|---|---|
| `subtotal` | Σ `price_snapshot × quantity` over the shop's items | `cart_items.price_snapshot` |
| `distance_km` | Haversine(vendor centroid, address centroid) | `DiscoveryService.haversineKm` |
| `delivery_fee` | `base_delivery_fee + per_km_fee × distance_km`, or `0` if `subtotal ≥ free_delivery_above` | `VendorProfile` |
| `platform_fee` | Flat amount from `AppProperties` (decision B); `0.00` default | `app.checkout.platform-fee` |
| `total` | `subtotal + delivery_fee + platform_fee` | Computed |
| `commission_amount` | `subtotal × commission_rate` (snapshotted rate) — **not** shown to the customer; stored on the order for Phase 10 | `VendorProfile.commissionRate` or global default |

**Tax (D-3):** prices are tax-inclusive. `subtotal`, `delivery_fee`, `platform_fee`, and `total` are all tax-inclusive. The UI displays "incl. taxes" — no tax line is added. No `tax_amount` column anywhere.

### 4.3 Validation performed by the quote

For each shop in the cart:

1. **Vendor is approved and accepting orders** — 403 if not.
2. **Address is within delivery radius** — `distance ≤ vendor.delivery_radius_km`. If not → 400 `VALIDATION_FAILED` ("Address is outside this vendor's delivery radius"). This reuses the Haversine check from `DiscoveryService`.
3. **Minimum order met** — `subtotal ≥ vendor.min_order_amount`. If not → 400 `MINIMUM_ORDER` (proposed new code). Per-shop error so the customer can add more from that shop or remove it.
4. **Slot availability** — the requested `slotStart` is within the vendor's hours, passes the lead-time floor and horizon cap, and has capacity. If full → 409 `SLOT_FULL` (proposed new code).
5. **Per-item availability re-check** — `inventory.available ≥ item.quantity`. If not → 409 `INSUFFICIENT_STOCK`.

The quote returns per-shop errors/warnings so the customer can fix one shop without losing the others (e.g., "Rose & Bud: subtotal below ₹200 minimum; Bloom Co: slot full").

---

## 5. Transactional checkout and inventory reservation

### 5.1 Checkout endpoint

`POST /api/v1/checkout` — creates one `Order` per vendor in the cart. **All-or-nothing** across shops (plan task 5.4). The operation is one `@Transactional` method.

**Request:** same as the quote, plus:
```json
{
  "addressId": 123,
  "items": [{ "vendorId": 1, "slotStart": "2026-10-15T09:00:00Z" }],
  "clientOrderId": "a1b2c3d4-...",  /* idempotency key, see decision E */
  "note": "Ring the bell twice",    /* top-level note */
  "platformFee": 0.00
}
```

### 5.2 Orders table (V13)

```sql
CREATE TABLE orders (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    vendor_profile_id   BIGINT        NOT NULL,
    client_order_id     CHAR(36)      NULL,
    status              ENUM('PLACED','PENDING_PAYMENT','CONFIRMED','PREPARING',
                             'OUT_FOR_DELIVERY','DELIVERED','CANCELLED',
                             'REJECTED','REFUNDED')
                        NOT NULL DEFAULT 'PLACED',
    payment_method      ENUM('COD','ONLINE') NOT NULL DEFAULT 'COD',
    payment_status      ENUM('PENDING','PAID','COLLECTED','FAILED',
                             'REFUND_PENDING','REFUNDED')
                        NOT NULL DEFAULT 'PENDING',
    subtotal            DECIMAL(10,2) NOT NULL,
    delivery_fee        DECIMAL(10,2) NOT NULL,
    platform_fee        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total               DECIMAL(10,2) NOT NULL,
    commission_rate     DECIMAL(5,4)  NULL,
    -- address snapshot
    delivery_address_line1  VARCHAR(255) NOT NULL,
    delivery_address_line2  VARCHAR(255) NULL,
    delivery_service_location_id BIGINT NULL,
    delivery_latitude   DECIMAL(10,8) NOT NULL,
    delivery_longitude  DECIMAL(11,8) NOT NULL,
    -- slot
    slot_start          DATETIME(6)   NULL,
    slot_end            DATETIME(6)   NULL,
    note                VARCHAR(1000) NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_client_order_id UNIQUE (user_id, client_order_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_vendor FOREIGN KEY (vendor_profile_id) REFERENCES vendor_profiles (id),
    CONSTRAINT fk_orders_address_location FOREIGN KEY (delivery_service_location_id) REFERENCES service_locations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_orders_user ON orders (user_id);
CREATE INDEX idx_orders_vendor_status ON orders (vendor_profile_id, status);
CREATE INDEX idx_orders_slot ON orders (vendor_profile_id, slot_start, slot_end);
```

- `client_order_id` is the idempotency key (decision E). The unique constraint `uq_orders_client_order_id (user_id, client_order_id)` means a replay of the same checkout returns the original order instead of creating a duplicate. NULL is allowed for orders without a key (e.g., POS in Phase 11), and MySQL unique indexes treat NULL as distinct, so multiple NULL rows coexist.
- Address fields are **snapshotted** as columns (not an FK to `addresses`). If the customer later edits or deletes the address, the order's delivery address is preserved. `delivery_service_location_id` is kept as an FK to `service_locations` for the `idx_orders_address_location` lookup; the lat/lng are copied for distance calculations that may not have the FK available.
- `commission_rate` is snapshotted at PLACED time so Phase 10 can compute `commission_amount = subtotal × commission_rate` even if the vendor's profile rate later changes.
- `status` defaults to `PLACED` (COD, D-1). `payment_method = COD`, `payment_status = PENDING`.
- `slot_start`/`slot_end` come from the customer's slot selection; they are stored as UTC datetimes.
- The `from_status`/`to_status` pair for the initial transition is recorded in `order_status_history` (§5.4 below).

### 5.3 Order items table (V13)

```sql
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
```

- `price_snapshot` is copied from `cart_items.price_snapshot` at checkout time (decision C). The order's `subtotal` is the sum of `price_snapshot × quantity` over its items — recomputed from the snapshot, not from the current `products.base_price`.
- `vendor_id` is denormalized (from `cart_items.vendor_id`) so Phase 6 can list a vendor's orders without joining through products.

### 5.4 Order status history (decision: create in Phase 5)

**Recommended: create the `order_status_history` table in V13.** Rationale: plan §6.1 states "every transition writes an `order_status_history` row" — including the initial `PLACED` transition. If deferred to Phase 6, the initial `PLACED` rows for all Phase 5 orders would lack history, and Phase 6 would have to retroactively insert them. Creating the table now keeps the audit trail complete from day one.

```sql
CREATE TABLE order_status_history (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    from_status ENUM('PLACED','PENDING_PAYMENT','CONFIRMED','PREPARING',
                     'OUT_FOR_DELIVERY','DELIVERED','CANCELLED','REJECTED',
                     'REFUNDED') NULL,
    to_status   ENUM('PLACED','PENDING_PAYMENT','CONFIRMED','PREPARING',
                     'OUT_FOR_DELIVERY','DELIVERED','CANCELLED','REJECTED',
                     'REFUNDED') NOT NULL,
    reason      VARCHAR(500)  NULL,
    actor_user_id BIGINT      NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_osh_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8b4_0900_ai_ci;

CREATE INDEX idx_osh_order ON order_status_history (order_id, created_at);
```

In Phase 5, only the initial `PLACED` row is written (`from_status = NULL`, `to_status = 'PLACED'`, `actor = NULL` for system, reason = NULL or "Order placed (COD)"). Phase 6 extends the write path to every subsequent transition.

### 5.5 Checkout transaction steps

The single `@Transactional` method in `CheckoutService.checkout(email, request)`:

1. **Idempotency check (decision E):** `SELECT ... FOR UPDATE` on `orders` where `user_id = ? AND client_order_id = ?`. If a row exists → return the existing order DTO (idempotent retry). This is inside the same transaction as the rest, so there is no TOCTOU window.
2. **Resolve the customer:** `UserRepository.findByEmail(email)` → `user.id`. Resolve the address: `AddressRepository.findByIdAndUserId(addressId, userId)` — ownership scoped, 404 if foreign/missing. The address's `latitude`/`longitude` centroid is already stored (B.4).
3. **Lock inventory rows in product-id order (D-24):** for each item in the grouped cart, acquire `InventoryRepository.findByProductIdForUpdate(productId)` sorted ascending by product id. Ordering by product id prevents deadlocks (D-24). Multiple carts racing for the same product serialize.
4. **Re-validate under the lock:**
   - Product still `ACTIVE` (re-read; it may have been deactivated since add-to-cart).
   - Vendor still `APPROVED` and `accepting_orders = true`.
   - `inventory.available ≥ item.quantity` (409 `INSUFFICIENT_STOCK` if not — the D-24 floor check).
5. **Commit reservations:** for each item, `reserved_quantity += quantity` and write a `RESERVE` movement (actor = authenticated user, `reference_id = order_id`, `reference_type = 'order'`). No `SALE_ONLINE` movement — that happens at vendor accept (Phase 6, plan §6.2).
6. **Compute fees:** distance (Haversine, borrowing from `DiscoveryService`), delivery fee (base + per_km × distance, or 0 if subtotal ≥ free_delivery_above), platform fee (from config, decision B), commission rate (snapshot). All `setScale(2, HALF_UP)`.
7. **Create the order row** per shop with snapshotted address, fees, slot, `status = PLACED`, `payment_method = COD`, `payment_status = PENDING`. Write the `order_status_history` PLACED row. Write `order_items` with `price_snapshot` from the cart.
8. **Clear the cart:** delete all `cart_items` for this user's cart (in the same transaction).
9. **Commit.** If any step 4 check fails, the entire transaction rolls back — no reservations, no orders, no cart clearing. The all-or-nothing guarantee across shops is provided by the single transaction boundary.

> **Reservation rollback on failure:** because every step is in one `@Transactional`, a failure in step 4 (stock shortage) or step 6 (distance exceeds radius) rolls back the inventory locks and the reservations are never written. There is no separate "undo" path — the transaction is the isolation boundary.

### 5.6 What checkout does NOT do (Phase 5 boundaries)

- Does not deduct physical stock. `quantity` is unchanged; only `reserved_quantity` increases (D-5).
- Does not create commission rows, ledger entries, or notifications (Phases 9–10).
- Does not transition the order beyond `PLACED` (Phase 6 state machine).
- Does not process online payments (D-1: COD only).

---

## 6. Customer-facing cart and checkout UI

### 6.1 Frontend feature slices

Following the existing `features/location/` and `features/vendor/` pattern (B.10), two new feature slices:

```
src/features/cart/
├── api.ts             # GET /cart, POST /cart/items, PATCH /cart/items/:id, DELETE /cart
├── queries.ts         # useCart, useAddToCart, useUpdateQuantity, useRemoveItem, useClearCart
├── types.ts           # CartResponse, CartItem, AddCartItemRequest, UpdateCartItemRequest
├── components/
│   ├── CartDrawer.tsx     # slide-out, grouped by vendor
│   ├── CartItemRow.tsx    # quantity stepper, price, remove
│   └── CartShopSummary.tsx# per-vendor subtotal + min-order badge
└── pages/CartPage.tsx     # full-page cart (optional; drawer may suffice)

src/features/checkout/
├── api.ts             # POST /checkout/quote, POST /checkout
├── queries.ts         # useCheckoutQuote, usePlaceOrder
├── types.ts           # QuoteRequest, QuoteResponse, OrderResponse
├── stores/checkout-store.ts  # Zustand for in-progress form state (not the cart)
└── pages/CheckoutPage.tsx
```

### 6.2 Cart drawer

- Slides out from the right (bottom bar icon on mobile, persistent sidebar on desktop).
- Groups items by vendor: shows the vendor name, thumbnail, and per-vendor subtotal.
- For each item: thumbnail (or placeholder — see issue 020), name, quantity stepper, price, remove button.
- Per-vendor: subtotal, delivery-fee estimate (from the last quote), min-order check badge.
- "Proceed to Checkout" button → navigates to `/checkout`.

### 6.3 Checkout page

A 3-step flow:

**Step 1 — Address:** list the customer's saved addresses (`GET /api/v1/addresses`); the default is selected first. "Add new address" links to the address-creation flow (4.1). The selected address id is sent in the quote/checkout payload.

**Step 2 — Per-shop slot + summary:**
- For each vendor in the cart, show the vendor card (name, rating, ETA).
- Call `GET /api/v1/vendors/{vendorId}/slots?addressId={selectedAddressId}&date=today` to populate the slot picker.
- Show the per-shop summary (subtotal, delivery fee, platform fee, min-order check, total).
- If a shop fails the quote (radius, min-order, out-of-stock), show the error inline and disable that shop's "confirm" — the customer can remove it from the cart.
- A single "Place Order" CTA creates all shops' orders in one call.

**Step 3 — Payment + confirmation:**
- Payment method selector: COD only (D-1). The UI shows "Cash on Delivery" with the collected-amount = `total`.
- Terms checkbox.
- "Place Order" → `POST /api/v1/checkout` with `clientOrderId` (UUID, decision E).
- On success: order confirmation page with the order id(s) and "track" links.

### 6.4 State management

- The cart is **server-side** (MySQL), fetched via TanStack Query key `["cart"]`. The Zustand auth store holds the access token (issue 022: persisted to `localStorage`).
- The checkout form state (selected address, selected slots, note) lives in a `checkout-store` Zustand (in-memory, not persisted — a browser refresh restarts checkout). This mirrors the D-15 precedent (vendor area: one resource, form state local).
- On checkout success, invalidate `["cart"]` so the drawer reflects the cleared cart.

### 6.5 Router changes

Add to `router.tsx`:
```tsx
{ path: "cart", element: <RequireAuth><CartDrawerOrPage /></RequireAuth> },
{ path: "checkout", element: <RequireAuth><CheckoutPage /></RequireAuth> },
```

Both behind `<RequireAuth>` (no specific role — any authenticated customer). This follows the existing `browse`/`cart`/`orders` placeholder pattern.

---

## 7. Security, error handling, concurrency, idempotency, and verification

### 7.1 Security

| Endpoint group | Namespace rule | Handler-level checks |
|---|---|---|
| `/api/v1/cart/**` | `hasRole("CUSTOMER")` | Owner = JWT subject's email; cart scoped by `user_id` |
| `/api/v1/checkout/**` | `hasRole("CUSTOMER")` | Address ownership (404 if foreign); vendor approval (D-13) |
| `/api/v1/vendors/{id}/slots` | `hasRole("CUSTOMER")` | Address ownership; vendor approval |

- A `FLORIST` or `ADMIN` hitting `/api/v1/cart` → 403 `FORBIDDEN` (role mismatch), before the controller.
- An unauthenticated request → 401 (the existing `CustomAuthenticationEntryPoint`).
- Customer must be `ACTIVE` (per 1.3: the JWT filter checks user status on every request).
- The idempotency `client_order_id` is scoped per user: `UNIQUE(user_id, client_order_id)`. A customer cannot collide with another customer's key.

### 7.2 Error handling

Reuse the existing `ErrorCode`/`BusinessException` factories. Two new codes are proposed:

| Code | HTTP | When | Factory |
|---|---|---|---|
| `SLOT_FULL` | 409 | The requested slot has reached `max_orders_per_slot` | `BusinessException.slotFull(...)` (new factory) |
| `MINIMUM_ORDER` | 400 | Shop subtotal < `vendor.min_order_amount` | `BusinessException.tooLow(...)` (new factory, `VALIDATION_FAILED` code) |

The existing `INSUFFICIENT_STOCK` (409) is reused for stock shortages at both cart-add and checkout.

### 7.3 Concurrency

- **Stock reservation:** inventory rows locked in ascending product-id order (`findByProductIdForUpdate`, D-24). N parallel checkouts for the last unit yield exactly one success (the `InventoryConcurrencyIntegrationTest` precedent uses `CountDownLatch`). Phase 5.T must replicate this for the `Quantity 1` case.
- **Slot capacity:** the capacity check is a count of `PLACED`/`CONFIRMED` orders for the vendor+slot. Under concurrent checkout, two customers can both pass the count and both proceed — the transaction commits one and the second hits the `max_orders_per_slot` cap at commit (or a `UNIQUE` constraint on a `slot_bookings` table). The simpler approach for Phase 5: count under the same lock as the order creation, and if capacity is exceeded, raise `409 SLOT_FULL`. The test asserts exactly `max_orders_per_slot` orders succeed.
- **Idempotency:** see decision E. The idempotency check is inside the checkout transaction (`SELECT ... FOR UPDATE` on `orders`), so concurrent replays of the same `client_order_id` serialize on the row lock.
- **Multi-shop rollback:** all shops' inventory locks, order creation, and cart clearing happen in one transaction. If shop A succeeds but shop B fails (e.g., stock shortage), the entire transaction rolls back — shop A's reservations are released (rolled back), shop B's never happened, and the cart is untouched. The test asserts this end-to-end.

### 7.4 Idempotency

See decision E (detailed in §B of this note... no, decision E is below).

### 7.5 Verification plan (Phase 5.T)

| Area | Test |
|---|---|
| Concurrency | N=8 parallel checkouts for a product with Q=1 produce exactly one `PLACED` order; the other 7 get `INSUFFICIENT_STOCK` |
| Multi-shop rollback | Cart with two vendors; one vendor's stock is exhausted; asserting that zero orders are created and the cart retains all items |
| Reservation semantics | After checkout, `inventory.quantity` is unchanged, `inventory.reserved_quantity` increased by the ordered quantity, and a `RESERVE` movement is written |
| All-or-nothing | Same as multi-shop rollback |
| Fee formula | Unit tests for Haversine distance, `delivery_fee = base + per_km × distance` (HALF_UP, scale 2), free-delivery threshold, platform fee |
| Minimum order | Cart with subtotal < vendor's `min_order_amount` → quote returns `MINIMUM_ORDER` |
| Radius | Address centroid 10 km from a vendor with 5 km radius → quote returns radius error |
| Slot capacity | `max_orders_per_slot = 2`; 3 concurrent checkouts for the same slot → 2 succeed, 1 gets `SLOT_FULL` |
| Slot computation | Vendor open 09:00–18:00, prep=30, duration=60; `GET .../slots?date=today` returns slots starting at 09:30, 10:30, ... |
| Idempotency | Replaying the same `client_order_id` returns the original order id; no duplicate orders, no double reservation |
| RBAC | `RoleBoundaryTester` sweep on `/api/v1/cart/**` and `/api/v1/checkout/**`: 401 unauthenticated, 403 FLORIST/ADMIN, 2xx CUSTOMER |
| Ownership | Customer A cannot view customer B's cart (404); customer A cannot use customer B's address (404) |
| Cart validation | Add DRAFT product → 404; add to INACTIVE vendor → 403; add with insufficient stock → 409; add with quantity ≤ 0 → 400 |

All Phase 5 integration tests extend `AbstractIntegrationTest`, mint real JWTs via `POST /auth/login`, and use the singleton Testcontainers MySQL (§B.9).

---

## 8. Explicit exclusions and deferred work

Phase 5 does **not** implement:

- **Online payments** (D-1: COD only) → Phase 7. `payment_method` is hardcoded to `COD`; no gateway, no webhook.
- **Vendor order management** (accept/reject, state transitions beyond `PLACED`) → Phase 6. The `OrderStateMachine` and all transitions in §6.1 except the initial `PLACED` are out of scope.
- **Auto-reject job** (task 6.5: "unaccepted PLACED orders older than N minutes") → Phase 6. The `orders.updated_at` column supports it, but the scheduler is not built.
- **Customer order history / tracking** (task 6.3) → Phase 6.
- **Payment gateway, webhook, reservation-expiry sweeper** (task 7.x) → Phase 7.
- **Cancellations, refunds, disputes** (Phase 8) → Phase 8.
- **Notifications, polling, SSE** (Phase 9) → Phase 9.
- **Commission row creation, settlement ledger** (task 10.x) → Phase 10. `commission_rate` is snapshotted on the order for this purpose, but the `commissions` and `settlement_entries` tables are not created.
- **POS** (Phase 11) → Phase 11.
- **Redis** → not reintroduced (removed in Stage 6).
- **Token storage fix (issue 022)**: the auth store persists tokens to `localStorage`. The checkout flow reads the cart via the access token in the store — this is the same exposure that already exists for vendor features. Fixing token storage is a Phase 13 (security hardening) concern, not Phase 5.
- **`platform_config` table**: Phase 10.1 and 6.5 mention this for runtime admin config. Phase 5 uses `AppProperties` instead (decision B). A future migration can introduce `platform_config` when admin-managed settings are needed.

---

## Decision B (APPROVED): Platform fee configuration

**Options:**

| Option | Mechanism | Pros | Cons | Migration |
|---|---|---|---|---|
| **B1** | `platform_config` table with admin CRUD | Runtime-tunable without restart; matches the plan's later references (10.1, 6.5) | New table, repository, admin CRUD endpoints; overkill for Phase 5 where COD is the only payment method and the fee is a fixed 0 by default | New table in V13 |
| **B2** | Extend `AppProperties` (`app.checkout.platform-fee`, `APP_CHECKOUT_PLATFORM_FEE`) | Minimal — one field + getter/setter, follows the existing `app.*` / `APP_*` convention; no new table | Not runtime-updateable; requires restart | None — just a field on `AppProperties` |

**Recommendation: B2.** Phase 5 has no admin-managed config UI or runtime settings API. The platform fee is a flat amount default of 0 (COD only, no payment gateway, D-1). `AppProperties` is the existing pattern (B.8); a dedicated table would sit unused until Phase 10. Adding `platformFee` (DECIMAL(10,2), default 0.00) and `defaultCommissionRate` (DECIMAL(5,4), default 0.10 = 10%) to `AppProperties` keeps the fee formula testable via config. When Phase 10 introduces `platform_config`, it becomes the runtime source and `AppProperties` the fallback/default.

**Consequence:** changing the platform fee requires a config change + restart. This is acceptable in Phase 5 (no live traffic, dev/demo environment).

---

## Decision C (APPROVED): Product price snapshots

**Options:**

| Option | When snapshot is taken | Re-check at checkout | Price-change handling |
|---|---|---|---|
| **C1 (recommended)** | At add-to-cart → stored on `cart_items.price_snapshot` | Quote/checkout re-reads `products.base_price` and returns a `STALE` warning if it differs | Snapshot is authoritative for the order total; customer is warned the price moved and can re-add to get the new price |
| C2 | At checkout → stored on `order_items.price_snapshot` only | Checkout re-reads and refuses if changed | Customer sees the current price at checkout, not the cart price; surprising if it jumped |
| C3 | No snapshot — always read current `products.base_price` | Checkout reads current price | Order total can differ from the quote; race condition |

**Recommendation: C1 (dual snapshot).** The `price_snapshot` is taken at add-to-cart time on `cart_items` (plan task 5.1 explicitly lists this column) and copied to `order_items` at checkout (plan §5.4: "address snapshot, fees and slot" implies all monetary values are snapshotted). The quote endpoint re-reads the current `products.base_price` and returns a per-item `warnings: ["price_changed_since_add_to_cart"]` flag when the two differ — this is advisory, not a refusal. The snapshot on the order is **authoritative** for the final total: the customer pays what the order records, not what the product costs now.

**Rationale:**
- Historical price accuracy: the order is an immutable record of what the customer agreed to pay. The `price_snapshot` on `order_items` is that record (D-22 precedent for `InventorySummary` embedding read-with-product data).
- If price changes after add-to-cart: the cart view shows the snapshot price (what the customer will pay); the quote shows both snapshot and current, flagging the mismatch.
- Checkout re-validates availability (stock, ACTIVE status) under the lock but does **not** re-price — re-pricing at checkout would create a mismatch between quote and final total, which is worse UX than a warning.
- Tax: prices are tax-inclusive (D-3), so `subtotal = Σ(price_snapshot × quantity)` requires no tax line. The UI says "incl. taxes."

**Consequence:** a product whose price is lowered after add-to-cart gives the customer the old (higher) price unless they re-add. This is standard cart behaviour and acceptable for v1.

---

## Decision D (APPROVED): Timezone source and booking window

**Problem:** `VendorHours` stores `DayOfWeek` + `LocalTime` with no timezone. `VendorHours` and `service_locations` have no `timezone` or `ZoneId` column. The server runs in UTC. Slot computation must interpret "open at 09:00" in the vendor's local time.

**Options:**

| Option | Timezone source | Lead time | Horizon |
|---|---|---|---|
| **D1 (recommended)** | Add `timezone` column to `service_locations` (V13, default `Asia/Kolkata` for the demo region). All slot math in `ZoneId.of(vendor.serviceLocation.timezone)`. Server `Clock` (UTC) converts to vendor TZ for "now" comparisons. | Min 2 hours (configurable: `app.checkout.min-lead-time-minutes`) | Max 7 days (configurable: `app.checkout.max-booking-horizon-days`) |
| D2 | Infer timezone from `service_locations.city` name → hardcoded map. No schema change. | Same | Same |
| D3 | Use server UTC for all slots. | Same | Same |

**Recommendation: D1.** A `timezone` column on `service_locations` is the minimal schema change that:
- Anchors every slot to a real `java.time.ZoneId` (DST-aware).
- Follows the D-4 precedent (service_locations is the single source of coordinates; the timezone is another centroid property).
- Leaves the server in UTC — "now" is `Instant.now(clock)` converted to the vendor's `ZoneId` for slot math only.

**Proposed defaults (to confirm):**
- `app.checkout.min-lead-time-minutes`: **120** (2 hours — a florist needs time to assemble flowers).
- `app.checkout.max-booking-horizon-days`: **7** (one week — flowers expire quickly; same-day is the demo).

**Same-day / midnight handling:**
- Same-day scheduling works naturally: "now in vendor TZ" → next available slot ≥ prep time from now.
- Overnight hours (`open_time > close_time`, e.g., 22:00 → 02:00): the slot generator allows `slot_end` to cross midnight, bounded by `close_time` on the following calendar day.
- DST: `ZoneId` handles transitions. The demo region (India) does not observe DST, so no production impact; the column exists for future expansion.

**Backend authority over client times (B.4):** the slot endpoint returns slot windows as UTC ISO strings. The customer selects one; the checkout validates that the selected `slot_start` is within the vendor's hours *on the server* (not trusting the client's date/time input). A slot outside hours or past the horizon → 400.

**Consequence:** V13 adds a `timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata'` column to `service_locations`, populated for the 8 seeded rows in the same migration.

---

## Decision E (APPROVED): Checkout idempotency

**Options:**

| Option | Mechanism | Replay safety | Schema |
|---|---|---|---|
| **E1 (recommended)** | `client_order_id` (UUID) column on `orders`, unique per user | `SELECT ... FOR UPDATE` on `(user_id, client_order_id)` inside the checkout transaction; if found, return the existing order | One column + one `UNIQUE(user_id, client_order_id)` constraint on V13 |
| E2 | Dedicated `idempotency_keys` table (method, key_hash, response_blob, expires_at) | Lookup by key hash before proceeding; response stored | New table, repository, hash logic — general-purpose but heavier |
| E3 | No idempotency; dedupe by inspecting recent orders | Check for a recent PLACED order with the same cart contents + address + slot | Fragile — false negatives; no protection for partial failures |

**Recommendation: E1 (client-order-id on orders).** The customer's frontend generates a UUID per checkout attempt (`crypto.randomUUID()`). It is sent as `client_order_id` in the `POST /api/v1/checkout` body. The checkout:

1. `SELECT ... FOR UPDATE` on `orders WHERE user_id = ? AND client_order_id = ?` — this is the **first** thing inside the `@Transactional` method, inside the lock so concurrent replays serialize.
2. If found → return the existing order's id. No second order, no double reservation.
3. If not found → proceed with the full checkout (§5.5).

**Why this is safe for MySQL/Spring Boot:**
- The `UNIQUE(user_id, client_order_id)` constraint is the backstop: even if two transactions race past the SELECT (impossible under the `FOR UPDATE` lock, but defensive), the second inserts and gets a duplicate-key violation, which Spring translates to a retry or a clean error rather than a duplicate order.
- The key is scoped per user, so two different customers can use the same UUID without collision.
- NULL `client_order_id` is allowed and treated as distinct by MySQL's unique index (NULL ≠ NULL), so orders without a key (POS, Phase 11) coexist.

**Trade-off vs. E2:** E1 only dedupes orders, not other checkout-side effects. But Phase 5 checkout has exactly two side effects — order creation and stock reservation — and both are on the same transactional order row. If a replay finds the order, it returns it and the reservation is already done (it was part of that order's transaction). So E1 is sufficient for Phase 5. A general `idempotency_keys` table (E2) can be introduced if Phase 7's webhook confirmation or Phase 11's POS needs idempotency on non-order effects.

**Retention:** `client_order_id` is retained forever with the order (it is the order's idempotency provenance). No expiry — an order that was placed should always be retrievable by its key.

**Consequence:** one `CHAR(36)` column + one `UNIQUE` constraint on V13. No separate table. The frontend generates UUIDs via `crypto.randomUUID()` (no server round-trip).

---

## Approval checklist

All of the following must be approved before Stage 1 implementation begins:

- [x] **Repo mismatch acknowledged:** `phase-5-cart-checkout` did not exist; branch created from `main @ 76b3048` (approved, item 1).
- [x] **Decision B:** Platform fee config via `AppProperties` (B2), not a `platform_config` table. Default `platformFee = 0.00`, `defaultCommissionRate = 0.10`.
- [x] **Decision C:** Price snapshot at add-to-cart (C1), copied to `order_items` at checkout; quote warns on price drift without refusing.
- [x] **Decision D:** Add `timezone` column to `service_locations` (V13 default `Asia/Kolkata`); min lead time **120 min**, max booking horizon **7 days** — both configurable via `AppProperties`.
- [x] **Decision E:** `client_order_id` UUID column on `orders` (E1); no separate idempotency table in Phase 5.
- [x] **`order_status_history`:** Create the table in V13 and write the initial `PLACED` row in Phase 5.
- [x] **Stock check HTTP code on cart-add:** `409 INSUFFICIENT_STOCK` (not `400` as plan task 5.2 states) — for consistency with the existing codebase.
- [x] **Frontend structure:** Two new feature slices (`features/cart/`, `features/checkout/`) following the existing `features/location/` pattern.
- [x] **Multi-vendor cart:** Confirmed allowed (one order per shop); single `carts` table, `vendor_id` on `cart_items`.

All items checked — Stage 1 implementation may begin upon explicit authorization.
