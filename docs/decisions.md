# Architecture Decisions

A log of significant architectural and technical decisions made for the FlowerConnect project. Follows the ADR (Architecture Decision Record) format.

---

## ADR-001: Maven Wrapper + Multi-Stage Dockerfile

**Status:** Accepted
**Date:** Phase 0 (Initial scaffold)

### Context

The project needs reproducible builds across developer machines and CI/CD. The backend runs in a Docker container in production but developers need a fast local development loop.

### Decision

- Use the Maven Wrapper (`mvnw`) checked into the repository so all developers and CI use the same Maven version.
- The backend `Dockerfile` uses a multi-stage build:
  - **Stage 1 (base)**: `eclipse-temrin:17-jre-alpine`, copies `mvnw`, `.mvn/`, `pom.xml`, and `src/`
  - **Stage 2 (build)**: Runs `./mvnw clean package -DskipTests`
  - **Stage 3 (runtime)**: `eclipse-temium:17-jre-alpine`, copies the built JAR, runs as non-root user

### Consequences

- Developers can run `./mvnw spring-boot:run` for fast iteration without building the full Docker image.
- The Docker build takes longer (full Maven lifecycle) but produces a minimal runtime image.
- `.mvn/` and `mvnw` are committed; `mvnw.cmd` is gitignored per `.gitignore` (Windows-specific wrapper variant).

---

## ADR-002: Flyway Baseline + Additive Migrations

**Status:** Accepted
**Date:** Phase 0 (Initial scaffold)

### Context

The database schema needs version-controlled migrations. An initial schema (`roles`, `users`) exists as `V1__baseline.sql`.

### Decision

- Use Flyway with `baseline-on-migrate: true` and `baseline-version: 1`.
- The initial schema is `V1__baseline.sql`.
- All future schema changes are additive migrations: `V2__`, `V3__`, etc.
- **Never edit an applied migration** — always write a new one.
- MySQL custom Docker image copies `00-init.sql` to `/docker-entrypoint-initdb.d/` to ensure the database exists before Flyway runs.

### Consequences

- Schema changes are traceable and reversible in principle.
- `validate-on-migrate: true` ensures Flyway fails fast if the schema drifts from migrations.
- `ddl-auto: validate` (not `update` or `create`) means Hibernate will not auto-modify the schema.

---

## ADR-003: JWT Access + Refresh Token Pair

**Status:** Accepted
**Date:** Phase 0 (Initial scaffold)

### Context

The application needs stateless authentication. The `.env.example` already defines `JWT_SECRET`, `JWT_ACCESS_TTL_MS` (15 min), and `JWT_REFRESH_TTL_MS` (7 days). The frontend auth store already supports `accessToken` and `refreshToken` fields.

### Decision

- Use JWT Bearer tokens.
- **Access token**: short-lived (15 min), sent in `Authorization: Bearer <token>`.
- **Refresh token**: long-lived (7 days), stored securely, used to obtain new access tokens.
- JWT secret must be at least 256 bits, configured via `JWT_SECRET` environment variable.
- Spring Security 6 filter chain validates tokens on every request.

### Consequences

- Frontend already stores both tokens in Zustand (persisted via `localStorage`).
- The `api.ts` interceptor already handles 401 responses by redirecting to `/login`.
- Refresh token rotation logic will need to be implemented in Phase 1.

---

## ADR-004: Feature-Sliced Frontend Directory Layout

**Status:** Accepted
**Date:** Phase 0 (Initial scaffold)

### Context

The frontend needs a scalable directory structure that separates concerns as the application grows.

### Decision

Adopt a feature-sliced design:

```
src/
├── app/           # App-level providers, routing, styles
├── pages/         # Route-level components (or colocated in features)
├── features/      # Feature modules (e.g., auth, catalog, orders)
│   └── auth/
│       └── stores/auth-store.ts
├── shared/        # Reusable utilities, API client, components
│   └── lib/api.ts
└── test/          # Test setup and shared test utilities
```

- State management: Zustand for app-level (auth), TanStack Query for server data.
- API calls go through `src/shared/lib/api.ts` (Axios with interceptors).
- Routing via `react-router-dom` v6, with routes defined in `src/app/router.tsx`.

### Consequences

- Each feature is self-contained and independently testable.
- Shared code (API client, hooks) lives in `shared/`.
- New features follow the same pattern: `src/features/<feature-name>/`.

---

## ADR-005: Kilo AGENTS.md + docs/ for Persistent Memory

**Status:** Accepted
**Date:** 2026-09-14

### Context

Kilo AI needs persistent project memory across sessions. The project has no existing agent configuration files.

### Decision

