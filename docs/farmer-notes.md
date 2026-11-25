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

## Link rules

- Farmer request creates a `PENDING` row.
- If a `REJECTED` row exists for the pair, reuse it and flip back to `PENDING`.
- If a `PENDING`, `ACTIVE`, or `SUSPENDED` row exists, return `409 CONFLICT`.
- Vendor transitions: `PENDING→ACTIVE|REJECTED`, `ACTIVE→SUSPENDED`,
  `SUSPENDED→ACTIVE`. Invalid transitions → `409 CONFLICT`.
- One link per farmer/vendor pair (`UNIQUE(farmer_id, vendor_id)`).
- Only approved vendors may be linked (existing approved-vendor visibility rules).

## Schema (`farmer_vendor_links`)

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK AUTO_INCREMENT | |
| farmer_id | BIGINT NOT NULL | FK → users(id) |
| vendor_id | BIGINT NOT NULL | FK → vendor_profiles(id) |
| status | ENUM(PENDING,ACTIVE,REJECTED,SUSPENDED) NOT NULL DEFAULT PENDING | native MySQL ENUM (D-20) |
| created_at | DATETIME(6) NOT NULL | `@CreationTimestamp` |
| updated_at | DATETIME(6) NOT NULL | `@UpdateTimestamp` |
| UNIQUE(farmer_id, vendor_id) | | one link per pair |
| INDEX(vendor_id, status) | | vendor listing by status |
| INDEX(farmer_id, status) | | farmer listing |

No separate passbook table (out of scope). No new money fields; `BigDecimal` /
`HALF_UP` / scale 2 conventions unchanged.

## Errors & Clock

- Errors use `ErrorCode`, `BusinessException`, and the shared `ErrorResponse`
  envelope (`timestamp` from injected `Clock`, `code`, `status`, `message`).
- `Clock` pattern unchanged: link timestamps use Hibernate
  `@CreationTimestamp`/`@UpdateTimestamp` (consistent with `VendorProfile`);
  no injected `Clock` in the farmer service.

## Tests

- `FarmerLinkServiceTest` (21), `FarmerLinkControllerTest` (15),
  `VendorFarmerLinkControllerTest` (15) — 51 tests, all green.
- Coverage: every valid and invalid transition, duplicate-request conflict,
  re-request after REJECTED, florist vs another florist's link (403), farmer
  vs another farmer's links, RBAC (401/403/200) for all new endpoints.

## Verification

- Backend unit: `./mvnw test` **1,373 passed, 0 failed, 0 errors**, BUILD
  SUCCESS (includes 51 new farmer tests).
- Frontend (no changes): `npm test` **853 passed across 81 files**,
  `npm run typecheck` clean, `npm run lint` clean.
- Commit hash: reported at commit time.

## Remaining (Stage 2+)

Deposits, collections, passbooks, farmer/vendor pages, customer
checkout/cart/orders, invoice logic, and vendor-to-vendor B2B work are
deferred to Stage 2+.
