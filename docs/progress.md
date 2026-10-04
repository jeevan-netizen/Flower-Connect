# Progress

Tracks what has been implemented and what remains. Updated after each session.

## Current Phase

**Phase 3d — Task 3.6 (Inventory API) completed**

Task 3.6 puts the Phase 3b inventory tables behind `VendorInventoryController` at
`/api/v1/vendors`, following the namespace decision of D-22 rather than the plan's singular
`/api/v1/vendor` wording, so `SecurityConfig`'s `hasRole("FLORIST")` rule already covers it. Eight
operations: read the current inventory, stock in, stock out, adjustment, write-off, low-stock
threshold, expiry date, paginated movement history, and a paginated vendor-wide low-stock list. (The
low-stock list sits at `/api/v1/vendors/inventory/low-stock` rather than nested under a product id,
because it is the one route in this task that is not per-product and nesting it would make
`/products/{productId}/inventory` ambiguous between a product and the literal segment `inventory`.)

Every write is `@RequiresApprovedVendor` at the class level (D-13) and ownership-scoped: a product
that does not exist is a 404 and a product owned by another vendor is a 403, checked *before* any
lock is taken. Availability is `quantity - reserved_quantity` and is never clamped — a change that
would drop `quantity` below `reserved_quantity` is refused with a dedicated `409 INSUFFICIENT_STOCK`,
because reporting success for a request that was not performed would leave the level disagreeing
with the movements that produced it (D-24). Adjustments and write-offs require a nonblank reason;
stock in and stock out treat the reason as optional. The authenticated principal is resolved as the
movement's actor, so no client can attribute a movement to someone else.

Each mutation locks the inventory row pessimistically, writes the new level, and appends exactly one
`stock_movements` row in one transaction; the two alert settings take the same lock even though they
write no movement, because Hibernate's whole-row `UPDATE` would otherwise write back a stale
`quantity`. The `reserved_quantity` floor is enforced in the service and by
`ck_inventory_reserved_le_quantity` (D-12). No migration: this reuses `inventory` and
`stock_movements` as written in task 3.3/3.4. 59 new unit tests (29 service + 30 controller slice),
35 HTTP integration tests, and 6 real-thread concurrency tests. The locking protocol itself is
D-24; the earlier 3.5 catalog work is summarised under **Completed Work**. Verified at
**397 unit tests** and **314 integration tests**.

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
- [x] **3.1 — Category entity**: `categories` table (V7 migration) with hierarchical structure (parent_id self-FK), `name`, `slug` (unique, auto-generated), `display_order`, `active` flag, `created_at`, `updated_at`. Four seed categories: Roses, Bouquets, Arrangements, Occasions. Admin CRUD at `/api/v1/admin/categories` (POST/PUT/DELETE/GET list with pagination), public read at `GET /api/v1/categories` (flat list of active categories, permitAll). Cycle prevention on parent assignment, delete protection (409 if has children), slug auto-generation with collision-safe suffix, audit trail on all mutations. 32 new unit tests + 9 integration tests. Verified with 296 unit + 231 integration tests.
- [x] **3.2 — Product entity**: `products` table (V8) with vendor_id/category_id FKs, name, slug (unique, auto-generated with collision-safe suffix), description, base_price, status (native ENUM DRAFT/ACTIVE/INACTIVE/ARCHIVED). `product_images` (V9) holds ordered images with a single primary flag — the one-primary rule is a service-level invariant (D-21) because MySQL error 1215 rejects the generated-column unique-index trick next to the required product_id FK. `ProductService.create` generates the slug, treats the unique constraint as the authority, and retries with numeric suffixes after a lost race
- [x] **3.3 — Inventory entity**: `inventory` table (V10), one row per product (unique on product_id) with `quantity`, `reserved_quantity`, `low_stock_threshold`, optional `expiry_date`. Created automatically at quantity 0 in the same transaction as the product; no stock movement is written for the initial zero. CHECKs: quantity >= 0, reserved_quantity >= 0, low_stock_threshold >= 0, reserved_quantity <= quantity. Availability = quantity − reserved (computed in the entity)
- [x] **3.4 — Stock movement log**: `stock_movements` table (V11) records every change — movement_type (native ENUM of the nine plan section 6.2 types), signed quantity_delta, reason, reference id/type, nullable actor. Append-only (no updated_at). Product creation writes no movement; the log records changes only
- [x] **3.5 — Catalog API**: `VendorProductController` at `/api/v1/vendors/products` — `POST` (201), `GET` (paginated, optional `status` / `categoryId` / `name` filters, page size clamped 1..100), `GET|PUT /{id}` (403 foreign / 404 missing), `PATCH /{id}/deactivate` (204). The vendor comes from the JWT subject, so cross-vendor access is not expressible in a request. `@RequiresApprovedVendor` on the class — the first production route to carry it. No migration; reuses `products`, `categories.active`, `vendor_profiles`. Listing filters are Criteria predicates in the new `ProductSpecifications` (D-23); soft delete sets `INACTIVE` and keeps the row, its images and its inventory (D-22). 24 new unit tests, 26 new integration tests
- [x] **3.6 — Inventory API**: `VendorInventoryController` at `/api/v1/vendors` — read current inventory, `POST .../stock-in`, `.../stock-out`, `.../adjustments` (signed, nonblank reason), `.../write-offs` (`WASTE`), `PUT .../low-stock-threshold`, `PUT .../expiry-date`, `GET .../movements?page&size` (paged history), and vendor-wide `GET /api/v1/vendors/inventory/low-stock?page&size`. Availability is `quantity - reserved_quantity` and is never clamped; a change that would breach the floor is refused with a dedicated `409 INSUFFICIENT_STOCK` rather than silently applied at the floor. `@RequiresApprovedVendor` on the class; 403 for a foreign product and 404 for a missing one, both resolved before any row lock is taken. Every mutation takes a pessimistic write lock on the inventory row, then writes the new level and exactly one `stock_movements` row in one transaction (D-24); the movement's actor is the authenticated principal, so it cannot be client-supplied. The two alert settings take the same lock even though they write no movement, because Hibernate's whole-row `UPDATE` would otherwise write back a stale `quantity`. Low-stock rule is `available <= lowStockThreshold`, vendor-scoped, via `InventorySpecifications`. No migration — reuses `inventory` and `stock_movements` from tasks 3.3/3.4. 59 new unit tests, 35 HTTP integration tests, 6 real-thread concurrency tests
- [ ] **3.7 — Expiry scheduler**: `@Scheduled` job using the injected `Clock`: expired stock → `WASTE` movement and product delisted
- [ ] **3.8 — Image handling**: `StorageService` interface: local disk (dev), S3 implementation pluggable by profile. Validation: type whitelist (JPEG/PNG/WebP) checked by content sniffing, max size, random filenames, no path traversal, resize/compress. Enforces the D-21 one-primary-image invariant transactionally
- [ ] **3.9 — Vendor catalog UI**: Product list and form (Shopify-style), inventory table with stock adjustments, low-stock badges
- [ ] **3.T — Tests**: Ownership checks; slug collision; expiry job with a fake clock; upload rejection cases; RBAC checklist

#### Phase 3 — Ordering & Payments (Not Started)
- [ ] Cart functionality
- [ ] Checkout flow
- [ ] Order lifecycle
- [ ] Stripe integration

#### Phase 4 — Delivery & Notifications (Not Started)
- [ ] Delivery assignment and tracking
- [ ] Email/SMS notifications

## Latest Changes

| Date       | Change                                    | Files affected                                      |
|------------|-------------------------------------------|-----------------------------------------------------|
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