- **`AGENTS.md`** at repository root: permanent coding rules, conventions, and workflow instructions. Written once, rarely changed. Kilo auto-discovers this file.
- **`CLAUDE.md`** at repository root: compatibility redirect to `AGENTS.md`.
- **`docs/` directory**: mutable project memory — `architecture.md`, `progress.md`, `decisions.md`, `known-issues.md`. Updated by the agent after each task. `docs/progress.md` is the ground truth for unfinished work.
- **`kilo.jsonc`** at repository root: Kilo configuration — `instructions` array (auto-loaded files), `agent` definitions (autonomous-engineer, backend-engineer, frontend-engineer), `command` definitions (build-check, self-maintain), and `permission` rules (denies `.env*` reads, asks for git push/rebase/reset).
- `.kilo/agent/*.md` and `.kilo/command/*.md` were attempted but fail YAML frontmatter validation in this Kilo CLI build. Agent and command definitions are consolidated in `kilo.jsonc` instead.

### Consequences

- New Kilo sessions auto-load `AGENTS.md` + `docs/*.md` via the `instructions` array in `kilo.jsonc`.
- `AGENTS.md` is write-protected by Kilo — changes require user approval. This enforces stability of core rules.
- `docs/*.md` files are editable by the agent — they stay current with project state.
- `kilo.jsonc` is validated against the Kilo JSON schema — invalid configs are rejected.
- `AGENTS.md` is kept concise (~80 lines) to minimize context consumption for limited/free models.

---

## ADR-006: Optimized for Free/Limited Models via OmniRoute

**Status:** Accepted
**Date:** 2026-09-14

### Context

The project uses Kilo AI with Claude models through OmniRoute, including free or rate-limited model endpoints. Limited models have small context windows and may exhaust iterations or produce inconsistent results on large tasks.

### Decision

- **`kilo.jsonc` `instructions` array** loads all essential docs at session start — no wasted exploration time.
- **`docs/progress.md`** is the primary recovery point — checked at the start of every session via the Session Startup Checklist.
- **Autonomous engineer agent** has `steps: 20` max iterations and `temperature: 0.2` for deterministic, focused output.
- **Agent prompts are trimmed** to concise bullet points (not paragraphs) — the full rules live in `AGENTS.md` and `docs/` to avoid duplicating large instructions in every prompt.
- **Subagents** (backend-engineer, frontend-engineer) have scoped permissions — they can only edit files in their domain, preventing unwanted cross-contamination and reducing context noise.
- **Workflow commands** (`/build-check`, `/self-maintain`) are self-contained templates that run a specific, bounded sequence of actions.
- **Commands use `agent: autonomous-engineer`** so slash commands launch the right agent automatically.

---

## ADR-006: Three-Layer Backend Architecture

**Status:** Accepted
**Date:** Phase 0 (Initial scaffold)

### Context

The backend needs a clean, maintainable architecture that separates concerns.

### Decision

- **Controller layer**: Thin REST controllers handling HTTP, validation, and DTO mapping. No business logic.
- **Service layer**: Business logic, transaction management, coordination between repositories.
- **Repository layer**: Spring Data JPA repositories for database access.
- DTOs mapped via MapStruct (annotation processor).
- Lombok used for boilerplate reduction (`@Slf4j`, `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`).
- All controllers annotated with `@Validated` and return `ResponseEntity<?>` with proper HTTP status.

### Consequences

- Controllers are thin and focused on request/response handling.
- Services contain transactional business logic.
- Repositories abstract database access.
- Test naming convention: `*ServiceTest`, `*ControllerTest`, `*RepositoryTest`.

## D-8: Registration duplicate-email behaviour

**Status:** Accepted
**Date:** Phase 1

### Context

A user who already has an account and tries to register again needs to be told,
or they get a confusing failure. A specific response reveals whether an email is
registered, which is user enumeration. Login and forgot-password can avoid this
by returning identical responses; registration cannot without a more complex
flow (such as emailing the existing owner instead of responding), which is out
of scope.

### Decision

Registration returns 409 Conflict with a specific message when the email is
already in use (the same applies to a duplicate phone). This is an accepted UX
trade-off. The "no user enumeration" requirement applies to login and
forgot-password only.

### Consequences

- Better UX: a user who already has an account is told so and can log in or
  reset the password.
- Registration reveals whether an email or phone is registered; this is accepted.
- Login and forgot-password must not leak account existence. The section 14
  acceptance criterion is scoped to those two endpoints.
- Rate limiting (task 1.11) is the mitigation to consider against bulk probing.

---

## D-9: Migrations rewritten before first deployment

**Status:** Accepted
**Date:** Phase 1

### Context

The project uses Flyway with `baseline-on-migrate: true` and an additive-only migration rule (ADR-002). Before the first deployment, the initial schema may need to be corrected — adding columns, constraints, or tables that were missing from V1. The question is whether to fix these issues by editing V1 or by writing a new migration.

### Decision

Migrations are rewritten before the first deployment. The additive-only migration rule (ADR-002) applies from the first deployment onward. Before deployment, V1 through the current version can be replaced entirely. After the first deployment, all migrations are additive — never edit an applied migration.

### Consequences

- Before deployment: schema corrections are straightforward — replace the migration files and let Flyway apply the corrected schema from scratch.
- After deployment: corrections must be additive — new V[n+1]__ migrations that fix issues introduced by earlier files. Editing applied migrations is forbidden.
- Flyway `validate-on-migrate: true` ensures schema drift is caught immediately if the migration files and database state diverge.

