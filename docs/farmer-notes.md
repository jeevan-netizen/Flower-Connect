# Farmer Module Notes (Stage 1)

Verified decisions, shortcuts, and the endpoint list for the farmer module.
Stage 1 implements only the FARMER role, farmer registration, and
farmer-to-vendor link requests plus vendor decisions. Deposits, collections,
passbooks, farmer/vendor pages, and B2B work are explicitly out of scope.

## Base branch

- Branch: `farmer-module`, created from `phase-10-billing-ledger` (fe48d3b).
- `phase-10-billing-ledger` is **not** merged into `main`; it is the appropriate
  base because the farmer module builds on the commission/ledger tables
  (`commissions`, `settlements`, `settlement_entries`, `platform_config`)
  introduced in Phase 10.
- Migration version: V35 (next free after V34). Additive only; no existing
  migration edited (ADR-002 / D-9).
  Stage 2 adds additive `V36__create_farmer_deposits.sql` (`farmer_deposits`
  table: `id`, `link_id` FK→`farmer_vendor_links`, `flower_type` VARCHAR(100),
  `gross_kg`/`deduction_kg`/`net_kg`/`price_per_kg` DECIMAL(10,2), `amount`
  DECIMAL(12,2), `deposited_at` DATETIME(6), `created_at` DATETIME(6), index
  on `(link_id, deposited_at)`).
  Stage 3 adds additive `V37__create_farmer_payments.sql` (`farmer_payments`
  table: `id`, `link_id` FK→`farmer_vendor_links`, `amount` DECIMAL(12,2),
  `mode` ENUM `CASH`/`UPI`, `note` VARCHAR(500) NULL, `status` ENUM
  `PENDING`/`CONFIRMED`/`REJECTED`, `requested_at` DATETIME(6),
  `decided_at` DATETIME(6) NULL), generated column `is_pending` (1 for PENDING,
  NULL otherwise) with `UNIQUE(link_id, is_pending)`, index on
  `(link_id, status)`).

## Role model

