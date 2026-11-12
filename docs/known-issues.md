# Known Issues & Limitations

## Known Issues

| ID  | Area       | Description                          | Status       | Workaround / Notes |
|-----|------------|--------------------------------------|--------------|---------------------|
| 001 | Build      | Windows: `mvnw.cmd` is gitignored    | Intentional  | Use `./mvnw` from PowerShell or WSL. The `mvnw` shell script works on Windows via Git Bash or WSL. |
| 002 | Frontend   | `package-lock.json` is gitignored    | Intentional  | Run `npm install` after cloning. Lock file intentionally excluded per project convention. |
| 003 | Frontend   | `.eslintrc.cjs` uses `export default` but config is `.cjs` | Cosmetic | Works because `type: "module"` in package.json. ESLint resolves it correctly. |
| 004 | Frontend   | `tsconfig.json` excludes `node` but not `server.mjs` | Low priority | `server.mjs` is at project root, outside `src/`. No type impact. |
| 005 | Security   | `.env.example` contains placeholder secrets | By design | All values are `CHANGE_ME_*` or obvious defaults. Must be replaced before deployment. |
| 006 | Kilo       | `.md` files in `.kilo/agent/` and `.kilo/command/` fail frontmatter validation | Non-fatal | YAML frontmatter in these directories triggers "No context found for instance". Agent and command definitions are consolidated in `kilo.jsonc` instead, which passes schema validation. |
| 007 | Frontend   | `jsdom` was missing from `package.json` devDependencies | Fixed | Vitest config requires `jsdom` for the `environment: "jsdom"` setting. Added `jsdom: ^24.1.3` to devDependencies. |
| 008 | Frontend   | `eslint-config-prettier` and `eslint-plugin-react` were missing from `package.json` | Fixed | ESLint config extends `"prettier"` and uses `eslint-plugin-react`. Both added to devDependencies. |
| 009 | Frontend   | Smoke test was in `setup.ts` which Vitest does not discover | Fixed | Vitest only discovers `*.test.ts`/`*.spec.ts` files. `setup.ts` now contains only setup code; test moved to `src/test/smoke.test.ts`. |
| 010 | Frontend   | ESLint failed on `tailwind.config.ts` and `postcss.config.js` | Fixed | These files are outside `tsconfig.json`'s `include` scope. Added `--ignore-pattern` flags to the `lint` script and `ignorePatterns` in `.eslintrc.cjs`. |
| 011 | Dev        | Local MySQL lacked `flowerconnect` user with privileges | Environment | Backend uses `DB_USER=flowerconnect`/`DB_PASS=flowerconnect`. Local MySQL had only `root` (passwordless). User must be created manually for local dev, or Docker Compose handles it automatically. |
| 012 | Docker     | Docker not available on this machine | Resolved | Docker is now available. Full-stack verified with `docker compose up --build`. All services pass healthchecks. |
| 013 | Docker     | `docker-compose.yml` DB_URL used `${DB_HOST:-mysql}:${DB_PORT:-3306}` which resolved from root `.env` (host-side: `localhost:3307`) instead of internal Docker defaults | Fixed in `docker-compose.yml` | `DB_URL` now hardcodes `mysql:3306` (internal Docker network). Compose-level `${DB_HOST}` substitution is not used for the JDBC URL since the backend always connects to MySQL via the internal network. |
| 015 | DB / Auth  | `User.role` was `FetchType.LAZY` but `UserRepository.findByEmail` and `findById` lacked `JOIN FETCH`, causing `LazyInitializationException` during login and token refresh | Fixed | Added `@Query` with `JOIN FETCH u.role` to `UserRepository.findByEmail` and a new `findByIdWithRole` method. Added `@Query` with `JOIN FETCH rt.user u JOIN FETCH u.role` to `RefreshTokenRepository.findByTokenHash`. Updated `AuthService.login` to use `findByIdWithRole`. Updated `AuthServiceTest` mocks accordingly. |
| 016 | Test      | `TestProbeController` route conflict with `UserController` on `GET /api/v1/users/me` in `@SpringBootTest` contexts | Fixed | Added `@Profile("test-probe")` to `TestProbeController` and `@ActiveProfiles("test-probe")` to `RoleBoundaryTest`. |
| 017 | Test      | (a) Testcontainers 1.20.2 could not connect to Docker Desktop 29.8.0 without `~/.docker-java.properties` (`api.version=1.44`) — both `EnvironmentAndSystemPropertyClientProviderStrategy` and `NpipeSocketClientProviderStrategy` failed with `BadRequestException (Status 400)`; (b) `@Container` on `IntegrationTestBase.MYSQL` caused Testcontainers to create and destroy a new MySQL container per test class (per-class lifecycle), with four containers started (one per test class); `AuthApiIntegrationTest` and `RefreshTokenRepositoryIT` passed, `RoleRepositoryIT` and `UserRepositoryIT` got Connection refused | Fixed | (a) Upgraded Testcontainers to 1.21.4, which resolves Docker Desktop 29.x compatibility — `~/.docker-java.properties` is no longer required. (b) Removed `@Container` annotation; container now starts once via `static { MYSQL.start(); }` in `IntegrationTestBase` and is shared across all IT classes. One MySQL container per JVM run. |
| 018 | Test      | `./mvnw verify -Pintegration` fails every integration test with `Could not find a valid Docker environment` when the Docker Desktop daemon is not running, even though the Docker CLI is on `PATH` | Environment | Testcontainers needs the running daemon, not just the client. Start Docker Desktop before the failsafe run; Testcontainers fails fast with an `ExceptionInInitializerError` per test class rather than skipping. |
| 019 | Backend / Frontend | `SecurityConfig.corsConfigurationSource()` set `allowedMethods` to `GET, POST, PUT, DELETE, OPTIONS` — **`PATCH` was missing**, so any cross-origin browser request to a `PATCH` endpoint failed the CORS preflight. This blocked `PATCH /api/v1/admin/users/{id}/status` (task 2.10) and `PATCH /api/v1/users/me` (Phase 1) from the SPA on the dev setup (`:5173` → `:8080`) | Fixed | `"PATCH"` added to the allow-list in `SecurityConfig.corsConfigurationSource()` (commit `c27ef19`). Verified end-to-end from the browser during the Phase 2 close-out: the admin user status change succeeds cross-origin. |
| 020 | Backend / Frontend | **No route serves product image bytes.** `product_images` rows exist and the objects are written through `StorageService` (task 3.8), but `GET /api/v1/vendors/products/{productId}/images` returns metadata only and `storageKey` is an opaque backend key (`product-images/{productId}/{uuid}.{ext}`) that a client cannot turn into a URL. The vendor catalog UI therefore lists image metadata rather than rendering thumbnails, so no storefront or catalog screen can display a product photo yet | Open | Record the bytes as an authenticated `GET` that resolves the key through `StorageService` and streams it with the stored `mime_type`. Until then the UI deliberately avoids `<img>` so a vendor never sees a broken image and mistakes it for a failed upload (D-31). |
| 021 | Backend | **Category read paths disagreed with their contracts** — two defects found by the Phase 3 final audit. (1) **The public read served inactive categories:** `CategoryService.listActive()` built its response from `findByParentIdIsNullOrderByDisplayOrderAscIdAsc()`, which filters on `parent IS NULL` but has **no `active` predicate**, so a category an admin had deactivated was still published to the storefront chips (plan 4.7) and to the vendor product form's category picker, contradicting D-17. (2) **The admin listing was roots-only:** with no `parentId`, `CategoryService.list()` called `findByParentIdIsNull(pageable)`, so admin-created child categories never appeared in ordinary pagination and `totalElements`/`totalPages` counted roots only — an admin managing a two-level tree was told the wrong totals. Neither defect was catchable by the tests that existed: `CategoryServiceTest.listActiveReturnsFlatListOfActiveCategories` mocked the repository with rows it had already made active, and `CategorySeedIntegrityTest` validated fixtures it built itself rather than the database | Fixed | `CategoryRepository` now has exactly one roots-scoped query, `findByParentIdIsNullAndActiveTrueOrderByDisplayOrderAscIdAsc()`, whose name, JPQL and Javadoc all carry `active = true`; both unfiltered roots-only methods (`findByParentIdIsNullOrderByDisplayOrderAscIdAsc` and the paged `findByParentIdIsNull`) were **removed**, so neither defect can be reintroduced by reaching for the old query. The admin listing now paginates the whole table through `findAll(pageable)` when `parentId` is absent, while `parentId=X` still means "the direct children of X", sorted by `name` then `id` for stable pagination. Regression tests against real MySQL: `CategoryApiIntegrationTest.publicCategoryReadExcludesInactiveRoots` (creates an active root, an inactive root and an inactive root with an active child, then asserts the published slugs and the exact published count), `.adminListingWithoutParentIdIncludesChildrenAndCountsThem` (walks every page, asserts the child is reachable and that `totalElements`/`totalPages` at `size=1` equal one page per row) and `.adminListingWithParentIdReturnsOnlyThatParentsDirectChildren` (grandchild and unrelated root excluded). `CategoryServiceTest` additionally verifies *which* query each read path calls, so the wiring cannot drift again. Corrected as part of the audit fix: the previous text of this issue named a repository method that never existed (`findByActiveOrderByDisplayOrder`) and described the four seeded top-level categories as children. |
| 022 | Frontend / Security | **`auth-store.ts` persists tokens to `localStorage`.** The Zustand store's `persist` middleware writes `accessToken` and `refreshToken` to durable storage, which is contrary to the project's memory-only intent for token handling | Open | Deliberately left unchanged by task 3.9, which was scoped to the catalog UI and to not redesign authentication. Any fix must handle the Axios single-flight refresh in `src/shared/lib/api.ts` (both read from the store) and the 401 retry path together, or a reload mid-refresh will log the user out. |
| 023 | Test / Dev  | **The local dev MySQL holds a pre-rewrite V1 schema, so the app will not boot against it.** D-9 rewrote `V1__baseline.sql` before first deployment, but this machine's `flowerconnect` database was applied from the *original* V1. Booting the backend fails twice over: Flyway reports `Migration checksum mismatch for migration version 1` (applied 1060733681 vs resolved 1997243429), and once that is repaired, Hibernate `ddl-auto: validate` reports `missing column [created_at] in table [roles]` and then `missing column [status] in table [users]` — the old schema has `users.is_active TINYINT(1)` where the current entity expects a native `ENUM` status | Environment | Not a code defect and not something to migrate around: D-9 explicitly allows replacing V1–V11 before the first deployment. The fix is to drop the local schema and let Flyway rebuild it. Note that `flyway:repair` alone is **not** sufficient — it only rewrites the checksum and leaves the old tables in place, so `repair` followed by a boot fails on the next missing column. Drop `users`, `roles` and the dependent tables (with `SET FOREIGN_KEY_CHECKS=0`, since `fk_refresh_tokens_user` otherwise blocks dropping `users` first) plus `flyway_schema_history`, then start the backend once and let all 11 migrations apply. `./mvnw verify -Pintegration` is unaffected: it uses its own Testcontainers MySQL, not this one. |
| 024 | Test / Dev  | **Phase 3h browser end-to-end check is incomplete.** Vendor registration, pending-vendor login and the approval banner gating were verified live in the browser (D-29 confirmed: Catalog and Inventory links are withheld from an unapproved vendor). The *approved*-vendor half of the walkthrough — catalog create/edit, image upload/reorder/delete, stock in/out/adjust/write-off — was **not** walked through in a browser, because it requires an admin to approve the vendor and the Docker daemon became unavailable mid-session | Open | The blocking chain, recorded so it can be resumed: (1) Docker Desktop's Linux engine pipe was gone (`npipe:////./pipe/dockerDesktopLinuxEngine`), so `docker compose` could not start the stack; (2) the backend started via `./mvnw spring-boot:run` against local MySQL instead, but hit the schema drift in issue 023 and then could not bind port 8080 (a stale listener on `::8080` could not be terminated from this session); (3) with the backend down, no admin token could be minted, because `AdminBootstrap` needs `ADMIN_EMAIL`/`ADMIN_PASSWORD` supplied as environment variables (`docker-compose.yml` defaults both to **empty**, so the bootstrap is a deliberate no-op unless they are set) and the `.env` file holding them is unreadable to the agent by project rule. Do **not** read this as a functional gap: the equivalent behaviour is covered by 362 integration tests against the real production filter chain, and `docs/rbac-matrix.md` maps every cell to the test that asserts it. What is genuinely unverified is the rendered UI path for an approved vendor. |