---

## D-10: Injectable Clock for testable time

**Status:** Accepted
**Date:** Phase 0 (Finalize)

### Context

`JwtService`, `RefreshTokenService`, and `RefreshToken` previously called `LocalDateTime.now()` / `new Date()` directly, making token-expiry logic non-deterministic in unit tests. Time-dependent security checks (token expiration, refresh-token reuse windows) must be controllable in tests.

### Decision

- Provide a `Clock` bean (`Clock.systemUTC()`) via `ClockConfig`, injected into `JwtService`, `RefreshTokenService`, `GlobalExceptionHandler`, and used in `JwtAuthenticationFilter` for request timestamps.
- `JwtServiceTest` uses `Clock.systemUTC()` directly (not a fixed clock) because the `jjwt` `parseClaimsJws` validates token expiry against the real system clock — a fixed clock in the past would cause `ExpiredJwtException` on parsing.
- `RefreshTokenServiceTest` uses `@Mock Clock` with `FIXED_TIME` so that token expiry dates in test data align with the mocked clock's `LocalDateTime.now(clock)`.
- - `TestClockConfig` (`@TestConfiguration` with `Clock.fixed(Instant.parse("2025-01-15T10:00:00Z"), UTC)`) is `@Import`-ed by `@WebMvcTest` classes, providing a deterministic fixed-clock `Clock` bean for `GlobalExceptionHandler` timestamps.

### Consequences

- All time-dependent logic is injectable and testable.
- `GlobalExceptionHandler` injects `Clock` (resolved via `@MockBean` in `@WebMvcTest` contexts).
- `ErrorResponse` and `BusinessException` carry an `ErrorCode` enum value rather than a raw HTTP status, allowing the exception handler to map `ErrorCode` → `HttpStatus` centrally.
- `ErrorResponse.errorCode` serialised as JSON `code` via `@JsonProperty("code")` so error bodies expose a `code` field consistently across the entry point, access-denied handler, and exception handler.

---

## D-11: Vendor role stays `FLORIST` (no `VENDOR` rename)

**Status:** Accepted
**Date:** Phase 2c

### Context

Plan v2.2 section 1.1 seeds the roles table as `CUSTOMER`, `VENDOR`, `ADMIN`, but the same
plan explicitly allows the existing name: "If the codebase names the vendor role differently
(e.g. `FLORIST`), use the code's name consistently everywhere." The `roles` table was seeded
as `FLORIST` in V3 and `FLORIST` is already referenced by Phase 1 auth code, the Phase 2b
vendor-profile tests, and the seeded role data.

Renaming the role would require a data migration over `users.role_id`, plus coordinated
changes to the security config, every role-boundary test, and the role assertions in the
frontend auth store — all to reach the same behaviour under a different string.

### Decision

Keep `FLORIST` as the application role name for vendors and use it consistently. The API
surface uses the vendor vocabulary (`/api/v1/vendors/**`, `vendor_profiles`), so only the
role *constant* differs from the plan's wording. No rename migration is written.

### Consequences

- `FLORIST` is the role name to use in `SecurityConfig` matchers, tests, and any future
  frontend route guards.
- Plan text and code will differ on this one identifier; `docs/rules.md` records the
  convention so it is not read as drift.
- If a `VENDOR` rename is ever wanted, it must be an additive migration updating the seeded
  `roles` row and every referencing `users.role_id`, not an edit to the applied V3 migration
  (ADR-002, D-9).

---

## D-12: Vendor invariants enforced in both the service and the database

**Status:** Accepted
**Date:** Phase 2c

### Context

Phase 2b created `vendor_profiles` and `vendor_hours` without range constraints, deferring
the invariant rules to Phase 2c. Those invariants (positive prep time, non-negative money,
chronological opening hours) must hold for any writer, not just HTTP callers going through
`VendorService` — scheduled jobs, admin tooling, and direct data fixes all write to these
tables.

### Decision

Validate in two places, deliberately:

- **DTO (`@Valid` + Bean Validation)** shapes and bounds of one request payload.
- **Service** cross-field rules that need the database or span rows: duplicate weekdays,
  `close_time > open_time`, service-location resolution, status-transition legality.
- **MySQL CHECK constraints (V6)** backstop so the invariant holds for non-HTTP writers.

The CHECK constraints are the authority for storage integrity; the service returns 400 with a
specific message before the database is ever reached, so clients get a useful error instead
of a constraint name.

### Consequences

- Adding a new invariant means touching the DTO, the service, and (where it is a storage
  rule) a new additive migration. V6 is additive; applied migrations are never edited.
- MySQL reports a CHECK violation as SQL error 3819, which Hibernate surfaces as
  `JpaSystemException`, **not** `DataIntegrityViolationException`. Tests that assert on these
  constraints must assert the constraint name from the failure chain rather than assuming the
  Spring translation — `VendorApiIntegrationTest.assertCheckViolation` is the reference
  helper.
