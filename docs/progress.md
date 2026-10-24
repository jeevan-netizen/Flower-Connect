# Progress

Tracks what has been implemented and what remains. Updated after each session.

## Current Phase

**Phase 7 (Payments) � COMPLETE** (final audit, 2026-10-24)

Phase 7 is implemented and verified on branch `phase-7-payments` at commit
`f0f2b52` plus the final-audit commit. All implementation stages are done:
Stage 1 COD payments model; Stage 2 online checkout with simulated intent;
Stage 3 signed webhook + simulated provider; Stage 3.5 reservation expiry job;
frontend Stage 3 payment UI. Final audit confirmed two simulator integration
test failures were caused by test setup (missing `order_items` row ? no
reserved stock to release; unauthenticated simulator route ? 401 not 404),
not shared database state. Both fixed and pass alone and together.

Verified: backend unit suite 1159 tests + integration suite 571 tests, 0
failures; frontend lint, typecheck, 782 tests, build all clean.

Stage 1 implements the Phase 5 data model: the `V13__create_carts_orders.sql`
Flyway migration (adding `service_locations.timezone` per D-5, and creating
`carts`, `cart_items`, `orders`, `order_items`, and `order_status_history`),
the matching JPA entities (`Cart`, `CartItem`, `Order`, `OrderItem`,
`OrderStatusHistory`) with inner enums `OrderStatus`, `PaymentMethod`,
`PaymentStatus`, and the corresponding repositories. A `CheckoutProperties`
configuration class was added for the platform fee, default commission rate,
min lead time, and max booking horizon (decision B-2, D-5). No services,
controllers, or DTOs were added in this stage — only the persistent domain
model and its configuration.

Task 5.2 implements the customer cart API: `CartService` (lazy cart creation,
price snapshots per C-1, customer isolation from JWT subject, vendor eligibility
checks at add-time, read-only stock checks with no reservation),
`CartController` at `/api/v1/cart` (GET, POST /items, PATCH /items/{id}, DELETE
/items/{id}, DELETE), DTOs (`CartResponse`, `CartItemResponse`,
`AddCartItemRequest`, `UpdateCartItemRequest`, `CartShopView`), the
`/api/v1/cart/**` → `hasRole("CUSTOMER")` security rule in `SecurityConfig`, and
the `InventoryRepository.findAllByProductIdIn` batch-fetch for the vendor-grouped
cart response.

Task 5.3 implements the checkout quote endpoint: `POST /api/v1/checkout/quote`
reads the authenticated customer's cart, validates address ownership, groups
items by vendor, and returns a per-shop breakdown with subtotals, delivery fees
(base + per-km × distance, free above threshold), platform fee (config or
request override), minimum-order check, distance/radius validation, slot format
and future-date validation (full slot logic in task 5.5), and per-item stock
and purchasability checks. Price drift is detected by comparing each cart
item's `priceSnapshot` against the current `products.base_price`; the response
includes `hasPriceChanges`, per-item `priceChanged`, and per-shop
`"price_changed_since_add_to_cart"` warnings so the customer must explicitly
confirm a changed quote before checkout (task 5.4). The quote is read-only
(`@Transactional(readOnly = true)`) — no stock reservation, no order creation,
no cart clearing. 22 service unit tests + 11 controller slice tests = 33 new
tests.

Task 5.4 implements the transactional checkout endpoint: `POST /api/v1/checkout`
places the order for the customer's cart across all vendors in a single atomic
`@Transactional` boundary.
- **Payment & Order Status:** COD only (`PaymentMethod.COD`), initial order status
  `OrderStatus.PLACED`, initial payment status `PaymentStatus.PENDING`.
- **Idempotency & Replay:** Migration `V15__add_checkout_idempotency.sql` creates
  `checkout_idempotency` (`user_id`, `client_order_id`, `checkout_fingerprint`,
  `created_at`, `completed_at`). Canonical inputs are hashed into an HMAC-SHA256
  fingerprint. Replay with identical payload returns cached orders with
  `idempotentReplay=true`. Conflicting payload returns 409 CONFLICT. Pending request
  returns 409 CONFLICT.
- **Multi-Vendor Partitioning:** Single-vendor checkouts store the exact `clientOrderId`.
  Multi-vendor checkouts deterministically derive an RFC 4122 UUID per vendor
  (`UUID.nameUUIDFromBytes((clientOrderId + ":" + vendorId).getBytes())`) to satisfy
  the `uq_orders_client_order_id` DB unique constraint while remaining queryable on replay.
- **Deadlock-Free Locking:** Inventory and VendorProfile rows are locked in deterministic
  ascending ID order (`findByProductIdForUpdate`, `findByIdForUpdate`).
- **Stock Reservation & Audit:** `reserved_quantity` is incremented under lock,
  physical `quantity` untouched, and auditable `StockMovement` of type `RESERVE`
  appended per order item.
- **Slot Capacity & Hours:** Checks `orderRepository.countBookedOrdersForSlot` under
  vendor lock; rejects with 409 `SLOT_FULL` if capacity is reached.
- **Price Drift Confirmation:** If catalog price drifted since cart addition and
  `confirmPriceChanges=false`, rejects with 409 CONFLICT. If `confirmPriceChanges=true`,
  updates cart snapshots and records confirmed catalog prices on orders.
- **Cart Cleanup:** Entire cart is cleared after order and status history creation.

Phase 5 Stage 1 + Task 5.2 + Task 5.3 + Task 5.4 verification:

| Command | Result |
|---|---|
| `./mvnw test` | **647 unit/slice tests** (537 Phase 4 baseline + 15 `CheckoutPropertiesTest` + 38 cart tests + 57 checkout tests), 0 failures / 0 errors / 0 skipped — `BUILD SUCCESS` |
| `./mvnw verify -Pintegration -Dit.test=CartOrderSchemaIT` | **22 integration tests**, 0 failures / 0 errors / 0 skipped — `BUILD SUCCESS` (Flyway applies V13 & V15, Hibernate `ddl-auto: validate` passes, all schema assertions green) |
| `./mvnw verify -Pintegration -Dit.test=CartApiIntegrationTest` | **19 integration tests**, 0 failures / 0 errors / 0 skipped — `BUILD SUCCESS` |
| `./mvnw verify -Pintegration -Dit.test=CheckoutApiIntegrationTest` | **12 integration tests**, 0 failures / 0 errors / 0 skipped — `BUILD SUCCESS` (RBAC, single/multi-vendor happy path, stale quoteHash rejection, foreign address rejection, stock drain rollback, replay caching, payload mismatch conflict, slot capacity, price drift confirmation, strengthened concurrent idempotency with response status/orders/rows/reservation/movement assertions) |

Tasks 5.6 and 5.T implement customer cart/checkout UI and frontend verification:
- `CartPage.tsx` verified across 12 tests: vendor grouping, unpurchasable blocking, price drift alerts, order totals, upper bound at `CART_MAX_QUANTITY` (99), and row controls disabled during in-flight mutations (premature synchronous `onAdjustingChange(false)` calls removed so mutation `.finally()` manages adjusting state).
- `CheckoutPage.tsx` verified across 15 tests: four-step stepper, address selection, empty address guidance blocking progression, native-radio slot picker, date-picker min date, slot unselected blocking, zero available slots messaging, price drift confirmation, and confirmation step.
- Documented clarifications / non-blocking gaps:
  - Upper bound: UI enforces `CART_MAX_QUANTITY` (99) per `types.ts:66` rather than 10.
  - Progression gating: Delivery warnings for out-of-radius (`withinRadius`) and minimum order unmet (`minOrderMet`) render warning banners but do not gate `canProceed` (requires product decision/clarification).
  - Quote retry: Quote failure in `handleQuote` remains on the slots step, so `ReviewStep` quote error retry is unreachable via UI.

| Frontend Command | Result |
|---|---|
| `npm run test -- CartPage.test.tsx CheckoutPage.test.tsx` | **27 unit tests** (12 CartPage + 15 CheckoutPage), 0 failures — `PASS` |
| `npm run test` | **587 tests across 53 test files**, 0 failures — `PASS` |
| `npm run typecheck` | `tsc -b` clean — exit code 0 |
| `npm run build` | `tsc -b && vite build` clean — exit code 0 |

Task 4.1 (customer address book) is implemented, verified and committed on branch
`phase-4-discovery-search`: the `addresses` table (V12), entity and repository,
DTOs and MapStruct mapper, `AddressService` with the one-default-per-customer
invariant enforced under a pessimistic lock on the caller's own `users` row
(D-32), and `AddressController` at `/api/v1/addresses` inside a CUSTOMER-only
namespace. Stage 0's design note (`docs/phase-4-stage-0-design.md`) records the
decisions carried into the implementation.

Stage 1 verification:

| Command | Result |
|---|---|
| `./mvnw test` | **527 unit tests** (508 Phase 3 baseline + 19 new), 0 failures / 0 errors / 0 skipped — `BUILD SUCCESS` |
| `./mvnw verify -Pintegration` | **390 integration tests** (365 baseline + 25 new), 0 failures / 0 errors / 0 skipped — `BUILD SUCCESS` |

### D-1 — Anonymous cart-query authentication error cascade (fixed and deployed)

