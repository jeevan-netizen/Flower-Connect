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
  table: `id`, `link_id` FK→`farmer_vendor_links`, `flower_type` VARCHAR(160),
  `gross_kg`/`deduction_kg`/`net_kg`/`price_per_kg` DECIMAL(10,2), `amount`
  DECIMAL(12,2), `deposited_at` DATETIME(6), `created_at` DATETIME(6), index
  on `(link_id, deposited_at)`).

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

## Endpoint summary (Stage 2)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/api/v1/vendor/farmer-links/{linkId}/deposits` | FLORIST | Record a flower deposit (201 on success) |

## Verification

- Backend unit: `./mvnw test` **1,399 passed, 0 failed, 0 errors**, BUILD
  SUCCESS (includes 26 new farmer deposit tests: `WeightDeductionPolicyTest`
  4, `FarmerDepositServiceTest` 12, `FarmerDepositControllerTest` 10).
- Frontend (no changes): `npm test` **853 passed across 81 files**,
  `npm run typecheck` clean, `npm run lint` clean.
- Money/decimal conventions (`BigDecimal`, `HALF_UP`, scale 2) unchanged.
- Commit hash: reported at commit time.

## Remaining (Stage 3+)

Collections, passbooks, farmer/vendor pages, customer checkout/cart/orders,
invoice logic, and vendor-to-vendor B2B work are deferred to Stage 3+.