- Because constraints fire at the database, tests that deliberately violate one must supply
  otherwise-valid row data, or the constraint under test may not be the one that fires first.

---

| ADR | Title                       | Type   | Status   | Date |
|-----|-----------------------------|--------|----------|------|
| 001 | Maven wrapper + thin Dockerfile | Decision | Accepted | Phase 0 |
| 002 | Flyway baseline + additive migrations | Decision | Accepted | Phase 0 |
| 003 | JWT access/refresh token pair | Decision | Accepted | Phase 0 |
| 004 | Feature-sliced frontend layout | Decision | Accepted | Phase 0 |
| 005 | Kilo AGENTS.md + docs/ for persistent memory | Decision | Accepted | Phase 0 |
| 006 | Optimized for free/limited models via OmniRoute | Decision | Accepted | Phase 0, revised |
| --- | ---                         | ---    | ---      | ---  |
| 003 | Three-Layer Backend Architecture | Decision | Accepted | Phase 0 |
| D-8 | Registration duplicate-email behaviour | Decision | Accepted | Phase 1 |
| D-9 | Migrations rewritten before first deployment | Decision | Accepted | Phase 1 |
| D-4 | No GPS � locations are chosen from a seeded area table | Decision | Accepted | Phase 2a |
| D-6 | A suspended vendor is hidden from discovery immediately, but in-flight orders continue | Decision | Accepted | Phase 2c |
| D-10 | Injectable Clock for testable time | Decision | Accepted | Phase 0 (Finalize) |
| D-11 | Vendor role stays `FLORIST` (no `VENDOR` rename) | Decision | Accepted | Phase 2c |
| D-12 | Vendor invariants enforced in service and database | Decision | Accepted | Phase 2c |
| D-13 | Approval gating is per-handler, not per-namespace | Decision | Accepted | Phase 2c |
| D-14 | Admin user status changes are self-targeting-protected and always revoke tokens | Decision | Accepted | Phase 2d |
| D-15 | The vendor area is one profile resource, edited through full-replacement PUTs | Decision | Accepted | Phase 2d |
| D-16 | The admin UI derives backend rules instead of inventing transitions | Decision | Accepted | Phase 2d |

## D-13: Approval gating is per-handler, not per-namespace

**Status:** Accepted
**Date:** Phase 2c (Task 2.7)

### Context

Plan task 2.7 requires that only `APPROVED` vendors may use catalog/order endpoints and appear in
discovery, and that a suspended vendor is hidden immediately while in-flight orders continue (D-6).

Two constraints pull in opposite directions. Role-based authorization is already configured at the
URL namespace level: `SecurityConfig` maps `/api/v1/vendors/**` to `hasRole("FLORIST")`. A
`PENDING_APPROVAL` vendor holds that role — the role is granted at registration, before approval —
so the namespace rule cannot express "approved". Widening it instead (e.g. a rule that covers only
some vendor sub-paths) would also lock a pending vendor out of `GET|PUT /api/v1/vendors/profile`,
which they must be able to use to see why they are not approved and to fix their application.

No catalog, inventory or order endpoint exists yet, so there is also nothing in the codebase to
attach a rule to, and Task 2.7 explicitly must not invent those APIs.

### Decision

Gating is expressed per handler, and every rule lives in one class.

- `VendorApprovalGuard` is the single owner of the "is this vendor approved?" rule. It reads
  `vendor_profiles.status` from the database on every call, resolves the profile from the JWT
  subject only, and raises `VendorNotApprovedException` (a `VENDOR_NOT_APPROVED` / 403 error) on any
  refusal.
- `@RequiresApprovedVendor` is a composed annotation carrying
  `@PreAuthorize("@vendorApprovalGuard.isApproved(authentication)")`, applied to the handler method or
  the controller class. It is enabled by `@EnableMethodSecurity` on `SecurityConfig`.
- The existing `hasRole("FLORIST")` namespace rule is left exactly as it is. It answers "is this a
  vendor account?"; the annotation answers "may this vendor transact?" Both run, in that order.
- Discovery is gated by `VendorProfileSpecifications.approved()`, a Criteria API predicate that
  every discovery query must compose. `VendorProfileRepository` now extends
  `JpaSpecificationExecutor<VendorProfile>`.

Approval state is deliberately **not** copied into the JWT. A status column read per request means an
admin approval takes effect on the vendor's very next request and a suspension takes effect on the
next request too, with no token re-issue and no cache invalidation to get wrong. The cost is one
indexed `SELECT` per guarded request.

The guard raises a typed exception rather than returning `false`, because a `false` result produces
Spring Security's opaque "Access Denied" body while the thrown exception reaches
`GlobalExceptionHandler` and produces the standard error envelope with an approval-specific `code`.
That keeps a client able to distinguish "not approved yet" from a plain permission failure.

### Consequences

- `SecurityConfig`'s vendor namespace rule still runs first, so a `CUSTOMER` or `ADMIN` is stopped
  with `FORBIDDEN` and never reaches the approval guard. Only a `FLORIST` can be refused with
  `VENDOR_NOT_APPROVED`.
