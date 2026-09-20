# 🌸 FlowerConnect — Implementation Plan (Revision 2.1)

Hyperlocal flower marketplace: **React 18 + Vite** frontend, **Java 17 / Spring Boot 3.x** backend, **MySQL 8** database. Modular monolith.

---

## 1. What changed in this revision

This revision fixes the contradictions and gaps found in the original plan (Revision 1, Phases 0–10). Phase numbers in this document are the **new** numbers.

### Revision 2.1: review fixes

| # | Issue found in Revision 2 | Resolution |
|---|---|---|
| 1 | Roles model unclear (`roles` table vs. `role` column) | One model: a seeded `roles` table plus `users.role_id` FK (0.5, 1.1, section 8) |
| 2 | Token storage decided late (D-5 finalised in Phase 13) | Decided and built in Phase 1: access token in memory only, refresh token in an httpOnly cookie. Phase 13 only audits |
| 3 | `SUSPENDED` / `DISABLED` enforcement unspecified | Task 1.3 defines behaviour for login, refresh and existing access tokens; admin status API in 2.8 |
| 4 | No way to run integration tests | Commands, Maven profile and verify scripts in section 3 and task 0.T |
| 5 | Password reset unthrottled until Phase 13 | Rate limiting moved to Phase 1 (1.11); Phase 13 reviews and extends it |
| 6 | Redis provisioned but unused | Removed from Phase 0 and the architecture; add later only on a measured need |
| 7a | Stock deducted at checkout | Stock is **reserved at checkout and deducted when the vendor accepts** (sections 6.1, 6.2; tasks 5.4, 6.2) |
| 7b | Browser geolocation | **No GPS.** City / area / pincode from a seeded `service_locations` table, created in Phase 2 because vendor registration needs it (D-4) |
| — | Ledger reversal on cancellation had no effect (entries exist only after delivery) | Reversal removed; cancelled orders never reach the ledger, disputes create adjustments |

### Phase map (old → new)

| Revision 1 | Revision 2 | Summary of change |
|---|---|---|
| Phase 0 Scaffolding | **Phase 0** | Error framework moved here (was 10.4); prod-profile skeleton, docs skeleton, test infra added |
| Phase 1 Auth | **Phase 1** | Cookie-based refresh token, account-status enforcement, auth rate limiting, forgot/reset password, admin bootstrap, role-boundary test matrix added; vendor registration moved to Phase 2 |
| Phase 2 Vendor/Admin | **Phase 2** | Delivery polygons dropped (radius only); service locations, delivery settings, opening hours, approval gating, admin user status, audit log added |
| Phase 3 Catalog/Inventory | **Phase 3** | Categories admin-owned; batch tracking replaced by simple stock + optional expiry + stock-movement log; upload validation added |
| Phase 4 Discovery | **Phase 4** | Address book added; discovery uses each vendor's own radius; search is location-aware (city/area/pincode, no GPS) |
| Phase 5 Cart/Checkout | **Phase 5** | Multi-shop cart (one order per shop); DB locking replaces Redis hold; stock reserved at checkout; fee formulas, radius and slot checks |
| Phase 6 Orders | **Phase 6 + Phase 9** | Order management stays in 6 (state machine, history, auto-reject, stock deducted on vendor accept); notifications and real-time split into 9 |
| — | **Phase 7 (new)** | Payments and COD settlement |
| — | **Phase 8 (new)** | Cancellations, refunds and disputes |
| Phase 8 Billing | **Phase 10** | Payouts replaced by a settlement ledger (COD makes vendors owe the platform) |
| Phase 7 POS | **Phase 11** | Commission-free rule, shared lock discipline, idempotency |
| Phase 9 Reviews/Analytics | **Phase 12** | Null-safe ratings, moderation |
| — | **Phase 13 (new)** | Security hardening |
| Phase 10 Polish/Testing | **Phase 14** | Adds demo, documentation and synopsis-reconciliation deliverables |
| — | **Phase 15 (new, optional)** | AI demand prediction (stretch, first to cut) |

### Problems fixed

1. **COD vs. billing:** the original settled vendors with payouts, but with COD the vendor holds the cash and owes the platform. Phase 10 now uses a signed settlement ledger.
2. **Inventory contradiction:** batch tracking vs. "defer batch tracking". Resolved: per-product stock + optional expiry date + movement log.
3. **Categories:** were both global and vendor-scoped. Now admin-owned; vendors only assign.
4. **Delivery reach:** three overlapping definitions (vendor radius, admin polygons, customer radius param). Now one: `distance ≤ vendor.delivery_radius_km`.
5. **Missing requirement coverage:** disputes, refunds and cancellations (Phase 8); payments and COD collection (Phase 7); address book (Phase 4); delivery settings and slots (Phases 2 and 5).
6. **Stock reservation:** Redis TTL hold plus `SELECT FOR UPDATE` was two sources of truth. Now DB-only: stock is reserved at checkout under ordered pessimistic locks and deducted when the vendor accepts. Redis is not provisioned in v1.
7. **Real-time:** `EventSource` cannot send an `Authorization` header. v1 uses polling; SSE is an optional upgrade with a fetch-based client.
8. **Ordering bugs:** vendor registration needed `vendor_profiles` before it existed (Phase 1 → 2); error handling moved from the last phase to the first.
9. **Testing and security:** per-phase test tasks and a recurring RBAC checklist replace "all testing at the end"; security is a dedicated phase.
10. **Estimates and acceptance criteria:** re-estimated with verification overhead; acceptance criteria now include concurrency, security and documentation checks.

---

## 2. Requirements restatement

FlowerConnect has three roles:

| Role | Core experience |
|---|---|
| **Customer** | Swiggy-style discovery: browse nearby shops, search/filter, add to cart (multiple shops allowed), check out, pay (COD in v1), track orders, cancel, raise disputes, rate/review |
| **Vendor** | Shopify/POS-style dashboard: catalog and stock, pricing, delivery settings, process incoming orders, billing and settlement statements, POS for walk-in sales |
| **Admin** | Platform governance: approve/reject/suspend vendors, manage categories and commission rates, resolve disputes, settle vendor accounts, view analytics, audit trail |

**Key domain concepts**

- **Hyperlocal:** city/area/pincode-based discovery (no GPS); each vendor sets its own delivery radius.
- **Perishable stock:** flowers expire; expired stock is written off and the product delisted.
- **Dual revenue streams:** commission on item subtotal, plus an optional flat platform fee.
- **POS mode:** vendors ring up walk-in sales from the same stock pool, commission-free.

---

## 3. Conventions