The shared header queried the customer cart for **every** visitor, including anonymous
ones. `GET /api/v1/cart` is CUSTOMER-only, so an anonymous page load answered 401 — and
the axios interceptor reads *any* 401 from a non-public endpoint as an expired access
token, so it started a refresh (`POST /api/v1/auth/refresh` -> 400 "Refresh token is
required") and then a logout (`POST /api/v1/auth/logout` -> 400). One pointless request
became three failing requests and three console errors, on every public route. The same
query also raised a 403 for a florist and an admin, who have no cart either.

The fix is three small changes and no backend change:
- `useCart()` gained an optional `{ enabled?: boolean }` that defaults to `true`, so
  `CartPage` and `CheckoutPage` — already behind a CUSTOMER-only route guard — are
  unchanged.
- The header passes `enabled: isAuthenticated && user?.role === CUSTOMER_ROLE`, the same
  condition under which the cart link is already rendered, so the query and the link
  cannot disagree.
- The response interceptor reads the auth store before refreshing: with no access token
  there is no session to renew, and the refresh credential is an HttpOnly cookie the
  frontend cannot inspect, so the original error is rejected instead. The refresh and
  retry path for a session whose access token *has* expired is unchanged.

Recorded as **D-41** in `docs/decisions.md`.

| Command | Result |
|---|---|
| `npm run test -- api.test.ts SiteHeader.test.tsx CartPage.test.tsx CheckoutPage.test.tsx` | **56 tests** (9 api + 16 SiteHeader + 12 CartPage + 19 CheckoutPage), 0 failures — `PASS` |
| `npm run test` | **736 tests across 65 test files**, 0 failures — `PASS` |
| `npm run typecheck` | `tsc -b` clean — exit code 0 |
| `npm run lint` | `eslint` clean — exit code 0 |
| `npm run build` | `tsc -b && vite build` clean — exit code 0 |

Browser-verified through the Playwright MCP tools against a local Vite dev server running
the **modified source** on port 5175, with the `/api` proxy reaching the unchanged backend
on 8080: `/`, `/browse`, `/search`, `/login`, `/register` and `/vendor/register` each
issued **zero** requests to `/api/v1/cart`, `/api/v1/auth/refresh` and
`/api/v1/auth/logout`, with zero console errors. The same audit against the then-running
Docker frontend reproduced the original cascade exactly (2/2/2 and six console errors per
route), so the measurement is sensitive to the defect.

**Deployed to the development stack on 2026-10-21.** The frontend image was rebuilt from
the working tree (`docker compose build frontend`) and only the frontend container was
recreated (`docker compose up -d --no-deps --force-recreate frontend`); the backend, MySQL
and MailHog were not rebuilt, restarted or recreated.

| Item | Value |
|---|---|
| Frontend image | `sha256:a049c351bba39fcbba32791fb5e2c104b2a0a15944ed55319c71fe1b7dea3e58` |
| Previous image | `sha256:f15135c911ee169dfbd47b1398d4e5cdb06bb9f83b2aa211b39b3d3f1b456072` — no longer inspectable, see the rollback limitation below |
| Frontend container | recreated, `Up (healthy)`, image healthcheck passing |
| Backend / MySQL / MailHog | unchanged, `Up (healthy)` throughout |
| Served bundle | `/assets/index-D2HE6egY.js` (was `/assets/index-UjrXHmrw.js`) |

Playwright MCP verification of the **deployed** build, with the per-route request and
console evidence reset to a fresh tab before each navigation:

| Route | `/api/v1/cart` | `/api/v1/auth/refresh` | `/api/v1/auth/logout` | Console errors | Public API requests |
|---|---:|---:|---:|---:|---|
| `/` | 0 | 0 | 0 | 0 | `GET /api/v1/locations` 200 |
| `/browse` | 0 | 0 | 0 | 0 | `GET /api/v1/locations` 200 |
| `/search` | 0 | 0 | 0 | 0 | `GET /api/v1/locations` 200, `GET /api/v1/categories` 200 |
| `/login` | 0 | 0 | 0 | 0 | `GET /api/v1/locations` 200 |
| `/register` | 0 | 0 | 0 | 0 | `GET /api/v1/locations` 200 |
| `/vendor/register` | 0 | 0 | 0 | 0 | `GET /api/v1/locations` 200 |

Every capture was non-empty, the pre-navigation request count was 0 on every route (which
is what shows the reset isolated each route rather than reusing a cumulative log), and
each public page rendered its normal content. The same audit against the pre-deployment
bundle reproduced the original cascade exactly (2/2/2 and six console errors per route).

Two limitations are recorded rather than worked around:
- The **signed-in customer flow was not browser-verified** — no approved customer
  credentials are available (`.env` is read-protected and demo seeding is disabled). The
  available coverage is the new `SiteHeader` test (a customer enables the cart query) and
  the unchanged `CartPage`/`CheckoutPage` suites. No browser verification is claimed for
  the signed-in flow.
- **The previous frontend image is no longer inspectable, so the tag-based rollback no
  longer works.** This Docker uses the containerd snapshotter, and the rebuild's retag
  garbage-collected the old manifest: `docker image inspect
  sha256:f15135c911ee…` returns "No such image" and no dangling image remains, so
  `docker tag sha256:f15135c911ee… flowerconnect-frontend:latest` would fail. Restoring
  the previous build would require rebuilding the pre-fix frontend from the committed
  source in a separate temporary context. No rollback was needed — the deployment
  succeeded — and that rebuild has not been attempted.

The D-1 source and documentation changes remain **uncommitted** in the working tree.

### Next Phase

**Phase 6 — Order management** per `docs/FlowerConnect_Implementation_Plan_v2.2.md` §7
(tasks 6.1–6.T): the order state machine, the vendor accept/reject/prepare/deliver
actions, the customer and vendor order APIs, the auto-reject job, and the customer
orders UI and vendor order board. Phase 4 (4.1–4.T) and Phase 5 (5.1–5.T) are
complete.

- [x] **6.1 — Order state machine** (Stage 1): `OrderStateMachine` is the sole service
  for changing order status, encoding the full section 6.1 transition table
  (including `PENDING_PAYMENT` transitions reserved for Phase 7 and customer/admin
  cancellation transitions reserved for Phase 8), writing one `order_status_history`
  row per transition from the injected `Clock`, publishing scalar-only domain events
  after commit, and delegating stock side effects to `InventoryService` under the
  agreed order-then-inventory ascending-product-id lock order. Additive Flyway
  migration `V16__add_order_status_history_actor_type.sql` records `actor_type` on
  the history row. Verified with an exhaustive table-driven `OrderStateMachineTest`
  covering every `(from, to, actor)` combination (333 tests) plus focused tests for
  reason validation, history/Clock fields, event publication, COD collection on
  delivery, and stock delegation.

- [x] **6.2–6.4 — Vendor order action endpoints, list and summary** (Stage 2):
  `GET|POST /api/v1/vendor/orders/{orderId}/accept|reject|prepare/out-for-delivery|delivered`,
  `GET /api/v1/vendor/orders` (paginated, optional `status`/`fromDate`/`toDate` filters)
  and `GET /api/v1/vendor/orders/summary` (today's summary in the vendor's timezone).
  Backed by `VendorOrderService` (ownership resolution, delegation to
  `OrderStateMachine`, listing, summary), `VendorOrderController`, and DTOs in
  `com.flowerconnect.vendor.dto`. Security: `/api/v1/vendor/**` → `hasRole("FLORIST")`
  in `SecurityConfig`; per-handler ownership (missing order 404, foreign order 403).
  Deliberately **no** `@RequiresApprovedVendor`, per D-6, so a suspended vendor can
  still process existing in-flight orders. Stock side effects via
  `OrderStateMachine` → `InventoryService` under the order-first, then
  ascending-product-id inventory lock order. Additive `V17` migration aligns the
  committed `V16` `actor_type` column (`VARCHAR`) with the `ActorType` entity enum
  (native MySQL `ENUM`) so `ddl-auto: validate` passes. Verified with
  `VendorOrderControllerTest` (12 slice tests) and `VendorOrderIntegrationTest`
  (15 integration tests against real MySQL), all green.

- [x] **6.3 — Customer order API** (Stage 3): `GET /api/v1/orders` (paginated,
  optional `status` filter) and `GET /api/v1/orders/{id}` (ownership, full detail).
  Backed by `CustomerOrderService` (ownership resolution, entity→DTO mapping) and
  `CustomerOrderController`, with DTOs in `com.flowerconnect.customer.order.dto`.
  Security: `/api/v1/orders/**` → `hasRole("CUSTOMER")` in `SecurityConfig`;
  per-handler ownership (missing order 404, foreign order 403). Read-only — order
  status is never changed here; all transitions continue to go through
  `OrderStateMachine`. The detail response includes payment method/status, vendor
  name, address snapshot, delivery slot, per-item snapshots, BigDecimal fee
  breakdown, and the chronological status timeline from `order_status_history`.
Verified with `CustomerOrderControllerTest` (6 slice tests) and
`CustomerOrderIntegrationTest` (7 integration tests against real MySQL), all
green.

- [x] **6.5 — Auto-reject job** (Stage 4): unaccepted `PLACED` orders older than
  a configurable number of minutes are auto-cancelled with the reservation
  released. The age threshold (`auto_reject_minutes`) is read from the new
  `platform_config` table (additive migration `V18`, seeded at 60 minutes), not
  hard-coded. `AutoRejectService` selects `PLACED` orders whose `created_at` is
  strictly older than `now - minutes` (injected `Clock`), processes each in its
  own `REQUIRES_NEW` transaction through `OrderStateMachine.transition`
  (`PLACED → CANCELLED`, actor `SYSTEM`, reason `AUTO_REJECTED_NO_RESPONSE`),
  and skips orders a vendor already moved out of `PLACED` (concurrent/rerun
  safe). `AutoRejectScheduler` is a thin scheduled component (`app.auto-reject-cron`,
  disabled in tests). Bounded batch (`app.auto-reject-batch-size`, default 100).
  Verified with `AutoRejectServiceTest` (4 unit tests) and
  `AutoRejectIntegrationTest` (13 integration tests against real MySQL), all
  green.

Remaining Phase 6 work: none — Stage 5 complete.

- [x] **6.6 — Customer orders UI** (Stage 5): `OrderHistoryPage` at `/orders` (paginated,
  newest-first order listing with status badges, loading/error/empty states) and
  `OrderDetailPage` at `/orders/:orderId` (status stepper, chronological timeline,
  payment method/status with "Cash on delivery" wording, address snapshot, delivery
  slot, item snapshots, fee breakdown with "incl. taxes"). Both guarded by
  `ProtectedRoute roles={["CUSTOMER"]}`. Checkout success screen links to `/orders`.
  Polling via `usePolling` (5–10s refetch, tab-visibility pause, terminal-status
  stop). Shared `usePolling` hook in `src/hooks/`. New Vitest/RTL tests for stepper
  rendering, polling pause/terminal-stop, loading/error/empty/populated states.

- [x] **6.7 — Vendor order board** (Stage 5): `VendorOrderBoardPage` at `/vendor/orders`
  (Kanban board with New/Accepted-Preparing/Out-for-delivery/Delivered-today columns,
  today summary strip, per-card next-valid actions, reject-reason dialog with
  validation, double-click protection). Guarded by `ProtectedRoute roles={["FLORIST"]}`.
  Polling via `usePolling`. 409 insufficient-stock handling with clear reject prompt.
  New Vitest/RTL tests for accept/reject flows, 409 insufficient stock, reject dialog
  validation, double-click protection, loading/error/empty/populated states.



Two defects were found by the new tests and fixed during Stage 1 rather than
worked around:

- **The default-address lock was not actually serialising.** The write path
  resolved the caller with a plain `SELECT` before taking `SELECT ... FOR
  UPDATE`. Under MySQL REPEATABLE READ the first plain read establishes the
  transaction snapshot, so the later "does a default already exist" read could
  not see rows a concurrent transaction committed after the snapshot — 8
  simultaneous first-address creations all became the default. The locking
  read is now the *first* read of the write transaction, so the snapshot is
  created under the lock (D-32). Caught only by `AddressConcurrencyIntegrationTest`;
  every sequential test passed while the race was live.
- **`isDefault` would have serialized as `default`.** A primitive `boolean
  isDefault` makes Lombok generate an `isDefault()` getter, from which Jackson
  strips the "is" prefix. Every boolean property — entity, request and response
  — is therefore named `defaultAddress` (D-33).

Phase 3 close-out (fully implemented and verified; per-task detail under
Completed Work): all tasks 3.1–3.9 and 3.T complete, with the Phase 3 RBAC
matrix in `docs/rbac-matrix.md` and the task 3.1 category audit findings
resolved with regression coverage. Two defects were fixed during that close-out
rather than worked around: the runtime image could not create its upload root
(`backend/Dockerfile` now creates and chowns `/var/lib/flowerconnect/uploads`,
and compose points `STORAGE_LOCAL_DIR` at it with a named volume), and
`/v3/api-docs` was missing every Phase 3 path because the running image was 46
hours stale (a stale-image artefact, not a code defect).

### Not verified

The **approved-vendor browser walkthrough** is incomplete — see issue 024. Registration,
pending-vendor login and the approval-gate withholding of the Catalog and Inventory links
were confirmed live in the browser; catalog create/edit, image upload/reorder/delete and the
four stock actions were not, because approving a vendor needs an admin token and Docker
became unavailable mid-session. This is a *rendered-UI* gap, not a behavioural one: the same
paths are covered by the 362 integration tests against the real production filter chain.

Two pre-existing issues remain open and are deliberately not fixed here, as neither belongs
to Phase 3: issue 020 (no route serves product image bytes, so the UI lists metadata — D-31)
and issue 021 (`GET /api/v1/categories` returns root categories only).

Two further follow-ups are recorded here rather than acted on, because each needs its own
approved scope:

- **`docs/decisions.md` is not valid UTF-8.** The file mixes single-byte Windows-1252
  characters with UTF-8 sequences, so a strict UTF-8 read reports invalid bytes. The
  condition predates this work — it is present in the committed file — and the edits made
  to it followed the file's existing convention rather than converting it. Repairing it
  means transcoding the whole file in one carefully scoped change, which is deliberately
  **not** done here.
- **The root route transition remounts the routed subtree.** The app shell keys its
  animation wrapper by the full pathname (`frontend/src/app/router.tsx:65`), so every
  pathname change remounts the routed subtree, and the page component's local state with
  it. The customer catalogue works around it by keeping its search filters and page in a
  store. Changing that key affects navigation and animation behaviour across the whole
  application, so it is recorded here as a separate frontend consideration that needs its
  own assessment — not as a defect in the customer catalogue.

One limitation of the D-1 verification is recorded here rather than worked around,
because it needs its own approved scope:

- **The signed-in customer flow was not browser-verified.** No approved customer
  credentials are available (`.env` is read-protected and demo seeding is disabled), so
  the D-1 fix is verified for anonymous visitors in a real browser — on the deployed
  Docker build since 2026-10-21 — and in *source* for a signed-in customer, by the new
  `SiteHeader` test and by the unchanged `CartPage` and `CheckoutPage` suites.

The per-task history below is unchanged.

### Phase 3g — Task 3.9 (Vendor catalog UI) completed

Task 3.9 is the first screen set built on the Phase 3c–3f APIs and changes no backend contract: it
consumes `GET /api/v1/categories`, the vendor catalog routes, the image routes and the inventory routes
exactly as tasks 3.5, 3.6 and 3.8 exposed them. Four screens, all inside the existing vendor shell so
the Phase 2 design language and components carry over rather than being restated:

- `/vendor/catalog` — the product table with search, a status filter, a category filter, pagination,
  low-stock badges and a link into each product.
- `/vendor/catalog/new` and `/vendor/catalog/:productId` — one Shopify-style form for create and edit,
  plus (once a product exists) the image manager, the stock panel with the four stock actions, the two
  alert settings, the movement history, and deactivation behind a confirmation.
- `/vendor/inventory` — the shop-wide low-stock table with the four stock actions per row and the
  movement history for whichever product a row acted on.

Three things the plan's one line does not settle are decided in D-29, D-30 and D-31:

- **Approval gating is a UX gate over the cached profile, not a second source of truth.**
  `ApprovedVendorGate` wraps the three catalog routes and reads the same `["vendor","profile"]` query
  the layout banner reads, so an unapproved vendor costs no extra request and the gate cannot disagree
  with the banner above it. `VendorLayout` withholds the Catalog and Inventory links from a vendor whose
  status is not `APPROVED`. D-13's authority is untouched: the backend still decides, per request, from
  `vendor_profiles.status`.
- **One stock dialog serves all four stock mutations, with the rules in one table.** `stock-in` and
  `stock-out` take a positive quantity and an optional reason; `adjustment` takes a signed non-zero
  quantity; `write-off` takes a positive quantity. Only the last two carry `@NotBlank`, and
  `requiresReason` decides both the rendered reason box and the resolver, so the two cannot disagree.
  A blank optional reason is sent as `null`, not `""`.
- **Images are shown as metadata, not as thumbnails.** `ProductImageResponse.storageKey` is the opaque
  backend key and no route serves the bytes, so the section lists filename, type, size and position
  instead of an `<img>` that would 404 — a broken image reads as a corrupt upload rather than as an
  absent delivery route. Recorded as a known issue, not a UI decision.

Two bugs in this task's own code were caught by the tests and fixed rather than asserted around: the
adjustment schema rejected a **negative** quantity (`/^\d+$/` where the DTO is signed), which made the
one action that is supposed to accept a signed value impossible; and `isPastExpiryDate` compared against
tomorrow, turning the "past its expiry date" marker red a day before the sweep that acts on it (D-25)
would. Both are noted in the component doc comments.

Verified at close-out with `npm run lint`, `npm run typecheck`, `npm run test` and `npm run build`:
**514 frontend tests across 45 files**, all green. No backend change, so no Maven run was required.

### Phase 3f — Task 3.8 (Image handling) completed

Task 3.8 is the first task that puts user-supplied bytes in the system.
`VendorProductImageController` is mounted at `/api/v1/vendors/products/{productId}/images`
(`POST`, `GET`, `PUT /{imageId}/primary`, `PUT /order`, `DELETE /{imageId}`) inside the existing
vendor namespace, so D-13's two authorization layers keep their order and no `SecurityConfig` change
was needed. No migration: `product_images` from task 3.2 already holds the rows, and the bytes live
behind a new abstraction.

Three things the plan's one line does not settle are decided in D-26, D-27 and D-28:

- **`StorageService` is chosen by profile, and the S3 half is a declared stub.**
  `LocalDiskStorageService` is `@Profile("!s3")`, `S3StorageService` is `@Profile("s3")` and throws
  `StorageException` on every method with a message naming what is missing. A property flag would let
  an environment variable point production at a developer's disk; a silently inert backend would let
  a deployment serve `201 Created` and write nothing. Local writes go to a `.part` sibling and are
  moved with `ATOMIC_MOVE`, and `resolve` rejects blank, backslash, absolute and root-escaping keys.
- **Every accepted image is decoded, scaled and re-encoded by the server.**
  `ImageTypeDetector` sniffs `FF D8 FF` / the 8-byte PNG signature / `RIFF....WEBP` — never the
  filename or the part's declared `Content-Type` — and `415 UNSUPPORTED_MEDIA_TYPE` is distinct from
  `VALIDATION_FAILED` so a client can tell "wrong format" from "broken file". Detection is a
  candidate, not an acceptance: `ImageProcessor` must then decode the file, which rejects the
  polyglot and the truncated file. Nothing is stored as it arrived, so EXIF/GPS is dropped and the
  stored bytes, dimensions and `mime_type` are the pipeline's own output. The pixel ceiling
  (40,000,000) is read from the header *before* `reader.read(0)`, because a few kilobytes can claim
  20000×20000 and cost 1.6 GB. `max-dimension` (1600) scales down and never upscales. WebP is
  decoded and stored as JPEG — `imageio-webp` registers a reader only, so keeping the format would
  mean shipping the file undecoded.
- **The one-primary rule is enforced under a product-scoped row lock.** D-21 settled that it is a
  service-level invariant; task 3.8 is the first task to write `is_primary`, so
  `ProductImageRepository.findByProductIdForUpdate` (`PESSIMISTIC_WRITE`, `ORDER BY sortOrder, id`)
  serialises the read-decide-write sequence after the ownership check, exactly as D-24 does for stock.
  The first image of a product becomes its cover automatically, deleting the cover promotes the next,
  a reorder must be an exact permutation, and `requireSinglePrimary` asserts the invariant afterwards
  so a silent violation becomes a failed request.

File and row are deliberately not atomic: an upload writes the object first and deletes it again if
the row cannot be written, while a delete removes the row first and treats a failed object removal as
a logged orphan. Neither direction can leave a visible row pointing at bytes that are not there.

93 new unit tests (36 service, 14 controller slice, 13 local disk, 5 S3 stub, 11 processor, 14
detector) and 36 integration tests against real MySQL and a real temporary directory, including a
`CountDownLatch`-released multi-thread case that proves the lock exists. The earlier 3.5–3.7 work is
summarised under **Completed Work**.

Verified at close-out with `./mvnw verify -Pintegration` (21:44 min): **506 unit tests** and **362
integration tests**, all green — `BUILD SUCCESS`.

## Completed Work

### Phase 0 — Scaffold (Realigning to plan v2.2)

- [x] Repository initialized with git (4 commits on `main`)
- [x] Backend scaffold: Spring Boot 3.2.5 application skeleton
  - `FlowerConnectApplication.java` with `@EnableAsync`, `@EnableScheduling`
  - `application.yml` with datasource, Flyway, JPA, and actuator config
  - Maven build via `mvnw` wrapper + multi-stage Dockerfile
  - `V1__baseline.sql` migration: `roles` and `users` tables with FK, unique constraints, and indexes
  - MySQL container with utf8mb4 charset/collation and init script
- [x] Frontend scaffold: React 18 + Vite + TypeScript
  - `main.tsx` entry with ThemeProvider, QueryClientProvider, React Router
  - `router.tsx` with placeholder routes: `/`, `/browse`, `/cart`, `/orders`, `/login`
  - `query-client.ts` with TanStack Query defaults
  - `theme.tsx` with light/dark theme persistence
  - `auth-store.ts` — Zustand store (persisted) with token + user state
  - `api.ts` — Axios instance with auth interceptor and 401 redirect
  - Tailwind CSS with brand color palette and Inter font
  - Vitest smoke test in `src/test/setup.ts`
- [x] Infrastructure
  - `docker-compose.yml`: MySQL 8, Mailhog, backend, frontend (Redis removed per plan v2.2)
  - `.env.example` with all environment variable templates
  - `.gitignore` for Maven, Node, env files, IDE artifacts, uploads
- [x] Project memory & agent configuration
  - `AGENTS.md` — primary agent instructions
  - `CLAUDE.md` — compatibility redirect
  - `docs/` — architecture, progress, decisions, known-issues
  - `kilo.jsonc` — Kilo project configuration (agents, commands, permissions)

### Pending Work

#### Phase 0 — Finalize
- [x] Verify backend builds with `./mvnw clean package`
- [x] Verify frontend builds with `npm run build`
- [x] Verify full stack boots with `docker compose up --build`
- [x] Run available backend tests
- [x] Run available frontend Vitest tests
- [x] Run TypeScript type checks
- [x] Run ESLint
- [x] Verify backend + frontend health endpoints
- [x] Validate Flyway migrations (V1 baseline applied and validated)
- [x] **0.7 Error framework**: `ErrorCode` enum (VALIDATION_FAILED, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, RATE_LIMITED, ACCOUNT_SUSPENDED), `BusinessException` base with factory methods, `@RestControllerAdvice` handling with injected `ObjectMapper` + `Clock` bean, bean-validation and malformed-JSON error handling, Spring Security `AuthenticationEntryPoint`/`AccessDeniedHandler` returning structured `ErrorResponse` (401/403), all 4 existing exceptions refactored to extend `BusinessException`
- [x] **0.8 Profiles**: `application-dev.yml` created, added `forward-headers-strategy`, `app.base-url`, `jwt.refresh-grace-seconds` to `application.yml`/`application-prod.yml`/`application-test.yml`, `AppProperties` registered in `@EnableConfigurationProperties`
- [x] **0.T Test baseline**: Surefire excludes `integration` tag by default; Maven `integration` profile runs tagged tests via `mvn verify -Pintegration`; `IntegrationTestBase` renamed to `AbstractIntegrationTest` with singleton Testcontainers MySQL (started in static initializer, NOT `@Container`); 78 unit tests + 32 integration tests pass
- [x] **0.10 Git ignore**: `.gitignore` covering `.env`, `node_modules`, `target`, `uploads`
- [x] **Stage 6 - Redis removal**: Removed Redis service, config, env vars, and volume from docker-compose.yml, application.yml, application-prod.yml per plan v2.2 (no Redis in v1)
- [x] **Stage 6 - Vite dev proxy**: Added `/api` proxy to backend in `vite.config.ts` for same-origin cookie flow
- [x] **Stage 6 - MySQL healthcheck**: Verified mysqladmin ping healthcheck with `depends_on: service_healthy` in docker-compose.yml
#### Phase 1 — Authentication (Realigning to plan v2.2)

- [x] V1 migration: roles, users with ENUM status column (ACTIVE/SUSPENDED/DISABLED)
- [x] V2 migration: refresh_tokens (family_id, revoked_at, replaced_by_id), password_reset_tokens
- [x] V3 migration: Seed CUSTOMER, FLORIST, ADMIN roles
- [x] User.Status enum replaces boolean active; RefreshToken.revokedAt/familyId/replacedById replace boolean revoked
- [x] JPA entities: Role, User, RefreshToken with repositories
- [x] JWT service: token generation, validation, parsing (HS256)
- [x] Password hashing with BCryptPasswordEncoder
- [x] Refresh token service: secure random generation, SHA-256 hashing, rotation, cleanup
- [x] Authentication service: register, login, refresh (with rotation), logout
- [x] Spring Security 6 filter chain with JWT authentication filter
- [x] User registration endpoint (`POST /api/v1/auth/register`)
- [x] Login endpoint (`POST /api/v1/auth/login`) — issue JWT access + refresh tokens
- [x] Token refresh endpoint (`POST /api/v1/auth/refresh`) — implements rotation
- [x] Logout endpoint (`POST /api/v1/auth/logout`)
- [x] Stage 4c CSRF mitigation: cookie-authenticated refresh/logout require `X-FlowerConnect-Client: 1` and an exact configured `Origin`; body-token compatibility remains header/Origin-free
- [x] Password reset token entity and repository (V2 migration)
- [x] Forgot password endpoint (`POST /api/v1/auth/forgot-password`) — generates reset token, delivers via Mailhog
- [x] Reset password endpoint (`POST /api/v1/auth/reset-password`) — validates token, updates password, revokes all refresh tokens
- [x] Change password endpoint (`POST /api/v1/users/me/password`) — verifies current password, updates, revokes all refresh tokens
- [x] Profile update endpoint (`PATCH /api/v1/users/me`) — updates fullName/phone, validates phone, rejects duplicate phone, ignores email/unknown fields
- [x] Email sender abstraction: `EmailSender` interface with `NoOpEmailSender` (default) and `SmtpEmailSender` (Mailhog)
- [x] Stage 5a unit tests: 26 new tests (AuthControllerTest +10, UserControllerTest +10, PasswordResetIntegrationTest +10, RefreshTokenServiceTest +12, AppPropertiesTest +4)
- [x] Stage 5a integration tests: 18 new tests (AuthApiIntegrationTest +10, PasswordResetIntegrationTest +10)
- [x] DTOs with Bean Validation + MapStruct mappers
- [x] Global exception handler (@RestControllerAdvice)
- [x] Scheduled cleanup job for expired/revoked refresh tokens and password reset tokens
- [x] Unit tests: 99 tests pass (JWT, auth service, controllers, mappers, error handling, AppProperties binding, password reset service, change password, profile update, token cleanup scheduler)
- [x] Integration tests: 68 tests pass (all pass, single Testcontainers MySQL 8 container + Mailhog container)
- [x] Frontend types (`types.ts`): RegisterRequest, LoginRequest, RefreshRequest, AuthResponse, UserResponse, ErrorResponse
- [x] Auth API client (`api.ts`): register, login, refresh, logout, fetchCurrentUser wrappers
- [x] Zustand auth store (`auth-store.ts`): login, register, refresh, logout, loadCurrentUser, setAuth with persistence
- [x] Axios interceptors (`shared/lib/api.ts`): request interceptor (Bearer token on non-public endpoints), response interceptor (401 handling with singleton refresh promise, retry-once, `/auth/refresh` excluded from refresh logic to prevent deadlock)
- [x] Login page (`LoginPage.tsx`): email/password form with zod validation, loading state, backend errors, navigation with `from` redirect
- [x] Register page (`RegisterPage.tsx`): fullName/email/phone/password/confirmPassword form with zod validation, CUSTOMER role only
- [x] Auth hooks (`useAuth.tsx`): RequireAuth/RequireUnauth guards, useInitAuth for initial user load, hasLoadedInitial edge case fix (sets true even when /users/me fails)
- [x] Router (`router.tsx`): /browse, /cart, /orders protected; /login, /register require unauth
- [x] Frontend tests: 41 tests pass (auth-store, LoginPage, RegisterPage, useAuth, api interceptors) — smoke test plus 40 new tests
- [x] **Stage 5b - Admin bootstrap**: `AdminBootstrap` ApplicationRunner creates ADMIN user on startup from ADMIN_EMAIL/ADMIN_PASSWORD env vars (idempotent, uses BCrypt via PasswordEncoder, warns if env vars missing)
- [x] **Stage 5b - Token cleanup extended**: `RefreshTokenCleanupScheduler` now cleans both refresh_tokens and password_reset_tokens; schedule configurable via `app.token-cleanup-cron` (default 2 AM daily); uses injected Clock
- [x] **Stage 5b - Rate limiting**: Bucket4j + Caffeine on /auth/login, /auth/register, /auth/forgot-password, /auth/reset-password (5 req/hour per email+IP); returns 429 with Retry-After header; hashed email keys; Caffeine eviction (2hr idle, max 10k entries); disabled for unit tests via test profile
- [x] **Stage 5b - springdoc/Swagger**: dependency added but NOT enabled due to WebMvcTest incompatibility; will be addressed in future phase

#### Phase 2 — Vendor Profiles, Delivery Settings & Admin Approval
- [x] **2a — Service Locations (Task 2.1)**: `service_locations` table (V4 migration) with Bengaluru seed data (8 areas), `ServiceLocation` entity, `ServiceLocationRepository` with custom search queries, DTOs (`ServiceLocationResponse`, `LocationSearchRequest`, `PageResponse`), MapStruct mapper, `LocationService` with search/pagination, public `GET /api/v1/locations` (hierarchical by default, paginated `PageResponse` when `pincode`/`area` filters are supplied, AND semantics), `/api/v1/locations/**` added to `permitAll()` in `SecurityConfig`. 32 new tests (15 unit + 10 controller + 7 seed integrity + 10 integration). Committed as `852cec6`.
- [x] **2b — Vendor profile data model (Tasks 2.2, 2.3, 2.4)**: `vendor_profiles` and `vendor_hours` tables (V5 migration) with business details, copied `service_location_id` FK plus lat/lng centroid copy, `delivery_radius_km`, `logo_url`, `status` ENUM (`PENDING_APPROVAL`/`APPROVED`/`REJECTED`/`SUSPENDED`), nullable `commission_rate` override, nullable `avg_rating` with `review_count` default 0, delivery settings (`min_order_amount`, `base_delivery_fee`, `per_km_fee`, `free_delivery_above`, `prep_time_minutes`, `slot_duration_minutes`, `max_orders_per_slot`, `accepting_orders`), and weekly `vendor_hours` (weekday, open, close, closed). Index `vendor_profiles(status, latitude, longitude)` per plan section 8. `VendorProfile` and `VendorHours` entities plus `VendorProfileRepository` and `VendorHoursRepository`. 27 new integration tests (10 profile repository + 7 hours repository + 10 schema/migration integrity). No endpoints added — registration, approval, audit log and admin user status are Phase 2c+.
- [x] **2c — Vendor registration API and admin vendor management (Tasks 2.5, 2.6)**: `audit_log` table plus vendor CHECK constraints (`V6__audit_log_and_vendor_checks.sql`), `AuditLog` entity and repository, `com.flowerconnect.vendor` feature slice (7 DTOs, `VendorMapper`, `VendorService`, `VendorAdminService`, `VendorController`, `AdminVendorController`). Endpoints: `POST /api/v1/vendors/register` (public, creates the FLORIST account and a `PENDING_APPROVAL` profile atomically), `GET|PUT /api/v1/vendors/profile` (own profile only, derived from the JWT subject), and `GET /api/v1/admin/vendors` plus `approve`/`reject`/`suspend`/`reinstate` under `/api/v1/admin/**`. Every admin transition writes an append-only `audit_log` row; reject and suspend require a reason; illegal transitions return 409. 122 new tests (37 service, 45 controller, 40 integration). Verified with 213 unit + 157 integration tests.
- [x] **2d — Approval gating (Task 2.7)**: `VendorApprovalGuard` (single owner of the approval rule), `@RequiresApprovedVendor` (composed `@PreAuthorize`), `VendorNotApprovedException` + `ErrorCode.VENDOR_NOT_APPROVED` (403), and `VendorProfileSpecifications.approved()` for discovery; `VendorProfileRepository` now extends `JpaSpecificationExecutor<VendorProfile>` and gained `findByUserEmail`; `@EnableMethodSecurity` added to `SecurityConfig`. No new production endpoint and no migration — the annotation guards no route until Phase 3 adds one. Gating is per-handler so the vendor's own `GET|PUT /api/v1/vendors/profile` stays reachable while a profile is not approved (D-13). 29 new tests (9 unit, 16 gating integration, 4 discovery integration). Verified with 222 unit + 177 integration tests.
- [x] **2d — Admin user management (Task 2.8)**: `GET /api/v1/admin/users` (optional `role`/`status` filters, page size clamped to `1..100`, sorting pinned to `createdAt` then `id`) and `PATCH /api/v1/admin/users/{id}/status` (status + required reason, revokes all active refresh tokens for the target, writes one `audit_log` row in the same transaction). Self-targeting is refused with 403; every transition including reactivation revokes tokens (D-14). New `UserStatusService` is the sole owner of status mutation, revocation and audit writing; `AdminUserService` owns listing only; `UserSpecifications` backs the listing over a now-`JpaSpecificationExecutor<User>` `UserRepository`; `AdminUserMapper` + `AdminUserResponse` keep credential material out of the payload. No migration — reuses `users.status` and `audit_log`. 79 new tests (10 `UserStatusServiceTest`, 8 `AdminUserServiceTest`, 24 `AdminUserControllerTest`, 37 `AdminUserStatusIntegrationTest`). Verified with 264 unit + 214 integration tests.
- [x] **2d — Vendor frontend (Task 2.9)**: the florist area of the SPA — `/vendor` (dashboard), `/vendor/profile`, `/vendor/settings`, `/vendor/hours` — built on the existing `GET|PUT /api/v1/vendors/profile`, guarded by a new reusable `ProtectedRoute roles={["FLORIST"]}`. `com.flowerconnect.vendor` is mirrored by `src/features/vendor/` (types with the full-replacement PUT builder, API client, TanStack Query hooks, formatters, Zod schemas mirroring the DTO bounds) plus shared `ProtectedRoute` and `api-error` helpers that normalise the backend `ErrorResponse` envelope. `VENDOR_NOT_APPROVED` (403) renders the approval banner as a state rather than an error, the service area is read-only by choice rather than for lack of data, and the whole area is one cache key cleared on logout (D-15). Added the `typecheck` npm script. 164 frontend tests across 19 files; no backend change and no migration.
- [x] **2d — Admin frontend (Task 2.10)**: the admin area of the SPA — `/admin` (dashboard), `/admin/vendors`, `/admin/users` — built on the existing `GET /api/v1/admin/vendors`, the four vendor transition routes, `GET /api/v1/admin/users`, and `PATCH /api/v1/admin/users/{id}/status`, guarded by the existing `ProtectedRoute roles={["ADMIN"]}`. New `src/features/admin/` slice: types (with `vendorActionsFor` deriving the offered transitions from the backend's `requireStatus` rules), API client, filter-and-page-keyed TanStack Query hooks that invalidate the whole listing after a mutation, formatters, a shared `ReasonDialog` used by both reject/suspend and the user status change, `Pagination`, `AdminLayout`, and `AdminErrorState` telling 403/404/409/other apart. Reuses the vendor slice's `Card`, `PageHeading`, `StatCard`, `TextField`, `SubmitButton`, `SuccessMessage`, `VENDOR_STATUS_LABELS` and `api-error` normalisation rather than duplicating them. Self-targeting user status is disabled on the signed-in admin's row for usability, with the backend 403 still handled (D-14). 85 new frontend tests across 5 files; no backend change and no migration (D-16).
- [x] **2.T — Phase 2 close-out verification**: every Phase 2 endpoint audited against the plan's
  Phase 2 test checklist. Added the missing `VendorApiIntegrationTest` cells listed under
  **Current Phase** — orphan-`vendor_profiles` assertions on both registration failure paths, the
  full 4-legal/12-illegal approval transition matrix at the HTTP boundary, `audit_log.actor_user_id`
  assertions on every vendor administrative path, and the remaining RBAC/404/400 cells
  (`PUT /vendors/profile` with no profile, all four admin actions against an unknown or non-positive
  id, reject/suspend/reinstate refused for a FLORIST token). Wrote up **D-4** and **D-6** in
  `docs/decisions.md`, both of which the docs and D-13 already cited but plan section 4 requires to
  be   recorded. Closed known issue 019 (`PATCH` CORS) in `docs/known-issues.md`. Two pre-existing
  assertions that only held when their class ran alone
  (`VendorApiIntegrationTest.anAdminCanListVendorsAndFilterByStatus` assuming its rows were on
  page 0, `VendorProfileRepositoryIT.shouldFindAllProfilesByStatus` assuming an empty
  `vendor_profiles` table) were rescaled to assert membership of the rows the test created — the
  shared singleton MySQL container accumulates profiles across the whole run. No production code
  and no migration changed. Verified at close-out: **264 unit tests**, **222 integration tests**,
  **314 frontend tests across 27 files**, plus `npm run lint`, `npm run typecheck` and
  `npm run build` — all green.

#### Phase 3a — Catalog and inventory management (Task 3.1 completed)
- [x] **3.1 — Category entity**: `categories` table (V7 migration) with hierarchical structure (parent_id self-FK), `name`, `slug` (unique, auto-generated), `display_order`, `active` flag, `created_at`, `updated_at`. Four seed categories: Roses, Bouquets, Arrangements, Occasions. Admin CRUD at `/api/v1/admin/categories` (POST/PUT/DELETE/GET list with pagination over every category, roots and children), public read at `GET /api/v1/categories` (active top-level categories only, permitAll — D-17, issue #025). Cycle prevention on parent assignment, delete protection (409 if has children), slug auto-generation with collision-safe suffix, audit trail on all mutations. 32 new unit tests + 9 integration tests. Verified with 296 unit + 231 integration tests.
- [x] **3.2 — Product entity**: `products` table (V8) with vendor_id/category_id FKs, name, slug (unique, auto-generated with collision-safe suffix), description, base_price, status (native ENUM DRAFT/ACTIVE/INACTIVE/ARCHIVED). `product_images` (V9) holds ordered images with a single primary flag — the one-primary rule is a service-level invariant (D-21) because MySQL error 1215 rejects the generated-column unique-index trick next to the required product_id FK. `ProductService.create` generates the slug, treats the unique constraint as the authority, and retries with numeric suffixes after a lost race
- [x] **3.3 — Inventory entity**: `inventory` table (V10), one row per product (unique on product_id) with `quantity`, `reserved_quantity`, `low_stock_threshold`, optional `expiry_date`. Created automatically at quantity 0 in the same transaction as the product; no stock movement is written for the initial zero. CHECKs: quantity >= 0, reserved_quantity >= 0, low_stock_threshold >= 0, reserved_quantity <= quantity. Availability = quantity − reserved (computed in the entity)
- [x] **3.4 — Stock movement log**: `stock_movements` table (V11) records every change — movement_type (native ENUM of the nine plan section 6.2 types), signed quantity_delta, reason, reference id/type, nullable actor. Append-only (no updated_at). Product creation writes no movement; the log records changes only
- [x] **3.5 — Catalog API**: `VendorProductController` at `/api/v1/vendors/products` — `POST` (201), `GET` (paginated, optional `status` / `categoryId` / `name` filters, page size clamped 1..100), `GET|PUT /{id}` (403 foreign / 404 missing), `PATCH /{id}/deactivate` (204). The vendor comes from the JWT subject, so cross-vendor access is not expressible in a request. `@RequiresApprovedVendor` on the class — the first production route to carry it. No migration; reuses `products`, `categories.active`, `vendor_profiles`. Listing filters are Criteria predicates in the new `ProductSpecifications` (D-23); soft delete sets `INACTIVE` and keeps the row, its images and its inventory (D-22). 24 new unit tests, 26 new integration tests
- [x] **3.6 — Inventory API**: `VendorInventoryController` at `/api/v1/vendors` — read current inventory, `POST .../stock-in`, `.../stock-out`, `.../adjustments` (signed, nonblank reason), `.../write-offs` (`WASTE`), `PUT .../low-stock-threshold`, `PUT .../expiry-date`, `GET .../movements?page&size` (paged history), and vendor-wide `GET /api/v1/vendors/inventory/low-stock?page&size`. Availability is `quantity - reserved_quantity` and is never clamped; a change that would breach the floor is refused with a dedicated `409 INSUFFICIENT_STOCK` rather than silently applied at the floor. `@RequiresApprovedVendor` on the class; 403 for a foreign product and 404 for a missing one, both resolved before any row lock is taken. Every mutation takes a pessimistic write lock on the inventory row, then writes the new level and exactly one `stock_movements` row in one transaction (D-24); the movement's actor is the authenticated principal, so it cannot be client-supplied. The two alert settings take the same lock even though they write no movement, because Hibernate's whole-row `UPDATE` would otherwise write back a stale `quantity`. Low-stock rule is `available <= lowStockThreshold`, vendor-scoped, via `InventorySpecifications`. No migration — reuses `inventory` and `stock_movements` from tasks 3.3/3.4. 59 new unit tests, 35 HTTP integration tests, 6 real-thread concurrency tests
- [x] **3.7 — Expiry scheduler**: `InventoryExpiryService` writes off available stock whose `expiry_date` has passed as a `WASTE` movement (null actor, no reference, reason naming the expiry date and the sweep date) and delists an `ACTIVE` product as `INACTIVE`; `InventoryExpiryScheduler` triggers it on `app.expiry-sweep-cron`. Expired means `expiryDate < LocalDate.now(clock)` on the injected `Clock`; reserved units are never written off (`quantity` stops at `reserved_quantity`); a row with nothing available is delisted without a movement. Candidates are selected unlocked and ordered by product id, then locked one at a time through the existing `findByProductIdForUpdate` with every condition re-checked under the lock, and the whole run is one transaction bounded by `app.expiry-sweep-max-rows`. Idempotency is the candidate predicate (`quantity > reservedQuantity OR status = ACTIVE`), not a processed flag (D-25). No endpoint and no migration. 16 new unit tests, 12 integration tests
- [x] **3.8 — Image handling**: `StorageService` (`store`/`delete`/`exists`/`describe`) with `LocalDiskStorageService` (`@Profile("!s3")`, `.part` + `ATOMIC_MOVE` writes, traversal-checked `resolve`, root created at startup) and `S3StorageService` (`@Profile("s3")`) as a declared stub that throws on every method and names what is missing — the AWS SDK is not a dependency and a silently inert implementation would serve `201 Created` and write nothing (D-26). `VendorProductImageController` at `/api/v1/vendors/products/{productId}/images` — `POST` (multipart part `file`, optional `primary` flag, 201), `GET` (ordered list), `PUT /{imageId}/primary`, `PUT /order` (must be an exact permutation of the product's image ids), `DELETE /{imageId}` (204, promotes the next image when the cover is removed). `@RequiresApprovedVendor` on the class; ownership resolved through `ProductService.requireOwnedProduct`, made package-private so "yours" is defined once for the catalog and the image routes. Validation order is ownership → empty part (400) → declared length over `max-file-size-bytes` (413, before the bytes are buffered) → content sniffing (415) → decode (400) → pixel budget (413) → image count (409), so a rejected upload leaves neither a row nor a file. Format is decided by the leading bytes only, never the filename or the part's `Content-Type`; every accepted upload is decoded, scaled to `max-dimension` and re-encoded, so EXIF/GPS is dropped and the stored bytes, dimensions and `mime_type` are the pipeline's own output; WebP is decoded and stored as JPEG (D-27). Keys are `product-images/{productId}/{uuid}.{ext}`, always server-generated. The one-primary invariant is enforced under `findByProductIdForUpdate` (`PESSIMISTIC_WRITE`, ordered by `sortOrder, id`), the first image becomes the cover automatically, and `requireSinglePrimary` asserts the invariant after every mutation (D-28). Uploads store the object then the row and remove the object if the row cannot be written; deletes remove the row first and treat a failed object removal as a logged orphan. No migration. 93 new unit tests, 36 integration tests including a `CountDownLatch` concurrency case
- [x] **3.9 — Vendor catalog UI**: four screens inside the existing vendor shell — `/vendor/catalog` (searchable, status- and category-filtered product table with pagination, low-stock badges and per-row links), `/vendor/catalog/new` + `/vendor/catalog/:productId` (one Shopify-style create/edit form, image manager, stock panel with the four stock actions, low-stock threshold and expiry date, movement history, deactivation behind a confirmation), and `/vendor/inventory` (shop-wide low-stock table with the four stock actions per row and the selected product's movement history). `ApprovedVendorGate` wraps the three gated routes over the cached `["vendor","profile"]` query and `VendorLayout` withholds the two new nav links from an unapproved vendor, so D-13 stays the authority (D-29). One `StockActionDialog` serves all four stock mutations with the per-action rules in one table; a blank optional reason is sent as `null` (D-30). Images render as metadata because no byte-serving route exists (D-31). The product editor is reachable at `/vendor/catalog/new` as its own route, so `useParams().productId` is *absent* there rather than the string "new". `isMissingVendorProfile` is scoped to the profile read by request URL, so a missing product no longer reports a missing account. 76 new frontend tests across 7 files (product form 9, stock dialog 15, approval gate 8, catalog page 14, inventory page 11, product editor page 17, plus additions to the API-client, query-cache, format and router suites). No backend change and no migration
#### Phase 4 — Customer Addresses, Discovery and Search
- [x] **4.1 — Address book**: `addresses` table (V12) — one row per saved address, `user_id` FK (CASCADE), `service_location_id` FK, `label`/`line1`/`line2`, centroid `latitude`/`longitude` copied from the service location at write time (D-4), `is_default` BIT(1) with no unique constraint (MySQL has no partial unique index and the generated-column trick conflicts with the required FK — the D-21 finding), so one-default-per-customer is a service-level invariant (D-32). `AddressService` resolves the caller from the JWT subject only; every write is one `@Transactional` whose *first* read locks the caller's own `users` row (`UserRepository.findByEmailForUpdate`, `PESSIMISTIC_WRITE`) and only then reads and mutates that user's addresses — the user row is the serialization point because a first-address creation has no address row to lock, and a plain read before the lock would poison the REPEATABLE READ snapshot (D-32). First address becomes the default automatically (an explicit `false` is not honoured); a later address is default only when the request says so and clears the previous default in the same transaction; deleting the default promotes the oldest remaining address (`MIN(id)`); deleting the only address leaves no default; an ordinary update never changes the stored flag unless the request sets it. `PUT` is a full replacement (D-15): an omitted `line2` clears it, an omitted `defaultAddress` keeps the flag, and changing the service location recopies the new centroid. `AddressController` at `/api/v1/addresses` — `GET` (paged, default first then id ASC, page/size validated 0../1..100), `POST` (201), `GET|PUT /{id}` (200), `DELETE /{id}` (204); `@Positive` path ids; a foreign id and a missing id are both 404 (ownership is scoped by `user_id` in every query); an unknown `serviceLocationId` is 400 `VALIDATION_FAILED` ("Unknown service location", the `VendorService` precedent). `SecurityConfig` maps `/api/v1/addresses/**` to `hasRole("CUSTOMER")` — a florist or admin account is 403 before the controller; unauthenticated is 401. 19 unit tests (`AddressServiceTest`) + 23 HTTP integration tests (`AddressApiIntegrationTest` — real JWTs minted through the login endpoint, so the full production filter chain runs; RBAC, ownership, validation, pagination, default rules and delete-promotion at the HTTP boundary) + 2 real-thread concurrency tests (`AddressConcurrencyIntegrationTest` — 8 simultaneous first-address creations leave exactly one default; 8 competing default switches leave exactly one). No frontend change.
- [x] **4.2 — Location picker (session-backed, no GPS)**: already implemented and committed before this task — the `frontend/src/features/location/` slice (`api.ts`, `queries.ts`, `stores/location-store.ts`, `hooks/useLocationSelection.ts`, `hooks/useSelectedLocation.ts`, `components/LocationPicker.tsx`, `LocationMenu.tsx`, `LocationOptions.tsx`, `types.ts` + 5 test files) with the shell's `LocationMenu` in the header and the hero pill on the landing page. The selection is the `service_locations.id` (never coordinates, never free text, no Geolocation API — D-4) and is held for the **session** in `sessionStorage` under `fc-location` (D-34); `useSelectedLocation` validates the stored id against the live area list and clears it when the row is gone, so a stale selection never reaches discovery or search.
- [x] 4.3 Geo discovery API (`GET /api/v1/discover`)
- [x] **4.4 — Product search API** (`GET /api/v1/search`): public, `locationId` **required** (missing or unknown → 400 `VALIDATION_FAILED`). Returns `PageResponse<PublicProductResponse>` — a deliberately narrower shape than the vendor area's `ProductResponse`: no `status`, no audit timestamps and no image `storageKey`, which is an internal storage-backend key (D-31, known issue 020). Eligibility is the discovery/search policy: `ACTIVE` product, `quantity - reservedQuantity > 0`, vendor `APPROVED` and accepting orders, and a haversine distance within the vendor's `deliveryRadiusKm`. Filters: `q` (case-insensitive name match), `category` (exact id, no descendant expansion), `priceMin`/`priceMax` (`priceMin > priceMax` → 400), `sort` (`distance|price_asc|price_desc|name`, default `distance`), `vendorId`, `page`/`size` (0..; 1..100). Geographic eligibility is applied **before** sorting and pagination — the candidate vendors are narrowed by a bounding-box prefilter and then by exact distance, and the complete eligible set is sorted (with an `id` tie-break) before it is paged, so no eligible product is omitted and the pages are stable.
- [x] **4.5 — Storefront API** (`GET /api/v1/vendors/{id}/storefront`): public, returns the vendor's public profile, weekly hours and a paginated `PageResponse<PublicProductResponse>`. A vendor that is unknown, not `APPROVED`, or not accepting orders is a **404** that does not say which (D-6). No delivery-radius filter is applied here — the customer has already chosen this florist — which is why this endpoint takes no `locationId` while search and product detail require one.
- [x] **4.6 — Customer home page** (`/browse`): now **public** (it was a `RequireAuth` placeholder) and shows the florists that deliver to the chosen area, nearest first, from `GET /api/v1/discover`. With no area chosen it shows the location prompt and **no location-dependent request is made** — the discovery query stays disabled.
- [x] **4.7 — Search and filter UI** (`/search`): public; a search box (submitted, not fired per keystroke), category chips, minimum and maximum price inputs, a sort dropdown and pagination. Filter state is held in an **in-memory store** rather than in the page component, and is **not** written to the URL — the same choice the vendor catalog and the two admin listings make, so there is one place a filter is read from and no second copy in the address bar that could disagree with what is on screen. The store is there because the app shell keys its route transition by the pathname: opening a product remounts the routed subtree, so a filter held in the page would be lost with it — and because the filters are part of the search query key, losing them would also refetch the unfiltered results. The store is **not persisted**, so a full reload starts from the defaults; the delivery location is the one piece of customer context that is deliberately remembered across a reload (D-34). Every filter change resets to page 0. The chips are the public category list, which is **root-level active categories only** (D-17); the search endpoint accepts any active id including children, but a child is not discoverable from that list (known issue 025). **The price bounds are two labelled number inputs, not the plan's "price slider"** — a two-handle range slider has no native accessible semantics, cannot be typed into and is awkward with a screen reader, so the two inputs mirror the backend's `priceMin`/`priceMax` contract exactly (documented deviation, D-39). The backend's `vendorId` filter is plumbed through the client and the query key but has **no UI control**; the plumbing is retained as an accepted unused seam. The category reference list is fetched even before an area is chosen — it is global reference data rather than location-dependent, and the request is accepted.
- [x] **4.8 — Storefront page** (`/vendors/:vendorId`): public; the shop's identity, delivery settings, opening hours and a paginated product grid. The endpoint takes no `locationId`, so a missing area is a **non-blocking advisory** rather than a gate. **The plan's "add-to-cart per item" is not wired on the storefront grid** — the only customer add-to-cart control is on the product detail page (4.9). The `showAddToCart`/`onAddToCart`/`isAdding` props on `ProductCard`/`ProductGrid` are retained as accepted unused seams.
- [x] **4.9 — Product detail** (`/vendors/:vendorId/products/:productId`): public; `GET /api/v1/products/{id}` with the selected `locationId`, a quantity field and an optional gift note of up to 1000 characters, sent through the **existing** Phase 5 cart API (`AddCartItemRequest.note`) with no change to the cart area. Opened from a florist's storefront **or from the search results**, it renders as a panel **over** that surface — the shop or the results stay behind it — with a close control that returns to it; entered directly, or after a reload, it renders on its own with a return link. The two presentations are told apart by an explicit marker the product link carries, not by the navigation type (D-37). Every ineligibility — unknown, inactive, sold out, an unapproved or non-accepting florist, or a florist outside the radius — is the same non-disclosing 404. **No image carousel is implemented**; no route serves image bytes (known issue 020, D-31), so the page shows a **metadata-only placeholder for the product's primary image** — filename, type and size — and never renders an `<img>`. **Deferred accessibility limitation:** the overlay declares `aria-modal="true"` but implements **no focus management and no focus trap**, and the surface behind it remains keyboard-reachable — `aria-modal="true"` alone does not implement focus trapping. The **browser-level refresh behaviour** of the product route requires a manual browser check, because the repository has no browser-test infrastructure; the automated tests cover the marker-comparison logic only.
- [x] **4.T — Phase 4 verification**: 769 backend unit tests and 485 integration tests *(backend counts as reported by the Phase 4 verification; not re-run during the Gate E audit)*, plus 697 frontend tests across 64 files. The public search matcher is narrowed to `HttpMethod.GET`, and a focused integration test proves a non-GET request under `/api/v1/search/**` is refused with 401 by the production filter chain. The two `DiscoveryControllerIntegrationTest` failures are **verified** in the failsafe report of 2026-10-19 21:44 and are **page-exhaustion membership** assertions: the shared singleton Testcontainers database accumulates ≥100 approved in-radius vendors, so the vendors the test created fall onto a later page of the `size=100` response, and the **ordering and distance assertions were never reached**, so the server's ordering and radius filtering are **not** shown to be incorrect. They are **not reproducible in isolation** (the accumulation comes from the whole failsafe run) and were **not** independently reproduced during the Gate E audit. Gate C's three new **non-transactional** integration-test classes add vendor-creating fixtures to the same shared database and are a **plausible contributor, not proven causation**. Tracked as a separate backend test-isolation follow-up; `docs/rbac-matrix.md` §4.3 carries the matching qualification.

#### Phase 5 — Cart and Checkout

- [x] **5.1 Stage 1 (domain model)**: `V13__create_carts_orders.sql` migration (`service_locations.timezone` column per D-5; `carts`, `cart_items`, `orders`, `order_items`, `order_status_history` tables with exact ENUM lists, FK constraints, unique constraints, indexes); JPA entities (`Cart`, `CartItem`, `Order` with inner enums `OrderStatus`/`PaymentMethod`/`PaymentStatus`, `OrderItem`, `OrderStatusHistory`); repositories (`CartRepository`, `CartItemRepository`, `OrderRepository`, `OrderItemRepository`, `OrderStatusHistoryRepository`); `CheckoutProperties` config class (`app.checkout.platform-fee` default 0.00, `default-commission-rate` default 0.10, `min-lead-time-minutes` default 120, `max-booking-horizon-days` default 7). No services, controllers, or DTOs — only the persistent domain model. Verified: 542 unit tests + 22 `CartOrderSchemaIT` integration tests pass.
- [x] **5.2 Cart API** (add/view/update/remove items, lazy cart creation, clear on checkout): `CartService` (lazy cart creation, price snapshots per C-1, customer isolation from JWT subject, vendor eligibility checks at add-time, read-only stock checks with no reservation), `CartController` at `/api/v1/cart` (GET, POST /items, PATCH /items/{id}, DELETE /items/{id}, DELETE), DTOs (`CartResponse`, `CartItemResponse`, `AddCartItemRequest`, `UpdateCartItemRequest`, `CartShopView`), `/api/v1/cart/**` → `hasRole("CUSTOMER")` in `SecurityConfig`, `InventoryRepository.findAllByProductIdIn` batch-fetch. 38 unit tests + 19 integration tests pass.
- [x] **5.3 Checkout quote endpoint** (`POST /api/v1/checkout/quote`): `CheckoutService` (read-only quote generation, price drift detection, per-shop validation, BigDecimal arithmetic), `CheckoutController`, DTOs (`CheckoutQuoteRequest`, `CheckoutQuoteResponse`, `CheckoutShopQuote`, `CheckoutItemQuote`), `/api/v1/checkout/**` → `hasRole("CUSTOMER")` in `SecurityConfig`. 22 service unit tests + 11 controller slice tests = 33 new tests pass.
- [x] **5.4 Checkout endpoint** (`POST /api/v1/checkout` — transactional, idempotent, stock reservation): `CheckoutService`, `CheckoutController`, DTOs, `checkout_idempotency` table via `V15` migration, deterministic vendor order partitioning, ASC ID locking, stock reservation with `StockMovement`, slot capacity enforcement, price drift confirmation. Verified: 57 unit/slice tests + 16 `CheckoutApiIntegrationTest` pass.
- [x] **5.5 Delivery slot discovery** (`GET /api/v1/vendors/{id}/slots`): `SlotService`, `SlotController`, `SlotValidationService`, DTOs, vendor timezone-aware calculation, lead-time and booking horizon filtering, operating hours checks, slot capacity verification under vendor lock. Verified: 11 `SlotControllerTest` slice tests + 14 `SlotServiceTest` unit tests + 17 `SlotValidationServiceTest` unit tests pass.
- [x] **5.6 Customer cart and checkout UI**: full-page `CartPage` (vendor grouping, quantity stepper [1..CART_MAX_QUANTITY=99], price drift alerts, unpurchasable item blocking, order totals) per D-35 deviation; four-step `CheckoutPage` (Address, Slots, Review, and Confirmation) with native-radio slot picker and progress stepper navigation semantics. Verified: 12 `CartPage.test.tsx` tests + 15 `CheckoutPage.test.tsx` tests pass.
- [x] **5.T Phase 5 verification & RBAC tests**: frontend edge cases verified with 5 new tests across `CartPage.test.tsx` (12 tests total: quantity upper bound at 99, controls disabled during in-flight mutations) and `CheckoutPage.test.tsx` (15 tests total: empty address book guidance, unselected slot blocking, zero available slots messaging); fixed synchronous adjusting state reset defect in `CartPage.tsx`; all 587 frontend tests passing, build clean, RBAC matrix documented in `docs/rbac-matrix.md`.

## Latest Changes

| Date       | Change                                    | Files affected                                      |
|------------|-------------------------------------------|-----------------------------------------------------|
| 2026-10-24 | Phase 8 Stage 5 (task 8.5): dispute resolution � ADMIN-only `POST /api/v1/admin/disputes/{disputeId}/resolve` accepts `RESOLVED_REFUND`, `RESOLVED_PARTIAL`, or `RESOLVED_REJECTED` with required bounded resolution note. Only from `OPEN` or `UNDER_REVIEW`; resolved disputes are final (repeat/conflicting ? 409). `RESOLVED_PARTIAL` requires `refund_amount > 0` and `<= order.total` (`BigDecimal`, INR, `HALF_UP` to 2 decimals). `RESOLVED_REFUND` stores full order total. Order status (`DELIVERED`) and stock never changed. Online refunds go through `PaymentService.markRefundedWithAmount`; COD payment stays `COLLECTED`, refund obligation recorded on dispute; rejected disputes have no payment/stock effect. Pessimistic write lock on dispute row before state check (exactly one winner on concurrent resolution). Audit log row (`DISPUTE_RESOLVED`) with actor, action, timestamp, before/after values. Scalar-only `DisputeResolved` event after commit with Phase 10 hook data (dispute/order/vendor IDs, outcome, refund amount, payment method, resolver ID, timestamp). `refund_amount` column on `disputes` (V23), `refunded_amount` column on `payments` (V24). All 29 integration tests pass; 1167 unit tests pass. | `backend/src/main/resources/db/migration/V23__add_refund_amount_to_disputes.sql`, `backend/src/main/resources/db/migration/V24__add_refunded_amount_to_payments.sql`, `backend/src/main/java/com/flowerconnect/dispute/domain/Dispute.java`, `backend/src/main/java/com/flowerconnect/dispute/dto/ResolveDisputeRequest.java`, `backend/src/main/java/com/flowerconnect/dispute/dto/DisputeDetailResponse.java`, `backend/src/main/java/com/flowerconnect/dispute/event/DisputeResolved.java`, `backend/src/main/java/com/flowerconnect/dispute/repository/DisputeRepository.java`, `backend/src/main/java/com/flowerconnect/dispute/service/DisputeService.java`, `backend/src/main/java/com/flowerconnect/dispute/controller/AdminDisputeController.java`, `backend/src/main/java/com/flowerconnect/payment/domain/Payment.java`, `backend/src/main/java/com/flowerconnect/payment/service/PaymentService.java`, `backend/src/test/java/com/flowerconnect/dispute/integration/DisputeIntegrationTest.java`, `docs/progress.md`, `docs/decisions.md`, `docs/known-issues.md` |
|------------|-------------------------------------------|-----------------------------------------------------|
| 2026-10-24 | Phase 8 Stage 3 (task 8.6): cancellation UI � `CancelOrderDialog` on `OrderDetailPage` (PLACED/CONFIRMED, optional reason, refund pending/refunded banners), `CancelReasonDialog` on vendor `OrderCard`/`VendorOrderBoardPage` (CONFIRMED/PREPARING, required reason), `AdminRefundsPage` with `MarkRefundedDialog` (paginated REFUND_PENDING queue, vendor/date filters, mark-refunded with non-blank reference note <= 500 chars, idempotent), `AdminOrderLookupPage` (admin cancel any non-terminal order, required reason). Conditional dialog rendering to avoid framer-motion role conflicts. New routes: `/admin/refunds`, `/admin/orders/lookup`. Guards: CUSTOMER on cancel/payment, FLORIST on vendor cancel, ADMIN on admin cancel/refunds. All 782 frontend tests pass; lint, typecheck, build clean. | `frontend/src/features/customer/orders/pages/OrderDetailPage.tsx`, `frontend/src/features/customer/orders/components/CancelOrderDialog.tsx`, `frontend/src/features/customer/orders/api.ts`, `frontend/src/features/customer/orders/queries.ts`, `frontend/src/features/customer/orders/types.ts`, `frontend/src/features/vendor/orders/components/OrderCard.tsx`, `frontend/src/features/vendor/orders/components/CancelReasonDialog.tsx`, `frontend/src/features/vendor/orders/pages/VendorOrderBoardPage.tsx`, `frontend/src/features/vendor/orders/api.ts`, `frontend/src/features/vendor/orders/queries.ts`, `frontend/src/features/vendor/orders/types.ts`, `frontend/src/features/admin/pages/AdminRefundsPage.tsx`, `frontend/src/features/admin/pages/AdminOrderLookupPage.tsx`, `frontend/src/features/admin/components/MarkRefundedDialog.tsx`, `frontend/src/features/admin/api.ts`, `frontend/src/features/admin/queries.ts`, `frontend/src/features/admin/types.ts`, `frontend/src/app/router.tsx`, `frontend/src/app/router.test.tsx`, `frontend/src/test/factories.ts`, `docs/progress.md`, `docs/decisions.md` (D-56) |
|------------|-------------------------------------------|-----------------------------------------------------|
| 2026-10-24 | Phase 8 Stage 2 (task 8.3): refund handling — `GET /api/v1/admin/refunds` paginated refund queue listing `REFUND_PENDING` payments with optional vendor/date filters; `POST /api/v1/admin/refunds/{orderId}/mark` to mark a refund complete with a non-blank reference note (max 500 chars); `PaymentService.markRefunded` as sole writer of payment status (transitions both `payments.status` and `orders.payment_status` `REFUND_PENDING` �' `REFUNDED`, idempotent when already `REFUNDED`, 409 for any other state); `refundedAt` column on `payments` (additive `V21` migration) stamped from injected `Clock`; `audit_log` row with actor, action `PAYMENT_REFUNDED`, entity type/id, and before/after reason; customer order detail exposes `refundedAt` and `paymentStatus` without internal fields. SecurityConfig: `/api/v1/admin/refunds/**` �' `hasRole("ADMIN")`. Unit suite 1167 tests + integration test `RefundIntegrationTest` (13 tests: RBAC 401/403/404/200, pagination, vendor/date filters, double-mark idempotency, wrong-state 409, audit row contents, customer-visible refund state), all green. | `backend/src/main/java/com/flowerconnect/payment/service/PaymentService.java`, `backend/src/main/java/com/flowerconnect/payment/controller/AdminRefundController.java`, `backend/src/main/java/com/flowerconnect/payment/dto/MarkRefundRequest.java`, `backend/src/main/java/com/flowerconnect/payment/dto/RefundMarkedResponse.java`, `backend/src/main/java/com/flowerconnect/payment/dto/RefundQueueItem.java`, `backend/src/main/java/com/flowerconnect/payment/domain/Payment.java`, `backend/src/main/java/com/flowerconnect/payment/repository/PaymentRepository.java`, `backend/src/main/java/com/flowerconnect/config/SecurityConfig.java`, `backend/src/main/java/com/flowerconnect/customer/order/service/CustomerOrderService.java`, `backend/src/main/java/com/flowerconnect/customer/order/dto/CustomerOrderResponse.java`, `backend/src/main/resources/db/migration/V21__add_refunded_at_to_payments.sql`, `backend/src/test/java/com/flowerconnect/payment/integration/RefundIntegrationTest.java`, `docs/progress.md`, `docs/decisions.md` |
| 2026-10-24 | Phase 7 final audit (`phase-7-payments` at f0f2b52): investigated and fixed two simulator integration-test failures � an `OrderItem`-setup gap in `simulatorFailCancelsGroupAndReleasesReservations` (reservation not released) and security-filter ordering masking the MVC 404 in `simulatorRouteIsNotRegisteredInProd` (root causes were not shared database state). Both tests pass alone and together. Full Phase 7 verification green: backend unit suite 1159 tests + integration suite 571 tests, 0 failures; frontend lint, typecheck, tests (782 passed), build all clean. Payment invariants audited against code evidence. | `SimulatorControllerIntegrationTest.java`, `SimulatorProdAbsenceIntegrationTest.java`, `docs/progress.md` |
| 2026-10-24 | Phase 7 frontend (task 7.6): payment UI — payment-method selector at checkout (COD default; ONLINE offered since 7.4 backend built), `PaymentStatusPage` at `/payment/:paymentGroupId` polling `GET /api/v1/payments/group/{paymentGroupId}` at 8 s (plan �9.4: 5–10 s), pausing while tab hidden, stopping at terminal states (`PAID`, `FAILED`, `REFUNDED`), resume action via `POST /api/v1/payments/{orderId}/resume` (refresh only — never marks paid; only the verified webhook flow completes payment), payment state on order detail. New `src/features/payment/` slice (types, api, queries), 7 RTL/Vitest tests covering pending�"terminal transitions, polling stop, resume API call, terminal-state UI, error handling with retry. CheckoutPage passes `paymentMethod` and navigates to `/payment/:paymentGroupId` for ONLINE, `/orders` for COD. Router adds `/payment/:paymentGroupId` guarded `CUSTOMER`. Backend unit suite: 1157 tests, 0 failures (AppPropertiesBindingTest fixed to run without a local MySQL account via minimal context + lazy init). Frontend: lint, typecheck, tests, build all clean. | `frontend/src/features/payment/**` (types.ts, api.ts, queries.ts, pages/PaymentStatusPage.tsx, PaymentStatusPage.test.tsx), `frontend/src/features/checkout/pages/CheckoutPage.tsx`, `frontend/src/features/checkout/types.ts`, `frontend/src/features/checkout/pages/CheckoutPage.test.tsx`, `frontend/src/app/router.tsx`, `frontend/src/test/factories.ts`, `backend/src/test/java/com/flowerconnect/config/AppPropertiesBindingTest.java`, `docs/progress.md`, `docs/decisions.md` |
| 2026-10-24 | Phase 7 Stage 3.5 (task 7.5): reservation expiry job — `PaymentExpiryService` cancels `PENDING_PAYMENT` orders older than a configurable number of minutes (`app.payment-expiry-minutes`, default 30) via `OrderStateMachine.transition` (PENDING_PAYMENT �' CANCELLED, actor SYSTEM), releasing reserved stock through `InventoryService.releaseReservedStock`; `PaymentExpiryScheduler` runs it on configurable cron (`app.payment-expiry-cron`, default every minute, disabled with "-"); each candidate in its own REQUIRES_NEW transaction so one failure never blocks the batch; bounded batch (`app.payment-expiry-batch-size`, default 100). Verified with `PaymentExpiryServiceTest` (2 unit tests) and `PaymentExpiryIntegrationTest` (13 integration tests vs real MySQL, all green). Full backend unit suite: 1159 tests, 0 failures. | `backend/src/main/java/com/flowerconnect/order/service/PaymentExpiryService.java`, `backend/src/main/java/com/flowerconnect/scheduler/PaymentExpiryScheduler.java`, `backend/src/main/java/com/flowerconnect/config/AppProperties.java`, `backend/src/test/resources/application-test.yml`, `backend/src/test/java/com/flowerconnect/order/service/PaymentExpiryServiceTest.java`, `backend/src/test/java/com/flowerconnect/order/PaymentExpiryIntegrationTest.java`, `docs/progress.md`, `docs/decisions.md` |
|------------|-------------------------------------------|-----------------------------------------------------|
| 2026-10-23 | Phase 6 Stage 5 (tasks 6.6, 6.7, 6.T): customer orders UI (`/orders` history + `/orders/:orderId` detail) and vendor order board (`/vendor/orders` Kanban). Shared `usePolling` hook (`src/hooks/usePolling.ts`) with tab-visibility pause and terminal-status stop. Customer pages guarded `CUSTOMER`, vendor page guarded `FLORIST`. Checkout success links to `/orders`. Reject-reason dialog with validation, 409 insufficient-stock handling, double-click protection. 775 frontend tests across 70 files, 0 failures; lint, typecheck, build clean. Browser E2E verified: place COD order → vendor accept (quantity & reserved_quantity both decrease) → deliver (payment_status=COLLECTED) → place 2nd order → reject (only reserved_quantity released, quantity unchanged). | `frontend/src/hooks/usePolling.ts`, `frontend/src/hooks/usePolling.test.tsx`, `frontend/src/features/customer/orders/**` (types, api, queries, components/OrderStatusStepper, pages/OrderHistoryPage, OrderDetailPage + 3 test files), `frontend/src/features/vendor/orders/**` (types, api, queries, components/TodaySummaryStrip, OrderCard, RejectReasonDialog, pages/VendorOrderBoardPage + test file), `frontend/src/app/router.tsx`, `frontend/src/features/vendor/components/VendorLayout.tsx`, `frontend/src/features/checkout/pages/CheckoutPage.tsx`, `frontend/src/test/factories.ts`, `frontend/src/app/router.test.tsx`, `docs/progress.md`, `docs/decisions.md`, `docs/known-issues.md` |
| 2026-10-23 | Phase 7 Stage 1 (tasks 7.1-7.2): COD payments model — `V19` migration (`payments`, `payment_events`, backfill); `Payment`/`PaymentEvent` entities + repositories; `PaymentGateway` + `CodPaymentProvider` (no external call); `PaymentService` sole writer of payment status (`createCodPayment`, `collectPayment`); checkout creates one pending COD payment row per order; delivery collects via `PaymentService` syncing `payments.status` + `orders.payment_status` + `collected_at`; reject/cancel leave payment `PENDING` (Option B); `PENDING_PAYMENT` counts toward slot capacity; auto-reject age measured from `PLACED` history entry (`findPlacedStaleBefore`). Additive migration only; no online provider/webhook/capture, no card data or secrets stored. `PaymentServiceTest`, `CodPaymentProviderTest`, `PaymentIntegrationTest` (7), `AutoRejectIntegrationTest` (14), `VendorOrderIntegrationTest` (15), full unit suite: all green. | `backend/src/main/resources/db/migration/V19__create_payments_and_payment_events.sql`, `backend/src/main/java/com/flowerconnect/payment/**` (domain, repository, service, gateway, provider), `backend/src/main/java/com/flowerconnect/checkout/service/CheckoutService.java`, `backend/src/main/java/com/flowerconnect/order/service/OrderStateMachine.java`, `backend/src/main/java/com/flowerconnect/order/repository/OrderRepository.java`, `backend/src/main/java/com/flowerconnect/order/service/AutoRejectService.java`, `backend/src/test/java/com/flowerconnect/payment/service/PaymentServiceTest.java`, `backend/src/test/java/com/flowerconnect/payment/provider/CodPaymentProviderTest.java`, `backend/src/test/java/com/flowerconnect/payment/integration/PaymentIntegrationTest.java`, `backend/src/test/java/com/flowerconnect/checkout/CheckoutServiceTest.java`, `backend/src/test/java/com/flowerconnect/order/service/OrderStateMachineTest.java`, `backend/src/test/java/com/flowerconnect/order/AutoRejectIntegrationTest.java`, `docs/progress.md`, `docs/decisions.md` |
| 2026-10-24 | Phase 7 Stage 3 (task 7.4 part 2): signed webhook + simulated provider — `WebhookController` (`POST /api/v1/payments/webhook`, permitAll, signature-authenticated), `WebhookSignatureService` (HMAC-SHA256 over timestamp + body, tolerance window, constant-time compare), `PaymentService.processProviderEvent` (group transitions via `OrderStateMachine.transitionGroup`, idempotency, late-event handling), `SimulatedPaymentProvider` + `SimulatorController` (`!prod` profile, customer auth + ownership), `WebhookSecretValidator` (prod startup check), `AppProperties` webhook-secret config, `SecurityConfig` matcher ordering fix (webhook permitAll before payments/**), `RateLimitFilter` IP-keyed for webhook. `WebhookControllerIntegrationTest` 13/13 pass, `WebhookSignatureServiceTest`, `WebhookSecretValidatorTest`, `PaymentServiceTest` pass, `SimulatorControllerIntegrationTest` (5 tests), `SimulatorProdAbsenceIntegrationTest` (2 tests). `resumePayment` fixed: no longer transitions payment/order, returns pending state, marks intent EXPIRED on timeout. Full unit suite: 1156 tests pass (1 environment-only failure: AppPropertiesBindingTest requires local MySQL `test` user). | `backend/src/main/java/com/flowerconnect/payment/controller/WebhookController.java`, `backend/src/main/java/com/flowerconnect/payment/service/WebhookSignatureService.java`, `backend/src/main/java/com/flowerconnect/payment/service/PaymentService.java`, `backend/src/main/java/com/flowerconnect/payment/provider/SimulatedPaymentProvider.java`, `backend/src/main/java/com/flowerconnect/payment/controller/SimulatorController.java`, `backend/src/main/java/com/flowerconnect/config/WebhookSecretValidator.java`, `backend/src/main/java/com/flowerconnect/config/SecurityConfig.java`, `backend/src/main/java/com/flowerconnect/config/AppProperties.java`, `backend/src/main/java/com/flowerconnect/config/RateLimitFilter.java`, `backend/src/main/java/com/flowerconnect/order/service/OrderStateMachine.java`, `backend/src/test/java/com/flowerconnect/payment/controller/WebhookControllerIntegrationTest.java`, `backend/src/test/java/com/flowerconnect/payment/controller/SimulatorControllerIntegrationTest.java`, `backend/src/test/java/com/flowerconnect/payment/controller/SimulatorProdAbsenceIntegrationTest.java`, `backend/src/test/java/com/flowerconnect/payment/service/WebhookSignatureServiceTest.java`, `backend/src/test/java/com/flowerconnect/config/WebhookSecretValidatorTest.java`, `docs/progress.md`, `docs/decisions.md` | `V20__create_payment_intents.sql` migration; `PaymentIntent` entity (`IntentStatus` CREATED/PAID/EXPIRED/FAILED) + `PaymentIntentRepository`; `AppProperties.intentTtlMinutes` (default 30); `CheckoutRequest`/`CheckoutQuoteRequest`/`CheckoutResponse` gain `paymentMethod` (COD default, ONLINE supported) + `paymentGroupId`/`intentReference`/`intentExpiresAt`; `CheckoutService` creates one shared intent + one `PENDING` payment per order for ONLINE (orders start `PENDING_PAYMENT`, stock reserved via `RESERVE` movement), COD path preserved; `PaymentService` adds `fetchPaymentState`, `fetchGroupState`, `resumePayment` (transitions via `OrderStateMachine`, syncs payment to PAID, intent to PAID); `PaymentController` exposes `GET /api/v1/payments/{orderId}`, `GET /api/v1/payments/group/{paymentGroupId}`, `POST /api/v1/payments/{orderId}/resume`; `/api/v1/payments/**` → `hasRole("CUSTOMER")`; `OrderRepository.findByVendorIdAndStatusNotOrderByCreatedAtDesc`; `VendorOrderService` excludes `PENDING_PAYMENT` from default list and summary. `OnlineCheckoutIntegrationTest` (7 tests vs real MySQL), `PaymentServiceTest` (17 unit tests), `PaymentControllerTest` (9 slice RBAC tests); full unit suite: 1133 tests, 0 failures, BUILD SUCCESS. | `backend/src/main/resources/db/migration/V20__create_payment_intents.sql`, `backend/src/main/java/com/flowerconnect/checkout/service/CheckoutService.java`, `backend/src/main/java/com/flowerconnect/checkout/dto/CheckoutRequest.java`, `backend/src/main/java/com/flowerconnect/checkout/dto/CheckoutQuoteRequest.java`, `backend/src/main/java/com/flowerconnect/checkout/dto/CheckoutResponse.java`, `backend/src/main/java/com/flowerconnect/payment/domain/PaymentIntent.java`, `backend/src/main/java/com/flowerconnect/payment/repository/PaymentIntentRepository.java`, `backend/src/main/java/com/flowerconnect/payment/service/PaymentService.java`, `backend/src/main/java/com/flowerconnect/payment/controller/PaymentController.java`, `backend/src/main/java/com/flowerconnect/payment/dto/PaymentStateResponse.java`, `backend/src/main/java/com/flowerconnect/payment/dto/PaymentGroupStateResponse.java`, `backend/src/main/java/com/flowerconnect/payment/dto/PaymentResumeResponse.java`, `backend/src/main/java/com/flowerconnect/config/AppProperties.java`, `backend/src/main/java/com/flowerconnect/config/SecurityConfig.java`, `backend/src/main/java/com/flowerconnect/order/repository/OrderRepository.java`, `backend/src/main/java/com/flowerconnect/vendor/service/VendorOrderService.java`, `backend/src/test/java/com/flowerconnect/checkout/integration/OnlineCheckoutIntegrationTest.java`, `backend/src/test/java/com/flowerconnect/payment/service/PaymentServiceTest.java`, `backend/src/test/java/com/flowerconnect/payment/controller/PaymentControllerTest.java`, `backend/src/test/java/com/flowerconnect/checkout/CheckoutServiceTest.java`, `docs/progress.md`, `docs/decisions.md` |
| 2026-10-22 | Phase 6 Stage 4 (task 6.5): auto-reject job — unaccepted `PLACED` orders older than a configurable number of minutes are auto-cancelled with the reservation released; `AutoRejectService`, `AutoRejectScheduler`, `PlatformConfig` entity/repository, `V18__create_platform_config_table.sql` migration (seeded `auto_reject_minutes` = 60), `AppProperties.auto-reject-cron`/`auto-reject-batch-size`; threshold read from `platform_config`, cutoff computed from injected `Clock` (strictly older than `now - minutes`), each candidate processed in its own `REQUIRES_NEW` transaction through `OrderStateMachine.transition` (`PLACED → CANCELLED`, actor `SYSTEM`, reason `AUTO_REJECTED_NO_RESPONSE`), bounded batch, cron disabled in tests. `AutoRejectServiceTest` (4 unit tests) + `AutoRejectIntegrationTest` (13 integration tests vs real MySQL): all green. Full backend unit suite: 1106 tests, 0 failures, BUILD SUCCESS. | `backend/src/main/java/com/flowerconnect/config/AppProperties.java`, `backend/src/main/java/com/flowerconnect/config/PlatformConfig.java`, `backend/src/main/java/com/flowerconnect/config/PlatformConfigRepository.java`, `backend/src/main/java/com/flowerconnect/order/repository/OrderRepository.java`, `backend/src/main/java/com/flowerconnect/order/service/AutoRejectService.java`, `backend/src/main/java/com/flowerconnect/scheduler/AutoRejectScheduler.java`, `backend/src/main/resources/db/migration/V18__create_platform_config_table.sql`, `backend/src/main/resources/application.yml`, `backend/src/test/resources/application-test.yml`, `backend/src/test/java/com/flowerconnect/order/service/AutoRejectServiceTest.java`, `backend/src/test/java/com/flowerconnect/order/AutoRejectIntegrationTest.java`, `docs/progress.md`, `docs/decisions.md`, `docs/known-issues.md` |
| 2026-10-22 | Phase 6 Stage 3 (task 6.3): customer order API — `GET /api/v1/orders` (paginated, optional status filter) and `GET /api/v1/orders/{id}` (ownership, full detail); `CustomerOrderService`, `CustomerOrderController`, DTOs in `com.flowerconnect.customer.order.dto`; `/api/v1/orders/**` → `hasRole("CUSTOMER")` in `SecurityConfig`; read-only, no status changes (all transitions go through `OrderStateMachine`); per-handler ownership (missing order 404, foreign order 403); detail includes payment method/status, vendor name, address snapshot, delivery slot, per-item snapshots, BigDecimal fee breakdown and chronological status timeline. `CustomerOrderControllerTest` (6 slice tests) + `CustomerOrderIntegrationTest` (7 integration tests vs real MySQL): all green. Full backend unit suite: 1102 tests, 0 failures, BUILD SUCCESS. | `backend/src/main/java/com/flowerconnect/config/SecurityConfig.java`, `backend/src/main/java/com/flowerconnect/order/repository/OrderRepository.java`, `backend/src/main/java/com/flowerconnect/customer/order/controller/CustomerOrderController.java`, `backend/src/main/java/com/flowerconnect/customer/order/service/CustomerOrderService.java`, `backend/src/main/java/com/flowerconnect/customer/order/dto/CustomerOrderResponse.java`, `backend/src/main/java/com/flowerconnect/customer/order/dto/CustomerOrderListItem.java`, `backend/src/main/java/com/flowerconnect/customer/order/dto/CustomerOrderItemResponse.java`, `backend/src/main/java/com/flowerconnect/customer/order/dto/CustomerOrderTimelineEntry.java`, `backend/src/main/java/com/flowerconnect/customer/order/dto/CustomerOrderPageResponse.java`, `backend/src/test/java/com/flowerconnect/customer/order/controller/CustomerOrderControllerTest.java`, `backend/src/test/java/com/flowerconnect/customer/order/CustomerOrderIntegrationTest.java`, `docs/progress.md`, `docs/decisions.md` |
| 2026-10-22 | Phase 6 Stage 2 (tasks 6.2–6.4): vendor order action endpoints (accept/reject/prepare/out-for-delivery/delivered), paginated vendor order list with status/date filters, and today's summary under `/api/v1/vendor/orders`; `VendorOrderService`, `VendorOrderController`, DTOs in `com.flowerconnect.vendor.dto`; `/api/v1/vendor/**` → `hasRole("FLORIST")` in `SecurityConfig`; no `@RequiresApprovedVendor` per D-6; stock side effects via `OrderStateMachine` → `InventoryService`; additive `V17` migration aligns committed `V16` `actor_type` (`VARCHAR`) with the `ActorType` entity enum (`ENUM`) so `ddl-auto: validate` passes. `VendorOrderControllerTest` (12 slice tests) + `VendorOrderIntegrationTest` (15 integration tests vs real MySQL): all green. Full backend unit suite: 1096 tests, 0 failures, BUILD SUCCESS. | `backend/src/main/java/com/flowerconnect/config/SecurityConfig.java`, `backend/src/main/java/com/flowerconnect/order/repository/OrderRepository.java`, `backend/src/main/java/com/flowerconnect/vendor/controller/VendorOrderController.java`, `backend/src/main/java/com/flowerconnect/vendor/service/VendorOrderService.java`, `backend/src/main/java/com/flowerconnect/vendor/dto/VendorOrderResponse.java`, `backend/src/main/java/com/flowerconnect/vendor/dto/VendorOrderPageResponse.java`, `backend/src/main/java/com/flowerconnect/vendor/dto/VendorOrderSummaryResponse.java`, `backend/src/main/java/com/flowerconnect/vendor/dto/VendorOrderRejectRequest.java`, `backend/src/main/resources/db/migration/V17__alter_order_status_history_actor_type_to_enum.sql`, `backend/src/test/java/com/flowerconnect/vendor/controller/VendorOrderControllerTest.java`, `backend/src/test/java/com/flowerconnect/vendor/VendorOrderIntegrationTest.java`, `docs/progress.md`, `docs/decisions.md`, `docs/known-issues.md` |
| 2026-10-21 | Phase 6 Stage 1 (task 6.1): order state machine — `OrderStateMachine` is the sole service for changing order status, encoding the full plan section 6.1 transition table (including `PENDING_PAYMENT` and Phase 8-reserved transitions), writing one `order_status_history` row per transition from the injected `Clock`, publishing scalar-only domain events after commit, and delegating stock side effects to `InventoryService` under the order-then-inventory ascending-product-id lock order. Additive `V16__add_order_status_history_actor_type.sql` migration. `OrderStateMachineTest` exhaustive table-driven + focused tests: 333 tests, 0 failures. Full backend unit suite: 1084 tests, 0 failures, BUILD SUCCESS. | `backend/src/main/java/com/flowerconnect/order/service/OrderStateMachine.java`, `backend/src/main/java/com/flowerconnect/order/domain/ActorType.java`, `backend/src/main/java/com/flowerconnect/order/domain/OrderStatusHistory.java`, `backend/src/main/java/com/flowerconnect/order/event/OrderStatusChanged.java`, `backend/src/main/java/com/flowerconnect/order/event/OrderCancelled.java`, `backend/src/main/java/com/flowerconnect/order/event/OrderDelivered.java`, `backend/src/main/java/com/flowerconnect/order/repository/OrderRepository.java`, `backend/src/main/java/com/flowerconnect/inventory/service/InventoryService.java`, `backend/src/main/java/com/flowerconnect/inventory/domain/StockDelta.java`, `backend/src/main/resources/db/migration/V16__add_order_status_history_actor_type.sql`, `backend/src/test/java/com/flowerconnect/order/service/OrderStateMachineTest.java`, `docs/progress.md`, `docs/decisions.md` |
| 2026-10-21 | D-1 fix deployed to the development frontend: the shared header no longer queries the customer cart for anonymous visitors, and the axios interceptor no longer starts a futile refresh/logout when there is no access token — `useCart()` gained an optional `enabled` flag (default `true`), the header gates on `isAuthenticated && user?.role === "CUSTOMER"`, and the interceptor rejects a 401 when no session exists. 736 frontend tests pass; typecheck, lint and build clean; the frontend image `sha256:a049c351bba3…` was rebuilt from the working tree and only the frontend container recreated, with all four services healthy; the six public routes were re-verified through Playwright MCP against the deployed build with zero cart/refresh/logout requests and zero console errors (D-41) | `frontend/src/features/cart/queries.ts`, `frontend/src/app/components/SiteHeader.tsx`, `frontend/src/shared/lib/api.ts`, `frontend/src/app/components/SiteHeader.test.tsx`, `frontend/src/shared/lib/api.test.ts`, `docs/progress.md`, `docs/decisions.md` |
| 2026-10-20 | Gate E final verification: D-37's browser-verification statements corrected to record the real-Chromium outcome and the stubbed catalogue responses, and two follow-ups recorded — the `docs/decisions.md` encoding and the root route-transition remount | `docs/decisions.md`, `docs/progress.md` |
| 2026-10-19 | Gate E remediation: the product detail is an explicit overlay of its parent surface (D-37), the product route is nested under the search surface as well as the storefront, the search filters moved to an in-memory store so they survive the route remount, and the quantity bound, primary-image selection and malformed-id guards were added; Stage 1 documentation corrections for the Phase 4 notes and D-36–D-40 | `frontend/src/features/customer/**` (navigation.ts and stores/search-store.ts new; ProductDetailPage, VendorStorefrontPage, SearchPage, ProductCard, ProductGrid, queries.ts), `frontend/src/app/router.tsx`, `frontend/src/app/router.test.tsx`, `docs/progress.md`, `docs/decisions.md`, `docs/rbac-matrix.md` |
| 2026-10-18 | Phase 5 Task 5.T: Frontend cart & checkout edge cases verified (5 new tests across CartPage and CheckoutPage); fixed synchronous adjusting reset in CartPage.tsx; all 587 frontend tests pass | `frontend/src/features/cart/pages/CartPage.tsx`, `frontend/src/features/cart/pages/CartPage.test.tsx`, `frontend/src/features/checkout/pages/CheckoutPage.test.tsx`, `docs/progress.md` |
| 2026-10-17 | Phase 5 Stage 4: Checkout accessibility refactor (native-radio slot picker with fieldset/legend, four-step stepper navigation semantics with `aria-current="step"`); full-page cart deviation recorded (D-35); Phase 5 RBAC matrix documented | `frontend/src/features/checkout/pages/CheckoutPage.tsx`, `docs/decisions.md`, `docs/progress.md`, `docs/rbac-matrix.md` |
| 2026-10-14 | Phase 5 Task 5.3: Checkout quote endpoint — `CheckoutService`, `CheckoutController`, DTOs, platform fee override, price drift detection, per-shop validation; all 613 unit tests pass | `backend/src/main/java/com/flowerconnect/checkout/**` (controller, dto, service), `backend/src/test/java/com/flowerconnect/checkout/**` (CheckoutServiceTest, CheckoutControllerTest), `docs/progress.md` |
| 2026-10-13 | Phase 5 Task 5.2: Customer cart API — `CartService`, `CartController`, DTOs, security rule, inventory batch-fetch; all 580 unit tests + 41 cart integration tests (19 CartApiIntegrationTest + 22 CartOrderSchemaIT) pass | `backend/src/main/java/com/flowerconnect/cart/**` (controller, dto, service), `backend/src/main/java/com/flowerconnect/config/SecurityConfig.java`, `backend/src/main/java/com/flowerconnect/inventory/repository/InventoryRepository.java`, `backend/src/test/java/com/flowerconnect/cart/**` (CartServiceTest, CartControllerTest, CartApiIntegrationTest, CartOrderSchemaIT), `docs/progress.md` |
| 2026-10-13 | Phase 5 Stage 1 (task 5.1): V13 migration with service_locations.timezone + carts/cart_items/orders/order_items/order_status_history tables; Cart/CartItem/Order/OrderItem/OrderStatusHistory entities with OrderStatus/PaymentMethod/PaymentStatus enums; five repositories; CheckoutProperties config class | `backend/src/main/resources/db/migration/V13__create_carts_orders.sql`, `backend/src/main/java/com/flowerconnect/cart/domain/*` (Cart, CartItem), `backend/src/main/java/com/flowerconnect/order/domain/*` (Order, OrderItem, OrderStatusHistory), `backend/src/main/java/com/flowerconnect/cart/repository/*`, `backend/src/main/java/com/flowerconnect/order/repository/*`, `backend/src/main/java/com/flowerconnect/config/CheckoutProperties.java`, `backend/src/main/java/com/flowerconnect/domain/ServiceLocation.java` (+timezone), `backend/src/main/java/com/flowerconnect/FlowerConnectApplication.java` (+registration), `backend/src/main/resources/application.yml` (+checkout props), `backend/src/test/resources/application-test.yml` (+checkout props), `backend/src/test/java/com/flowerconnect/order/CartOrderSchemaIT.java`, `backend/src/test/java/com/flowerconnect/config/CheckoutPropertiesTest.java` |
| 2026-10-10 | Implement Task 4.3: Geo discovery API (`GET /api/v1/discover`) | DiscoveryResponse DTO, DiscoveryService, DiscoveryController, SecurityConfig update, unit and integration tests |
| 2026-10-09 | Phase 4 Stage 1 (task 4.1): customer address book — V12 migration, entity/repository, DTOs/MapStruct mapper, service with the one-default-per-customer invariant under a user-row lock (D-32), REST controller at `/api/v1/addresses` in a CUSTOMER-only namespace; two defects fixed during verification (REPEATABLE READ snapshot poisoning of the lock protocol, and the `isDefault` wire name — D-33) | `backend/src/main/java/com/flowerconnect/customer/**` (domain, repository, dto, mapper, service, controller), `backend/src/main/java/com/flowerconnect/repository/UserRepository.java`, `backend/src/main/java/com/flowerconnect/config/SecurityConfig.java`, `backend/src/main/resources/db/migration/V12__create_addresses.sql`, `backend/src/test/java/com/flowerconnect/customer/**` (AddressServiceTest, AddressApiIntegrationTest, AddressConcurrencyIntegrationTest), `docs/decisions.md` (D-32, D-33), `docs/rbac-matrix.md`, `docs/progress.md` |
| 2026-10-05 | Phase 3 final audit fix (task 3.1): active-only public category read and an all-category admin listing with correct pagination, plus regression coverage at service and HTTP level | `CategoryRepository.java`, `CategoryService.java`, `CategoryController.java`, `CategoryServiceTest.java`, `CategoryApiIntegrationTest.java`, `docs/decisions.md` (D-17, D-18), `docs/known-issues.md` (#021 closed, #025, #026), `docs/progress.md` |
| 2026-10-05 | Phase 3h close-out (task 3.T): RBAC matrix for every Phase 3 endpoint mapped to the integration test that asserts each cell; runtime image can create its upload root; `/v3/api-docs` verified at 34 endpoints after rebuilding a stale image | `docs/rbac-matrix.md` (new), `docs/progress.md`, `docs/known-issues.md` (issues 023, 024 + Phase 3h gotchas), `backend/Dockerfile`, `docker-compose.yml` |
| 2026-10-05 | Phase 3g Task 3.9: vendor catalog UI — listing with search/filters/pagination, Shopify-style create/edit, image manager, per-product and shop-wide stock panels, movement history, approval gate over the cached profile | `src/app/router.tsx`, `src/features/vendor/**` (types, api, queries, format, form-schema, 9 components, 3 pages, 5 test files), `src/shared/{types.ts,format.ts,components/Pagination.tsx}`, `src/shared/lib/api-error.ts`, `src/test/{factories.ts,api-errors.ts}`, `src/features/admin/**` (pagination extracted to shared), `docs/decisions.md` (D-29, D-30, D-31), `docs/progress.md`, `docs/known-issues.md` |
| 2026-10-04 | Phase 3f Task 3.8: StorageService with a profile-selected local backend and a declared S3 stub, content-sniffed image pipeline that decodes/rescales/re-encodes, one-primary invariant under a product row lock | `storage/**` (StorageService, LocalDiskStorageService, S3StorageService, StorageProperties, StorageException, image/{ImageFormat, ImageTypeDetector, ImageProcessor, ImageUploadProperties, ProcessedImage}), `VendorProductImageController.java`, `ProductImageService.java`, `ProductImageOrderRequest.java`, `ProductImageRepository.java`, `ProductService.java`, `ErrorCode.java`, `BusinessException.java`, `GlobalExceptionHandler.java`, `pom.xml`, 4 `application*.yml`, 7 test classes, `docs/decisions.md` (D-26, D-27, D-28), `docs/progress.md`, `docs/known-issues.md` |
| 2026-10-04 | Phase 3e Task 3.7: inventory expiry sweep on the injected Clock, configurable cron and bounded batch, self-clearing candidate predicate | `InventoryExpiryService.java`, `InventoryExpiryScheduler.java`, `InventoryRepository.java`, `AppProperties.java`, `application-test.yml`, `InventoryExpiryServiceTest.java`, `InventoryExpirySchedulerTest.java`, `InventoryExpiryIntegrationTest.java`, `docs/decisions.md` (D-25), `docs/progress.md` |
| 2026-10-04 | Phase 3d Task 3.6: vendor inventory API with pessimistic row locking, reserved-quantity floor, and movement audit trail | `InventoryService.java`, `VendorInventoryController.java`, `InventoryRepository.java`, `StockMovementRepository.java`, `InventorySpecifications.java`, `StockMovementMapper.java`, `ErrorCode.java`, `BusinessException.java`, `GlobalExceptionHandler.java`, 10 DTOs, `InventoryServiceTest.java`, `VendorInventoryControllerTest.java`, `VendorInventoryIntegrationTest.java`, `InventoryConcurrencyIntegrationTest.java`, `docs/decisions.md` (D-24), `docs/progress.md`, `docs/known-issues.md` |
| 2026-10-03 | Phase 3c Task 3.5: vendor catalog API with approval gating, ownership scoping, filtered listing and soft delete | `VendorProductController.java`, `ProductService.java`, `ProductRepository.java`, `ProductSpecifications.java`, `ProductRequest.java`, `VendorProductControllerTest.java`, `VendorCatalogIntegrationTest.java`, `ProductServiceTest.java`, `ProductInventoryIntegrationTest.java`, `docs/decisions.md` (D-22, D-23), `docs/progress.md`, `docs/known-issues.md` |
| 2026-10-03 | Phase 3b Tasks 3.2–3.4: product, image, inventory and stock-movement data model with ProductService foundation | `V8__products.sql`, `V9__product_images.sql`, `V10__inventory.sql`, `V11__stock_movements.sql`, `backend/src/main/java/com/flowerconnect/catalog/**` (Product, ProductImage, repositories, DTOs, ProductMapper, ProductService), `backend/src/main/java/com/flowerconnect/inventory/**` (Inventory, StockMovement, repositories), `backend/src/test/java/com/flowerconnect/catalog/**` (ProductServiceTest, ProductInventoryIntegrationTest), `docs/decisions.md` (D-20, D-21), `docs/progress.md` |
| 2026-10-03 | Phase 3a Task 3.1: Category entity with hierarchical admin CRUD and public read | `V7__categories.sql`, `backend/src/main/java/com/flowerconnect/catalog/**` (domain, repository, service, controller, dto, mapper), `SecurityConfig.java`, `backend/src/test/java/com/flowerconnect/catalog/**` (CategoryServiceTest, AdminCategoryControllerTest, CategoryApiIntegrationTest, CategorySeedIntegrityTest), `docs/decisions.md` (D-17, D-18, D-19), `docs/progress.md` |
| 2026-10-01 | Phase 2 close-out: 2.T checklist gaps closed, D-4/D-6 written up, issue 019 closed | `VendorApiIntegrationTest.java`, `docs/progress.md`, `docs/decisions.md`, `docs/known-issues.md` |
| 2026-10-01 | Task 2.10 admin frontend: dashboard, vendor management with reason dialog, user management with pagination and filters | `src/app/router.tsx`, `src/app/router.test.tsx`, `src/features/admin/**` (types, api, queries, format, form-schema, components, pages, 5 test files), `src/test/factories.ts`, `docs/*` |
| 2026-10-01 | Task 2.9 vendor frontend: dashboard, profile, settings and hours screens behind a reusable `ProtectedRoute` | `src/app/router.tsx`, `src/features/vendor/**` (types, api, queries, format, form-schema, components, pages), `src/shared/components/ProtectedRoute.tsx`, `src/shared/lib/api-error.ts`, `src/features/auth/types.ts`, `src/test/**`, `package.json`, `vite.config.ts`, `docs/*` |
| 2026-10-01 | Task 2.8 admin user management: listing with filters, status change with revocation + audit, self-targeting refused | `AdminUserController.java`, `AdminUserService.java`, `UserStatusService.java`, `AdminUserMapper.java`, `UserSpecifications.java`, 3 DTOs, `UserRepository.java`, 4 test classes, `docs/*` |
| 2026-10-01 | Task 2.7 approval gating: guard, composed `@PreAuthorize`, approved-only specification | `VendorApprovalGuard.java`, `RequiresApprovedVendor.java`, `VendorNotApprovedException.java`, `VendorProfileSpecifications.java`, `VendorProfileRepository.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `ErrorCode.java`, `BusinessException.java`, 3 test classes, `docs/*` |
| 2026-09-27 | Stage 6: Redis removal, Vite dev proxy, MySQL healthcheck, .gitignore uploads, known-issues update | `.gitignore`, `docker-compose.yml`, `application.yml`, `application-prod.yml`, `vite.config.ts`, `docs/known-issues.md` |
| 2026-09-14 | Initial Phase 0 scaffold established     | All files (initial commits)                          |
| 2026-09-14 | Created persistent project memory (optimized for limited models) | `AGENTS.md`, `CLAUDE.md`, `docs/*`, `kilo.jsonc` |
| 2026-09-14 | Fixed unit test failures across all test classes | `JwtService.java`, `AuthControllerTest.java`, `UserControllerTest.java`, `AuthServiceTest.java`, `RefreshTokenServiceTest.java` |
| 2026-09-14 | Completed Phase 1 authentication backend | V2/V3 migrations, auth services, controllers, 51 unit tests |
| 2026-09-15 | Completed Phase 1 authentication frontend | login/register pages, auth API client, Zustand store, route guards, init hook, token refresh/retry interceptor, 41 frontend tests |
| 2026-09-15 | Fixed /auth/refresh 401-deadlock in axios interceptor | `shared/lib/api.ts` response interceptor skips public endpoints |
| 2026-09-15 | Fixed init edge case: hasLoadedInitial now set on /users/me failure | `auth-store.ts` loadCurrentUser catch block |
| 2026-09-15 | Housekeeping: gitignored build artifacts | `.gitignore` (added tsconfig.tsbuildinfo, *.tsbuildinfo) |
| 2026-09-17 | Added auth-aware navigation with logout (nav component) | `frontend/src/app/router.tsx` |
| 2026-09-17 | Fixed login LazyInitializationException (eager Role fetch in JPA queries) | `UserRepository.java`, `RefreshTokenRepository.java`, `AuthService.java`, `AuthServiceTest.java` |
| 2026-09-18 | Added phone-number uniqueness to registration (app-level + DB constraint) | V5 migration, `UserRepository.java`, `AuthService.java`, `User.java`, tests |
| 2026-09-19 | Fixed Testcontainers per-class container lifecycle causing connection refused | Removed `@Container` from `IntegrationTestBase.MYSQL`, using singleton static container pattern |
| 2026-09-21 | Completed Stage 1b: login status check, email normalization, stronger token tests, integration tests | `AuthService.java`, `UserDetailsImpl.java`, `UserDetailsServiceImpl.java`, `RefreshTokenService.java`, `AuthServiceTest.java`, `RefreshTokenServiceTest.java`, `AuthApiIntegrationTest.java`, `RefreshTokenRepositoryIT.java`, `UserRepositoryIT.java` |
| 2026-09-21 | Phase 0 Finalize: completed 0.7 Error framework, 0.8 Profiles, 0.T Test baseline | `ErrorCode.java`, `BusinessException.java`, `ClockConfig.java`, `AppProperties.java`, `ErrorResponse.java`, `GlobalExceptionHandler.java`, `CustomAuthenticationEntryPoint.java`, `CustomAccessDeniedHandler.java`, `JwtService.java`, `RefreshTokenService.java`, `RefreshToken.java`, `JwtAuthenticationFilter.java`, `SecurityConfig.java`, `JwtProperties.java`, `FlowerConnectApplication.java`, `application.yml`, `application-dev.yml`, `application-prod.yml`, `application-test.yml`, `AbstractIntegrationTest.java`, 4 IT files, `JwtServiceTest.java`, `RefreshTokenServiceTest.java` |
| 2026-09-21 | Stage 2b: Clock in GlobalExceptionHandler, JwtAuthenticationFilter SUSPENDED→403, code/timestamp assertions, AppProperties tests | `GlobalExceptionHandler.java`, `JwtAuthenticationFilter.java`, `ErrorResponse.java`, `AuthApiIntegrationTest.java`, `UserControllerTest.java`, `AuthControllerTest.java`, `RoleBoundaryTest.java`, `RefreshTokenServiceTest.java`, `MutableClock.java`, `AppPropertiesTest.java`, `application-test.yml` |
| 2026-09-26 | Stage 5b: Admin bootstrap, token cleanup, rate limiting, springdoc | `AdminBootstrap.java`, `RateLimitConfig.java`, `RateLimitFilter.java`, `RateLimitProperties.java`, `RefreshTokenCleanupScheduler.java`, `AppProperties.java`, `SecurityConfig.java`, `UserRepository.java`, `pom.xml`, `application.yml`, `application-dev.yml`, `application-test.yml`, `RateLimitIntegrationTest.java`, `RefreshTokenCleanupSchedulerTest.java` |
| 2026-09-26 | Stage 5a: Forgot/Reset/Change password + PATCH /users/me implemented and tested | `AuthController.java`, `UserController.java`, `PasswordResetService.java`, `PasswordResetToken.java`, `PasswordResetTokenRepository.java`, `EmailSender.java`, `NoOpEmailSender.java`, `SmtpEmailSender.java`, DTOs, `AuthControllerTest.java`, `UserControllerTest.java`, `AuthApiIntegrationTest.java`, `PasswordResetIntegrationTest.java`, `AbstractIntegrationTest.java`, `docker-compose.yml`, `pom.xml` |
| 2026-09-25 | Stage 4c: cookie-authenticated refresh/logout CSRF mitigation | `AuthController.java`, `AuthApiIntegrationTest.java`, `AuthControllerTest.java`, `shared/lib/api.ts`, `shared/lib/api.test.ts`, `docs/progress.md` |
| 2026-09-22 | Stage 2c: ddl-auto validate, shared TestClockConfig replaces @MockBean Clock, test property cleanup | `application-test.yml`, `TestClockConfig.java`, `AuthControllerTest.java`, `UserControllerTest.java`, `RoleBoundaryTest.java` |

## Session Notes

- Working on Windows; use PowerShell paths (e.g., `./mvnw` works, `.\mvnw` also works).
- `mvnw.cmd` is gitignored — Windows users should use `./mvnw` which delegates to the wrapper.
- `package-lock.json` is gitignored — use `npm install`, not `npm ci`, for local dev.
- All Kilo configuration lives in `kilo.jsonc` (validated). Agent and command `.md` files in `.kilo/` directories fail YAML validation in this Kilo CLI build.
- `docs/progress.md` is the ground truth for unfinished work — always check before starting new tasks.
- Phase 1 backend implemented, realigning to plan v2.2: 99 unit tests and 73 integration tests pass, with a single Testcontainers MySQL 8 container (singleton pattern) + Mailhog container.
- Phase 1 frontend auth implemented, realigning to plan v2.2: login/register UI, auth API client, Zustand store, route guards, token refresh/retry interceptor; 42 frontend tests pass (including the smoke test).
- Stage 5a complete: forgot-password, reset-password, change-password, PATCH /users/me endpoints fully tested with unit and integration tests covering all acceptance criteria.
- Stage 5b complete: admin bootstrap, token cleanup extended to password_reset_tokens, rate limiting with Bucket4j+Caffeine, springdoc added (disabled for tests).
- Stage 6 complete: Redis removed, Vite dev proxy added, MySQL healthcheck verified, .gitignore updated for uploads, known-issues.md updated.