- Every Phase 3+ catalog, inventory and order route must carry `@RequiresApprovedVendor`. Forgetting
  it fails open for that route; this is the main review checklist item when adding vendor features.
- `@RequiresApprovedVendor` is **inert in `@WebMvcTest` slices**, because those slices supply their
  own `SecurityFilterChain` and never load the production `SecurityConfig` that enables method
  security. Gating must therefore be covered by a `@SpringBootTest` (see
  `VendorApprovalGatingIntegrationTest`), not by a slice test. This is a deliberate trade: a global
  `WebMvcConfigurer`/`HandlerInterceptor` would have been pulled into every slice and required
  mocking the guard in six test classes for no extra safety.
- D-6 is honoured by scoping the rule to vendor-feature access and discovery only. Suspension stops
  new catalog, inventory and order access and removes the vendor from discovery; it deliberately does
  **not** block order reads, delivery tracking, or the vendor's own profile route, so in-flight
  orders continue to completion.

---

## D-14: Admin user status changes are self-targeting-protected and always revoke tokens

**Status:** Accepted
**Date:** Phase 2d (Task 2.8)

### Context

Task 2.8 adds `PATCH /api/v1/admin/users/{id}/status`, letting an admin move a user between
`ACTIVE`, `SUSPENDED` and `DISABLED`. Two questions the plan does not answer have to be settled
before the endpoint can be written.

First: can an admin suspend or disable *itself*? The plan only says an admin may change another
user's status. A self-targeting call is the one where a mistake is unrecoverable through the API,
because the acting admin revokes the very credential it is authenticated with and then no admin
route remains reachable for that account.

Second: should reactivating a user (`SUSPENDED` → `ACTIVE`) revoke refresh tokens too? Revoking on
every transition is uniform and simple; exempting reactivations keeps a legitimate user logged in.
The plan requires revocation for `SUSPENDED` and `DISABLED` and is silent on reactivation.

### Decision

- **Self-targeting is refused** with `403 FORBIDDEN`. The target id is compared against the JWT
  subject, so the rule holds for every admin, not just the seeded one. Targeting *another* admin is
  allowed — with one admin account there is no recovery path, but locking that out would make the
  platform unadministrable, and a second admin is created by `AdminBootstrap` or an existing
  registration, not by self-modification.
- **Every** status change revokes all active refresh tokens for the target, reactivation included.
- No transition matrix is enforced. `ACTIVE`, `SUSPENDED` and `DISABLED` are mutually reachable in
  both directions; no same-status or terminal-state rule was invented, because the plan defines
  none and an over-strict rule would block legitimate corrections.
- A nonblank `reason` is required on every change, not only on the punitive ones, so the
  `audit_log` row is always interpretable.
- `UserStatusService` is the single owner of status mutation, token revocation and audit writing.
  `AdminUserService` owns listing only. Neither may be reached from anywhere else.
- The audit row shares the status-change transaction: `saveAndFlush` on the user is followed by
  revocation and the audit insert, so a rolled-back status change leaves no orphaned audit entry
  claiming a transition that did not happen.

### Consequences

- Reactivating a user forces a re-login. This is intentional: a status change is treated as a
  trust boundary crossing, and a token issued before it should not survive it.
- Any future "unsuspend without re-login" requirement is a behaviour change to this decision, not
  an incidental bug fix.
- Listing is a specification query (`UserSpecifications.role(...)` / `.status(...)`) over
  `UserRepository`, now a `JpaSpecificationExecutor<User>`. Page size is clamped to `1..100` and
  sorting is pinned to `createdAt` then `id` so pagination is stable for equal timestamps.
- Integration tests must not assume page 0 contains their own rows: the shared singleton MySQL
  container accumulates users across every IT class in the run. `AdminUserStatusIntegrationTest`
  reads the last page for creation-order assertions and walks all pages for filter assertions.

---

## D-15: The vendor area is one profile resource, edited through full-replacement PUTs

**Status:** Accepted
**Date:** Phase 2d (Task 2.9)

### Context

Task 2.9 builds the florist-facing screens on the Phase 2c/2d API, which exposes exactly two
endpoints: `GET /api/v1/vendors/profile` and `PUT /api/v1/vendors/profile`. The plan asks for a
dashboard, a profile editor, delivery settings, and operating hours. Several things about that API
shape are not obvious from the endpoint list alone, and each one forces a frontend choice.

`PUT` is a full replacement of the profile's editable scalars, not a patch. Sending only the fields
on the visible screen would silently reset every other field to its default. The `hours` field is a
sub-resource with different semantics from the scalars: omitting it means "keep the stored week",
while sending it replaces all seven rows. And `GET /api/v1/locations` returns the service-area list
without ids, so the profile page can display the stored area but cannot offer a picker for it.

### Decision

- **One resource, four views.** The dashboard, profile, settings, and hours screens all read the
  same `["vendor","profile"]` query and all write through the same mutation. There is no separate
  settings or hours endpoint to keep in step, and no screen can show state another screen has
  already invalidated.