| Topic | Rule |
|---|---|
| **Roles** | A small seeded `roles` table (`CUSTOMER`, `VENDOR`, `ADMIN`); `users.role_id` is a foreign key to it, one role per user in v1. The role name is carried in the JWT. If the codebase names the vendor role differently (e.g. `FLORIST`), use the code's name consistently everywhere. |
| **Entity names** | Table and entity names below are the design baseline. If the implemented schema splits or renames entities (e.g. separate shop/vendor tables), map names but keep the semantics. |
| **API prefix** | `/api/v1/...` for every endpoint. |
| **Money** | `DECIMAL(10,2)` / `BigDecimal`, currency INR, rounding `HALF_UP` to 2 decimals. Never `double`. |
| **Migrations** | Flyway. Never edit an applied migration; add a new `Vn__description.sql`. |
| **Errors** | One `ApiError` shape via a global `@RestControllerAdvice`. `BusinessException` factories: `badRequest`, `notFound`, `forbidden`, `conflict` (no `new` on static factories). Inject Spring's `ObjectMapper`; never construct one manually (loses the JSR-310 module). |
| **Ownership** | Foreign resource → 403; nonexistent resource → 404. Distinguish the two explicitly. |
| **Queries** | Named parameters only; no string-concatenated JPQL/SQL. |
| **Pagination** | Every list endpoint is paginated. |
| **Docker Compose** | Container-to-container URLs use service names (`mysql:3306`); never read host `.env` values meant for local tooling. Use `docker compose` (space syntax). |
| **Time** | Inject a `Clock` bean into anything time-dependent (schedulers, expiry, timeouts) so tests can control time. |

### Test commands

| Purpose | Command |
|---|---|
| Backend unit tests (default run) | `./mvnw test` (Windows: `mvnw.cmd test`); excludes tests tagged `integration` |
| Backend integration tests | `./mvnw verify -Pintegration`; runs only `integration`-tagged tests. **Docker must be running** (Testcontainers starts MySQL) |
| Frontend checks | In `frontend/`: `npm run lint`, `npm run typecheck`, `npm test`, `npm run build` |
| Everything | `scripts/verify.sh` (or `scripts/verify.ps1` on Windows): backend unit → backend integration → frontend lint, typecheck, test, build; stops at the first failure |

If Docker is unavailable, integration tests cannot run. Record that in `docs/known-issues.md` instead of marking the phase done.

### Definition of Done (every phase)

A phase is closed only when **all** of these are true, each backed by real terminal output:

1. Backend compiles; app boots cleanly against MySQL.
2. New unit tests pass (`./mvnw test`) **and** at least one integration test for the phase's critical flow passes (`./mvnw verify -Pintegration`).
3. **RBAC checklist:** for every new endpoint, one test each for unauthenticated (401), wrong role (403), foreign resource (403), nonexistent resource (404), correct role (2xx).
4. Frontend lint, typecheck, tests and build pass (when the phase has UI).
5. Manual end-to-end check performed (real requests or UI, not only unit tests).
6. `docs/progress.md` and `docs/known-issues.md` updated; committed with a conventional message.

---

## 4. Open decisions

Each has a recommended default so work can proceed. Record the final choice in `docs/decisions.md`.

| # | Decision | Recommended default | Needed before |
|---|---|---|---|
| D-1 | Online payments in v1? | **COD only.** Amend the synopsis (Stripe → future scope) or build the optional Phase 7.4 test-mode gateway | Phase 5 |
| D-2 | Who delivers? | **Vendor self-delivery.** No delivery-partner role in v1 | Phase 5 |
| D-3 | GST / tax | Prices are **tax-inclusive**; no separate tax computation; UI says "incl. taxes" | Phase 5 |
| D-4 | Location input | **No GPS.** Customers and vendors choose city / area / pincode from a **seeded `service_locations` table** (demo region); coordinates are that location's centroid | Phase 2 |
| D-5 | Token storage | **Decided:** access token in memory only; refresh token in an **httpOnly, Secure, SameSite=Strict cookie** scoped to `/api/v1/auth` | Phase 1 (built there; Phase 13 audits) |
| D-6 | Suspended vendor with in-flight orders | Hidden from discovery immediately; **existing orders continue to completion** | Phase 2 |
| D-7 | AI demand prediction in scope? | **Stretch only** (Phase 15); first thing cut | Phase 14 |

---

## 5. Architecture overview

```
┌────────────────────────────────────────────────────────────┐
│                      React SPA (Vite)                      │
│   ┌───────────┐   ┌──────────────┐   ┌────────────────┐    │
│   │  Customer │   │    Vendor    │   │     Admin      │    │
│   │    App    │   │  Dashboard   │   │   Dashboard    │    │
│   └─────┬─────┘   └──────┬───────┘   └───────┬────────┘    │
└─────────┬────────────────┬───────────────────┬─────────────┘
          │                │                   │              
                  REST (polling for status)                  
┌─────────┴────────────────┴───────────────────┴─────────────┐
│               Spring Boot 3.x API  (/api/v1)               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Spring Security (JWT + role-based access)            │  │
│  ├──────────────────────────────────────────────────────┤  │
│  │ Modules: auth · vendor · catalog · inventory · geo   │  │
│  │  search · cart · order · payment · dispute · billing │  │
│  │  pos · notification · admin · audit                  │  │
│  ├──────────────────────────────────────────────────────┤  │
│  │ Spring Data JPA · Domain events · @Scheduled jobs    │  │
│  └───────────────────────────┬──────────────────────────┘  │
└──────────────────────────────┬─────────────────────────────┘
                               │
                    ┌──────────┴───────────┐
                    │      MySQL 8.x       │
                    │  (single source of   │
                    │        truth)        │
                    └──────────────────────┘
```

**Backend package layout**

```
com.flowerconnect
├── common/        # BaseEntity, ApiResponse/ApiError, BusinessException, config, Clock
├── auth/          # AuthController, JwtService, User, RefreshToken, PasswordResetToken
├── vendor/        # VendorProfile, VendorHours, registration, settings
├── catalog/       # Category, Product, ProductImage, StorageService
├── inventory/     # Inventory, StockMovement, StockService, ExpiryScheduler
├── geo/           # GeoService (Haversine), ServiceLocation (seeded city/area/pincode), LocationUtils
├── search/        # SearchService, filters, sorting
├── address/       # Address (customer address book)
├── cart/          # Cart, CartItem, CartService
├── order/         # Order, OrderItem, OrderStateMachine, OrderStatusHistory
├── payment/       # Payment, PaymentGateway (Cod / optional online)
├── dispute/       # Dispute, DisputeService, CancellationService
├── billing/       # Commission, Settlement, SettlementEntry, BillingService
├── pos/           # PosTransaction, PosController
├── review/        # Review, RatingAggregator
├── notification/  # Notification, NotificationService, EmailService, templates
├── audit/         # AuditLog, AuditService
└── admin/         # AdminController, PlatformConfig, Analytics
```

**Frontend structure**

```
src/
├── api/           # Axios instance, typed API clients
├── components/    # Shared UI components
├── features/
│   ├── auth/      # Login, Register, Reset password, guards
│   ├── customer/  # Discover, Search, Storefront, Cart, Checkout, Orders, Disputes
│   ├── vendor/    # Dashboard, Catalog, Inventory, Orders, POS, Billing, Settings
│   └── admin/     # Vendors, Categories, Disputes, Settlements, Analytics, Config
├── hooks/         # useAuth, useCart, useLocation, usePolling
├── layouts/       # CustomerLayout, VendorLayout, AdminLayout
├── routes/        # Route definitions + ProtectedRoute
├── store/         # Zustand stores (auth, cart, ui)
├── types/         # Shared TypeScript types
└── utils/         # Formatters, validators, geo helpers
```

---

## 6. Domain rules (single source of truth)

These rules are referenced by several phases. Implement each in exactly one service.

### 6.1 Order state machine (`OrderStateMachine`)

Every transition goes through one service, is validated against this table (illegal transition → 409), and writes an `order_status_history` row (actor, reason, timestamp).

