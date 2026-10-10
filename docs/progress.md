# Progress

Tracks what has been implemented and what remains. Updated after each session.

## Current Phase

**Phase 4 (Customer addresses, discovery and search) — Tasks 4.1, 4.3 and 4.4 COMPLETE**

Task 4.1 (customer address book) is implemented, verified and committed on branch
`phase-4-discovery-search`: the `addresses` table (V12), entity and repository,
DTOs and MapStruct mapper, `AddressService` with the one-default-per-customer
invariant enforced under a pessimistic lock on the caller's own `users` row
(D-32), and `AddressController` at `/api/v1/addresses` inside a CUSTOMER-only
namespace. Stage 0's design note (`docs/phase-4-stage-0-design.md`) records the
decisions carried into the implementation.

Task 4.3 (geo discovery, `GET /api/v1/discover`) is implemented and verified: a
public `permitAll()` endpoint returning approved, order-accepting vendors within
their `delivery_radius_km` of a required `locationId`, with a bounding-box
prefilter on `idx_vendor_profiles_status_geo` and an exact Haversine refinement,
a `distanceKm`/`estimatedDeliveryFee` per vendor, and a sorted, paginated
`PageResponse` envelope. Migrations: none (reused V5).

Task 4.4 (product search, `GET /api/v1/search`) is implemented and verified: a
public endpoint returning ACTIVE, in-stock products from approved vendors within
radius, with `q` / `category` (active subtree) / `priceMin` / `priceMax` /
`vendorId` filters, four sort modes (default distance), in-memory pagination
after the radius filter, and D-35's decisions on the shared geo helpers
(`GeoDistance`, `GeoCandidates`, `PageResponses`, `DeliveryFee`), the sort
whitelist, the cross-field price rule and the unknown-vendor empty page.

Stage 1 verification:

| Command | Result |
|---|---|
| `./mvnw test` | **558 unit tests** (508 Phase 3 baseline + 19 for task 4.1 + 10 for task 4.3 + 21 for task 4.4), 0 failures / 0 errors / 0 skipped — `BUILD SUCCESS` |
| `./mvnw verify -Pintegration` | **414 integration tests** (365 baseline + 25 for task 4.1 + 6 for task 4.3 + 18 for task 4.4), 0 failures / 0 errors / 0 skipped — `BUILD SUCCESS` |

### Next Phase

**Phase 4 remaining tasks 4.2, 4.5–4.T** per `docs/FlowerConnect_Implementation_Plan_v2.2.md` —
location picker (4.2, session-backed), storefront API (4.5), then the customer
frontend (4.6–4.9) and Phase 4 verification (4.T).

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
- [ ] 4.2 Location picker (session-backed, no GPS)
- [x] **4.3 — Geo discovery API**: public `GET /api/v1/discover` (`permitAll()`, mirroring `/api/v1/locations`) returning approved, order-accepting vendors within their `delivery_radius_km` of a required `locationId`. The query takes a bounding-box prefilter derived from the maximum approved `delivery_radius_km` (served by `idx_vendor_profiles_status_geo`) and then an exact Haversine refinement per vendor, so the radius rule is the vendor's own, not a global one. Response carries `distanceKm` and `estimatedDeliveryFee` (`baseDeliveryFee + perKmFee x distanceKm`) per vendor, `freeDeliveryAbove` as informational only, in the standard `PageResponse` envelope sorted by distance then id. Missing `locationId` is 400 via the `MissingServletRequestParameterException` handler ("Required parameter 'locationId' is not present"); an unknown one is 400 `VALIDATION_FAILED` ("Unknown service location"). No migration (reuses V5). 10 unit tests (`DiscoveryServiceTest`) + 6 integration tests (`DiscoveryControllerIntegrationTest`) covering radius inclusion/exclusion, non-approved exclusion, both 400s, pagination metadata and distance-then-id ordering
- [x] **4.4 — Product search API**: public `GET /api/v1/search` returning ACTIVE, in-stock products (`quantity - reserved_quantity > 0`) from approved, order-accepting vendors within their delivery radius, with `q` / `category` / `priceMin` / `priceMax` / `vendorId` filters, a four-value `sort` whitelist (`distance | price_asc | price_desc | name`, default `distance`, normalised by trim + lowercase, every mode tie-broken by product id so pagination is stable) and in-memory pagination taken after the radius filter and sort, because `distanceKm` is not a column on `products` (D-35). Eligibility is one Specification (`SearchSpecifications.eligibleForStorefront`) following the D-23 precedent; positive stock is a correlated `EXISTS` subquery over `inventory` because `Inventory` owns the FK and `Product` deliberately has no inverse mapping. A `category` filter resolves to the whole active subtree, pruning at an inactive node; an unknown or inactive category is 400. `priceMin > priceMax` is a cross-field 400; there is no per-field positivity bound, because a negative price bracket has a well-defined empty result. An unknown `vendorId` is an empty page, not a 400 — a filter that selects nothing is a successful search with zero results. The geo work is shared with task 4.3 through three utilities in `com.flowerconnect.geo`: `GeoDistance` (Haversine, bounding-box deltas, display scale), `GeoCandidates` (the whole vendor-radius resolution — bounding box from the maximum approved radius, then the exact Haversine refinement per vendor, each returned with its distance) and `PageResponses` (clamping, the page window, the `PageResponse` envelope, with 64-bit window arithmetic so a caller-supplied page cannot overflow it); `DeliveryFee` holds the fee formula both endpoints quote, so they cannot drift. `SecurityConfig` maps `/api/v1/search/**` to `permitAll()`. `Product.vendor`/`Product.category` carry `@BatchSize(size = 100)` so the storefront's lazy loads batch instead of running one select per row. No migration. 21 unit tests (`SearchServiceTest`) + 18 integration tests (`SearchControllerIntegrationTest`)
- [ ] 4.5 Storefront API (`GET /api/v1/vendors/{id}/storefront`)
- [ ] 4.6 Customer home page (location picker, vendor cards)
- [ ] 4.7 Search and filter UI
- [ ] 4.8 Storefront page (product grid, add-to-cart)
- [ ] 4.9 Product detail modal (carousel, note)
- [ ] 4.T Phase 4 verification & RBAC tests

## Latest Changes

| Date       | Change                                    | Files affected                                      |
|------------|-------------------------------------------|-----------------------------------------------------|
| 2026-10-10 | Implement Task 4.4: Product search API (`GET /api/v1/search`) | SearchResponse DTO, SearchSpecifications, SearchService, SearchController, GeoDistance/GeoCandidates/PageResponses/DeliveryFee shared utils, DiscoveryService refactor, Product @BatchSize, SecurityConfig update, unit and integration tests |
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