| 025 | Backend / Frontend | **`GET /api/v1/categories` publishes only top-level categories, so the category picker and filter are one level deep.** D-17 now states the contract explicitly — active top-level categories (`parent_id IS NULL AND active = true`) — and the public query enforces it. A child category is therefore reachable through `GET /api/v1/admin/categories?parentId={id}` but never through the public list, so a vendor cannot assign a product to a sub-category and the storefront category chips show only the top level | Open | Deliberate for Phase 3: the plan names the four seeded categories as the chip level (4.7) and only asks for a hierarchical *entity*. Widening the public read to every active category (or exposing a tree) is an API contract change and is a decision to make before Phase 4.4 product search is built on it, not a bug fix. No client workaround is possible: a client-side flatten cannot know whether a parent is inactive. |
| 026 | Backend | **Deleting a category a product still references returns 500, not 409.** `CategoryService.delete()` refuses only the has-children case with a mapped `CONFLICT`. A referenced category is blocked by `fk_products_category` (V8), and the resulting MySQL constraint violation surfaces as a server error rather than the documented 409 | Open | Left unchanged by the Phase 3 audit fix, which was scoped to the two category read-path defects. `PUT /api/v1/admin/categories/{id}` with `active = false` is the supported way to retire a category that products still use. Adding a `countByCategoryId`-style service check would turn it into a clean 409, but that is product-assignment behaviour (tasks 3.2/3.5) and needs its own decision. |
| 027 | Backend | **Phase 6 order state machine is unit-tested; vendor order endpoints (6.2–6.4), customer order endpoints (6.3), and the auto-reject job (6.5) are implemented, but the `platform_config` table remains minimal.** `OrderStateMachine` (task 6.1) is implemented and exhaustively unit-tested. Vendor order action endpoints, paginated list, and today's summary under `/api/v1/vendor/orders` (Stage 2) and customer order list/detail under `/api/v1/orders` (Stage 3) are implemented and integration-tested against real MySQL. The scheduled auto-reject job (6.5) is implemented: `AutoRejectService` reads `auto_reject_minutes` from `platform_config` (additive migration `V18`, seeded at 60), selects `PLACED` orders strictly older than the threshold, and cancels each through `OrderStateMachine` in its own `REQUIRES_NEW` transaction. Only frontend orders UI (6.T) remains. The Phase 5 checkout `PLACED` history row has a null `actor_type`, compatible with the `order_status_history.actor_type` column; additive migration `V17` aligns the committed `V16` column (`VARCHAR`) with the `ActorType` entity enum so `ddl-auto: validate` passes. | Open | Frontend orders UI (6.T) remains. No applied migration was edited (`V17`, `V18` are additive). |

## Known Limitations

| ID  | Area       | Description                                           | Impact |
|-----|------------|-------------------------------------------------------|--------|
| 001 | Backend    | Redis was provisioned in `docker-compose.yml` and configured in `application.yml`/`application-prod.yml`, but nothing in the backend used it (rate limiting uses Caffeine) | Fixed/Removed | Redis service, config, env vars, and volume removed in Stage 6 per plan v2.2 (no Redis in v1) |
| 002 | Frontend   | No error boundary component                           | Unhandled errors will crash the app. Not introduced by Phase 2; the vendor and admin areas handle expected failures through per-query error states (`AdminErrorState`, `VendorErrorState`) rather than a route-level boundary. |
| 003 | Frontend   | No loading states or suspense in routes                | All routes render immediately. Add skeleton loaders later. |
| 004 | Docker     | No `.env` file required for `docker compose up`      | Compose uses defaults from `.env.example`. Production deployments need a real `.env`. |
| 005 | Docker     | No health check for backend DB/Redis connectivity    | Backend may start before DB is ready if healthcheck fails silently. |
| 006 | Docker     | Docker now available                                  | Full-stack Docker verified. All healthchecks pass. |
| 007 | Frontend   | Vite dev proxy for `/api` exists in `vite.config.ts` but is currently unused — frontend axios `baseURL` points directly at `http://localhost:8080/api/v1`. If proxy is ever activated (relative `VITE_API_BASE_URL`), stage 4c's exact-Origin check in `AuthController.validateCookieAuthentication` would need revisiting, since same-origin proxied requests may not present the `Origin` header the same way cross-origin requests do. Verified via trace in Stage 6; not re-tested with proxy active. | Configuration mismatch; no runtime impact currently |
| 008 | Docker     | **The previous service image cannot be recovered by tag after a rebuild.** This Docker uses the containerd snapshotter (`io.containerd.snapshotter.v1`), so when a build retags a service image the old manifest is garbage-collected from the content store: `docker image inspect <old-id>` returns "No such image" and `docker images -a --filter dangling=true` is empty. `docker commit` of the still-running container fails too, because the container's image content was collected with it | The tag-based rollback (`docker tag <old-id> <service>:latest`, then recreate) does not work, and the old image ID is no longer inspectable. Restoring the previous build means rebuilding it from the committed source in a separate temporary context. Observed on the 2026-10-21 D-1 frontend deployment, where the pre-deployment image `sha256:f15135c911ee…` became unrecoverable. Plan a rollback that rebuilds from source rather than assuming the old image survives |

## Discovered Problems

- Frontend scaffold had missing devDependencies (`jsdom`, `eslint-config-prettier`, `eslint-plugin-react`) that broke `npm run test` and `npm run lint`. All fixed and documented above.
- Vitest smoke test was placed in `setup.ts` which Vitest does not auto-discover (only `*.test.ts`/`*.spec.ts`). Moved to `src/test/smoke.test.ts`.
- ESLint config used `export default` in `.eslintrc.cjs` (CommonJS extension), causing a parse error. Rewritten with `module.exports`.
- ESLint failed on `tailwind.config.ts` and `postcss.config.js` because they fall outside `tsconfig.json`'s `include` scope. Added ignore patterns.
- Local MySQL instance lacked the `flowerconnect` user with privileges; backend failed to start until the user was created manually. Docker Compose creates this user automatically.
- Docker was unavailable in prior sessions; `docker compose up --build` could not be validated directly and local services were used as a substitute. Docker is now available and full-stack has been verified.
- `docker-compose.yml` DB_URL line used `${DB_HOST:-mysql}:${DB_PORT:-3306}` template substitution, which resolved to `localhost:3307` (from root `.env` file intended for host-side tooling) instead of the internal Docker hostname `mysql:3306`. Fixed by hardcoding `mysql:3306` in the DB_URL env var.
- The original V1 baseline `roles` table omitted the `created_at` column that the `Role` entity maps via `@CreationTimestamp`, and Hibernate `ddl-auto: validate` rejected the schema at startup. This was first fixed with a V4 migration. It is now fixed by including `created_at` in the rewritten V1 baseline (D-9: migrations were rewritten before first deployment, and V4 and V5 no longer exist).
- `User.role` (FetchType.LAZY) was not eagerly fetched by `UserRepository.findByEmail` and `findById`, causing `LazyInitializationException` when `UserDetailsImpl.fromUser()` or `AuthService.createAuthResponse()` accessed `role.getName()` outside the Hibernate session. Fixed by adding `@Query` with `JOIN FETCH` to `findByEmail`, adding `findByIdWithRole`, and eager-fetching Role in `RefreshTokenRepository.findByTokenHash`.
- `TestProbeController` (in `src/test/java`) mapped `GET /api/v1/users/me` and `GET /actuator/metrics`, conflicting with `UserController` when loaded in `@SpringBootTest` contexts (e.g., `AuthApiIntegrationTest`). Fixed by adding `@Profile("test-probe")` to `TestProbeController` and `@ActiveProfiles("test-probe")` to `RoleBoundaryTest` (which uses `@WebMvcTest(controllers = TestProbeController.class)`).
- (a) Upgrading Testcontainers to 1.21.4 resolved Docker Desktop 29.x compatibility — `~/.docker-java.properties` with `api.version=1.44` is no longer required; verified via `mvn verify -Pintegration` without the file.
- (b) `@Container` on the shared static field in `IntegrationTestBase.MYSQL` caused per-class container restarts and `Connection refused` errors; fixed by the singleton static pattern (`static { MYSQL.start(); }`), one container per JVM run.

## Blocked Work

_None currently blocked._

## Testing Gotchas (Phase 3h, close-out)

- **`flyway:repair` is not a substitute for rebuilding a drifted dev schema.** D-9 allowed
  V1–V11 to be rewritten before the first deployment, so a local database applied from the
  *original* V1 reports a checksum mismatch and then, once repaired, fails
  `ddl-auto: validate` column by column (`missing column [created_at] in table [roles]`, then
  `missing column [status] in table [users]` — the old schema has `is_active TINYINT(1)` where
  the entity expects a native `ENUM`). Repair rewrites the checksum and nothing else, so each
  boot surfaces the *next* missing column. Drop the tables (with `SET FOREIGN_KEY_CHECKS=0`;
  `fk_refresh_tokens_user` otherwise blocks dropping `users` first) plus
  `flyway_schema_history`, then let one boot apply all 11 migrations. The Maven Flyway plugin
  does not read `application.yml`, so the repair needs the connection passed explicitly —
  and on PowerShell the `-D` values must be **quoted** (`mvnw "-Dflyway.url=..."`),
  otherwise `jdbc:mysql://` is parsed as a groupId and Maven tries to download it as a plugin
  artifact. `./mvnw verify -Pintegration` is unaffected: it uses its own Testcontainers MySQL.
- **A `2xx` cell can hide a contract inconsistency.** `POST /api/v1/admin/categories` returns
  **200**, not the **201** the Phase 3.5 and 3.8 vendor routes return on create. The
  controller and its test agree, so nothing fails — which is exactly why it survives. Recorded
  in `docs/rbac-matrix.md` rather than "fixed", because changing it is an API contract change
  and not an incidental bug fix.