| From | To | Actor | Conditions / side effects |
|---|---|---|---|
| — | `PLACED` | system (checkout, COD) | Stock **reserved** in the checkout transaction (not deducted) |
| — | `PENDING_PAYMENT` | system (checkout, online) | Stock **reserved**; expires after N minutes (Phase 7) |
| `PENDING_PAYMENT` | `PLACED` | system (payment confirmed) | Reservation unchanged |
| `PENDING_PAYMENT` | `CANCELLED` | system / customer | Payment failed or timed out; reservation released |
| `PLACED` | `CONFIRMED` | vendor | **Reserved stock committed** (`quantity −= q`, `reserved_quantity −= q`); re-checks `quantity ≥ q`, else 409 |
| `PLACED` | `REJECTED` | vendor (reason required) | Reservation released; refund if already paid |
| `PLACED` | `CANCELLED` | customer / admin / system auto-reject | Reservation released; refund if already paid |
| `CONFIRMED` | `PREPARING` | vendor | — |
| `CONFIRMED` | `CANCELLED` | customer / vendor (reason) / admin | Restock (`quantity += q`) |
| `PREPARING` | `OUT_FOR_DELIVERY` | vendor | — |
| `PREPARING` | `CANCELLED` | vendor (reason) / admin | **No restock** (stock stays deducted); reason kept in history |
| `OUT_FOR_DELIVERY` | `DELIVERED` | vendor | COD: `payment_status = COLLECTED`; commission row and ledger entry created; review unlocked |
| `OUT_FOR_DELIVERY` | `CANCELLED` | admin only (failed delivery) | No restock; no ledger effect (ledger entries are created only at delivery) |

`DELIVERED`, `CANCELLED`, `REJECTED` are terminal. Post-delivery problems go through **disputes** (Phase 8).

**Payment fields on `orders`:** `payment_method` (`COD`, `ONLINE`) and `payment_status` (`PENDING`, `PAID`, `COLLECTED`, `FAILED`, `REFUND_PENDING`, `REFUNDED`).

### 6.2 Inventory rules (`StockService`)

- `available = quantity − reserved_quantity`.
- Every stock change writes a `stock_movements` row (`STOCK_IN`, `STOCK_OUT`, `ADJUSTMENT`, `RESERVE`, `RELEASE`, `SALE_ONLINE`, `SALE_POS`, `RESTOCK`, `WASTE`).
- **Checkout (all payment methods):** `reserved_quantity += q`. Stock is **not** deducted at checkout.
- **Vendor accepts (`PLACED → CONFIRMED`):** `quantity −= q; reserved_quantity −= q` (`SALE_ONLINE`). The accept re-checks `quantity ≥ q`, because an expiry write-off or manual adjustment may have reduced stock; if not, 409 and the vendor must reject.
- **Before acceptance** (vendor reject, customer/admin cancel, auto-reject, payment failure or timeout): `reserved_quantity −= q` (release). `quantity` is unchanged.
- **After acceptance:** cancelled before `PREPARING` → `quantity += q` (`RESTOCK`); cancelled from `PREPARING` onward → no restock.
- **Locking:** pessimistic lock (`SELECT ... FOR UPDATE`) on inventory rows **ordered by product id** to avoid deadlocks, for every operation above. Insufficient available stock at checkout → 409.
- **Manual adjustments** may not push `quantity` below `reserved_quantity` (409).
- **POS sales** use the same lock discipline and may only consume available stock.
- **Expiry:** expired stock is written off (`WASTE`) and the product delisted. Pending orders affected are caught by the accept re-check.

### 6.3 Fee formulas (`PricingService`)

- `distance_km` = Haversine between the delivery address and the vendor. Both coordinates are area centroids from `service_locations`, so distance is an area-level approximation.
- `delivery_fee = base_delivery_fee + per_km_fee × distance_km`, or `0` if the shop's subtotal ≥ `free_delivery_above`.
- `platform_fee` = flat amount from `platform_config` (default 0), charged per order.
- `commission = subtotal × rate`, where the rate is the vendor override or else the global default. **Commission applies to item subtotal only**, not to delivery or platform fees.
- Delivery fee goes to the vendor (self-delivery); platform fee goes to the platform.
- The applied commission rate is **snapshotted** on the commission row.

### 6.4 Money flow and settlement

| Order type | Who collects | Ledger entry for the vendor (signed; + = platform owes vendor) |
|---|---|---|
| **Online** | Platform collects `S + D + P` | `+ (S − C + D)` |
| **COD** | Vendor collects `S + D + P` in cash | `− (C + P)` (vendor owes the platform) |
| **POS** | Vendor | none (commission-free, outside the marketplace ledger) |

`S` = item subtotal, `D` = delivery fee, `P` = platform fee, `C` = commission. Weekly settlement **nets** online credits against COD dues.

---

## 7. Implementation phases

Every phase also carries the **Definition of Done** (section 3). Tasks marked **T** are test tasks and are part of the phase, not deferred.

### Phase 0: Scaffolding, infrastructure and foundations
*Complexity: Medium · Est: 5–7 h*

| # | Task | Detail |
|---|---|---|
| 0.1 | Spring Boot init | Web, JPA, Security, Validation, MySQL driver, Lombok, MapStruct, Flyway, Actuator; Testcontainers and Mockito for tests |
| 0.2 | React init | Vite, React 18, TypeScript, React Router 6, Axios, TanStack Query, Tailwind, Zustand, react-hook-form, Zod, Vitest + React Testing Library |
| 0.3 | Docker Compose | MySQL 8, Mailhog, backend, frontend; `frontend/.dockerignore` excluding `node_modules`; service-name URLs inside containers. **No Redis in v1** (section 9) |
| 0.4 | Base structure | Package layout and frontend folders from section 5 |
| 0.5 | Flyway baseline | `V1__baseline.sql`: `roles` (seeded: `CUSTOMER`, `VENDOR`, `ADMIN`) and `users` (with `role_id` FK). Later phases add their own migrations |
| 0.6 | CI-ready scripts | `mvnw` wrapper, lint/format configs, `.env.example` (never commit `.env`), and `scripts/verify.sh` / `scripts/verify.ps1` running the full check sequence from section 3 |
| 0.7 | Error framework | `ApiError`, `BusinessException` factories, `@RestControllerAdvice`, injected `ObjectMapper`, injectable `Clock` bean |
| 0.8 | Profiles | `application-dev.yml`, `application-prod.yml` skeleton (env-var placeholders), health endpoint |
| 0.9 | Docs skeleton | `docs/progress.md`, `docs/known-issues.md`, `docs/decisions.md` (records D-1 to D-7) |
| 0.T | Test baseline | Surefire excludes tag `integration` by default; a Maven profile `integration` runs only tagged tests (Testcontainers, Docker required). One sample unit test and one sample integration test prove both commands in section 3 work |

### Phase 1: Authentication and user management
*Complexity: Medium · Est: 9–11 h*