- **Every PUT resends the full editable profile.** `buildProfileUpdateRequest(profile, overrides,
  hours)` starts from the fetched profile and layers the edited fields on top, so a partial screen
  cannot blank the parts it does not show. `hours` is passed only by the hours screen.
- **Form state is kept as strings and converted when the payload is built.** This keeps `""`
  distinguishable from `0`, so clearing an optional fee sends `null` rather than `0`, and it avoids
  `NaN` from `valueAsNumber` on an empty number input. Zod bounds mirror the DTO annotations
  (`@Digits`, `@DecimalMin`, `@DecimalMax`, `@Min`, `@Max`) so the client rejects an out-of-range
  value before the network; the backend stays authoritative.
- **The service area is read-only in the UI.** The stored area is shown as resolved text.
  `GET /api/v1/locations` does expose the row id, so this is a scope choice rather than a data
  limit: the florist profile editor was specified as a business-details and settings screen, and
  moving a shop to a different service area is a re-registration concern. The vendor-registration
  wizard is where the area is chosen.
- **The whole vendor area is a single cache key**, cleared on logout, so a second florist signing in
  on the same browser cannot see the previous one's profile.
- **`VENDOR_NOT_APPROVED` (403) is a state, not an error.** The vendor's own profile stays reachable
  while a profile is pending, so the pages render the approval banner and still allow editing the
  application — the same reasoning as D-13, applied to the client.

### Consequences

- A future `PATCH` endpoint or a dedicated `/vendors/hours` resource would replace
  `buildProfileUpdateRequest` and the resend-everything rule. That is a change to this decision, not
  an incidental refactor.
- Validation is duplicated between the DTO annotations and `form-schema.ts`. If a bound changes in
  one place it must change in the other, or the client will either reject valid input or send invalid
  input.
- Because the profile is fetched once and reused as the base for every PUT, a concurrent edit from
  another tab is overwritten rather than merged. Acceptable in v1; it needs a revision/etag to fix
  properly.

---

## D-16: The admin UI derives backend rules instead of inventing transitions

**Status:** Accepted
**Date:** Phase 2d (Task 2.10)

### Context

Task 2.10 builds the admin screens on top of two backend capabilities that were written to
different rules, and the plan does not say which rule the frontend should encode.

`VendorAdminService` (task 2.6) enforces a strict transition table through `requireStatus`:
`PENDING_APPROVAL -> APPROVED | REJECTED`, `APPROVED -> SUSPENDED`, `SUSPENDED -> APPROVED`.
Anything else is a 409. `UserStatusService` (task 2.8) enforces **no** transition matrix at all �
`ACTIVE`, `SUSPENDED` and `DISABLED` are mutually reachable in both directions, and the only rule
is that an admin may not change their own account (403, D-14). So neither "one generic status
picker" nor "one shared transition table" is correct for both screens.

The listings are also paginated and filtered, which means a status change can move a record *out*
of the filter that is on screen or onto a different page.

### Decision

- **Vendor actions are derived, not offered generically.** `vendorActionsFor(status)` in
  `src/features/admin/types.ts` is a direct transcription of the backend's `requireStatus` table,
  and the row renders exactly those buttons. A `REJECTED` profile shows "No further action
  available" because the backend has no route back from `REJECTED`.
- **User status offers every status except the one held.** That is the honest encoding of "the
  backend enforces no matrix"; the only exclusion is the current value, so a button cannot offer a
  no-op.
- **`approve` and `reinstate` collect no reason.** Their routes take no request body at all
  (`AdminVendorController`), and rendering a reason box would imply the backend records one. The
  same shared `ReasonDialog` serves both shapes via a `requiresReason` flag.
- **Every mutation invalidates the whole listing** under `["admin","vendors"]` / `["admin","users"]`
  instead of writing the updated row into the current page's cache. A transition can leave the
  active filter or the current page, so patching one row would leave the screen lying about both.
- **Listings are keyed by their filters** (`["admin","vendors","list",status,page]`) so a filter
  change is a cache miss rather than a stale render, and the broad invalidation still reaches every
  page/filter combination.
- **Self-targeting status change is disabled on the signed-in admin's own row for usability only.**
  The backend check in `UserStatusService` is unaffected, and the UI still renders a 403 correctly
  if a call is attempted regardless.

### Consequences

- The client now holds a second copy of `VendorAdminService`'s transition table. If the backend
  changes a rule, `vendorActionsFor` must change with it or the UI will offer a 409. The table is
  small, lives in one place, and `api.test.ts` asserts it row-by-row.
- Any future endpoint that *does* enforce a user-status matrix will need a matching client table.
  That is a deliberate follow-on, not an incidental fix.
- Invalidating the whole listing costs one extra request per mutation per distinct filter/page key
  in flight. That is cheaper than the alternative being wrong.
- `AdminErrorState` treats 409 as an expected race between administrators ("refresh and try again")
  rather than a fault, because with two admins acting on one profile that is what it usually means.