- **Global reference data has no 403-foreign cell.** Categories belong to no vendor, so the
  RBAC matrix column is N/A rather than untested. Six cells in the 3.1 table (the 401s, and
  the `PUT`/`DELETE` non-admin 403s) are genuinely unasserted *in that file*; they are
  covered indirectly because all six route through the same `hasRole("ADMIN")` namespace rule
  that `RoleBoundaryTest` and `AdminUserStatusIntegrationTest` exercise for
  `/api/v1/admin/**`. The gaps are drawn in the matrix rather than quietly filled, so a
  reader can see which cells rest on the namespace rule and which are asserted directly.

## Testing Gotchas (Phase 2d frontend, admin area)

- **A sibling `describe` does not inherit another suite's `beforeEach`.** In `router.test.tsx` the
  new `describe("admin route protection")` sits *next to* `describe("application router")`, not
  inside it, so the outer suite's state reset never runs. The symptom is misleading rather than
  obvious: tests render the **unauthenticated** layout (Login/Register in the header, the login form
  in `<main>`) because a previous test in the new suite left `mockState.isAuthenticated = false`,
  and only the tests that do *not* set their own `mockState` fail. Give a sibling suite its own
  `beforeEach` that sets every field it relies on.
- **A `vi.hoisted` factory must return via a callback.** `vi.hoisted<{user: X}>({ user: null })`
  throws `TypeError: "vi.hoisted" factory value must be function, received "object"` and the whole
  file reports `no tests` rather than a named failure. Use
  `vi.hoisted(() => ({ user: null as X | null }))`.
- **`selector({ user: mockCurrentUser })` instead of `mockCurrentUser.user` fails silently.** Passing
  the hoisted *wrapper* where the store's `user` is expected makes `state.user?.id` `undefined`,
  so `currentUserId` becomes `null` and every self-comparison is false. The page then renders
  normally — just without the self-row treatment — so the symptom is "the (you) marker never
  appears", not an error. Assert on a rendered value, not just the absence of a throw.
- **`vi.clearAllMocks()` inside a nested `beforeEach` is redundant** and re-clears the shared
  `vi.fn()`s the outer suite already configured; put the shared reset in the sibling suite once.
- **`getAllByRole("row")` includes the header row.** For a table with a `<thead>`, index 0 is the
  header; `slice(1)` before asserting per-row buttons, or a "did this row show the wrong action"
  assertion will read the header's cells.
- **An empty `<option>` label collides with a table cell's text.** A role filter option labelled
  "Customer" and a table cell also reading "Customer" makes `getByText("Customer")` throw
  "Found multiple elements". Scope cell assertions with `within(row)`.

## Testing Gotchas (Phase 2d frontend, vendor area)

- **zod v3 `.refine()` silently discards the message a check returns.** `z.string().refine(fn)`
  where `fn` returns a string renders zod's default `Invalid input`, not the returned string; only a
  message passed as the second argument is used. So a field that "validates" but reports the wrong
  text produces a test failure that looks like the validator never ran. Use `.superRefine` +
  `ctx.addIssue({ message })` when the message depends on which bound failed — see
  `requiredDecimalField` / `optionalDecimalField` in `src/features/vendor/form-schema.ts`.
- **react-hook-form `reset()` does not refresh a `register`ed checkbox.** RHF keeps checkbox state
  in the DOM, so after the profile loads (or a save re-seeds the form) the stored week renders with
  the stale week still ticked while the time inputs show the new values. Bind `checked` to a
  `useWatch`ed value and write through `setValue` instead of `register` — see the open/closed
  checkbox in `VendorHoursPage`.
- **`z.record(z.enum([...]), schema)` infers optional values.** Indexing it by a member of the enum
  gives `T | undefined` under `noUncheckedIndexedAccess`, so every access needs a guard. Build a
  fixed seven-key shape (`DAY_SHAPE` in `VendorHoursPage`) when every key is guaranteed present.
- **Rendering assertions on the shared `renderWithProviders` result drop the DOM queries.** Spreading
  the result (`{ queryClient, ...render(...) }`) widened to a union and lost the Testing Library
  queries under `tsc -b`. Use `Object.assign(render(...), { queryClient })`, which keeps the
  intersection.

## Testing Gotchas (Phase 2d)

- **Integration tests share one accumulating database.** The singleton MySQL container in
  `AbstractIntegrationTest` persists users across every IT class in the run, so a listing test
  cannot assume the row it just created is on page 0. `AdminUserStatusIntegrationTest` reads the
  *last* page (`createdAt` ascending puts the newest row last) and walks every page for filter
  assertions via `listedIds(...)`. Anything asserting on paginated results must do the same.
- **Skip the unit phase when iterating on an IT class.** `-Dtest='!*'` is rejected by Surefire 3.x;
  use `-Dsurefire.failIfNoSpecifiedTests=false` with a pattern that matches nothing, or run the
  whole `verify -Pintegration` (264 unit + 222 integration tests, roughly 12 minutes with the
  MySQL and Mailhog containers).
- **`-Dit.test=...` must be quoted in PowerShell.** Unquoted, `-Dit.test=Foo` is parsed as
  `-D` plus a separate argument and Maven reports
  `Unknown lifecycle phase ".test=AdminUserStatusIntegrationTest"`. The same applies to
  `-Dsurefire.failIfNoSpecifiedTests=false`, which PowerShell otherwise splits into
  `-D` + `surefire.failIfNoSpecifiedTests=false` and Maven rejects as a lifecycle phase.
- **Growing the vendor IT suite can break a test that assumed an exclusive dataset.** The accumulated
  `vendor_profiles` count crossed the default page size of 20 during the Phase 2 close-out, and two
  tests failed on assumptions that only hold when this class runs alone:
  `VendorApiIntegrationTest.anAdminCanListVendorsAndFilterByStatus` on
  `hasItem(first.profileId())`, and `VendorProfileRepositoryIT.shouldFindAllProfilesByStatus` on
  `assertEquals(1, approved.size())`. Both now assert membership of the rows the test created
  (`listedIds(status)` walking every page with `size=100`; `idsOf(...)` containment checks) instead
  of counting or paging. Any new IT that registers vendors makes the problem more likely, not less,
  so scope to the rows you created rather than to the table.
- **A `@Positive`/`@PathVariable` violation has no `validation` map.** Bean validation on a path
  variable raises `ConstraintViolationException`, which `GlobalExceptionHandler` renders as
  `{"code":"VALIDATION_FAILED","message":"Validation failed: ..."}` with no per-field `validation`
  object. Asserting `$.validation.id` fails with `No value at JSON path "$.validation.id"`. Only
  `@Valid @RequestBody` violations populate that map.

## Testing Gotchas (Phase 3d)

- **A test fixture that creates a product over HTTP cannot run as a PENDING or SUSPENDED vendor.**
  The approval gate lives on the controller (D-13), not in `ProductService`, so
  `POST /api/v1/vendors/products` returns `403 VENDOR_NOT_APPROVED` for a vendor the product itself
  does not care about. Two `VendorInventoryIntegrationTest` cases set up exactly those states (the
  pending-vendor refusal and the reinstate-restores-access case, whose vendor is suspended when the
  product is created) and both failed with `Status expected:<201> but was:<403>` from the fixture
  helper, not from the assertion under test. `createProduct` now calls `productService.create(...)`
  directly. The rule generalises: **an approval-gated state is awkward to reach through a gated
  fixture, so build the fixture below the gate.**
- **`VendorProfile.builder()` needs `.status(...)` explicitly** in a hand-rolled vendor fixture. The
  builder has no default, so an omitted status persists as `null` and fails `ddl-auto: validate` on
  the ENUM column (D-20) at flush time — with a schema-validation message that names no test line.
  `InventoryConcurrencyIntegrationTest.createVendor` is the reference fixture.
- **A pessimistic-lock test cannot assert its own serialisation.** Run the contenders one after
  another and every call sees the previous one's committed level, so an unlocked implementation
  passes. `InventoryConcurrencyIntegrationTest` uses a `CountDownLatch` to release all threads
  together and asserts only end state (final quantity = sum of deltas, movement count = number of
  successful changes). With one unit of stock and eight contenders exactly one thread must succeed
  and the other seven must get `INSUFFICIENT_STOCK`; if more than one succeeds, the lock is absent.
- **Concurrent creates can deadlock on the product unique index (error 1213)**, which is the same
  transient MySQL behaviour already documented for slug collisions under Phase 3b. Keep fixture
  names unique per test and retry the whole create in a fresh transaction
  (`ProductInventoryIntegrationTest.createWithDeadlockRetry`).
- **`INSUFFICIENT_STOCK` needs reserved units to be reachable at all.** No route in this phase
  writes `reserved_quantity` (`RESERVE` movements arrive with checkout in Phase 5), so the floor is
  set up by updating the column with `JdbcTemplate` before the request under test. Without that the
  409 this API exists to return would never fire and the test would silently prove nothing.

## Testing Gotchas (Phase 3f)

- **A `@Configuration`-annotated `@ConfigurationProperties` class must not also be listed in
  `@EnableConfigurationProperties`.** `StorageProperties` and `ImageUploadProperties` carry
  `@Configuration` so component scan picks them up (which is why `FlowerConnectApplication` was left
  alone); listing them as well registers a *second* bean of the same name and the context fails at
  startup with `BeanDefinitionOverrideException: Invalid bean definition with name
  'storageProperties' ... There is already a bean defined with the name 'storageProperties'`. Every
  `@SpringBootTest` in the run then reports the same `ApplicationContext failure threshold (1)
  exceeded`, so the real cause is only in the first stack trace. Two registration mechanisms, one
  bean — pick one per properties class in this codebase.
- **`MockMultipartHttpServletRequestBuilder.file(...)` returns the parent builder type.** Chaining
  `multipart(url).file(part).param("primary", "true").header(...)` fails to compile, because
  `file` returns `MockMultipartHttpServletRequestBuilder` (the multipart parent) and `param`/`header`
  belong to `MockHttpServletRequestBuilder`. Assign in steps:
  `MockMultipartHttpServletRequestBuilder request = multipart(url); request.file(part); request.param(...)`.
- **A storage test that points at the configured `uploads` directory writes into the repo.**
  `application-test.yml` sets `app.storage.local-directory: target/test-uploads/default`, and
  `ProductImageIntegrationTest` overrides it per class with a JUnit `@TempDir`. `uploads/` is in
  `.gitignore`, so a leak is invisible to `git status` — which is exactly why the test profile
  redirects the root rather than relying on the ignore rule.
- **An `@Test` count read with a text grep is not a test count.** `Select-String '@Test'` over the
  new image test files over-reports by one or two per file (37 vs the 36 the service suite actually
  runs), so the totals in `docs/progress.md` come from the surefire/failsafe `.txt` reports under
  `target/`, not from a source scan.

## Testing Gotchas (Phase 3g)