- Vendor role stays `FLORIST` (D-11); `FARMER` added as a distinct role (id 4,
  seeded idempotently in V35 following V3's `INSERT IGNORE` pattern).
- JWT claims carry the role from the `User.role` entity — no JWT code change
  needed; a `FARMER` user's token simply includes `ROLE_FARMER`.
- `SecurityConfig`:
  - `POST /api/v1/farmer/register` → `permitAll` (public entry point).
  - `/api/v1/farmer/**` → `hasRole("FARMER")`.
  - `/api/v1/vendor/farmer-links/**` → `hasRole("FLORIST")`, declared before the
    broad `/api/v1/vendor/**` rule so it matches first.
- Authorization is two layers: URL namespace (role) + service-layer ownership
  (farmer owns `farmer_id`; florist owns `vendor_id`). A foreign link ID returns
  `404 NOT_FOUND` when missing and `403 FORBIDDEN` when owned by another
  florist, matching the existing ownership convention (D-24 / vendor inventory).

## Endpoints

### Farmer (FARMER role; `POST /register` is public)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/farmer/register` | Create a FARMER account (reuses email/password/fullName/phone) |
| GET | `/api/v1/farmer/vendors?query=` | List/search approved vendors eligible to link, with caller's link status |
| POST | `/api/v1/farmer/links` | `{ "vendorId": ... }` — request a PENDING link to a vendor |
| GET | `/api/v1/farmer/links` | List only the authenticated farmer's own links |

### Florist (FLORIST role)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/vendor/farmer-links?status=` | List links on the caller's vendor, optional status filter |
| POST | `/api/v1/vendor/farmer-links/{id}/accept` | PENDING → ACTIVE |
| POST | `/api/v1/vendor/farmer-links/{id}/reject` | PENDING → REJECTED |
| POST | `/api/v1/vendor/farmer-links/{id}/suspend` | ACTIVE → SUSPENDED |
| POST | `/api/v1/vendor/farmer-links/{id}/reactivate` | SUSPENDED → ACTIVE |
| POST | `/api/v1/vendor/farmer-links/{id}/deposits` | Record a flower deposit on an `ACTIVE` link (`{ "flowerType", "grossKg", "pricePerKg", "depositedAt?" }`) |

## Link rules

- Farmer request creates a `PENDING` row.
- If a `REJECTED` row exists for the pair, reuse it and flip back to `PENDING`.
- If a `PENDING`, `ACTIVE`, or `SUSPENDED` row exists, return `409 CONFLICT`.
- Vendor transitions: `PENDING→ACTIVE|REJECTED`, `ACTIVE→SUSPENDED`,
  `SUSPENDED→ACTIVE`. Invalid transitions → `409 CONFLICT`.
- One link per farmer/vendor pair (`UNIQUE(farmer_id, vendor_id)`).
- Only approved vendors may be linked (existing approved-vendor visibility rules).

## Deposit rules

**Weight deduction policy** (`com.flowerconnect.farmer.policy.WeightDeductionPolicy`):
pure, no database access, lower-bound-inclusive brackets:

| Gross kg | Deduction kg |
|----------|--------------|
| below 10 | 0 |
| 10 to below 20 | 1 |
| 20 to below 30 | 2 |
| 30 to below 40 | 3 |
| 40 to below 50 | 4 |
| 50 and above | 5 |

Calculation: `net = gross − deduction`, `amount = net × pricePerKg`, both rounded
to 2 decimals with `HALF_UP`. Gross weight, deduction, net weight, rate and
amount are persisted on each row so historical deposits are unaffected by later
policy changes.

Example: gross 32 kg at 100/kg → deduction 3 kg, net 29 kg, amount 2900.

**Schema (`farmer_deposits`)**

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK AUTO_INCREMENT | |
| link_id | BIGINT NOT NULL | FK → farmer_vendor_links(id) |
| flower_type | VARCHAR(100) NOT NULL | |
| gross_kg | DECIMAL(10,2) NOT NULL | must be > 0 |
| deduction_kg | DECIMAL(10,2) NOT NULL | from weight-deduction policy |
| net_kg | DECIMAL(10,2) NOT NULL | gross − deduction |
| price_per_kg | DECIMAL(10,2) NOT NULL | must be > 0 |
| amount | DECIMAL(12,2) NOT NULL | net × price per kg |
| deposited_at | DATETIME(6) NOT NULL | defaults to clock now when not supplied; future rejected |
| created_at | DATETIME(6) NOT NULL | `@CreationTimestamp` |
| INDEX(link_id, deposited_at) | | |

- A florist may only record deposits on links belonging to their own vendor
  (404 if missing, 403 if foreign).
- The link must be `ACTIVE` (409 `CONFLICT` otherwise).
- `depositedAt` is optional; when omitted it defaults to the injected `Clock`.
- Future `depositedAt` is rejected (400 `VALIDATION_FAILED`).
- Request validation: `flowerType` required (max 100 chars), `grossKg > 0`,
  `pricePerKg > 0` (400 `VALIDATION_FAILED`).
- No separate passbook table (out of scope).

## Collections schema (`farmer_payments`)

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK AUTO_INCREMENT | |
| link_id | BIGINT NOT NULL | FK → farmer_vendor_links(id) |
| amount | DECIMAL(12,2) NOT NULL | |
| mode | ENUM(CASH,UPI) NOT NULL | |
| note | VARCHAR(500) NULL | |
| status | ENUM(PENDING,CONFIRMED,REJECTED) NOT NULL DEFAULT PENDING | native MySQL ENUM |
| requested_at | DATETIME(6) NOT NULL | |
| decided_at | DATETIME(6) NULL | set from injected Clock on confirm/reject |
| is_pending | TINYINT(1) GENERATED | 1 for PENDING, NULL otherwise |
| UNIQUE(link_id, is_pending) | | at most one PENDING per link |
| INDEX(link_id, status) | | |

- Outstanding balance is computed as `sum(deposit amounts) - sum(CONFIRMED
  collection amounts)`; no balance column is stored.
- PENDING and REJECTED collections remain visible in the passbook but do not
  reduce the balance.

## Endpoint summary (Stage 2)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/api/v1/vendor/farmer-links/{linkId}/deposits` | FLORIST | Record a flower deposit (201 on success) |

## Endpoint summary (Stage 3)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/api/v1/farmer/links/{linkId}/passbook` | FARMER | Link passbook: summary, balance, entries with running balance |
| POST | `/api/v1/farmer/links/{linkId}/collections` | FARMER | Request a PENDING collection (amount <= balance) |
| GET | `/api/v1/vendor/farmer-links/{linkId}/passbook` | FLORIST | Vendor view of a link passbook |
| GET | `/api/v1/vendor/farmer-collections?status=` | FLORIST | Vendor's pending/confirmed/rejected collections |
| POST | `/api/v1/vendor/farmer-collections/{paymentId}/confirm` | FLORIST | Confirm a PENDING collection (balance rechecked under lock) |
| POST | `/api/v1/vendor/farmer-collections/{paymentId}/reject` | FLORIST | Reject a PENDING collection (no balance change) |

## Collection rules

- A florist may only record deposits on links belonging to their own vendor
  (404 if missing, 403 if foreign).
- The link must be `ACTIVE` (409 `CONFLICT` otherwise).
- `depositedAt` is optional; when omitted it defaults to the injected `Clock`.
- Future `depositedAt` is rejected (400 `VALIDATION_FAILED`).
- Request validation: `flowerType` required (max 100 chars), `grossKg > 0`,
  `pricePerKg > 0` (400 `VALIDATION_FAILED`).
- A farmer may request a collection only on their own `ACTIVE` link, for an
  amount no greater than the outstanding balance; at most one PENDING
  collection per link.
- Confirmation recomputes the balance under a pessimistic lock on the link row;
  if the balance is now too low, the request stays PENDING and a 409 is
  returned.
- Rejection sets status to REJECTED and `decided_at`; no balance change.
- A PENDING request may still be confirmed or rejected when its link is
  suspended.
- Ownership: 404 for missing link/payment, 403 for foreign link/payment.

## Verification

- Backend unit: `./mvnw test` **1,434 passed, 0 failed, 0 errors**, BUILD
  SUCCESS (includes 35 new farmer collection tests: `WeightDeductionPolicyTest`
  4, `FarmerDepositServiceTest` 12, `FarmerDepositControllerTest` 10,
  `FarmerCollectionServiceTest` 21, `FarmerCollectionControllerTest` 7,
  `VendorCollectionControllerTest` 7).
- Frontend: **856 passed across 82 files** (includes 3 new
  `useEligibleVendors` tests in `queries.test.tsx`), `npm run typecheck` clean,
  `npm run lint` clean, `npm run build` clean.
- Money/decimal conventions (`BigDecimal`, `HALF_UP`, scale 2) unchanged.
- **Bug fix:** `useEligibleVendors` had `enabled: query !== undefined`, which
  disabled the query when `query` was `undefined` (i.e., on initial load with
  empty search). Changed to `enabled: true` so the vendor list loads on initial
  page load. Added `queries.test.tsx` (3 tests) covering empty-query fetch,
  filtered-query fetch, and loading state.
- Commit hash: reported at commit time.

## Browser verification (Stage 3)

Playwright E2E test (`tests/e2e/farmer-module-flow.test.ts`) passed through
real Chromium browser. Browser-verified steps:

1. Farmer login (via API token injection into localStorage)
2. Farmer navigates to vendor discovery (`/farmer/vendors`) — vendors load
3. Farmer requests to join vendor — "Requested" badge appears
4. Vendor accepts link (API) — farmer sees "ACTIVE" link status
5. Vendor adds three deposits via API (32 kg → deduction 3 kg, net 29 kg,
   amount 2900; 60 kg → deduction 5 kg; 20 kg → deduction 2 kg)
6. Farmer views passbook — correct balance (7450.00), all three entries with
   running balance
7. Farmer requests collection (1000.00) — "Pending" badge appears
8. Vendor confirms collection (API) — balance drops to 6450.00
9. Vendor suspends link (API) — "SUSPENDED" status shown
10. Farmer attempts new deposit/collection on suspended link — blocked with
    "Deposits require an active link" error
11. Passbook remains readable on both farmer and vendor sides after suspension

Vendor-side actions (accept, deposits, confirm, suspend) were exercised via API
calls rather than browser UI, as the vendor UI pages are not yet implemented.

## Remaining (Stage 4+)

Vendor-to-vendor B2B work, customer checkout/cart/orders, invoice logic are
deferred to Stage 4+.