| # | Task | Detail |
|---|---|---|
| 1.1 | Roles and users | `roles` (seeded lookup: `CUSTOMER`, `VENDOR`, `ADMIN`) and `users`: id, email, phone, password_hash, `role_id` (FK → `roles`; one role per user in v1), status (`ACTIVE`, `SUSPENDED`, `DISABLED`), created_at. **Vendor approval status lives only in `vendor_profiles`** (single source of truth) |
| 1.2 | Security and tokens | Spring Security + JWT. **Access token:** 15 minutes, returned in the JSON body, held in memory only by the SPA. **Refresh token:** 7 days, in an **httpOnly, Secure, SameSite=Strict cookie scoped to `/api/v1/auth`**, never readable by JavaScript (`Secure` on in prod, configurable off for plain-http local dev). Rotation with reuse detection, BCrypt passwords, refresh tokens stored hashed (SHA-256) and revoked on logout, `@PreAuthorize` by role. Finalises D-5. SPA and API must be same-site in every environment (Vite dev proxy locally, one reverse-proxied origin in prod); CORS allows credentials for the exact configured origin only |
| 1.3 | Account status enforcement | **Login:** status is checked only after the password verifies; `SUSPENDED` → 403 `ACCOUNT_SUSPENDED`; `DISABLED` → 401 identical to invalid credentials. **Refresh:** rejected (401) unless `ACTIVE`. **Status change:** `UserStatusService` revokes all of the user's refresh tokens in the same transaction. **Existing access tokens:** the JWT filter does a primary-key status lookup on every request, so suspension applies from the next request (the 15-minute lifetime is only a backstop). User status governs authentication; `vendor_profiles.status` governs vendor features only |
| 1.4 | Auth API | `POST /api/v1/auth/register` (**customer only**), `/login`, `/refresh` (reads the cookie, returns a new access token, rotates the cookie), `/logout` (revokes and clears the cookie), `GET /api/v1/users/me` |
| 1.5 | Password reset | `/auth/forgot-password`, `/auth/reset-password`; single-use, expiring, hashed token; email via Mailhog; identical response whether or not the email exists; throttled by 1.11 |
| 1.6 | Admin bootstrap | First admin created from env-configured credentials on first boot (dev seed); no public admin registration |
| 1.7 | React auth pages | Login, customer register, forgot/reset password. Vendor onboarding entry point links to Phase 2 |
| 1.8 | Route guards | `<ProtectedRoute roles={['VENDOR']} />`, redirect logic, no stuck spinner if the initial session restore fails |
| 1.9 | Auth store and interceptor | Zustand store holds the access token **in memory only**: no `localStorage` / `sessionStorage` persistence of tokens. Axios uses `withCredentials`. On app load a silent `/auth/refresh` restores the session; the "initial load finished" flag is set on success **and** failure. On 401 the interceptor refreshes once (single-flight) and retries, and **skips public endpoints** (a 401 from `/auth/refresh` must not trigger another refresh). `/auth/refresh` and `/auth/logout` are cookie-authenticated, so they require a custom header (e.g. `X-Requested-With`) and an `Origin` check; every other endpoint uses the Bearer header and is not exposed to CSRF |
| 1.10 | Role-boundary test utility | Reusable `RoleBoundaryTester` running one request as CUSTOMER / VENDOR / ADMIN / unauthenticated and asserting expected status codes; reused by every later phase |
| 1.11 | Rate limiting | Bucket4j (in memory) from day one: login and register per IP and per email, refresh per IP, forgot/reset password limited per email and per IP (e.g. 5 requests per hour); 429 with `Retry-After`. Limits live in configuration |
| 1.T | Tests | Register/login/refresh/logout flows; rotation reuse detection; reset-token expiry and single use; **status matrix** (suspended and disabled users cannot log in, refresh, or use an already-issued access token); cookie flags; refresh without the custom header rejected; 429 behaviour |

### Phase 2: Vendor profiles, delivery settings and admin approval
*Complexity: Medium · Est: 8–10 h*

