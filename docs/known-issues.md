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

## Deprecation Notices

_None._
