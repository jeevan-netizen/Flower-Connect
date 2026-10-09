# FlowerConnect — Post-GSD Removal Phase Handoff

**Date:** 2026-10-09  
**Branch:** `phase-4-discovery-search`  
**Latest Commit:** `a49cb15 feat(4.1): customer address book`  
**Status:** Clean working tree, Phase 4 Stage 1 complete and fully verified  

---

## 1. Project Context & Current Position

- **Active Phase:** Phase 4 — Customer Addresses, Discovery and Search
- **Active Stage:** Stage 1 (Task 4.1) **COMPLETE**; Stage 2 (Task 4.2) **NEXT**
- **Base Plan Reference:** `docs/FlowerConnect_Implementation_Plan_v2.2.md` (§7: Tasks 4.1–4.T)
- **Design Note:** `docs/phase-4-stage-0-design.md` (records Stage 0 architectural decisions and baseline)
- **Primary Agent Rules:** `AGENTS.md` (standard FlowerConnect developer rules)
- **Configuration:** `kilo.jsonc` (FlowerConnect multi-agent configuration and command workflows)

---

## 2. Completed Tasks & Commits

### Phase 4 (Current)
- **Task 4.1 (Stage 1): Customer Address Book** (`a49cb15`)
  - Flyway migration `V12__create_addresses.sql` (`addresses` table with FKs to `users` and `service_locations`, centroid coordinates snapshot per D-4, `defaultAddress` boolean).
  - Entity `Address`, `AddressRepository`, `AddressMapper` (MapStruct), `AddressRequest`/`AddressResponse`/`AddressPageResponse`.
  - `AddressService` with single-transaction writes serialized via pessimistic write lock on caller's `users` row (`UserRepository.findByEmailForUpdate`) to enforce single-default-per-customer invariant (D-32, D-33).
  - `AddressController` mounted at `/api/v1/addresses` secured with `hasRole('CUSTOMER')`.
  - Comprehensive test suite: 19 unit tests (`AddressServiceTest`), 23 HTTP integration tests (`AddressApiIntegrationTest`), and 2 concurrency tests (`AddressConcurrencyIntegrationTest` covering 8 simultaneous creations and 8 competing default switches).
  - Decisions D-32 and D-33 added to `docs/decisions.md`.

### Completed Historical Phases
- **Phase 0:** Architecture, Scaffold, Spring Boot 3.2.5 + React 18/Vite, Flyway baseline (`V1`), Error framework, Testcontainers MySQL.
- **Phase 1:** Auth & User Management (JWT access token in-memory, httpOnly refresh cookie, BCrypt, status enforcement, Caffeine rate limiting).
- **Phase 2:** Service Locations, Vendor Profiles, Opening Hours, Admin Approval Gating, User Status Management, Vendor/Admin UI.
- **Phase 3:** Category Management, Product Catalog, Inventory with pessimistic locking, Scheduled Expiry Sweep, Image Pipeline, Vendor Catalog & Inventory UI (`da08cc4`, `2f1dc29`, `2562b67`, `35d28ce`, `fd5b8e8`, `3d475ab`, `5623411`, `89625ad`).

---

## 3. Pending Tasks for Phase 4

Per `docs/FlowerConnect_Implementation_Plan_v2.2.md` and `docs/phase-4-stage-0-design.md`:

| Task | Scope | Acceptance Criteria |
|---|---|---|
| **4.2** | Location picker (Frontend) | Session-backed location selector using `service_locations` (no GPS/browser geolocation, per D-4). Stored in client state/session. |
| **4.3** | Geo discovery API (`GET /api/v1/discover`) | Find vendors delivering to the selected `serviceLocationId` using Haversine distance filtering against `vendor_profiles.delivery_radius_km`. |
| **4.4** | Product search API (`GET /api/v1/search`) | Search across active products in deliverable vendors by query keyword, category filter, price range, and stock availability. |
| **4.5** | Storefront API (`GET /api/v1/vendors/{id}/storefront`) | Public storefront returning vendor profile, opening hours, active categories, and active products with inventory status. |
| **4.6** | Customer home page (UI) | Location picker modal/bar, hero bouquet animation with WebGL fallback, deliverable vendor cards, category carousels. |
| **4.7** | Search and filter UI (UI) | Search bar, category filters, price slider, in-stock toggle, responsive product card grid. |
| **4.8** | Storefront page (UI) | Vendor header, delivery radius badge, opening hours badge, categorized product grid, add-to-cart triggers. |
| **4.9** | Product detail modal (UI) | Image gallery/carousel, description, pricing, inventory badge, custom greeting card note input, quantity selector. |
| **4.T** | Phase 4 Verification & RBAC | Complete integration test matrix covering customer, vendor, admin, and unauthenticated access for all Phase 4 endpoints. |

---

## 4. Test & Verification Baseline

| Verification Check | Target / Command | Verified Status |
|---|---|---|
| Backend Unit Tests | `./mvnw test` | **527 tests passing**, 0 failures, 0 skipped (`BUILD SUCCESS`) |
| Backend Integration Tests | `./mvnw verify -Pintegration` | **390 tests passing**, 0 failures, 0 skipped (`BUILD SUCCESS`) |
| Frontend Lint | `npm run lint` (in `frontend/`) | **Clean** (0 errors, 0 warnings) |
| Frontend Typecheck | `npx tsc -b` (in `frontend/`) | **Exit code 0** (clean) |
| Frontend Unit Tests | `npm run test` (in `frontend/`) | **514 tests passing across 45 test files** |
| Frontend Build | `npm run build` (in `frontend/`) | **Clean** (681 modules transformed, production bundle built) |
| API Documentation | `/v3/api-docs` | Verified with all current routes including `/api/v1/addresses` |

---

## 5. Active Known Issues & Constraints

- **Issue #018:** Integration tests require the Docker daemon for Testcontainers (MySQL 8).
- **Issue #020:** Product image binary serving route is deferred (images listed as metadata in UI per D-31).
- **Issue #021 / #025:** `GET /api/v1/categories` returns root categories only by design.
- **Issue #024:** Approved-vendor browser walkthrough UI test pending Docker/admin token (backend integration tests cover all routes).
- **Issue #026:** Deleting a category with linked products triggers SQL foreign key violation rather than 409 response.

---

## 6. Uncommitted & Untracked State

- Working tree is **clean**; all code, migrations, and tests are committed.
- No stashes or uncommitted changes exist.

---

## 7. Next Recommended Action

Resume Phase 4 without GSD:
1. Implement **Task 4.2 (Location picker UI)** in `frontend/src/features/customer/` or `frontend/src/shared/components/` with session storage persistence.
2. Follow standards in `AGENTS.md` and `kilo.jsonc` commands (`build-check`, `backend-check`, `frontend-check`).
3. Proceed to **Task 4.3 (Geo discovery backend endpoint)**.