---

## D-4: No GPS � locations are chosen from a seeded area table

**Status:** Accepted
**Date:** Phase 2a (Task 2.1)

### Context

Customers and vendors both need a location: for delivery address, for delivery radius, and for
geo discovery. The plan's recommended default is **no browser Geolocation API and no GPS** �
a caller picks city / area / pincode from a seeded `service_locations` table for the demo region,
and the coordinates are that area's centroid. This had to be settled before task 2.1, because
vendor registration, vendor addresses and discovery all read coordinates from the same place.

Three options were on the table: browser geolocation (an accurate device fix), free-text address
with a geocoder, or a fixed area list. The first two both need a third-party dependency or a
permission prompt and produce coordinates no two users ever agree on, which makes "is this vendor
within my delivery radius" ambiguous at the boundary.

### Decision

- `service_locations` is the **only** source of coordinates in the system. It is seeded by the
  `V4__service_locations.sql` Flyway migration (Bengaluru demo region, 8 areas) and is read-only
  from the application's point of view � there is no admin CRUD over it in v1.
- No `Geolocation` API call and no geocoding anywhere in the frontend or backend. The browser
  never learns a device position.
- One row per area, unique on `(city, area)`, with a single centroid for `latitude`/`longitude`.
  Coordinates are therefore an **area-level approximation**, not an address or a doorstep position.
- `GET /api/v1/locations` is public (`permitAll`) and returns city ? areas ? pincodes, so the
  picker needs no token; it is also searchable by `pincode` or `area`.
- `vendor_profiles.service_location_id` is the authoritative reference; `latitude`/`longitude` on
  the profile are a **denormalized copy** taken from the centroid at write time so a geo query can
  hit one composite index (`idx_vendor_profiles_status_geo`) instead of joining.
- Customer addresses (Phase 4) will follow the same rule and copy the centroid of the chosen
  `service_location_id`.

### Consequences

- Distance and radius checks are area-level. A vendor can be "2.1 km away" from a customer in the
  same pincode. The alternative is a false precision the data does not support.
- A copy can drift from its source. The rule is that any code changing a profile's
  `service_location_id` re-copies the centroid in the same statement;
  `VendorApiIntegrationTest.anUpdateRecopiesTheCoordinatesWhenTheServiceLocationChanges` asserts it.
- `GET /api/v1/locations` returns the row id on every area, in both the hierarchical and the
  paginated search shape, so a client can address a location directly. The vendor profile editor
  still renders the stored area read-only (D-15); ids being available is not what would change that.
- Growing beyond the seeded demo region means seeding more areas, not adding a geocoder. That is a
  deliberate v1 boundary, revisited if the market leaves the demo region.

---

## D-6: A suspended vendor is hidden from discovery immediately, but in-flight orders continue

**Status:** Accepted
**Date:** Phase 2c (Tasks 2.6, 2.7)

### Context

Plan task 2.7 requires that only `APPROVED` vendors can use catalog/order endpoints and appear in
discovery, and that "a suspended vendor is hidden immediately; in-flight orders continue to
completion" (D-6). Those two halves pull in opposite directions: the first is a hard stop, the
second is an explicit promise *not* to hard-stop anything already in progress.

"Suspension" covers two different situations. A vendor suspended for a conduct problem (fraud,
abuse) should arguably lose access to everything at once. A vendor suspended for an operational
reason � stock exhausted, a seasonal pause, a pending verification � would strand paying customers
whose flowers are already arranged if the suspension also killed their order reads and delivery
tracking. Phase 2 has no way to tell those apart, and the plan does not define a suspension reason
taxonomy, so a single rule has to cover both.

### Decision

Suspension is enforced **on new work and on discovery only**. The rule is expressed as a positive
list of what a non-approved vendor loses, so an unlisted route is not accidentally closed:

| Surface | Suspended vendor | Rationale |
|---|---|---|
| Discovery / geo search | Hidden immediately | The plan's explicit requirement; a suspended vendor must not receive new orders |
| Catalog and inventory writes | Refused (`VENDOR_NOT_APPROVED`) | New work must stop now |
| Order creation | Refused | New work must stop now |
| Existing order **reads** for that vendor | Allowed | D-6: in-flight orders continue to completion |
| Delivery tracking / order status updates | Allowed | D-6: an order cannot complete if the vendor cannot see or advance it |
| The vendor's own `GET\|PUT /api/v1/vendors/profile` | Allowed | See D-13 � the vendor must still be able to read and fix their application |

In code this is `VendorApprovalGuard` behind `@RequiresApprovedVendor` (D-13), which is a
per-handler annotation rather than a URL-namespace rule. No Phase 2 route carries it yet, because
no catalog or order endpoint exists; `VendorApprovalGatingIntegrationTest` proves the behaviour
against a test-probe controller under the real `/api/v1/vendors/**` namespace so that the rule is
already verified for the Phase 3+ routes that will attach it.

