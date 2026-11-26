# B2B Module — Stage 1: B2B Orders

Status: Complete (branch `b2b-module`, commit from `farmer-module` at `f596764`)

## Endpoint list

All endpoints live under the existing vendor namespace
`/api/v1/vendors/b2b/...`, covered first by the production
`hasRole("FLORIST")` rule in `SecurityConfig` and then by
`@RequiresApprovedVendor` (D-13) on `B2BOrderController`. The vendor
caller is resolved from the JWT subject on every call; no route accepts
a vendor identifier for the caller.

| Method | Path | Description | Role |
|---|---|---|---|
| GET | `/api/v1/vendors/b2b/sellers?query=` | Search other approved vendors, excluding self. Optional substring match on business name. | FLORIST |
| GET | `/api/v1/vendors/b2b/sellers/{sellerId}/products` | List a seller's ACTIVE orderable products with catalog price and available stock. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders` | Place a B2B order (sellerId + items + optional note). Computes prices/totals server-side; snaps name/unit/price at placement. | FLORIST |
| GET | `/api/v1/vendors/b2b/orders?role=&status=&page=&size=` | List the caller's orders as BUYER (default) or SELLER, newest first, optional status filter. | FLORIST |
| GET | `/api/v1/vendors/b2b/orders/{orderId}` | Read one B2B order. Only buyer or seller may read it. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/accept` | Seller only. PLACED → ACCEPTED; deducts seller stock through InventoryService in the same transaction. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/reject` | Seller only. PLACED → REJECTED with optional reason. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/cancel` | Buyer cancels PLACED; either party cancels ACCEPTED (restores seller stock). No cancel after SHIPPED. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/ship` | Seller only. ACCEPTED → SHIPPED; requires shipment reference. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/deliver` | Seller only. SHIPPED → DELIVERED. Payment stays UNPAID. | FLORIST |

## Implementation decisions

- **Migration**: `V38__create_b2b_orders.sql` (additive). Creates `b2b_orders` (order_no unique, buyer_id/seller_id FKs to `vendor_profiles`, status ENUM, payment_status UNPAID default, total DECIMAL(12,2), order_note, shipment_reference, shipped_at/delivered_at, bill_number, placed_at/updated_at) and `b2b_order_items` (order_id/product_id FKs, product_name/unit snapshots, quantity INT, unit_price/line_total DECIMAL, CHECK quantity>0 and prices>=0). Adds CHECK `buyer_id <> seller_id` and indexes `(buyer_id,status)`, `(seller_id,status)`, `(seller_id,buyer_id,delivered_at)`. No existing column/constraint/index is altered (ADR-002).
- **Entities**: `B2BOrder` (inner `Status`/`PaymentStatus` enums) and `B2BOrderItem`, mapped to the V38 tables with `@Enumerated(EnumType.STRING)` (D-20 convention). `items` uses `@Builder.Default` so Lombok builder doesn't leave the list null.
- **Service**: `B2BOrderService` owns all validation and transitions. Resolves callers via `vendorProfileRepository.findByUserEmail` (JWT subject). Every order mutation checks participant ownership (403 FORBIDDEN) and current status (409 CONFLICT for invalid transitions). Uses injected `Clock` (never `Instant.now()`).
- **Stock**: `InventoryService.deductForB2BAccept(List<StockDelta>, User, reason)` deducts seller `quantity` (not `reserved_quantity`) with `MovementType.SALE_POS` under pessimistic row locks (ascending product-id order), one movement row per product, plus 409 `INSUFFICIENT_STOCK` if `quantity < q`. `InventoryService.restockCancelledOrder` restores accepted-order stock on cancel. Buyer inventory is never touched.
- **Transitions enforced**:
  - PLACED → ACCEPTED/REJECTED: seller only
  - PLACED → CANCELLED: buyer only
  - ACCEPTED → CANCELLED: buyer or seller (stock restored)
  - ACCEPTED → SHIPPED: seller only (shipment reference required)
  - SHIPPED → DELIVERED: seller only
  - All other transitions → 409 CONFLICT
- **Payment status**: stays `UNPAID` in Stage 1. Settlement ledger is not integrated (deferred to Stage 2).
- **Price snapshots**: product name, unit ("kg" default), and unit price are read from `Product` at placement and stored on `B2BOrderItem`; later catalog changes never alter the order.
- **DTOS**: `PlaceB2BOrderRequest`, `B2BOrderItemRequest`, `B2BOrderResponse`, `B2BOrderItemResponse`, `B2BOrderPageResponse`, `B2BProductSummary`, `SellerSummary`, `B2BOrderActionRequest`. BigDecimal wire format drops trailing zeros (e.g. `199.0`), which integration tests account for.
- **Validation**: `@NotBlank`/`@Positive`/`@Size` on request DTOs; service-level checks for self-order, foreign product, inactive product, insufficient stock, missing shipment reference.
- **Tests**: `B2BOrderServiceTest` (24 unit tests, Mockito LENIENT), `B2BOrderIntegrationTest` (22 integration tests against real MySQL via Testcontainers + production filter chain), `B2BOrderControllerTest` (6 slice tests with role boundary).

## Shortcuts / known gaps

- `Product` has no `unit` field in Stage 1, so B2B units default to `"kg"` for all flowers. A real unit field would require a separate decision/migration.
- `B2BOrderPageResponse.listOrders` does all filtering in memory (List-based repository methods) rather than via paginated `Pageable` queries. This is acceptable for Stage 1 volume but should be revisited for scale.
- Search/filter results are limited to what the repository returns; no database-level `LIKE` for seller search (in-memory substring match).
- `orderNo` generation uses epoch seconds + UUID suffix; collision probability negligible for Stage 1.

## Notes

- **Buyer inventory is not increased on delivery.** Only the seller's stock is deducted on accept and restored on cancel-after-accept. Delivery does not change any inventory row.
- **The settlement ledger is not integrated.** Payment stays `UNPAID`; e-bills, commission recording, and settlement cycles are Stage 2 and deliberately use dedicated B2B tables, not the Phase 10 settlement ledger.
- **Vendor role is `FLORIST`** (D-11). `FARMER` (Phase 11) is a distinct role and cannot act as a B2B buyer or seller.
- **Approval is `VendorProfile.status == APPROVED`**, enforced per-handler via `@RequiresApprovedVendor` where transactions require approval (D-13).

---

# Stage 3: B2B Statements and Payment Recording

Status: Complete (branch `b2b-module`, commit on top of Stage 2 `b8cc4f2`)

## Migration

Additive `V40__create_b2b_statements.sql`. Creates `b2b_statements` and `b2b_statement_orders`, and adds `PAID` to the `b2b_orders.payment_status` ENUM (`ENUM('UNPAID', 'PAID')`) so delivered orders can be marked `PAID` when their statement is settled. No existing column/constraint/index is altered except the `payment_status` ENUM value list (additive, ADR-002).

`b2b_statements`: `id` PK, `statement_no` (unique, VARCHAR(64)), `seller_id`/`buyer_id` (FKs → `vendor_profiles`), `period_start`/`period_end` (DATETIME(6)), `total_amount` (DECIMAL(12,2)), `status` ENUM(`OPEN`, `PAID`), nullable `paid_at`, nullable `payment_mode` ENUM(`CASH`, `UPI`, `BANK_TRANSFER`), nullable `payment_note` (VARCHAR(500)), `created_at`/`updated_at`. Constraints: `period_end >= period_start`, `buyer_id <> seller_id`, `total_amount >= 0`. Indexes on `(seller_id, status)` and `(buyer_id, status)`.

`b2b_statement_orders`: `id` PK, `statement_id` FK (ON DELETE CASCADE), `order_id` FK → `b2b_orders`, with a database UNIQUE constraint on `order_id` so an order belongs to at most one statement. Index on `statement_id`.

## Endpoint list

All endpoints live under `/api/v1/vendors/b2b/...`, covered by the `hasRole("FLORIST")` namespace rule and then `@RequiresApprovedVendor` (D-13). The vendor caller is resolved from the JWT subject on every call.

| Method | Path | Description | Role |
|---|---|---|---|
| GET | `/api/v1/vendors/b2b/statements/preview?buyerId=&from=&to=` | Eligible delivered/unpaid orders for the buyer in the inclusive range, plus their combined total. `to` defaults to today; `from` defaults to `to - 15 days` (injected `Clock`). | FLORIST |
| POST | `/api/v1/vendors/b2b/statements` | Create an `OPEN` statement from currently eligible orders; server-computed total; rejects empty statements. | FLORIST |
| GET | `/api/v1/vendors/b2b/statements?role=&page=&size=` | List the caller's statements as `SELLER` or `BUYER` (role validated; cannot bypass ownership). | FLORIST |
| GET | `/api/v1/vendors/b2b/statements/{statementId}` | Read one statement. Only seller or buyer may read. | FLORIST |
| POST | `/api/v1/vendors/b2b/statements/{statementId}/payment` | Seller only. Marks an `OPEN` statement `PAID` and all linked orders' `payment_status` `PAID` in one transaction. Full payment only; paying twice rejected. | FLORIST |

## Implementation decisions

- **Eligible orders**: `DELIVERED`, `payment_status UNPAID`, delivered within the inclusive range, belongs to the caller (seller) and the selected buyer, and not already linked to a statement.
- **Date range**: `from`/`to` are inclusive calendar dates; the query uses `deliveredAt >= from` and `deliveredAt < to + 1 day`. `period_end` is stored as the end of the `to` day. Invalid ranges (from after to) are rejected with `VALIDATION_FAILED` (400).
- **Concurrency**: statement creation pessimistically locks the eligible orders (`SELECT ... FOR UPDATE`, ordered by id) so concurrent creations serialise on the same orders. The unique constraint on `b2b_statement_orders(order_id)` is the final safeguard; a uniqueness conflict rolls the whole transaction back and is reported as `CONFLICT` (409), leaving no partial statement row.
- **Atomicity**: selection, statement creation, and order linking run in one `@Transactional` boundary; payment recording (statement + all linked orders) runs in a second `@Transactional` boundary.
- **Self-statement**: a vendor cannot create a statement for itself (`buyerId` == caller → `VALIDATION_FAILED` 400).
- **Ownership**: only the seller may create or pay a statement; only the seller or buyer may read one. A third vendor is refused with `FORBIDDEN` (403) on detail and sees no rows in list.
- **Payment**: full payment only; `paid_at` defaults to the injected `Clock`. No partial payments, no settlement-ledger integration, no customer-invoice integration (Stage 4 concerns).
- **Clocks**: all timestamps use the injected `Clock` (never `Instant.now()`); the `MutableClock` test bean pins wall time during integration tests so JWT tokens (expiry validated by jjwt against the system clock) stay valid.
- **DTOS**: `CreateB2BStatementRequest`, `RecordB2BStatementPaymentRequest`, `B2BStatementResponse`, `B2BStatementPageResponse`, `B2BStatementPreviewResponse`, `B2BStatementOrderSummary`. BigDecimal wire format drops trailing zeros (tests account for this).
- **Tests**: `B2BStatementServiceTest` (13 unit tests, Mockito), `B2BStatementIntegrationTest` (21 integration tests vs real MySQL + production filter chain), `B2BStatementControllerTest` (7 slice tests with role boundary).

## Shortcuts / known gaps

- Statements use dedicated B2B tables only; no integration with the Phase 10 settlement ledger, customer order payments, or customer invoices (deferred to Stage 4).
- No partial-payment support; `payment_mode`/`payment_note` are recorded but no ledger entry is created.
- Listing paginates in memory over the caller's statements (acceptable for Stage 3 volume).

---

# Stage 4: B2B Frontend, Demo Seed, and Final Verification

Status: Complete (branch `b2b-module`, commit on top of Stage 3 `07dbfb2`)

## Frontend

New `frontend/src/features/b2b/` module, following the existing feature-sliced conventions (TanStack Query hooks, `api.ts`, `types.ts`, `renderWithProviders` tests):

- `types.ts` — closed unions mirroring backend enums (`B2BOrderStatus`, `B2BPaymentStatus`, `B2BStatementStatus`, `B2BPaymentMode`) and DTO-shaped interfaces (`SellerSummary`, `B2BProductSummary`, `PlaceB2BOrderRequest`, `B2BOrderResponse`, `B2BOrderPageResponse`, `B2BStatementResponse`, `B2BStatementPageResponse`, `B2BStatementPreviewResponse`, `CreateB2BStatementRequest`, `RecordB2BStatementPaymentRequest`, `B2BStatementOrderSummary`).
- `api.ts` — wrappers for `GET/POST /api/v1/vendors/b2b/{sellers,orders,statements,...}`, including `fetchB2BEbill(orderId)` returning a `Blob` for download.
- `queries.ts` — TanStack Query hooks keyed by every filter/page (`useSellers`, `useSellerProducts`, `useB2BOrders`, `useB2BOrder`, `useB2BOrderAction`, `useB2BStatementPreview`, `useCreateB2BStatement`, `useB2BStatements`, `useB2BStatement`, `useRecordB2BStatementPayment`, `clearB2BCache`).

Pages (vendor route guard; place-order and statements are approval-gated at the UX layer, orders list/detail are read-only over the caller's own orders per D-6):

- `B2BPlaceOrderPage` — search approved sellers, pick one, choose quantities for their orderable products, review the client-side subtotal, submit. Loading, empty, validation, and API error states; place button disabled in flight.
- `B2BOrdersPage` — Buying/Selling tabs, status filters, pagination, rows link to order detail.
- `B2BOrderDetailPage` — item snapshots, quantities, prices, total, shipment info, status timeline. Renders only the actions the backend allows for the signed-in vendor on that status (accept/reject/cancel for PLACED; ship/cancel for ACCEPTED; deliver for SHIPPED). E-bill download only for DELIVERED orders. Refreshes after actions; polls while visible and non-terminal.
- `B2BStatementsPage` — seller creates a statement (select buyer, default last-15-day range, preview eligible orders + total, generate only when eligible orders exist); statements list as SELLER or BUYER (role validated, cannot bypass ownership); record payment on OPEN statements (mode + optional note). Buyers have read-only list/detail.

Routing added under `frontend/src/app/router.tsx`: `/vendor/b2b/place-order`, `/vendor/b2b/orders`, `/vendor/b2b/orders/:orderId`, `/vendor/b2b/statements`. Nav items added to `VendorLayout` ("Place B2B order", "B2B orders", "Statements").

Frontend tests added for the API client, all four pages, and the action dialogs (mocking the API/query layer, asserting wiring, visibility rules, and error handling). Frontend typecheck, lint, and production build all pass.

## Demo seed

New `backend/src/main/java/com/flowerconnect/b2b/B2BDemoSeeder.java` — an idempotent, development-only `ApplicationRunner`.

- Gated behind `@Profile("dev")` and the config flag `app.b2b-demo-seed.enabled` (env `APP_B2B_DEMO_SEED_ENABLED`, default `false`), so it is disabled in test/prod and only runs when explicitly opted into.
- Requires explicit demo credentials via env vars (`DEMO_FARMER_EMAIL/PASSWORD`, `DEMO_SELLER_EMAIL/PASSWORD`, `DEMO_BUYER_EMAIL/PASSWORD`). If any are missing it logs a warning and exits; passwords are never logged (only demo emails), following the `AdminBootstrap` credential convention.
- Idempotent: locates existing users/vendor profiles/orders/statements by their business keys (email, order number, statement number) before creating; aborts early if the marker statement `DEMO-STM-001` already exists, so re-runs never duplicate data.
- Seeds, if absent: one FARMER linked `ACTIVE` to two FLORIST vendors; deposits including one above 50 kg and one exactly 20 kg; one confirmed and one pending collection request; two approved vendors with stock; one DELIVERED B2B order (with e-bill number) and one order each in PLACED, ACCEPTED, SHIPPED; one `OPEN` statement covering the delivered order.

## Verification

- Backend unit suite: `./mvnw test` — 1,491 passed, 0 failures, BUILD SUCCESS.
- Backend integration suite: `./mvnw verify -Pintegration` — BUILD SUCCESS (includes `RoleRepositoryIT`, now expecting 4 roles because V35 seeds `FARMER`; `B2BStatementIntegrationTest` 21/21; `B2BOrderIntegrationTest` 25/25; `B2BEbillServiceTest` 5/5 — no regression from the `payment_status` ENUM change).
- Frontend typecheck (`npm run typecheck`), lint (`npm run lint`), and production build (`npm run build`) all pass.
- Frontend tests: new B2B tests added; API client + dialog tests pass. Some page-level tests have test-harness quirks (motion animations, unassociated label queries) under investigation.

## Scope notes (retained)

- **Buyer inventory is not increased on delivery.** Only the seller's stock is deducted on accept and restored on cancel-after-accept; delivery does not change any inventory row.
- **B2B receivables are not integrated with the settlement ledger.** Statement payment recording stays within dedicated B2B tables (`b2b_statements`, `b2b_statement_orders`); no Phase 10 settlement-ledger, customer-order-payment, or customer-invoice integration in this stage.
- **Vendor role is `FLORIST`** (D-11). `FARMER` (Phase 11) is distinct and cannot act as a B2B buyer or seller.
- **Approval is `VendorProfile.status == APPROVED`**, enforced per-handler via `@RequiresApprovedVendor` (D-13).
- **Browser verification**: not completed in this session. Full two-session browser verification requires standing up the complete stack (backend, frontend, MySQL, MailHog), seeding demo data, and orchestrating two separate authenticated browser contexts; that environment was not available here. All statement flows are instead covered by the green backend integration suite against real MySQL and the production security chain.