| # | Task | Detail |
|---|---|---|
| 2.1 | Service locations | Seeded `service_locations` (city, area, pincode, lat, lng: one centroid per area) loaded by a Flyway migration for the demo region; public `GET /api/v1/locations` (city → areas → pincodes, searchable by pincode or area name). The **only** source of coordinates in the system; no GPS (D-4). Build this first: vendor registration, addresses and discovery depend on it |
| 2.2 | `vendor_profiles` entity | business_name, description, address, `service_location_id` (lat/lng copied from that location's centroid), `delivery_radius_km`, logo_url, `status` (`PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `SUSPENDED`), `commission_rate` (nullable override), `avg_rating` and `review_count` (nullable/0 until reviews exist) |
| 2.3 | Delivery settings | On the profile: `min_order_amount`, `base_delivery_fee`, `per_km_fee`, `free_delivery_above`, `prep_time_minutes`, `slot_duration_minutes`, `max_orders_per_slot`, `accepting_orders` |
| 2.4 | Opening hours | `vendor_hours` (weekday, open, close, closed flag); used to compute delivery slots |
| 2.5 | Vendor registration API | `POST /api/v1/vendors/register` creates the user **and** profile in one transaction → `PENDING_APPROVAL`; the vendor selects city / area / pincode from `service_locations`. `GET/PUT /api/v1/vendors/profile` for the vendor's own profile and settings |
| 2.6 | Admin vendor management | `GET /api/v1/admin/vendors` (filter by status), approve, reject (reason), suspend, reinstate. Every action writes an `audit_log` row |
| 2.7 | Approval gating | Only `APPROVED` vendors can use catalog/order endpoints and appear in discovery. A suspended vendor is hidden immediately; in-flight orders continue (D-6) |
| 2.8 | Admin user status | `PATCH /api/v1/admin/users/{id}/status` (`ACTIVE` / `SUSPENDED` / `DISABLED`, reason required) built on the `UserStatusService` from 1.3, so refresh tokens are revoked; audit-logged; an admin cannot change their own status |
| 2.9 | Vendor dashboard shell | Sidebar navigation, stats cards, profile and settings editor (location, radius, hours, fees) |
| 2.10 | Admin dashboard shell | Vendor list with approve/reject/suspend actions and reason dialog; user list with status actions |
| 2.T | Tests | Location seed integrity and lookup; registration atomicity (no orphan user or profile on failure); approval state transitions; gating (pending/suspended vendor blocked); user status API revokes access; RBAC checklist; audit rows written |

> **Dropped:** admin-defined delivery-zone polygons and the `delivery_zones` table. Delivery reach is the vendor's radius only (v1).

### Phase 3: Catalog and inventory management
*Complexity: High · Est: 7–9 h*

| # | Task | Detail |
|---|---|---|
| 3.1 | Category entity | Hierarchical (Roses, Bouquets, Arrangements, Occasions). **Admin-managed CRUD only**; vendors read and assign. Seeded initial set |
| 3.2 | Product entity | vendor_id, name, slug (collision-safe suffix), description, category_id, base_price, status. Images live only in `product_images` (ordered, one primary); **no images JSON column** |
| 3.3 | Inventory entity | One row per product: `quantity`, `reserved_quantity`, `low_stock_threshold`, optional `expiry_date`. Created automatically at quantity 0 when a product is created |
| 3.4 | Stock movement log | `stock_movements` records every change (type, quantity delta, reason, reference id, actor) |
| 3.5 | Catalog API | Vendor-scoped product CRUD; ownership: 403 foreign / 404 missing |
| 3.6 | Inventory API | Stock in/out, adjustment with reason, write-off, low-stock list; availability = quantity − reserved; adjustments may not drop `quantity` below `reserved_quantity` (409) |
| 3.7 | Expiry scheduler | `@Scheduled` job using the injected `Clock`: expired stock → `WASTE` movement and product delisted |
| 3.8 | Image handling | `StorageService` interface: local disk (dev), S3 implementation pluggable by profile. **Validation:** type whitelist (JPEG/PNG/WebP) checked by content sniffing, max size, random filenames, no path traversal, resize/compress |
| 3.9 | Vendor catalog UI | Product list and form (Shopify-style), inventory table with stock adjustments, low-stock badges |
| 3.T | Tests | Ownership checks; slug collision; expiry job with a fake clock; upload rejection cases; RBAC checklist |

### Phase 4: Customer addresses, discovery and search
*Complexity: High · Est: 8–10 h*

| # | Task | Detail |
|---|---|---|
| 4.1 | Address book | `addresses`: label, line1, line2, `service_location_id` (city / area / pincode), lat and lng (copied from that location's centroid), `is_default`. CRUD at `/api/v1/addresses`, owner-only |
| 4.2 | Location picker | City / area / pincode selector backed by `service_locations` (created in Phase 2); the chosen location is remembered for the session. **No browser Geolocation API and no GPS** (D-4) |
| 4.3 | Geo discovery | `GET /api/v1/discover?locationId=` returns `APPROVED`, accepting vendors where `distance ≤ vendor.delivery_radius_km`. Bounding-box prefilter on indexed lat/lng, then Haversine; sorted by distance; paginated. Coordinates are area centroids, so distance is an area-level approximation. (MySQL `POINT` + spatial index / `ST_Distance_Sphere` is an optional later optimisation.) |
| 4.4 | Product search | `GET /api/v1/search?q=&category=&priceMin=&priceMax=&sort=&vendorId=&locationId=`; only active, in-stock products from vendors that deliver to the given location |
| 4.5 | Storefront API | `GET /api/v1/vendors/{id}/storefront`: public profile and active products; 404 for non-approved vendors |
| 4.6 | Customer home page | Location picker → vendor cards: distance, delivery fee, prep time/ETA, minimum order, rating (shows "New" when null) |
| 4.7 | Search and filter UI | Search bar, category chips, price slider, sort dropdown |
| 4.8 | Storefront page | Banner, product grid, add-to-cart per item |
| 4.9 | Product detail modal | Image carousel, description, optional **gift note** (a free-text `note` stored on the cart/order item; no variant tables in v1) |
| 4.T | Tests | Public endpoints work anonymously; zero results return an empty page, not an error; radius edge cases; unknown `locationId` rejected; parameter binding; address ownership |

### Phase 5: Cart and checkout
*Complexity: High · Est: 7–9 h*

| # | Task | Detail |
|---|---|---|
| 5.1 | Cart entities | `carts` and `cart_items` (product_id, vendor_id, quantity, `price_snapshot`, `note`). **Multiple shops allowed**; the cart view groups items by shop |
| 5.2 | Cart API | Add/update/remove; stock checked on add (400 if insufficient); ownership 403/404 |
| 5.3 | Checkout quote | `POST /api/v1/checkout/quote`: per-shop subtotal, delivery fee, platform fee, total. Validates minimum order, delivery radius against the chosen address, and slot availability. Returns per-shop errors |
| 5.4 | Checkout | One `@Transactional` operation: ordered pessimistic locks, stock re-check under the lock (409 on shortage), one `Order` per shop with address snapshot, fees and slot; all-or-nothing across shops; clears the cart. Stock is **reserved** for every payment method (`reserved_quantity += q`), never deducted at checkout; **COD → `PLACED`**, **online → `PENDING_PAYMENT`** |
| 5.5 | Delivery slots | Slots computed from `vendor_hours`, `prep_time_minutes`, `slot_duration_minutes`, and `max_orders_per_slot`; slot chosen per shop |
| 5.6 | Cart UI | Slide-out drawer grouped by shop, quantity stepper, price breakdown |
| 5.7 | Checkout page | Address selection, per-shop slot picker, payment method, order summary, place-order CTA |
| 5.T | Tests | **Concurrency:** N parallel checkouts for the last unit produce exactly one order; multi-shop all-or-nothing rollback (reservations included); checkout reserves but does not deduct; fee formula unit tests; minimum-order and radius rejections; RBAC checklist |

### Phase 6: Order management
*Complexity: High · Est: 7–9 h*

| # | Task | Detail |
|---|---|---|
| 6.1 | Order state machine | `OrderStateMachine` implementing section 6.1; illegal transition → 409; every transition writes `order_status_history` |
| 6.2 | Vendor actions API | **Accept** (commits the reserved stock; 409 if `quantity < q`, see section 6.2), **reject** (reason; releases the reservation), prepare, out for delivery, delivered (COD collection recorded here) |
| 6.3 | Customer order API | `GET /api/v1/orders` (paginated), `GET /api/v1/orders/{id}` (ownership) with status timeline from history |
| 6.4 | Vendor order API | Incoming orders by status and date; today's summary |
| 6.5 | Auto-reject job | Unaccepted `PLACED` orders older than a configurable number of minutes (in `platform_config`) are auto-cancelled with the reservation released and a notification |
| 6.6 | Customer orders UI | History, order detail with status stepper (polling, see Phase 9) |
| 6.7 | Vendor order board | Kanban-style board: New → Preparing → Out for delivery → Delivered |
| 6.T | Tests | Exhaustive transition table (every allowed and disallowed pair); accept commits stock, reject and auto-reject release it; accept fails with 409 when stock was written off; vendor cannot touch another vendor's order; auto-reject with a fake clock; RBAC checklist |

### Phase 7: Payments and COD settlement *(new)*
*Complexity: Medium · Est: 4–6 h (+5–7 h if the optional online gateway is built)*

| # | Task | Detail |
|---|---|---|
| 7.1 | Payments model | `payments` (order_id, method, provider, provider_ref, amount, status, timestamps); `payment_status` lifecycle from section 6.1 |
| 7.2 | Gateway abstraction | `PaymentGateway` interface; `CodPaymentProvider` (v1). Online provider is pluggable |
| 7.3 | COD collection | Marking an order `DELIVERED` sets `payment_status = COLLECTED`; vendor "cash to collect / cash collected" summary |
| 7.4 | *Optional (D-1):* online payment | Test-mode gateway: create intent, **signature-verified webhook**, idempotent confirmation `PENDING_PAYMENT → PLACED`, failure releases the reservation |
| 7.5 | Reservation expiry job | Scheduled sweeper cancels `PENDING_PAYMENT` orders older than N minutes (`PAYMENT_TIMEOUT`) and releases reserved stock. Only relevant when online payment exists, but implemented so reservations can never leak |
| 7.6 | Payment UI | Payment-method selector at checkout (COD default; online only if 7.4 is built), payment status on the order page |
| 7.T | Tests | COD flow end to end; webhook idempotency and bad-signature rejection (if 7.4); expiry sweeper releases stock |

### Phase 8: Cancellations, refunds and disputes *(new)*
*Complexity: Medium · Est: 5–6 h*

| # | Task | Detail |
|---|---|---|
| 8.1 | Cancellation rules | Implement the "who / when" columns of section 6.1 in `CancellationService`; reason required for vendor and admin cancellations |
| 8.2 | Cancel API | `POST /api/v1/orders/{id}/cancel` with reason. Releases the reservation, restocks, or leaves stock deducted, per section 6.2, and publishes an `OrderCancelled` domain event (used by notifications; no ledger effect, because ledger entries exist only after delivery) |
| 8.3 | Refund handling | Paid online orders: `REFUND_PENDING → REFUNDED` (admin marks in v1, or gateway refund if 7.4 exists). COD orders cancelled before delivery: no money has moved |
| 8.4 | Disputes | `disputes`: order_id, raised_by, reason_code, description, status (`OPEN`, `UNDER_REVIEW`, `RESOLVED_REFUND`, `RESOLVED_PARTIAL`, `RESOLVED_REJECTED`), resolution_note, resolved_by, timestamps. Customer raises one per order within N days of `DELIVERED`; vendor responds; **admin resolves** |
| 8.5 | Dispute resolution effects | Refund or partial refund creates a ledger `ADJUSTMENT` (Phase 10), writes an audit row, and sends notifications |
| 8.6 | UI | Customer "Report a problem", vendor response form, admin dispute queue and resolution dialog |
| 8.T | Tests | Cancel permissions by state and actor; release vs. restock vs. no restock; one open dispute per order; dispute window; RBAC checklist |

### Phase 9: Notifications and real-time updates
*Complexity: Medium · Est: 4–5 h*

| # | Task | Detail |
|---|---|---|
| 9.1 | Domain events | `OrderPlaced`, `OrderStatusChanged`, `OrderCancelled`, `DisputeOpened/Resolved`, `VendorApproved/Rejected`, handled with `@TransactionalEventListener(AFTER_COMMIT)` and `@Async` so a rollback never sends a notification |
| 9.2 | In-app notifications | `notifications` table; list, unread count, mark-as-read API; notification bell in the UI |
| 9.3 | Email | Templates and an async sender (Mailhog in dev); failures are logged and never break the order flow |
| 9.4 | Polling | TanStack Query `refetchInterval` (5–10 s): vendor **new-order alert** (toast/sound) and customer order tracking |
| 9.5 | *Optional:* SSE | `SseEmitter` per user with heartbeat; client uses a **fetch-based SSE library** because `EventSource` cannot send an `Authorization` header; disable proxy buffering |
| 9.T | Tests | No notification on rolled-back transactions; notification ownership; unread counts |

### Phase 10: Billing, commissions and settlement ledger
*Complexity: High · Est: 8–10 h*

| # | Task | Detail |
|---|---|---|
| 10.1 | Commission rates | Global default in `platform_config`, per-vendor override on `vendor_profiles`; the rate in force is snapshotted per order |
| 10.2 | Commission record | `commissions` row created once when an order becomes `DELIVERED` (unique on `order_id`, so idempotent): order_id, vendor_id, subtotal, rate, commission_amount, platform_fee |
| 10.3 | Settlement ledger | `settlement_entries` (vendor_id, order_id nullable, type, signed amount, created_at). Types: `ONLINE_ORDER_CREDIT`, `COD_DUES`, `DISPUTE_ADJUSTMENT`, `SETTLEMENT_PAYMENT`. Amounts follow section 6.4. Entries are created only when an order is delivered; cancelled orders never reach the ledger |
| 10.4 | Settlement cycles | Weekly scheduled generation per vendor into `settlements`: period, total credits, total dues, **net amount**, direction (`PLATFORM_PAYS_VENDOR`, `VENDOR_PAYS_PLATFORM`, `ZERO`), status (`PENDING → SETTLED`) with an admin reference note. Online credits are netted against COD dues |
| 10.5 | Vendor billing UI | Revenue dashboard, commission breakdown, cash to remit, settlement history, downloadable statements (CSV/PDF) |
| 10.6 | Admin billing UI | Platform revenue (commission + platform fees), per-vendor report, outstanding dues, mark settlement as settled |
| 10.7 | Overdue dues policy | Admin can flag or suspend vendors with dues overdue beyond a configured period (audit-logged) |
| 10.T | Tests | Formula unit tests with `BigDecimal` rounding; online vs. COD ledger amounts; dispute-adjustment idempotency; netting; a full week generates the expected settlement |

### Phase 11: POS (walk-in sales)
*Complexity: Medium · Est: 5–6 h*

| # | Task | Detail |
|---|---|---|
| 11.1 | POS entities | `pos_transactions` and `pos_items`: vendor_id, items, total, payment_method (`CASH`, `CARD`, `UPI`, recorded as a label only; no gateway), timestamp |
| 11.2 | POS API | Quick-sale endpoint (no cart persistence) with an **idempotency key** against double submit; daily summary |
| 11.3 | Inventory sync | Same lock discipline and pool as checkout; writes `SALE_POS` movements; a sale may only consume **available** stock (`quantity − reserved_quantity`), so it cannot take units reserved for pending orders; shortage → 409 |
| 11.4 | POS UI | Fullscreen layout: product grid with quick-add, running total, payment toggle, print receipt; tablet-optimised |
| 11.5 | POS reports | Daily/weekly sales summary in the vendor dashboard. POS revenue is **commission-free** and shown separately from marketplace revenue |
| 11.T | Tests | POS and online order racing for the last unit: exactly one succeeds; idempotency key replay; POS does not create ledger entries |

### Phase 12: Ratings, reviews and analytics
*Complexity: Medium · Est: 5–6 h*

| # | Task | Detail |
|---|---|---|
| 12.1 | Review entity | `reviews`: order_id (unique), customer_id, vendor_id, rating 1–5, comment, vendor_reply, `hidden` flag, created_at |
| 12.2 | Review API | Submit (delivered orders only, owner only), vendor reply, admin hide/unhide; aggregate `avg_rating` and `review_count` updated on `vendor_profiles` |
| 12.3 | Review UI | Stars on vendor cards ("New" when none), review list on storefront, post-delivery prompt |
| 12.4 | Admin analytics | Total orders, revenue, active vendors/customers, top vendors, dispute rate; date-range aggregation queries with supporting indexes |
| 12.5 | Vendor analytics | Order trends, revenue chart, top-selling products, repeat-customer rate; POS shown separately |
| 12.T | Tests | Only delivered orders can be reviewed; one review per order; aggregate recalculation; hidden reviews excluded |

### Phase 13: Security hardening *(new)*
*Complexity: Medium · Est: 3–4 h*

The core protections (cookie-based tokens, account-status enforcement, auth rate limiting) are built in Phase 1. This phase audits and extends them.

| # | Task | Detail |
|---|---|---|
| 13.1 | Rate-limit review | Extend limits to other sensitive endpoints (checkout, dispute creation, uploads), tune values from real usage, add 429 tests. Move to a shared store only if the app ever runs on more than one instance |
| 13.2 | Upload security audit | Re-verify Phase 3.8 rules; storage outside the web root; served with correct `Content-Type` and `X-Content-Type-Options: nosniff` |
| 13.3 | CORS, headers and CSRF | Explicit allowed origins per profile, credentials only for the configured origin; security headers; verify that the cookie-authenticated endpoints (`/auth/refresh`, `/auth/logout`) enforce the custom-header and `Origin` checks from 1.9 |
| 13.4 | Token storage audit | Verify cookie flags (httpOnly, Secure in prod, SameSite=Strict, path) and that no token appears in `localStorage`, `sessionStorage`, logs or URLs |
| 13.5 | Audit coverage | Vendor approve/reject/suspend, commission changes, settlement marking, dispute resolution, user status changes: who, what, when, before/after |
| 13.6 | Endpoint access sweep | Automated test that enumerates every controller mapping and asserts an explicit access rule; full RBAC matrix run |
| 13.7 | Validation and error hygiene | No stack traces in responses; no user enumeration in login/reset; password policy; request-size limits |
| 13.8 | Dependency and secrets scan | `npm audit`, OWASP dependency-check, secret scan; no secrets in the repo; actuator endpoints restricted in prod |
| 13.T | Tests | Limits on the extended endpoints; sweep test fails when a new endpoint has no rule; upload attack samples rejected; cookie flags asserted |

### Phase 14: Testing, polish, production readiness and deliverables
*Complexity: Medium · Est: 8–10 h*

| # | Task | Detail |
|---|---|---|
| 14.1 | Integration tests | `@SpringBootTest` + Testcontainers (MySQL) for the full journey: register → discover → cart → checkout → vendor accepts → delivered → commission → weekly settlement; plus concurrency scenarios |
| 14.2 | React tests | Vitest + React Testing Library for key components and flows |
| 14.3 | API documentation | SpringDoc OpenAPI (`/swagger-ui.html`); each endpoint documents its required role |
| 14.4 | Responsive pass | Mobile-first customer app; desktop/tablet vendor dashboard; tablet POS |
| 14.5 | Seed data | Dev-profile seed: demo vendors, categories, products with flower photos, and documented demo accounts for CUSTOMER, VENDOR and ADMIN |
| 14.6 | Production readiness | Profiles (dev/staging/prod), externalised config, health/readiness, prod-like `docker compose` run |
| 14.7 | Performance sanity | `EXPLAIN` on discovery, search and order-list queries; N+1 check; pagination verified everywhere |
| 14.8 | Documentation | README (setup incl. Windows/Docker notes), architecture and ER diagram, API summary, known issues, test report |
| 14.9 | Demo script | Step-by-step walkthrough of the full order flow for all three roles with expected results |
| 14.10 | Synopsis reconciliation | Resolve every row of section 10: amend the synopsis text or implement the item |
| 14.11 | Clean-clone rehearsal | Fresh clone, fresh database, `docker compose up`, run the demo script end to end |
| 14.12 | Report material | Screenshots, results, limitations, future scope |

### Phase 15: AI demand prediction *(optional stretch; first to cut)*
*Complexity: Medium · Est: 6–8 h*

| # | Task | Detail |
|---|---|---|
| 15.1 | Sales history | Aggregate daily sales per product from orders and POS; seed clearly labelled **synthetic** history for the demo |
| 15.2 | Forecast service | Baseline statistical forecast (moving average / exponential smoothing with day-of-week factors and configurable festival multipliers). Describe it honestly as a statistical baseline, not deep learning |
| 15.3 | API | `GET /api/v1/vendors/me/forecast?days=7` |
| 15.4 | Vendor UI | Forecast chart and "suggested stock-up" table |
| 15.5 | Evaluation | Error (e.g. MAPE) on held-out synthetic data, reported in the documentation |

---

## 8. Data model (29 core tables)

| Table | Purpose | Phase |
|---|---|---|
| `roles`, `users` | `roles` is a small seeded lookup (CUSTOMER, VENDOR, ADMIN); `users.role_id` references it, one role per user in v1 | 0–1 |
| `refresh_tokens` | Hashed refresh tokens, rotation, revocation | 1 |
| `password_reset_tokens` | Hashed, single-use, expiring reset tokens | 1 |
| `vendor_profiles` | Business details, radius, delivery settings, approval status, rating aggregate | 2 |
| `vendor_hours` | Weekly opening hours (drives delivery slots) | 2 |
| `audit_log` | Who did what, when, before/after (admin and settlement actions) | 2, 13 |
| `categories` | Hierarchical, admin-managed | 3 |
| `products`, `product_images` | Catalog; images only in `product_images` | 3 |
| `inventory` | quantity, reserved_quantity, low_stock_threshold, optional expiry_date | 3 |
| `stock_movements` | Audit trail of every stock change | 3 |
| `addresses` | Customer address book | 4 |
| `service_locations` | Seeded city / area / pincode with centroid lat/lng; the only source of coordinates (no GPS, D-4) | 2 |
| `carts`, `cart_items` | Multi-shop cart with price snapshot and gift note | 5 |
| `orders`, `order_items` | One order per shop; address snapshot, fees, slot, payment fields | 5–6 |
| `order_status_history` | Every state transition with actor and reason | 6 |
| `payments` | Payment records per order | 7 |
| `disputes` | Customer disputes and admin resolutions | 8 |
| `notifications` | In-app notifications | 9 |
| `commissions` | One row per delivered marketplace order | 10 |
| `settlement_entries` | Signed ledger of amounts between platform and vendor | 10 |
| `settlements` | Weekly netted settlement per vendor | 10 |
| `pos_transactions`, `pos_items` | Walk-in sales | 11 |
| `reviews` | One review per delivered order | 12 |
| `platform_config` | Default commission, platform fee, auto-reject minutes, timeouts | 6–10 |

*Removed from Revision 1:* `delivery_zones` (radius only) and `payouts` (replaced by `settlements` + `settlement_entries`).

**Indexes to plan for:** `vendor_profiles(status, lat, lng)`, `products(vendor_id, status)`, `orders(user_id, created_at)`, `orders(vendor_id, status, created_at)`, `inventory(product_id)` unique, `commissions(order_id)` unique, `reviews(order_id)` unique, `settlement_entries(vendor_id, created_at)`, `service_locations(city, area)`, `service_locations(pincode)`.

---

## 9. Key technical decisions

| Decision | Choice | Rationale |
|---|---|---|
| Auth | JWT access + refresh, rotation, hashed refresh tokens | Stateless for the SPA; revocable |
| Token storage | Access token in memory only; refresh token in httpOnly, SameSite=Strict cookie; **built in Phase 1** (D-5) | Refresh token unreadable by JS; no rework later |
| Account status | Checked at login, at refresh and on every request (PK lookup); a status change revokes refresh tokens | Suspension takes effect on the next request |
| Rate limiting | Bucket4j in memory from Phase 1 | Auth and reset endpoints are protected from the start |
| Stock reservation | Reserve at checkout under DB pessimistic locks (ordered by product id); deduct when the vendor accepts | One source of truth; deadlock-safe; a vendor only commits stock for orders they accept |
| Redis | **Not provisioned in v1** | Nothing needs it; add only if profiling (14.7) shows a caching need |
| Geo search | Bounding-box prefilter + Haversine over area centroids; per-vendor radius | Good enough for v1; spatial index later |
| Location input | City / area / pincode from seeded `service_locations`; no GPS (D-4) | Matches the spec; no geocoding service or device permission needed |
| Inventory | Per-product stock, optional expiry, movement log | Simple and auditable; batch/lot tracking deferred to v2 |
| Cart | Multi-shop; one order per shop; all-or-nothing checkout | Matches the marketplace model; one transaction |
| Payments | COD in v1 behind a `PaymentGateway` interface | Online provider pluggable later (D-1) |
| Billing | Signed settlement ledger with weekly netting | Correct for both COD and online |
| Real-time | Polling (5–10 s) in v1; SSE optional with fetch-based client | `EventSource` cannot send auth headers |
| Notifications | Domain events, `AFTER_COMMIT`, async | No notifications for rolled-back work |
| Money | `DECIMAL(10,2)`, `BigDecimal`, `HALF_UP` | No floating-point errors |
| Migrations | Flyway, additive only | Version-controlled schema |
| State management | Zustand + TanStack Query | Lightweight; caching and polling built in |
| Styling | Tailwind CSS | Consistent design tokens; rapid UI |
| File uploads | Local disk (dev) / S3 (prod) behind `StorageService` | Pluggable by profile |

---

## 10. Synopsis reconciliation

The submitted synopsis lists technologies that differ from this plan. Each row needs a decision before Phase 14; either amend the synopsis text or implement the item.

| Synopsis says | This plan | Action |
|---|---|---|
| Stripe payments | COD in v1; optional test-mode gateway (7.4) | Decide D-1. Amend to "COD in v1, online payments as future scope", or build 7.4 |
| Twilio / SendGrid notifications | In-app notifications + SMTP email (Mailhog in dev) | Amend to "SMTP email + in-app; SMS as future scope", or point SMTP at a SendGrid relay |
| Mapbox geo | Haversine SQL over seeded city/area/pincode centroids; no GPS or map service | Amend the synopsis to match |
| Redis 7 caching | Not provisioned in v1 | Amend to future scope, or add Redis with caching for category and discovery reads if 14.7 shows a need |
| Flyway, Docker Compose, Zustand, TanStack Query, Zod | As planned | None |
| 12 functional modules | Mapped across Phases 1–13 | Cross-check each synopsis module against a phase and flag any that lack one |

---

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Overselling under concurrency | High | High | Ordered pessimistic locks; concurrency tests in Phases 5 and 11 |
| Vendors not remitting COD dues | Medium | High | Settlement ledger; dues policy and suspension (10.7) |
| Geo query performance | Medium | Medium | Bounding-box prefilter; indexes; `EXPLAIN` review (14.7) |
| Perishable-stock complexity | Medium | Medium | Simple stock + optional expiry; batch tracking deferred to v2 |
| Payment integration | High | High | COD-only v1 behind an interface; gateway optional (D-1) |
| Reservation leaks (orders never accepted or paid) | Medium | High | Auto-reject (6.5) and payment-expiry sweeper (7.5); release rules in `StockService` |
| Time overrun | High | High | Priority tiers and cut order (section 13) |
| Unverified AI-assisted code changes | High | High | Definition of Done requires real compile/test/boot output and diff review |
| Image upload attacks | Medium | Medium | Content sniffing, size limits, random names (3.8, 13.2) |
| Schedulers hard to test | Medium | Low | Injectable `Clock`; fake clock in tests |

---

## 12. Estimated effort

| Phase | Hours |
|---|---|
| 0 Scaffolding and foundations | 5–7 |
| 1 Auth | 9–11 |
| 2 Vendor profiles and approval | 8–10 |
| 3 Catalog and inventory | 7–9 |
| 4 Addresses, discovery, search | 8–10 |
| 5 Cart and checkout | 7–9 |
| 6 Order management | 7–9 |
| 7 Payments and COD | 4–6 |
| 8 Cancellations, refunds, disputes | 5–6 |
| 9 Notifications and real-time | 4–5 |
| 10 Billing and settlement ledger | 8–10 |
| 11 POS | 5–6 |
| 12 Reviews and analytics | 5–6 |
| 13 Security hardening | 3–4 |
| 14 Testing, polish, deliverables | 8–10 |
| **Total (coding effort)** | **93–118** |

- These are rough estimates for hands-on build time. With per-diff review and real verification on every phase, plan for roughly **1.5×: about 140–177 hours**.
- Optional add-ons: online payment gateway (7.4) +5–7 h; Phase 15 (AI forecast) +6–8 h.
- Revision 1 estimated 66–86 h; the increase reflects the added phases (7, 8, 13), the settlement ledger, and the documentation and demo deliverables.

---

## 13. Priorities and cut order

**Build order.** Phase numbers follow dependency order. Phases 11 (POS) and 12 (Reviews) do not block Phases 8–10 and can be built later. Phase 8 needs Phase 6; Phase 10 needs Phases 6–8; Phase 12 needs Phase 6.

| Tier | Phases | Why |
|---|---|---|
| **1: Core demo path** | 0–7, the ledger core of 10 (10.1–10.3), 14 | Register → discover → order → deliver → commission, plus a working demo |
| **2: Should have** | 8, 9, rest of 10, 13 | Completes the requirements (disputes, notifications, settlement) and security |
| **3: Nice to have** | 11, 12, 15 | POS, reviews and analytics, AI forecast |

**If a deadline forces cuts, in this order:** Phase 15 → analytics (12.4–12.5) → POS (11) → SSE (9.5) → online gateway (7.4) → reviews (12.1–12.3). Do **not** cut the concurrency tests (5.T), the RBAC checklist, or security items 13.1, 13.2 and 13.6.

---

## 14. Acceptance criteria

**Functional**
- [ ] Customer can register, set an address, discover nearby shops, search products, add items from several shops, check out with COD, and track each order
- [ ] Vendor can register, wait for admin approval, configure delivery settings, manage catalog and stock, process orders through delivery, and use POS
- [ ] Admin can approve/reject/suspend vendors, manage categories, configure commission, resolve disputes, settle vendor accounts, and view analytics
- [ ] Customer can cancel within the allowed states and raise a dispute after delivery
- [ ] Notifications reach the vendor (new order) and customer (status changes)

**Correctness and concurrency**
- [ ] N concurrent checkouts for the last unit yield exactly one order; a multi-shop failure rolls back every shop
- [ ] POS and marketplace sales deduct from the same stock; a POS/online race for the last unit yields exactly one sale
- [ ] Expired stock is written off and the product delisted (verified with an injected clock)
- [ ] Stock is reserved at checkout and deducted only when the vendor accepts; before acceptance, reject, cancel, auto-reject and payment timeout all release the reservation
- [ ] Commission and settlement amounts match the formulas in sections 6.3 and 6.4, including COD dues and disputes
- [ ] Order status can only change along the transition table; every change is recorded in history

**Security**
- [ ] Role-based access enforced on every endpoint; the automated endpoint sweep passes; foreign resources return 403 and missing ones 404
- [ ] Auth endpoints are rate-limited; uploads are validated; no stack traces or user enumeration in responses
- [ ] Suspended or disabled accounts cannot log in, refresh, or keep using an already-issued access token; the refresh token is never readable by JavaScript
- [ ] Admin and settlement actions appear in the audit log; no secrets in the repository

**Quality and documentation**
- [ ] All backend and frontend tests pass; build, lint and typecheck are clean
- [ ] All APIs documented in Swagger UI with their required roles
- [ ] Responsive UI: customer (mobile), vendor dashboard (desktop/tablet), POS (tablet)
- [ ] README, demo script, ER diagram and known-issues list complete; the demo runs from a clean clone
- [ ] Every row of the synopsis reconciliation (section 10) is resolved