Discovery filtering is `VendorProfileSpecifications.approved()`, a Criteria predicate every
discovery query must compose � it is not enforced by the guard, because discovery is a read over
many vendors rather than a request from one.

### Consequences

- **Every Phase 3+ catalog, inventory and order-creation route must carry
  `@RequiresApprovedVendor`.** Omitting it fails open for that route. This is the single main
  review checklist item when adding vendor features, and it is the reason the guard is a named
  annotation rather than an interceptor that might be unregistered.
- **Reads of in-flight orders must deliberately omit the annotation.** This is the half of D-6 that
  is easy to lose: a Phase 3 reviewer who annotates an order-read route "for consistency" has
  silently broken the guarantee that a suspended vendor can finish delivering.
- Status is read from `vendor_profiles` per request and never cached in the JWT, so suspension takes
  effect on the vendor's very next request with no token re-issue (D-13). There is no separate
  "suspended_at" timestamp and no cache to invalidate.
- D-6 is honoured by scoping, not by a permanent state: `reinstate` returns the profile to
  `APPROVED`, and `VendorApprovalGatingIntegrationTest.reinstatementRestoresAccess` asserts access
  is restored immediately.
- A future need to distinguish conduct suspensions from operational ones requires a suspension
  reason taxonomy. That would be a change to this decision, not an incidental refinement.

---

## D-17: Categories are public reference data with admin-managed CRUD

**Status:** Accepted
**Date:** Phase 3a (Task 3.1)

### Context

Plan task 3.1 introduces the `categories` table: a small hierarchical lookup (Roses, Bouquets, Arrangements, Occasions) that is admin-managed CRUD; vendors read and assign products to categories. The read endpoint must be accessible to both vendors (for product assignment) and customers (for browsing/filtering in the future storefront).

Two patterns existed in the codebase:
- `GET /api/v1/locations` is `permitAll()` � public reference data
- `GET /api/v1/vendors/profile` requires `FLORIST` role � vendor-private data

Categories are reference data like locations, not private data like vendor profiles. Making them public aligns with the future storefront (Phase 4.5) where customers browse by category.

### Decision

- `GET /api/v1/categories` is `permitAll()` � public read access, mirroring `/api/v1/locations`
- `GET /api/v1/admin/categories` and all mutations (`POST`, `PUT`, `DELETE`) require `ROLE_ADMIN` via the existing `/api/v1/admin/**` rule in `SecurityConfig`
- The public endpoint returns a flat list of **active** categories, each with `id`, `name`, `slug`, `parentId`, `displayOrder`, so the client can reconstruct a tree if needed

### Consequences

- The public read endpoint only returns `active = true` categories; inactive categories are hidden
- Admin listing (`/api/v1/admin/categories`) returns all categories (active and inactive) with pagination
- Future storefront browsing can call the public endpoint without authentication

---

## D-18: Category deletion is hard delete with child protection

**Status:** Accepted
**Date:** Phase 3a (Task 3.1)

### Context

Categories form a hierarchy (parent ? children). The plan says "admin-managed CRUD". Two options for deletion:
1. Soft delete: set `active = false`, keep row, allow reactivation
2. Hard delete: remove row, refuse if children exist (409 CONFLICT)

The `active` boolean flag already exists for soft hiding. Hard delete with child protection is simpler and matches the "CRUD" wording. The `active` flag provides the soft-hide alternative when an admin wants to temporarily remove a category without losing its children.

### Decision

- `DELETE /api/v1/admin/categories/{id}` performs a **hard delete** (row removed)
- If the category has children (`countByParentId > 0`), the delete is refused with 409 CONFLICT
- To remove a branch, the admin must first delete/reparent all children, or use `PUT` to set `active = false` for a soft hide
- Future product assignment (Phase 3.2) will add a 409 check for products referencing the category

### Consequences

- Admins have two levers: hard delete (permanent, requires no children) and soft hide (reversible, via `active` flag)
- The audit trail records `CATEGORY_DELETED` with the entity id before removal
- Child protection prevents accidental data loss in the hierarchy

---

## D-19: Category slugs are auto-generated with collision-safe suffix

**Status:** Accepted
**Date:** Phase 3a (Task 3.1)

### Context

Categories need a unique, URL-friendly identifier (`slug`). The admin should not have to manually invent unique slugs. The plan for products (task 3.2) mentions "slug (collision-safe suffix)". Categories need the same treatment.

### Decision

- The `slug` field is **not** accepted from the client; it is generated by `CategoryService` from the `name`
- Generation: lowercase, non-alphanumeric collapsed to single hyphen, trimmed (e.g., "Hybrid Tea" ? "hybrid-tea")
- If the slug already exists, a numeric suffix `-2`, `-3`� is appended until unique (e.g., "roses" ? "roses-2")
- The unique constraint on `slug` in the database backs this rule
- Admin can override by updating the name, which regenerates the slug

### Consequences

- The request DTO (`CategoryRequest`) has no `slug` field � the service owns it entirely
- Slug collision handling is deterministic and testable
- The slug is stable unless the admin changes the category name