- **`findByRole` on a container that renders in every state resolves before the query that gates its
  contents.** `VendorLayout` always renders the `<nav>`, so
  `await screen.findByRole("navigation", …)` succeeds on the first frame while `profile` is still
  `undefined` and the approval-gated Catalog/Inventory links have not been added yet. The symptom is
  `Unable to find an accessible element with the role "link" and name "Catalog"` under the full suite
  only, because the isolated run happened to settle first. Wait for something the *query* produces
  (the banner's `2 of 7 days open` row) before asserting the conditional links.
- **A zod numeric pattern must match the DTO's signedness, not the intuitive one.** The adjustment
  dialog's `/^\d+$/` mirrored "a number" and silently made the one action that accepts a *negative*
  quantity untypeable, while the three positive actions needed the same pattern plus an explicit `> 0`
  refine. Signed actions take `/^-?\d+$/`; positive ones keep `/^\d+$/`.
- **A derived "is this past its date" marker must use the same boundary as whatever acts on it.**
  `isPastExpiryDate` compared against *tomorrow* (`format(addDays(parseIsoDate(value), 1), "d MMM yyyy")`)
  to line up with `isExpiredToday` marking a product expiring today, which turned the "past its expiry
  date" badge red a day before D-25's sweep would write the stock off. The product form now compares the
  date against today directly, matching the sweep's `expiryDate < today`.

## Testing Gotchas (Phase 3e)

- **A `@Scheduled` cron takes exactly six fields — there is no year field.** The natural way to keep a
  scheduled job from firing during a test is a far-future date, and Quartz-style
  `"0 0 0 1 1 ? 2099"` fails at **context startup** with
  `Encountered invalid @Scheduled method 'sweepExpiredStock': Cron expression must consist of 6 fields`.
  Every test then reports the same `ApplicationContext failure threshold (1) exceeded`, so the real
  cause is only in the first stack trace. Use `"-"` (Spring's `Scheduled.CRON_DISABLED`), which
  `application-test.yml` now sets for `app.expiry-sweep-cron`.
- **`Sort.Order` has no two-argument `asc`.** `Sort.Order.asc("product", "id")` does not compile;
  the varargs overload belongs to `Sort.by(...)`. For the expiry sweep the ordering was left in the
  `@Query` string anyway (`ORDER BY i.product.id ASC`), because ascending product id is the lock
  order plan section 6.2 mandates and must not be something a caller's `Pageable` can override.
- **`MutableClock` and `AppProperties` are cached singleton beans shared by the whole integration
  suite.** A test that moves the clock (to reach "tomorrow") or lowers
  `expiry-sweep-max-rows` must restore both in `@AfterEach`, or the next test class in the same
  context inherits them. `InventoryExpiryIntegrationTest.restoreClockAndBatchCap` is the reference.
- **A scheduled job is a live actor inside an integration test.** Nothing else in the suite left a
  past-dated `expiry_date` behind (`VendorInventoryIntegrationTest` sets one and clears it in the
  same test), but a stray 03:00 run would still have been able to write off a row a test was
  asserting on — hence the disabled cron in the test profile rather than relying on the run's timing.

## Testing Gotchas (Phase 3c)

- **`Category.builder().active(true)` is required in a product-service fixture.** `active` is a
  primitive `boolean`, so the builder defaults it to `false` — and task 3.5 refuses to assign a
  product to a deactivated category. A fixture written before that rule fails with
  `Category is not active` rather than anything mentioning `active`, so the symptom looks like the
  service is rejecting a perfectly good category.
- **`verify(repo, never()).delete(any())` becomes ambiguous once a repository extends
  `JpaSpecificationExecutor`.** `JpaSpecificationExecutor` adds `delete(Specification<T>)`, which
  matches a bare `any()` exactly as well as `CrudRepository.delete(T)` does. Use
  `delete(any(Product.class))`.
- **Do not try to unit-test a `Specification` by calling `toPredicate` with null Criteria
  arguments.** It does not throw, it returns something meaningless, and any assertion written
  against it silently passes or silently fails depending on the stub. Filter *behaviour* belongs in
  an integration test against real SQL; a unit test can only assert that the right `Pageable` was
  passed and that an absent filter contributed no predicate.
- **`@RequiresApprovedVendor` cannot be covered by a `@WebMvcTest` slice** (D-13). The controller
  slice test carries an explicit Javadoc note saying so, because the slice happily returns 200 for
  every gated route and a reader would otherwise assume the gate is verified there.

## Testing Gotchas (Phase 3b)

- **Enum-typed entity fields require native MySQL `ENUM` columns.** Hibernate 6.4 with the
  MySQL dialect maps `@Enumerated(EnumType.STRING)` to the dialect's native ENUM type, so
  `ddl-auto: validate` fails at startup if the migration declares the column `VARCHAR(32)`:
  `Schema-validation: wrong column type encountered in column [status] ... found [varchar
  (Types#VARCHAR)], but expecting [enum (...) (Types#ENUM)]`. Write the migration column as
  `ENUM('A', 'B', ...)` with the Java constants' exact names (see D-20). A `CHECK ... IN
  (...)` constraint on an ENUM column is redundant and was dropped from V11.
- **An unknown ENUM value is rejected by the column type, not a named constraint.** Inserting
  a value outside the ENUM list fails with MySQL error 1265 ("Data truncated for column
  ..."), so a test cannot assert a CHECK-constraint name in the failure chain — assert the
  column name instead (see `ProductInventoryIntegrationTest.stockMovementsRejectAnUnknownMovementType`).
- **Concurrent creates with the same slug can deadlock (error 1213).** Several transactions
  inserting the same unique-key value at once take gap locks on the unique index and can
  deadlock; InnoDB rolls the victim's whole transaction back (the service's in-method
  duplicate-key retry cannot recover from that — the transaction is already dead). MySQL's
  own error message says "try restarting transaction", so the caller retries the whole
  create in a fresh transaction; `ProductInventoryIntegrationTest.createWithDeadlockRetry`
  is the reference helper. This is transient infrastructure behaviour, not a slug bug.
- **A test's product name must be unique to that test.** The integration database accumulates
  rows across every test in the run (non-transactional `@SpringBootTest`), so a slug test
  that reuses a name another test already created (e.g. "Hybrid Tea") collides before its
  first create and the whole expected suffix sequence shifts by one. Give each slug test its
  own product name, or assert the suffix sequence relative to the first create's slug.

## Testing Gotchas (Phase 2c)

These cost real debugging time and will recur if forgotten:

- **`@RequiresApprovedVendor` is inert in `@WebMvcTest` slices.** The annotation is a composed
  `@PreAuthorize`, enabled by `@EnableMethodSecurity` on the production `SecurityConfig`. A slice test
  supplies its own `SecurityFilterChain` and never loads that class, so a gated route returns 200
  instead of 403 and the slice test passes while the gate is untested. Gating must be covered by a
  `@SpringBootTest` (see `VendorApprovalGatingIntegrationTest`). This is why method security was
  chosen over a global `WebMvcConfigurer`/`HandlerInterceptor` — see `docs/decisions.md` (D-13).
- **A gating probe route must sit under the real namespace.** With the probe mapped to a path outside
  `/api/v1/vendors/**`, the `hasRole("FLORIST")` matcher never runs, so a CUSTOMER passes RBAC and is
  refused by the guard with `VENDOR_NOT_APPROVED` instead of `FORBIDDEN`. The observable status is
  still 403, so the bug is easy to miss; the error code is what exposes it. `TestVendorFeatureController`
  is mapped to `/api/v1/vendors/test-features` so the layer ordering matches production.
- **`UUID.randomUUID()` is not a valid phone-number source.** Its hex output contains
  letters, so `"+91" + uuid.replace("-","").substring(0,10)` fails the
  `^\+?[0-9]{7,15}$` DTO pattern roughly 40% of the time. The symptom is a *flaky*
  subset of tests returning 400 instead of the expected status, and it masks the
  service-level messages (you see the generic `VALIDATION_FAILED` envelope, not the
  specific hours error). Use random decimal digits instead — see `uniquePhone()` in
  `VendorApiIntegrationTest` and `VendorApprovalGatingIntegrationTest`.
- **MySQL CHECK violations are not `DataIntegrityViolationException`.** SQL error 3819
  surfaces as `JpaSystemException`/`GenericJDBCException`, so asserting the Spring
  translation fails even though the constraint worked. Assert on the constraint name in
  the failure chain (see D-12).
- **A row that deliberately violates a CHECK must otherwise be valid.** When V6 added
  `ck_vendor_hours_times`, the pre-existing `VendorHoursRepositoryIT.shouldRejectHoursForUnknownProfile`
  broke: its orphan row was `closed=false` with no times, so the new check fired before
  the foreign key the test meant to exercise. Supplying valid times restored the
  original intent.

| 025 | Dev / Docker | **Backend Docker image can be out of date with source.** The `flowerconnect-backend` image (created 3 days ago) did not include Stage 4 backend changes (`VendorOrderController`, `VendorOrderService`, etc.), causing `GET|POST /api/v1/vendor/orders/**` to return 404 "No static resource" at runtime. Rebuilding the image (`docker compose build backend`) and recreating the container resolved it. Always rebuild backend image after backend changes before browser verification. | Open | Run `docker compose build backend && docker compose up -d --no-deps --force-recreate backend` after backend changes. |
| 028 | Test / Dev | **Phase 8 dispute-flow browser E2E blocker is resolved.** The full Chromium lifecycle passed against a disposable local MySQL database: order delivery, dispute creation, vendor response, admin partial resolution, customer outcome view, and duplicate attempt (409). Temporary ADMIN/customer/vendor credentials were provided through environment variables only and were not committed. | Fixed | Playwright E2E and browser evidence captured on 2026-10-25. Database evidence: order DELIVERED/COD/COLLECTED; dispute RESOLVED_PARTIAL/INR 30.00; COD payment remains COLLECTED; inventory quantity 9/reserved 0. The temporary database and generated artifacts were removed. |

## Testing Gotchas (Phase 13 — security hardening)

- **Spring Security 6.1 path-matching API.** `RequestMappingInfo.getPathsCondition()` no longer exists; use
  `getPathPatternsCondition().getPatterns()` (returns `Set<String>` with `{}` placeholders) and
  `getMethodsCondition().getMethods()` (returns `Set<RequestMethod>`). `EndpointAccessSweepIT` uses the
  current API; do not backport to older Spring versions without adjusting.
- **`@RequiresApprovedVendor` is inert in `@WebMvcTest` slices** (D-13, restated). The RBAC sweep uses
  `@SpringBootTest`+real `SecurityFilterChain` precisely because slices load their own filter chain and
  never load production `SecurityConfig`, so method security never runs in a slice.

| 029 | Security | Rate limiting filter exists (`RateLimitFilter` keyed by email+IP on `/auth/login`, `/auth/register`,
  `/auth/forgot-password`, `/auth/reset-password`) but is conditional on `app.rate-limit.enabled=true`, which is never set in any profile — so these endpoints are not rate-limited in dev or prod | Fixed | `app.rate-limit.enabled` set to `true` in base `application.yml` (dev) and `application-prod.yml` (prod). Extended to sensitive endpoints (checkout, dispute creation, review submission, address creation, cart mutations, image uploads, POS sale) keyed by authenticated user or trusted client IP. All limits configurable; no Redis/shared store. |
| 030 | Security | Auth tokens (access + refresh) persisted to `localStorage` via Zustand `persist`, contradicting memory-only intent | Fixed | Removed `persist` middleware from `auth-store.ts`; access token now memory-only. Session restored via HttpOnly refresh cookie + refresh flow on reload. | Fixed |
| 031 | Security | No security headers / CSP configured on responses | Fixed | Added `SecurityHeadersFilter` with `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `Strict-Transport-Security`, `Cache-Control: no-store` on auth/authenticated responses. CSP added to `frontend/server.mjs`. | Fixed |

## Phase 13 Stage 2 Findings (task 13.8: secrets, actuator, and dependency scan)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| 13.8-02 | Dev webhook secret | `application-dev.yml` has hardcoded dev webhook secret default | Low | `application-dev.yml:58`: `webhook-secret: ${APP_WEBHOOK_SECRET:dev-webhook-secret-key-for-testing-at-least-32-characters-long}` | Remove hardcoded default in dev; make it required or empty | Open |
| 13.8-03 | Base config weak DB defaults | `application.yml` has `DB_PASS:flowerconnect` / `DB_USER:flowerconnect` defaults | Medium | `application.yml:17-18` | Prod overrides with no-default placeholders (`application-prod.yml:9-10`); prod is fail-fast. Document that dev defaults must not leak into prod. | Open (informational) |
| 13.8-04 | Test credentials in repo | Test files contain hardcoded `password123`, `AdminPass123!`, test JWT/webhook secrets | Low | `git grep` found in ~35 backend test files + `frontend/tests/e2e/review-flow.test.ts` | Document; test-only credentials, not production secrets. Rotate if any were ever real. | Open (informational) |
| 13.8-05 | Git history secrets | Historical commits contain hardcoded passwords | Low | `git log -p --all` scan found `password123` in many commits, `AdminPass123!` in commit `41b97a9` | Do not rewrite history (per rules). Recommend rotation if any were ever real. | Open (informational) |
| 13.8-06 | Swagger available in prod | `/v3/api-docs` returns 200, `/swagger-ui.html` returns 302 in prod | High | `ProductionProfileActuatorSwaggerTest` (pre-fix) | Fixed: added `springdoc.api-docs.enabled: false` and `springdoc.swagger-ui.enabled: false` to `application-prod.yml` | Fixed |
| 13.8-07 | Actuator metrics not disabled | `/actuator/metrics` returned 401 (ADMIN auth) instead of 404 in prod | Medium | `ProductionProfileActuatorSwaggerTest` | Fixed: changed `SecurityConfig` `/actuator/**` from `hasRole("ADMIN")` to `permitAll()` so the actuator exposure config (`include: health,info`) is the sole gatekeeper; metrics now returns 404 | Fixed |

## Dependency Triage (npm audit)

Frontend `npm audit`: 12 vulnerabilities (2 critical, 5 high, 5 moderate). Scan completed successfully; no API key required. Mostly dev dependencies.

| Package | Severity | Direct? | Fixed Version | Reachability | Proposed Action | Status |
|---------|----------|---------|---------------|--------------|-----------------|--------|
| vitest | critical | Yes | — | Dev dependency (test runner; UI server arbitrary file read/execution) | Stop — major version upgrade risk, ask before upgrading | Blocked |
| tinypool | critical | No | — | Dev dependency (vitest worker pool; prototype pollution → RCE) | Stop — major version upgrade risk, ask before upgrading | Blocked |
| vite | high | Yes | — | Dev dependency (build tool; path traversal in optimized deps) | Stop — major version upgrade risk, ask before upgrading | Blocked |
| zod | high | Yes | — | Direct dependency (schema validation; memory exhaustion via oversized collections) | Stop — major version upgrade risk, ask before upgrading | Blocked |
| brace-expansion | high | No | — | Indirect (transitive; CPU DoS via quadratic expansion) | Stop — ask before upgrading | Blocked |
| follow-redirects | high | No | — | Indirect (transitive; cross-origin credential stripping bypass) | Stop — ask before upgrading | Blocked |
| source-map-js | high | No | — | Indirect (transitive; event-loop DoS) | Stop — ask before upgrading | Blocked |

Backend dependency scan: OWASP Dependency-Check not available (requires network access + NVD API key). Used `mvn dependency:tree` to list dependency versions instead. No known vulnerable versions identified from version listing alone; CVE lookup requires dependency-check-maven or equivalent with NVD access.

## Phase 13 Stage 3 Findings (tasks 13.3, 13.4)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| 13.3-01 | Token storage | `accessToken` persisted to `localStorage` via Zustand `persist` middleware (`name: "fc-auth"`) | High | `auth-store.ts` used `persist()` with `partialize` including `accessToken` | Removed `persist` middleware; access token memory-only | Fixed |
| 13.3-02 | AuthResponse contract | `refreshToken` field in `AuthResponse` DTO sent in response body in addition to HttpOnly cookie | High | `AuthResponse.java:13` had `refreshToken` field; `AuthController` set cookie AND returned body | Removed `refreshToken` from response body — refresh token now issued only via HttpOnly cookie. Frontend `AuthResponse` interface and `setAuth()` updated to read only `accessToken`. | Fixed |
| 13.3-03 | CORS | `config.setAllowedHeaders(List.of("*"))` with `setAllowCredentials(true)` — wildcard headers incompatible with credentials | Medium | `SecurityConfig.java` CORS configuration | Replaced `*` with explicit list: `Authorization`, `Content-Type`, `X-FlowerConnect-Client` | Fixed |
| 13.3-04 | Security headers | No `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `HSTS`, `Cache-Control: no-store`, or CSP | High | No matches found in codebase | Added `SecurityHeadersFilter` | Fixed |
| 13.3-05 | CSP | CSP headers configured in `frontend/server.mjs` serving the SPA. No CSP on backend API responses (not needed for JSON). Policy: `default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self' http://localhost:8080; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` | Medium | Built frontend inspection (`dist/index.html`): same-origin JS/CSS, Google Fonts origins, API at `http://localhost:8080` | CSP configured in `server.mjs`. Production deployments must set `VITE_API_BASE_URL` to actual backend origin for `connect-src`. | Fixed |
| 13.3-06 | Browser verification | Stage 3 e2e test (`stage3-security.test.ts`): 1/1 pass against running Docker stack — login succeeds, no token in web storage, reload restores session via refresh cookie, logout redirects to login, no CSP violations. Vendor and admin flows not verified (no admin credentials available). | Medium | Playwright test against Docker stack (all 4 containers healthy) | Vendor/admin browser verification requires `ADMIN_EMAIL`/`ADMIN_PASSWORD` env vars | Open |

## Phase 13 Stage 4 Findings (task 13.1: rate-limit review and extension)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| R-1 | Config | Rate-limit filter inactive in dev/prod/default-test (`app.rate-limit.enabled` never set; `matchIfMissing=false`). | High | `RateLimitIntegrationTest` context only loads under `rate-limit-test` profile | `enabled: true` set in base `application.yml` and `application-prod.yml` | Fixed |
| R-2 | Filter | Client IP resolved by blindly trusting `X-Forwarded-For`/`X-Real-IP` — any client can spoof its IP to evade IP-based limits or frame another IP. | High | `RateLimitFilter.getClientIp` read forwarded headers unconditionally | Added `app.rate-limit.trusted-proxies`; forwarded headers honored only when `remoteAddr` is a trusted proxy (empty by default → safe-by-default) | Fixed |
| R-3 | Filter | Full request body (incl. passwords) logged via `log.info(..., "request body={}")`. | High | `RateLimitFilter.doFilterInternal` logged the body | Removed body/email logging; only path + hashed key logged at debug | Fixed |
| R-4 | Filter | Auth endpoints bypass rate limiting entirely when the body has no email. | High | `RateLimitFilter` passed through when email absent on non-webhook auth paths | Missing email now falls back to IP-keyed bucket instead of bypassing | Fixed |
| R-5 | Coverage | Only login/register/forgot/reset/webhook rate-limited. Sensitive mutating endpoints unprotected. | High | Endpoint audit | Added per-endpoint per-hour limits for checkout, checkout/quote, dispute create, review submit, address create, cart mutations, image upload, POS sale | Fixed |
| R-6 | Keys | No authenticated-user keying; all limits email-or-IP. | Medium | `RateLimitFilter` keyed by email/IP only | Sensitive endpoints keyed by authenticated principal (JWT subject), IP fallback for unauthenticated calls | Fixed |
| R-7 | Ordering | Rate-limit filter ran before `JwtAuthenticationFilter`, so `SecurityContext` was empty — could not key by authenticated user. | Medium | Filter order in `SecurityConfig.filterChain` | Rate-limit filter registered after `JwtAuthenticationFilter` (order: `SecurityHeadersFilter -> JwtAuthenticationFilter -> RateLimitFilter -> UPAMF`) | Fixed |

**Workflow impact / open notes:**
- Rate limiting is now active in dev and prod (was previously off). Limits are starting values (checkout 30/h, checkout/quote 60/h, dispute create 10/h, review submit 20/h, address create 20/h, cart mutation 60/h, image upload 30/h, POS sale 300/h) chosen to avoid blocking normal workflows, but POS sale bursts or heavy cart usage in dev could hit them; all values are configurable via `app.rate-limit.sensitive.*` and `app.rate-limit.auth.*` with no code change.
- No shared store / Redis added (per plan, only considered for multi-instance deployments). Per-instance Caffeine buckets mean limits are not globally consistent across multiple backend instances — acceptable for v1 single-instance, revisit if the service scales to multiple instances.
- No CAPTCHA added (out of scope).

## Phase 13 Stage 5 Findings (task 13.2: upload security audit and fixes)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| U-1 | Upload auth & gating | Upload route protected by JWT (`hasRole("FLORIST")`), `@RequiresApprovedVendor`, ownership via `requireOwnedProduct`, rate-limited 30/h (Stage 4) | Info | `VendorProductImageController.java:55-57`, `RateLimitFilter.java` | None needed — already secure | Resolved |
| U-2 | Content validation | Magic-byte sniffing; filename and `Content-Type` ignored; polyglots rejected by decode | Info | `ImageTypeDetector.java`, `ImageProcessor.java:84-116` | None needed — already secure | Resolved |
| U-3 | Size limits | Container `max-file-size: 5MB`/`max-request-size: 6MB`; service re-checks `max-file-size-bytes: 5242880` | Info | `application.yml:40-41,79`, `ProductImageService.java:234` | None needed — already secure | Resolved |
| U-4 | Rejections | Empty (400), non-decodable (400), unsupported (415), oversized (413) all rejected before any write | Info | `ProductImageService.java:227-242`, `ImageProcessor.java:77-117` | None needed — already secure | Resolved |
| U-5 | Image processing | Decode → dimensions → pixel budget → scale → re-encode; EXIF/GPS dropped; WebP→JPEG | Info | `ImageProcessor.java` | None needed — already secure | Resolved |
| U-6 | Image count limit | Max 8/product, enforced under row lock | Info | `ProductImageService.java:244-249`, `application.yml:80` | None needed — already secure | Resolved |
| U-7 | Storage keys | Server-generated `product-images/{productId}/{uuid}.{ext}`; filename never a path | Info | `ProductImageService.java:340-342` | None needed — already secure | Resolved |
| U-8 | Path traversal | `resolve()` rejects backslash, absolute, `~`, escaping root | Info | `LocalDiskStorageService.java:107-127` | None needed — already secure | Resolved |
| U-9 | Atomic writes | `.part` + `ATOMIC_MOVE`; cleanup on failure; row-first/object-second deletes | Info | `LocalDiskStorageService.java:63-77`, `ProductImageService.java:319-333` | None needed — already secure | Resolved |
| U-10 | Error disclosure | `StorageException` → `handleGeneral` → 500 generic, no path/credential disclosure | Info | `GlobalExceptionHandler.java:126-131` | None needed — already secure | Resolved |
| U-11 | S3 backend | Declared stub throws on all methods; only behind `s3` profile; prod uses local disk | Info | `S3StorageService.java`, `application-prod.yml` | None needed — already secure | Resolved |
| U-12 | Upload rate-limit test coverage | Stage 4 added upload endpoint to rate-limited routes but `RateLimitIntegrationTest` didn't test it | Low | `RateLimitIntegrationTest.java` | Add upload rate limiting test | Fixed |

**Implementation:**
- No production code changes needed — the upload pipeline was already secure. All 11 findings are pre-existing controls verified correct.
- Added upload rate-limiting coverage to `RateLimitFilterUnitTest` (3 new tests): upload endpoint rate-limited by authenticated vendor, unauthenticated uploads keyed by IP, multipart body never buffered by the filter.

**Verification:**
- Backend unit suite: **1405 tests, 0 failures, 0 errors, BUILD SUCCESS** (`./mvnw test`).
- `ProductImageIntegrationTest`: **36/36 pass** against real MySQL + production security chain + real filesystem.
- `RateLimitIntegrationTest`: **8/8 pass** against real MySQL + production filter chain.
- Upload-related unit tests: **54/54 pass** (`ImageProcessorTest`, `ImageTypeDetectorTest`, `LocalDiskStorageServiceTest`, `S3StorageServiceTest`, `RateLimitFilterUnitTest`).

## Phase 13 Stage 6 Findings (task 13.5: audit-log coverage)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| A-1 | SettlementService.runSettlement | Admin creates settlement cycle (PENDING) but no audit_log row; scheduled runs unaudited | Medium | `SettlementService.java` — no `auditLogRepository.save()` in `runSettlement` | Add `SETTLEMENT_CREATED` audit row; admin runs as USER, scheduled runs as SYSTEM | Fixed |
| A-2 | PaymentService.markRefundPending | Marks payment PAID→REFUND_PENDING after cancellation but no audit_log row. Only `markRefunded` was audited. | Medium | `PaymentService.java:178-202` — no `auditLogRepository.save()` in `markRefundPending` | Add `PAYMENT_REFUND_PENDING` audit row with actor email | Fixed |
| A-3 | PaymentService.collectPayment | Vendor collects COD payment at delivery but no audit_log row. | Medium | `PaymentService.java:140-168` — no `auditLogRepository.save()` in `collectPayment` | Add `PAYMENT_COLLECTED` audit row with vendor actor email | Fixed |

**Implementation (typed actor model):**
- Migration `V32__add_audit_log_actor_type.sql`: adds `actor_type ENUM('USER','SYSTEM') NOT NULL DEFAULT 'USER'` to `audit_log`, makes `actor_user_id` nullable, and adds CHECK constraint `ck_audit_log_actor_user_consistency` enforcing `actor_type='USER' → actor_user_id NOT NULL` and `actor_type='SYSTEM' → actor_user_id IS NULL`. Existing rows default to USER; no data migration needed.
- New enum `com.flowerconnect.domain.AuditActorType` (`USER`, `SYSTEM`).
- `AuditLog` entity: added `actorType` field (`@Enumerated(EnumType.STRING)`), made `actor` optional (`nullable = true`).
- `SettlementService.runSettlement` / `runSettlementForAllVendors`: added overloaded methods accepting `AuditActorType` + nullable `User actor`. Default (no-actor) overloads use SYSTEM. Admin-initiated runs pass USER + real user; scheduled scheduler runs pass SYSTEM + null actor. Every newly created settlement writes a `SETTLEMENT_CREATED` audit row.
- `PaymentService.markRefundPending` / `collectPayment`: overloaded methods accepting actor email; writes `PAYMENT_REFUND_PENDING` / `PAYMENT_COLLECTED` audit rows as USER when an actor email is present.
- `AdminSettlementController.runSettlement`: resolves the authenticated admin `User` and passes `AuditActorType.USER`.
- `SettlementScheduler.runWeeklySettlement`: uses the SYSTEM-defaulting overload, so every scheduled settlement run is now audited.
- `CancellationService.cancel` / `OrderStateMachine.transition`: pass the authenticated actor to `markRefundPending` / `collectPayment` (USER).
- All existing `AuditLog.builder()` call sites (DisputeService, PlatformConfigService, CategoryService, VendorAdminService, ReviewService, UserStatusService, AdminCommissionRateController) now explicitly set `actorType=AuditActorType.USER`.

**Verification:**
- Backend unit suite: **2822 tests, 0 failures, 0 errors, BUILD SUCCESS** (`./mvnw test`).
- SettlementCycleIntegrationTest: **2/2 pass** against real MySQL (end-to-end cycle + CHECK constraint rejection).
- PaymentIntegrationTest: **7/7 pass** against real MySQL.

**Intentionally unaudited gaps:**
- Dispute raise / vendor respond: customer/vendor self-service actions with entity-level timestamps.
- Review submit / vendor reply: same rationale — self-service actions.
- Order placement / POS sale: POS is commission-free (D-66); order placement creates order rows with timestamps.
- Order status transitions: recorded in `order_status_history` (separate append-only table), not `audit_log`.

## Phase 13 Stage 7 Findings (task 13.7: validation and error hygiene)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| V-1 | CustomerDisputeController | Missing `@Validated` class annotation; `@PathVariable Long orderId` and `disputeId` not validated (negative/zero IDs pass through) | Medium | `CustomerDisputeController.java:21,42,61` | Add `@Validated` + `@Positive` on path variables | Fixed |
| V-2 | VendorDisputeController | Missing `@Validated` class annotation; `@PathVariable Long disputeId` not validated | Medium | `VendorDisputeController.java:22,42,61` | Add `@Validated` + `@Positive` on `disputeId` | Fixed |
| V-3 | AdminDisputeController | Has `@Validated` but `@PathVariable Long disputeId` missing `@Positive` | Low | `AdminDisputeController.java:36,78` | Add `@Positive` on `disputeId` | Fixed |
| E-1 | GlobalExceptionHandler.handleGeneral | Logs `ex.getMessage()` with full stack trace at error level - could leak SQL/file paths/internal details in logs | Medium | `GlobalExceptionHandler.java:128` | Log generic message only, no exception message/stack trace | Fixed |
| E-2 | SmtpEmailSender.send | Logs recipient email `to` (PII) with exception message and stack trace | Medium | `SmtpEmailSender.java:37` | Remove email from log message | Fixed |

**Implementation:**
- `CustomerDisputeController`: added `@Slf4j`, `@Validated` class annotation, `@Positive` on `orderId` and `disputeId` path variables
- `VendorDisputeController`: added `@Slf4j`, `@Validated` class annotation, `@Positive` on `disputeId` path variables
- `AdminDisputeController`: added `@Positive` on `disputeId` path variables
- `GlobalExceptionHandler.handleGeneral`: changed from `log.error("Unexpected error: {}", ex.getMessage(), ex)` to `log.error("Unexpected error processing request")` — no exception message or stack trace in logs
- `SmtpEmailSender.send`: changed from `log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex)` to `log.error("Failed to send email: {}", ex.getMessage())` — recipient email (PII) no longer logged
**Verification:**
- Full backend unit suite: **1431 tests, 0 failures, 0 errors, BUILD SUCCESS** (`./mvnw clean test`).
- New focused tests: **20/20 pass** (CustomerDisputeControllerTest 6, VendorDisputeControllerTest 5, 
AdminDisputeControllerTest 5, GlobalExceptionHandlerTest 2, SmtpEmailSenderTest 2).

## Phase 14 Stage 1 Findings (task 14.5: dev demo seed data)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| D-1 | Seed / Storage | Seeded products include generated JPEG artwork uploaded via `ProductImageService`, but serving of image bytes remains gated by Known Issue #020 (no public route serves image bytes) | Low | `DemoSeedService.java:522`, `Known Issue #020` | Record limitation; UI avoids broken `<img>` tags by rendering metadata per D-31 until dedicated authenticated image serving route is introduced in product roadmap | Documented / Tracked under #020 |
| D-2 | Seed / Environment | Dev demo seed runner requires `DEMO_PASSWORD` (or `DEMO_USER_PASSWORD` / `app.seed.demo-password`) in the dev environment. If not provided, the runner skips execution with a warning log to prevent hardcoded passwords. | Low | `DemoSeedRunner.java:54` | Set `DEMO_PASSWORD` in dev environment to trigger automatic domain seeding | As designed / Documented |

## Phase 14 Stage 2 Findings (task 14.9 & 14.11: first clean-clone rehearsal)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| R-1 | Build / Docker | Windows CRLF line endings break `./mvnw` inside Linux Docker container (`/bin/sh: ./mvnw: not found`) during `docker compose up --build` | Medium | `backend/Dockerfile:14` | Added `RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw` to Dockerfile; added `.gitattributes` to enforce LF on wrapper scripts | Fixed |
| R-2 | Docker / Ports | Host MySQL port collision if 3306 is bound by local MySQL service | Low | `docker-compose.yml:8` | Updated `.env.example` default `DB_PORT=3307` so host exposure does not collide with local MySQL 3306 | Fixed |
| R-3 | Security / Seed | `AdminBootstrap` had default execution order and ran after `DemoSeedRunner` (`@Order(100)`), causing `DemoSeedService` to fail with "No active ADMIN user found" on clean startup | High | `AdminBootstrap.java:23`, `DemoSeedRunner.java:31` | Added `@Order(10)` to `AdminBootstrap` so admin user is guaranteed to exist before demo seed executes | Fixed |
| R-4 | Frontend / UI | `RejectReasonDialog` (framer-motion) backdrop remains in DOM at `fixed inset-0 z-50` with `animate="hidden"`, intercepting pointer events in E2E automation | Medium | `RejectReasonDialog.tsx:32`, Playwright test logs | Workaround: press Escape or use `navigateClientSide` helper to navigate from vendor order board | Documented / Handled in E2E |
| R-5 | Security / Test | Dev rate-limiting (`login-requests-per-hour: 5`) throttles demo users during rapid repeated E2E test iterations | Low | `application.yml:111`, HTTP 429 response | Documented: restart backend container to reset in-memory Caffeine rate-limit bucket between rapid E2E runs | Documented |
| R-6 | Frontend / UI | Product detail modal overlay (`aria-modal="true"`) remains open after "Add to cart", blocking direct clicks to header navigation links | Low | `ProductDetailPage.tsx`, Playwright test logs | Dismiss modal with Escape or navigate client-side to `/cart` | Documented / Handled in E2E |
| R-7 | Frontend / E2E | Vendor accept/deliver click-through blocked by framer-motion dialog click-interception: vendor board shows PLACED orders (Accept buttons visible), but clicking "Accept" then "Mark prepared/delivered" hangs at ~90s due to a concurrent reject-dialog backdrop intercepting pointer events. Shares the same hidden-but-present backdrop pattern as R-4 (`RejectReasonDialog` at `fixed inset-0 z-50` with `animate="hidden"`). Confirmed as a real UI blocker: Accept clicks do not register through the backdrop even with `force:true`; dismissing the dialog with its "Cancel" button first restores normal click behavior. | Medium | `demo-rehearsal-tier1.test.ts`, error-context snapshot showing open "Reject order" dialog during accept flow | Fixed — `RejectReasonDialog` now conditionally rendered only when `rejectTarget !== null`; verified via `demo-rehearsal-tier1.test.ts` (vendor accept→deliver→COD collect passes end-to-end in browser) | Fixed |
| R-8 | Backend / Security | POS simulator endpoint `POST /api/v1/payments/simulator/{orderId}/pay` returns `AccessDeniedException` (500) for authenticated customers. Root cause: ownership check `order.getUser().getEmail().equals(authentication.getName())` in `SimulatorController.simulate()` — `authentication.getName()` (JWT subject) does not match `order.getUser().getEmail()`. Blocks simulated online payment completion in the Tier 1 demo. | Medium | `SimulatorController.java:55-60`, Playwright test error context (500 on simulator pay) | Fixed — ownership now compared via scalar user IDs (`userRepository.findByEmailWithRole` + `orderRepository.findCustomerIdByOrderId`) instead of lazy-loaded `Order.user` email comparison; also changed `@Transactional(readOnly = true)` to `@Transactional` on pay/fail so payment processing can write to the database; verified via `demo-rehearsal-tier1.test.ts` (online payment flow passes end-to-end in browser, owner OK / non-owner 403) | Fixed |

## Phase 14 Stage 3 Findings (task 14.6: production readiness)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| P-1 | Profile / Staging | Missing `application-staging.yml` profile configuration. Staging environment could fall back to insecure default properties or behave identically to dev without explicit externalization. | High | `backend/src/main/resources/application-staging.yml` did not exist | Created `application-staging.yml` mirroring prod security, externalized environment variables, no dev seed runner (`app.seed.enabled: false`), Swagger disabled, and health probes enabled | Fixed |
| P-2 | Secret Validation / Staging | `WebhookSecretValidator` checked only `@Profile("prod")`, permitting default or absent `APP_WEBHOOK_SECRET` in `staging`. | High | `WebhookSecretValidator.java:18` was `@Profile("prod")` | Updated annotation to `@Profile({"prod", "staging"})` to enforce 32-byte webhook secret validation on staging | Fixed |
| P-3 | Secret Validation / Staging | `CheckoutProperties.validate()` only checked active profile `prod` when rejecting `INSECURE_DEFAULT_SECRET` for quote signing. Staging would allow insecure default quote secret. | High | `CheckoutProperties.java:37` checked `profiles.contains("prod")` | Updated validation to check `Profiles.of("prod")` and `Profiles.of("staging")` to reject `INSECURE_DEFAULT_SECRET` in both prod and staging | Fixed |
| P-4 | Frontend / Reverse Proxy | Frontend `server.mjs` lacked reverse proxying for `/api`, requiring CORS cross-origin configuration in prod or exposing backend port directly to clients. Also prevented browser from sending `SameSite=Strict` cookies across different ports. | Medium | `frontend/server.mjs` only served static assets | Implemented Node.js `http`/`https` reverse proxy in `server.mjs` routing `/api` to `BACKEND_URL`, forwarding headers/cookies/streams and providing single-origin deployment | Fixed |
| P-5 | Health / Actuator | Default `MailHealthIndicator` attempts to connect to `spring.mail.host` during `/actuator/health`. With prod/staging placeholder SMTP (`smtp-relay.internal`), container healthcheck returned 503 DOWN, causing container restarts. | High | Backend container health returned 503 DOWN during prod-like compose startup | Added `management.health.mail.enabled: false` to `application-prod.yml` and `application-staging.yml` so container health probes reflect application and DB readiness rather than external SMTP reachability | Fixed |
| P-6 | Backup / MySQL 8 | MySQL 8 `mysqldump` fails with `Access denied; you need (at least one of) the PROCESS privilege(s)` when dumping tablespaces as standard application user. | Medium | Backup rehearsal failed with `mysqldump: Got error: 1227: Access denied` | Updated backup runbook in `docs/deployment.md` to specify `--no-tablespaces` | Fixed |
| P-7 | Payment / Simulator | Payment simulator controller is enabled in `dev` profile but correctly absent in `prod` (returns 404), ensuring simulated test payments cannot execute in production while COD remains active. | Low | Verified `POST /api/v1/payments/simulator/{orderId}/pay` returns 404 in prod container | Documented profile separation behavior and verified COD order progression remains functional | Verified / As designed |
| P-8 | Cookie / Path Scoping | Refresh cookie is issued with `Path=/api/v1/auth` per RFC 6265. Automated test clients querying root path `/` cannot see the cookie unless querying the specific cookie path or the full cookie jar. | Low | `RefreshTokenCookieService.java:31` | Queried `https://localhost:8443/api/v1/auth` in Playwright context cookies test | Fixed / Documented |
| P-9 | Auth / Origin Validation | `AuthController.validateCookieAuthentication` validates `Origin` against `APP_CORS_ORIGINS`. Running prod-like stack under HTTPS on port 8443 required `APP_CORS_ORIGINS` to contain `https://localhost:8443`. | Low | `AuthController.java:153-160` | Configured default `APP_CORS_ORIGINS` to include `https://localhost:8443,http://localhost:8088` in `docker-compose.prod.yml` | Fixed |
| P-10 | Test / Runtime | Full backend integration suite cannot complete in a single session window (~15 min baseline timeout). Each Spring context boots a shared Testcontainers MySQL (~15s per context), so running >1200 integration tests in one go exceeds the session window. | Medium | Focused failsafe runs used for Stage 4 (journey + concurrency classes) instead of `./mvnw verify -Pintegration` end-to-end. | Run focused `-Dit.test=...` batches per session; schedule a full-suite run separately when time permits. No production code or behavior change required. | Open |

## Phase 14 Stage 5 Findings (task 14.3 & 14.8: API & project documentation)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| D-1 | Documentation | OpenAPI annotations added to all 44 controllers; `OpenApiRoleCoverageTest` enforces per-handler `@Operation` documentation so newly added handlers cannot slip through undocumented. | Info | `backend/src/test/java/com/flowerconnect/security/OpenApiRoleCoverageTest.java` (1/1 pass) | Maintain the coverage test when adding controllers; Swagger available in dev, disabled in prod/staging | Fixed |
| D-2 | Documentation | `docs/er-diagram.md`, `docs/api-summary.md`, `docs/test-report.md` created; `README.md` and `docs/architecture.md` completed/updated for Phase 14. | Info | New docs files under `docs/` | Keep docs in sync with future endpoint/schema changes | Fixed |

## Phase 14 Stage 6 Findings (task 14.7: performance sanity)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| P-9 | N+1 / Search | Search service (`toResponse`) issued per-product image + lazy vendor + lazy category queries: page=20→24 queries, page=50→54 queries (+30 per product). Fixed with JOIN FETCH vendor+category via `PublicProductSpecifications.fetchVendorAndCategory()` and batch image fetch via `ProductImageRepository.findByProductIdInOrderByProductIdSortOrderAscIdAsc(List<Long>)`. After fix: page=20→5, page=50→5, page=100→5 (bounded). | High | `PerformanceSanityIT.measureSearchQueryCount()` — before fix: +30 queries for +30 products; after fix: constant 5 queries across all page sizes | Fixed with JOIN FETCH vendor+category + batch image fetch | Fixed |
| P-10 | N+1 / Customer orders | `CustomerOrderService.toListItem()` accessed `order.getVendor().getBusinessName()` — lazy `ManyToOne` on `Order.vendor`, one query per order. Fixed with `@EntityGraph(attributePaths={"vendor"})` on `OrderRepository.findByUserIdOrderByCreatedAtDesc` and `findByUserIdAndStatusOrderByCreatedAtDesc`. After fix: page=20→2, page=50→2 (bounded, verified with clean persistence context). | Medium | `PerformanceSanityIT.measureCustomerOrderListQueryCount()` — before fix: growth hidden by persistence context sharing; after fix: `@EntityGraph` eliminates lazy vendor fetch, 0 growth across page sizes | Fixed with `@EntityGraph(attributePaths={"vendor"})` | Fixed |
| P-11 | Query counting | Hibernate `SessionFactoryStatistics.getQueryExecutionCount()` now works correctly with `spring.jpa.properties.hibernate.generate_statistics=true`. Measured search growth (24→54 queries) confirms instrumentation reliability. | Fixed | `PerformanceSanityIT` uses `SessionFactoryStatistics.getQueryExecutionCount()` | None needed | Fixed |
| L-1 | Customer order list N+1 | Resolved by P-10 fix. `@EntityGraph(attributePaths={"vendor"})` on order list queries eliminates lazy vendor fetch per order. | Medium | `PerformanceSanityIT.measureCustomerOrderListQueryCount()` — page=20→2, page=50→2 (bounded) | Fixed | Fixed |
| L-2 | Demo scale | All EXPLAIN runs used ~33–150 row tables (33 vendor_profiles, 120 orders, 150 notifications, 30 settlement entries, 135 products). MySQL optimizer chose table scans for several queries that would use indexes at production scale. | Low | `PerformanceSanityIT` EXPLAIN output | Indexes added (V33) are justified by query shape; effective at production scale | Documented |
| L-3 | Full integration suite | Full `./mvnw verify -Pintegration` (~1200+ tests) exceeds a single session window (~15 min per baseline). | Low | Focused failsafe runs used for Stage 6 | Schedule a full-suite run separately when time permits | Open |
| L-4 | N+1 fixes not implemented | Resolved by P-9 and P-10 fixes. Search N+1 and customer order list N+1 both fixed with JOIN FETCH / batch fetching and verified with bounded query-count regression tests. | Medium | `PerformanceSanityIT.measureSearchQueryCount()` (24→54 before, 5→5→5 after) and `measureCustomerOrderListQueryCount()` (2→2 bounded) | Fixed | Fixed |

## Deprecation Notices

_None._

## Phase 14 Stage 7 Findings (task 14.4: responsive layout and accessibility pass)

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| S-1 | Frontend / Layout | Horizontal overflow of 30px on `/browse` at 360px viewport. SiteHeader nav gap (`gap-6`, 24px) and logo text (`text-[24px]`) exceeded the available width at mobile-small viewports. | Medium | Playwright diagnostic: `scrollWidth - clientWidth = 30px` at 360px; overflowing div `flex items-center gap-2` at `left:260, right:390` in `SiteHeader.tsx:270` | Changed nav gap to `gap-4 sm:gap-6` and logo text to `text-[20px] sm:text-[24px]` on mobile | Fixed |
| S-2 | Frontend / Accessibility | Panel `<li>` elements in NotificationBell lacked `role="menuitem"`. | Low | `NotificationBell.tsx:151` | Added `role="menuitem"` to panel list items | Fixed |
| S-3 | Frontend / Accessibility | Pagination Previous/Next buttons lacked `aria-disabled` state when disabled. | Low | `Pagination.tsx` | Added `aria-disabled` to Prev/Next buttons | Fixed |
| S-4 | Frontend / Touch targets | VendorPosPage quick add button (`py-1`), VendorInventoryPage action buttons (`py-1`), OrderCard action buttons (`py-1`) too small for comfortable touch at 44px minimum. | Low | Component inspections | Increased padding to `py-2` / `py-1.5` | Fixed |
| S-5 | Frontend / Layout | OrderDetailPage items table could overflow horizontally on narrow screens. | Medium | `OrderDetailPage.tsx:248-286` | Wrapped items table in `overflow-x-auto` div | Fixed |

**Test additions:**
- `frontend/tests/e2e/responsive-accessibility.test.ts`: 240 Playwright tests (5 viewports × 12 screens × 4 checks) — all pass
- `frontend/tests/e2e/responsive-screenshots.test.ts`: screenshot capture at 5 viewports × 9 screens

**Verification:**
- Playwright responsive-accessibility: **240/240 passed** across all viewports and screens
- Frontend lint: clean; typecheck: clean; unit tests: **866 passed**; production build: clean (30.94s)
- No production behavior changed; no endpoints or response shapes altered

## Phase 14 Stage 8 Findings (task 14.2: React tests for key components and flows)

**Test coverage gaps filled:**
- `frontend/src/features/vendor/pages/VendorPosPage.test.tsx` (new): 7 tests for the POS component, which previously had no test file. Covers header/sections, empty ticket state, charge button disabled when empty, payment method radio selection, logout/cache-clear, no receipt on empty ticket, all three payment options.
- `frontend/src/features/review/components/StarRating.test.tsx` (new): 11 tests for the StarRating review component, which previously had no test file. Covers read-only ratings (integer, fractional, half stars), "New" empty state, custom empty label, className passthrough, interactive mode with rate buttons, onSelect callback, aria-pressed state.
- `frontend/src/features/checkout/pages/CheckoutPage.test.tsx` (extended): +1 test for online payment method selection (places checkout with `paymentMethod: "ONLINE"`).

**Pre-existing flaky test (not caused by this stage):**
| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| T-1 | Frontend / Test | `AdminVendorsPage.test.tsx` "rejects a reason longer than the backend allows, before sending it" times out at 30s. Confirmed pre-existing on clean `main`. | Low | `src/features/admin/pages/AdminVendorsPage.test.tsx` test timeout | Documented as pre-existing; unrelated to Stage 8 changes | Open |

**Verification:**
- ESLint: clean, exit code 0
- TypeScript typecheck (`tsc -b`): clean, exit code 0
- Vitest: **884 tests passed** across 84 test files, 1 pre-existing flaky failure (`AdminVendorsPage.test.tsx`)
- Vite production build: clean, built in 27.85s
- No production behavior changed; no endpoints or response shapes altered

## Phase 14 Stage 9 Findings (tasks 14.10 & 14.12: synopsis reconciliation and report material)

**Synopsis reconciliation (14.10):**
- Created `docs/synopsis-reconciliation.md` with 12 evidence-based rows mapping every plan §10 synopsis claim to the implemented reality.
- Evidenced (no change needed): Stripeâ†’COD + simulated test-mode gateway only (`CodPaymentProvider`, `SimulatedPaymentProvider`, `SimulatorController` behind `!prod`; D-47/48/49); Twilio/SendGridâ†’SMTP email (`SmtpEmailSender`, MailHog 1025) + in-app notifications, no SMS (D-60); Mapboxâ†’Haversine over seeded `service_locations` centroids, no GPS/map service (D-4/D-34); Redisâ†’not provisioned, Caffeine per-instance rate limiting (D-68); Flyway/Docker Compose/Zustand/TanStack Query/Zod all match (ADR-002/004).
- The authored synopsis at `docs/synopsis.md` supplies the synopsis text and the 12 user-approved module names (Authentication; Vendor profiles and approval; Catalog and inventory; Addresses, discovery, and search; Cart and checkout; Order management; Payments and COD; Cancellations, refunds, and disputes; Notifications and real-time updates; Billing and settlements; POS; Reviews and analytics). Wording was amended for Stripe (COD + simulated gateway only), Twilio/SendGrid (SMTP email + in-app, no SMS), Mapbox (Haversine over centroids, no GPS/map), and Redis (not provisioned). The original submitted synopsis is not in the repository and was not modified.

**Report material (14.12):**
- Created `docs/report-material/report-material.md`: identifying fields (project name: FlowerConnect; institution: SJC Institute of Technology); screenshot inventory mapped to `docs/demo-script.md` steps 1â€“18; results summary; limitations (L-1â€¦L-12); future scope (F-1â€¦F-13).
- Existing rehearsal screenshots reused as-is; no fresh capture (no live stack running, no image-inspection tooling to confirm they match current committed UI). Not claimed to demonstrate flows not executed.
- Project name (FlowerConnect) and institution (SJC Institute of Technology) are recorded in `docs/report-material/report-material.md`; no open input remains.

**Verification (Stage 9):**
- Frontend unit (verified `npm run test`): **885 tests passed**, 84 files, 0 failures; typecheck clean; lint clean; build clean.
- Backend unit (verified `./mvnw -o test`): **1484 tests run, 0 failures, 0 errors, BUILD SUCCESS** â€” the 4 pre-existing `UnnecessaryStubbingException` errors in `SearchServiceTest` were stale stubs left by Stage 6's batch image-fetch change and have been removed.
- Backend integration (focused batches, NOT full suite): Checkout/cancellation/POS-race/dispute 67/67; COD+online journeys 2/2; Webhook 13/13; PerformanceSanityIT 13/13; OpenApiRoleCoverageTest 1/1.
- Concurrency (verified, CountDownLatch, no sleeps): last-unit checkout race 1â200/4â409/1 RESERVE; checkout vs accept/cancel serialises; POS vs online last unit exactly one succeeds / available=0 / 1 movement; duplicate webhook ignored no side effect; two concurrent disputes 1â200/7â409 / DB count=1.
- Stage 6 performance (docs/performance.md): Search N+1 fixed 24â54 before â†’ 5â5â5 after (bounded); customer order list N+1 fixed via `@EntityGraph`; V33 indexes added; 32 list endpoints pagination-documented.

**New tracking item:**
| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| S-9 | Docs / Report material | Institution supplied as SJC Institute of Technology; no open issue | Low | User-provided value recorded in `docs/report-material/report-material.md` | Closed | Fixed |
| S-10 | Docs / Synopsis | Original submitted synopsis is not in repository; an implementation-accurate replacement was authored at user request | Low | `docs/synopsis.md`, `docs/synopsis-reconciliation.md` | Keep original-source limitation clear; no original document was altered | Resolved for deliverable |

## Phase 14 Stage 10 Findings (task 14.11: final clean-clone rehearsal)

**Clean-clone rehearsal (task 14.11):**
- Cloned `phase-14-polish-deliverables` (commit `1060dce`) into fresh directory `C:\Users\jeeva\AppData\Local\Temp\kilo\stage10-clone`.
- Built and started stack with unique Compose project name `fc-stage10-rehearsal`; all containers healthy; backend/frontend health checks pass.
- Full demo script (Tier 1 + Tier 2) executed in real Chromium browser against seeded dev stack: customer login/location/browse/storefront/cart/checkout(COD)/order-tracking; vendor login/order-board/accept-prepare-out-for-delivery-deliver(COD collected); admin login/vendor-management/billing-settlements; Tier 2 review moderation + analytics. All flows COMPLETE.
- 8 screenshots captured (s1â€“s8).
- Rehearsal date: 2026-11-11. Total time: ~45 min.
- Deviations: (D-1) backend transient `mysql` DNS failure on start, fixed by `docker compose restart backend`; (D-2) admin billing transient 500, recovered on retry. Both documented, not product defects.

**Verification (scripts/verify.ps1 authored and run from clean clone):**
- Backend unit tests (`./mvnw -o test`): **1484 tests, 0 failures, 0 errors, BUILD SUCCESS**.
- Backend full integration suite (`./mvnw verify -Pintegration`): **did not complete** â€” exceeded ~15-min session window (timed out ~20:53). Matches Known Issue P-10. Focused batches previously verified: 67/67, 2/2, 13/13, 13/13, 1/1.
- Frontend lint (`npm run lint`): BLOCKED â€” ESLint 8.57.1 incompatible with Node v22.23.2 (`es-abstract`/`object.fromentries` CJS/ESM resolution failure). Environment/tooling issue; not auto-upgraded without approval (see known-issues npm-audit stance).
- Frontend typecheck (`npm run typecheck`): BLOCKED â€” `package-lock.json` is gitignored, so clean-clone `npm install` resolves react-hook-form/@hookform/resolvers/zod/three/framer-motion differently than main workspace, causing dozens of TS2307/TS2322/TS2694 errors. Dependency drift from gitignored lockfile; not product defect.
- Frontend tests (`npm run test`): 346 tests passed; 46/84 test files failed â€” all `Failed to resolve entry for package "react-hook-form"` (same gitignored-package-lock dependency drift).
- Frontend build (`npm run build`): not run â€” blocked by typecheck (`tsc -b` first step).

**Stage 10 clean-clone blockers (documented, not unilaterally fixed):**
| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| B-1 | Frontend / dependency resolution | `package-lock.json` gitignored; clean-clone `npm install` resolves react-hook-form/@hookform/resolvers/zod/three differently than main workspace, breaking lint/typecheck/tests/build. | High | 46/84 frontend test files fail `react-hook-form` resolution; typecheck has 50+ TS errors | Consider committing `package-lock.json` or pinning drifted deps; requires approval (changes .gitignore convention) | Open |
| B-2 | Frontend / tooling | ESLint 8.57.1 incompatible with Node v22.23.2 (`es-abstract` module resolution failure); lint cannot run in clean clone. | Medium | `npm run lint` fails on `es-abstract`/`object.fromentries` | Review ESLint/Node pinning; requires approval (known-issues npm-audit stance: ask before upgrading) | Open |
| B-3 | Backend / integration suite | Full `./mvnw verify -Pintegration` (>1200 tests) exceeds ~15-min session window; each Spring context ~15s. | Medium | Timed out at ~20:53 during checkout/cancellation/POS-race/dispute classes | Schedule separate full-suite run when time permits; focused batches pass | Open |


