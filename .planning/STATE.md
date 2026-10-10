# FlowerConnect — Current Project State

## Status Summary
- **Current Phase:** Phase 3 completed & closed out
- **Next Phase:** Phase 4 — Customer Addresses, Discovery and Search
- **Repository Branch:** `phase-3-catalog-inventory` (clean working tree)
- **Last Commit:** `da08cc4 test(3.1): make category integration tests discoverable`

## Completed Phases
| Phase | Scope | Status | Verification |
|---|---|---|---|
| **Phase 0** | Scaffold, Spring Boot + React Vite skeleton, Flyway baseline, Error framework, Testcontainers MySQL | ✅ Complete | Build & smoke tests pass |
| **Phase 1** | Auth & user management, JWT in-memory + httpOnly cookie refresh, status enforcement, rate limiting | ✅ Complete | 99 unit + 68 integration tests |
| **Phase 2** | Service locations, vendor profiles, opening hours, admin approval gating, user status, vendor/admin UI | ✅ Complete | 264 unit + 222 integration tests |
| **Phase 3** | Category CRUD, product catalog, inventory with pessimistic locks, scheduled expiry sweep, image pipeline, vendor catalog/inventory UI | ✅ Complete | 508 unit + 362 integration tests, 514 frontend tests |

## Verification Snapshot
- **Backend Unit Tests:** 508 passing (`./mvnw test`), 0 failures, 0 skipped
- **Backend Integration Tests:** 362 passing (`./mvnw verify -Pintegration`), 0 failures
- **Frontend Checks:** `npm run lint` clean, `npm run typecheck` (tsc -b) exits 0, `npm run test` (514 Vitest tests passing across 45 files), `npm run build` clean (681 modules)
- **API Documentation:** 34 endpoints registered and documented in OpenAPI `/v3/api-docs`
- **Database Migrations:** V1 through V11 applied cleanly

## Key Technical Decisions in Effect
- **D-4:** Centroid-based locations from seeded `service_locations` (no GPS / browser geolocation).
- **D-5:** Access token in-memory only; refresh token in httpOnly, Secure, SameSite=Strict cookie scoped to `/api/v1/auth`.
- **D-13 / D-29:** Approval gating via `@RequiresApprovedVendor` / `VendorApprovalGuard` and `ApprovedVendorGate` UI layer.
- **D-21 / D-28:** One primary image per product maintained via pessimistic write lock on `ProductImageRepository.findByProductIdForUpdate`.
- **D-24:** Stock mutations use pessimistic write locking on `inventory` row and write immutable `stock_movements`.
- **D-25:** Expiry sweep runs on configurable cron with injected `Clock`, delists products and records `WASTE` stock movement.
- **D-26 / D-27:** Image pipeline sniffs magic bytes, decodes, scales, and re-encodes; S3 storage service is a declared profile stub.

## Active Known Issues / Minor Limitations
- **#020:** Product image binary serving route is deferred (images listed as metadata in UI).
- **#025:** Public category read `GET /api/v1/categories` is top-level (roots-only) by design.
- **#026:** Deleting a category with linked products triggers SQL foreign key violation (500) rather than a clean 409 error response.

## Immediate Recommended Next Action
Begin planning and execution for **Phase 4: Customer Addresses, Discovery and Search**:
1. **Task 4.1:** Customer address book data model and CRUD (`addresses` entity and `/api/v1/addresses` endpoint).
2. **Task 4.2:** Frontend session-backed location picker using `service_locations`.
3. **Task 4.3:** Discovery endpoint `GET /api/v1/discover?locationId=` with Haversine distance filtering against `vendor_profiles.delivery_radius_km`.
4. **Task 4.4:** Search endpoint `GET /api/v1/search` with keyword, category, price, and active/in-stock filters.
5. **Task 4.5:** Public storefront endpoint `GET /api/v1/vendors/{id}/storefront`.
