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
- **Never edit an applied migration** â€” always write a new one.
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
â”œâ”€â”€ app/           # App-level providers, routing, styles
â”œâ”€â”€ pages/         # Route-level components (or colocated in features)
â”œâ”€â”€ features/      # Feature modules (e.g., auth, catalog, orders)
â”‚   â””â”€â”€ auth/
â”‚       â””â”€â”€ stores/auth-store.ts
â”œâ”€â”€ shared/        # Reusable utilities, API client, components
â”‚   â””â”€â”€ lib/api.ts
â””â”€â”€ test/          # Test setup and shared test utilities
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
- **`docs/` directory**: mutable project memory â€” `architecture.md`, `progress.md`, `decisions.md`, `known-issues.md`. Updated by the agent after each task. `docs/progress.md` is the ground truth for unfinished work.
- **`kilo.jsonc`** at repository root: Kilo configuration â€” `instructions` array (auto-loaded files), `agent` definitions (autonomous-engineer, backend-engineer, frontend-engineer), `command` definitions (build-check, self-maintain), and `permission` rules (denies `.env*` reads, asks for git push/rebase/reset).
- `.kilo/agent/*.md` and `.kilo/command/*.md` were attempted but fail YAML frontmatter validation in this Kilo CLI build. Agent and command definitions are consolidated in `kilo.jsonc` instead.

### Consequences

- New Kilo sessions auto-load `AGENTS.md` + `docs/*.md` via the `instructions` array in `kilo.jsonc`.
- `AGENTS.md` is write-protected by Kilo â€” changes require user approval. This enforces stability of core rules.
- `docs/*.md` files are editable by the agent â€” they stay current with project state.
- `kilo.jsonc` is validated against the Kilo JSON schema â€” invalid configs are rejected.
- `AGENTS.md` is kept concise (~80 lines) to minimize context consumption for limited/free models.

---

## ADR-006: Optimized for Free/Limited Models via OmniRoute

**Status:** Accepted
**Date:** 2026-09-14

### Context

The project uses Kilo AI with Claude models through OmniRoute, including free or rate-limited model endpoints. Limited models have small context windows and may exhaust iterations or produce inconsistent results on large tasks.

### Decision

- **`kilo.jsonc` `instructions` array** loads all essential docs at session start â€” no wasted exploration time.
- **`docs/progress.md`** is the primary recovery point â€” checked at the start of every session via the Session Startup Checklist.
- **Autonomous engineer agent** has `steps: 20` max iterations and `temperature: 0.2` for deterministic, focused output.
- **Agent prompts are trimmed** to concise bullet points (not paragraphs) â€” the full rules live in `AGENTS.md` and `docs/` to avoid duplicating large instructions in every prompt.
- **Subagents** (backend-engineer, frontend-engineer) have scoped permissions â€” they can only edit files in their domain, preventing unwanted cross-contamination and reducing context noise.
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

The project uses Flyway with `baseline-on-migrate: true` and an additive-only migration rule (ADR-002). Before the first deployment, the initial schema may need to be corrected â€” adding columns, constraints, or tables that were missing from V1. The question is whether to fix these issues by editing V1 or by writing a new migration.

### Decision

Migrations are rewritten before the first deployment. The additive-only migration rule (ADR-002) applies from the first deployment onward. Before deployment, V1 through the current version can be replaced entirely. After the first deployment, all migrations are additive â€” never edit an applied migration.

### Consequences

- Before deployment: schema corrections are straightforward â€” replace the migration files and let Flyway apply the corrected schema from scratch.
- After deployment: corrections must be additive â€” new V[n+1]__ migrations that fix issues introduced by earlier files. Editing applied migrations is forbidden.
- Flyway `validate-on-migrate: true` ensures schema drift is caught immediately if the migration files and database state diverge.

---

## D-10: Injectable Clock for testable time

**Status:** Accepted
**Date:** Phase 0 (Finalize)

### Context

`JwtService`, `RefreshTokenService`, and `RefreshToken` previously called `LocalDateTime.now()` / `new Date()` directly, making token-expiry logic non-deterministic in unit tests. Time-dependent security checks (token expiration, refresh-token reuse windows) must be controllable in tests.

### Decision

- Provide a `Clock` bean (`Clock.systemUTC()`) via `ClockConfig`, injected into `JwtService`, `RefreshTokenService`, `GlobalExceptionHandler`, and used in `JwtAuthenticationFilter` for request timestamps.
- `JwtServiceTest` uses `Clock.systemUTC()` directly (not a fixed clock) because the `jjwt` `parseClaimsJws` validates token expiry against the real system clock â€” a fixed clock in the past would cause `ExpiredJwtException` on parsing.
- `RefreshTokenServiceTest` uses `@Mock Clock` with `FIXED_TIME` so that token expiry dates in test data align with the mocked clock's `LocalDateTime.now(clock)`.
- - `TestClockConfig` (`@TestConfiguration` with `Clock.fixed(Instant.parse("2025-01-15T10:00:00Z"), UTC)`) is `@Import`-ed by `@WebMvcTest` classes, providing a deterministic fixed-clock `Clock` bean for `GlobalExceptionHandler` timestamps.

### Consequences

- All time-dependent logic is injectable and testable.
- `GlobalExceptionHandler` injects `Clock` (resolved via `@MockBean` in `@WebMvcTest` contexts).
- `ErrorResponse` and `BusinessException` carry an `ErrorCode` enum value rather than a raw HTTP status, allowing the exception handler to map `ErrorCode` â†’ `HttpStatus` centrally.
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
frontend auth store â€” all to reach the same behaviour under a different string.

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
`VendorService` â€” scheduled jobs, admin tooling, and direct data fixes all write to these
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
  Spring translation â€” `VendorApiIntegrationTest.assertCheckViolation` is the reference
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
| D-4 | No GPS — locations are chosen from a seeded area table | Decision | Accepted | Phase 2a |
| D-6 | A suspended vendor is hidden from discovery immediately, but in-flight orders continue | Decision | Accepted | Phase 2c |
| D-10 | Injectable Clock for testable time | Decision | Accepted | Phase 0 (Finalize) |
| D-11 | Vendor role stays `FLORIST` (no `VENDOR` rename) | Decision | Accepted | Phase 2c |
| D-12 | Vendor invariants enforced in service and database | Decision | Accepted | Phase 2c |
| D-13 | Approval gating is per-handler, not per-namespace | Decision | Accepted | Phase 2c |
| D-14 | Admin user status changes are self-targeting-protected and always revoke tokens | Decision | Accepted | Phase 2d |
| D-15 | The vendor area is one profile resource, edited through full-replacement PUTs | Decision | Accepted | Phase 2d |
| D-16 | The admin UI derives backend rules instead of inventing transitions | Decision | Accepted | Phase 2d |
| D-22 | Vendor catalog lives in the vendor namespace; removal is soft | Decision | Accepted | Phase 3c |
| D-23 | Optional listing filters are composed Specifications | Decision | Accepted | Phase 3c |
| D-24 | Stock mutations take a pessimistic row lock, after the ownership check | Decision | Accepted | Phase 3d |
| D-25 | The expiry sweep writes off available stock only, and delists by exclusive transition | Decision | Accepted | Phase 3e |
| D-26 | The storage backend is chosen by profile, and the S3 backend is a declared stub | Decision | Accepted | Phase 3f |
| D-27 | Every accepted image is decoded, scaled and re-encoded by the server | Decision | Accepted | Phase 3f |
| D-28 | The one-primary image rule is enforced under a product-scoped row lock | Decision | Accepted | Phase 3f |
| D-29 | Frontend approval gating reuses the cached vendor profile and withholds the links | Decision | Accepted | Phase 3g |
| D-30 | One stock dialog serves all four mutations, and the rules live in one table | Decision | Accepted | Phase 3g |
| D-31 | Product images are listed as metadata because no route serves the bytes | Decision | Accepted | Phase 3g |
| D-32 | The default-address lock is the first read in the transaction | Decision | Accepted | Phase 4 (task 4.1) |
| D-33 | Boolean DTO properties are named after the state, not the question | Decision | Accepted | Phase 4 (task 4.1) |
| D-34 | Geo discovery API is public and uses bounding-box + Haversine | Decision | Accepted | Phase 4 (task 4.3) |
| D-35 | Full-page cart route accepted over slide-out drawer for multi-vendor layout | Decision | Accepted | Phase 5 (task 5.6) |
| D-36 | The customer catalogue's product detail is an overlay over its parent surface, and standalone on direct entry | Decision | Accepted | Phase 4 (task 4.9) |
| D-37 | The overlay is distinguished by explicit navigation state and a per-page-load token | Decision | Accepted | Phase 4 (task 4.9), implemented |
| D-38 | Location-gated catalogue surfaces versus the storefront's non-blocking area advisory | Decision | Accepted | Phase 4 (tasks 4.6–4.9) |
| D-39 | Customer price bounds are two labelled number inputs, not a range slider | Decision | Accepted | Phase 4 (task 4.7) |
| D-40 | An anonymous add-to-cart redirects to login and does not persist a pending add | Decision | Accepted | Phase 4 (task 4.9) |
| D-41 | The customer cart is fetched only for a customer, and a 401 without a session does not trigger a refresh | Decision | Accepted | Phase 5 (task 5.T, defect D-1) |

## D-13: Approval gating is per-handler, not per-namespace

**Status:** Accepted
**Date:** Phase 2c (Task 2.7)

### Context

Plan task 2.7 requires that only `APPROVED` vendors may use catalog/order endpoints and appear in
discovery, and that a suspended vendor is hidden immediately while in-flight orders continue (D-6).

Two constraints pull in opposite directions. Role-based authorization is already configured at the
URL namespace level: `SecurityConfig` maps `/api/v1/vendors/**` to `hasRole("FLORIST")`. A
`PENDING_APPROVAL` vendor holds that role â€” the role is granted at registration, before approval â€”
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

Second: should reactivating a user (`SUSPENDED` â†’ `ACTIVE`) revoke refresh tokens too? Revoking on
every transition is uniform and simple; exempting reactivations keeps a legitimate user logged in.
The plan requires revocation for `SUSPENDED` and `DISABLED` and is silent on reactivation.

### Decision

- **Self-targeting is refused** with `403 FORBIDDEN`. The target id is compared against the JWT
  subject, so the rule holds for every admin, not just the seeded one. Targeting *another* admin is
  allowed â€” with one admin account there is no recovery path, but locking that out would make the
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
  application â€” the same reasoning as D-13, applied to the client.

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
Anything else is a 409. `UserStatusService` (task 2.8) enforces **no** transition matrix at all —
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

## D-4: No GPS — locations are chosen from a seeded area table

**Status:** Accepted
**Date:** Phase 2a (Task 2.1)

### Context

Customers and vendors both need a location: for delivery address, for delivery radius, and for
geo discovery. The plan's recommended default is **no browser Geolocation API and no GPS** —
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
  from the application's point of view — there is no admin CRUD over it in v1.
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
reason — stock exhausted, a seasonal pause, a pending verification — would strand paying customers
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
| The vendor's own `GET\|PUT /api/v1/vendors/profile` | Allowed | See D-13 — the vendor must still be able to read and fix their application |

In code this is `VendorApprovalGuard` behind `@RequiresApprovedVendor` (D-13), which is a
per-handler annotation rather than a URL-namespace rule. No Phase 2 route carries it yet, because
no catalog or order endpoint exists; `VendorApprovalGatingIntegrationTest` proves the behaviour
against a test-probe controller under the real `/api/v1/vendors/**` namespace so that the rule is
already verified for the Phase 3+ routes that will attach it.

Discovery filtering is `VendorProfileSpecifications.approved()`, a Criteria predicate every
discovery query must compose — it is not enforced by the guard, because discovery is a read over
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
- `GET /api/v1/locations` is `permitAll()` — public reference data
- `GET /api/v1/vendors/profile` requires `FLORIST` role — vendor-private data

Categories are reference data like locations, not private data like vendor profiles. Making them public aligns with the future storefront (Phase 4.5) where customers browse by category.

### Decision

- `GET /api/v1/categories` is `permitAll()` — public read access, mirroring `/api/v1/locations`
- `GET /api/v1/admin/categories` and all mutations (`POST`, `PUT`, `DELETE`) require `ROLE_ADMIN` via the existing `/api/v1/admin/**` rule in `SecurityConfig`
- The public endpoint returns the **active top-level** categories (`parent_id IS NULL AND active = true`), ordered by `display_order` then `id`, each row carrying `id`, `name`, `slug`, `parentId`, `displayOrder`. Top-level rows have a null parent, and the parent fields stay on the DTO so a client that is later served a child row can place it.
- The active predicate lives in one repository method, `findByParentIdIsNullAndActiveTrueOrderByDisplayOrderAscIdAsc`, so there is no unfiltered roots-only query to reach for by mistake. The Phase 3 audit found the public read going through an unfiltered roots query while this decision promised active-only; that method no longer exists.
- The admin listing returns **every** category — roots and children, active and inactive — paginated by `name` then `id`. An absent `parentId` means the whole table, not the roots; a supplied `parentId` means the direct children of that parent. `totalElements`/`totalPages` therefore describe every row an admin manages. The Phase 3 audit found this branch calling a roots-only paged query, which hid admin-created children from ordinary pagination and understated both counts.

### Consequences

- The public read endpoint only returns `active = true` categories; inactive categories are hidden. An inactive parent is not published merely because a child is active
- Only top-level categories reach the public endpoint, so the storefront category chips (plan 4.7) and the vendor product form's category picker are one level deep — see known issue #025
- Admin listing (`/api/v1/admin/categories`) returns all categories (active and inactive) with pagination, including children
- Future storefront browsing can call the public endpoint without authentication
- The public read is unpaged by design (it is a small reference list); the admin listing is paged. Both return the same `CategoryPageResponse` envelope

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
- A category a product still references is **not** guarded by a service check. `fk_products_category` (V8) is the backstop and refuses the delete, but the refusal arrives as a SQL constraint violation rather than a mapped 409 — see known issue #026. The promised "409 for products referencing the category" check was never written, and the Phase 3 audit recorded that as an open gap rather than a settled rule.

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
- If the slug already exists, a numeric suffix `-2`, `-3`… is appended until unique (e.g., "roses" ? "roses-2")
- The unique constraint on `slug` in the database backs this rule
- Admin can override by updating the name, which regenerates the slug

### Consequences

- The request DTO (`CategoryRequest`) has no `slug` field — the service owns it entirely
- Slug collision handling is deterministic and testable
- The slug is stable unless the admin changes the category name

---

## D-20: Enum-typed columns are native MySQL ENUM

**Status:** Accepted
**Date:** Phase 3b (Tasks 3.2–3.4)

### Context

Tasks 3.2–3.4 add two enum-typed fields: `products.status`
(`DRAFT`/`ACTIVE`/`INACTIVE`/`ARCHIVED`) and
`stock_movements.movement_type` (the nine movement types of
plan section 6.2). Both are mapped in the entities with
`@Enumerated(EnumType.STRING)`. The obvious migration column
type is `VARCHAR(32)` with a `CHECK ... IN (...)` constraint —
the pattern used for `movement_type` in the first draft of
V11. That draft failed Hibernate schema validation at startup:

```
Schema-validation: wrong column type encountered in column
[status] in table [products]; found [varchar (Types#VARCHAR)],
but expecting [enum ('DRAFT','ACTIVE','INACTIVE','ARCHIVED')
(Types#ENUM)]
```

Hibernate 6.4 with the MySQL dialect maps
`@Enumerated(EnumType.STRING)` properties to the dialect's
native ENUM type, so `ddl-auto: validate` expects an ENUM
column, not a VARCHAR.

### Decision

Enum-typed entity fields are stored in native MySQL `ENUM`
columns whose values mirror the Java enum constants exactly
(uppercase, same order). This matches the existing convention
already established by `V1__baseline.sql` (`users.status`)
and `V5__vendor_profiles.sql` (`vendor_profiles.status`),
which is why those pass validation. A separate `CHECK ... IN
(...)` constraint on an ENUM column is redundant — the type
system itself rejects unknown values — so none is written.

### Consequences

- An unknown enum value is rejected by the column type (MySQL
  error 1265, "Data truncated for column ..."), not by a named
  CHECK constraint; tests assert the column name in the failure
  chain instead of a constraint name
- ENUM comparison in MySQL is case-insensitive, so `'draft'`
  and `'DRAFT'` are equivalent on write
- Adding a new enum constant requires a new additive migration
  (`ALTER TABLE ... MODIFY COLUMN ... ENUM(...)`) — ENUM value
  lists are part of the column definition
- The `stock_movements` vocabulary is enforced by the ENUM
  column; the draft V11 CHECK constraint was dropped

---

## D-21: Exactly one primary image per product is a service-level invariant

**Status:** Accepted
**Date:** Phase 3b (Task 3.2)

### Context

Task 3.2 requires exactly one primary image per product, and
the plan asks for a database-level guarantee "if
MySQL-compatible, otherwise a documented service-level
invariant". The natural MySQL encoding is a unique key on
`(product_id, is_primary)` filtered to primary rows — but
MySQL has no partial/filtered unique indexes. The usual
workaround is a generated column
(`is_primary_flag INT GENERATED ALWAYS AS (IF(is_primary, 1, NULL))`)
with a unique key on `(product_id, is_primary_flag)`, since
NULLs are ignored by unique keys.

That workaround is incompatible with this schema: the
generated column depends on `is_primary`, and the table also
needs `FOREIGN KEY (product_id) REFERENCES products (id)`.
MySQL rejects any foreign key on a column that a generated
column depends on (error 1215 "Cannot add foreign key
constraint" — the FK's referenced/dependent column set
conflicts with the generated-column dependency), so the
generated-column trick cannot coexist with the required
`product_id` FK. This was verified against a live MySQL 8
container before abandoning the approach.

### Decision

The one-primary-image rule is a **service-level invariant**,
enforced transactionally when image upload lands (plan task
3.8, out of scope for 3.2–3.4): within one transaction,
setting an image primary clears the previous primary for the
same product, and the write is refused if a primary already
exists. The read side is already in place:
`ProductImageRepository.countByProductIdAndPrimaryIsTrue`
lets the service check the rule, and
`findByProductIdOrderBySortOrderAscIdAsc` serves the ordered
image list. The `product_images` table keeps `is_primary` as
a plain `BIT(1)` column with no unique constraint.

This follows the precedent of D-12 (invariants enforced in
both service and database where the database can express
them) — here the database cannot express the rule, so the
service is the sole enforcement point, documented here.

### Consequences

- A second primary image for one product is rejected by the
  service with a 409 when the image API is built; the
  database alone does not prevent it
- The rule is covered by the integration test exercising the
  repository helper and the ordered image query
- If MySQL ever gains filtered unique indexes (or the project
  moves to a database that has them), this decision should be
  revisited and the constraint promoted to the database

---

## D-22: The vendor catalog lives inside the vendor namespace, and removal is soft

**Status:** Accepted
**Date:** Phase 3c (Task 3.5)

### Context

Task 3.5 exposes `products` to vendors. Three things were not settled by the plan and each one
forces a choice that later tasks will inherit.

**Where the routes live.** The plan's prose names `/api/v1/vendor/products`, singular. The existing
vendor routes are `/api/v1/vendors/**` (plural), and `SecurityConfig` maps that whole namespace to
`hasRole("FLORIST")`. A second, parallel namespace would need its own matcher, and two nearly
identical prefixes is exactly the kind of near-miss that produces a rule attached to the wrong one.

**How a product is removed.** Phase 3b made a product's images and inventory cascade on a hard
delete, and `ProductService` had a `delete` that did exactly that. But `stock_movements` is an
append-only log: deleting the product a movement refers to makes that movement unreadable and
silently rewrites the meaning of the inventory history. Task 3.8 will add real image uploads and
task 3.6 real stock movements, which is precisely when that history starts to matter.

**Whether lifecycle transitions are policed.** `products.status` has four values
(`DRAFT`/`ACTIVE`/`INACTIVE`/`ARCHIVED`). `VendorAdminService` enforces a strict transition table for
*vendor* approval (task 2.6), but `UserStatusService` (task 2.8) enforces none for *user* status,
and the two were written to different rules. Applying the wrong one of those precedents to products
would either block a vendor correcting their own mistake or refuse to let them retire an item.

### Decision

- **Routes are `/api/v1/vendors/products`.** The singular form in the plan text is treated as a typo;
  the plural namespace is what `SecurityConfig`, the vendor frontend and the docs already use. The
  namespace rule then covers the catalog with no change to `SecurityConfig`, and D-13's layering
  holds with the namespace answering "is this a vendor account?" and `@RequiresApprovedVendor`
  answering "may this vendor transact?".
- **Removal is soft.** `ProductService.deactivate` sets `INACTIVE` and leaves the row, its images
  and its inventory in place. The database cascade remains available, but no product route uses it.
  A vendor can bring a product back through `PUT`, which is what makes `INACTIVE` "hidden" rather
  than "gone".
- **No transition table.** Any of the four statuses may be set from any other, and omitting `status`
  on an update leaves the stored value untouched. The plan defines no product-status matrix, and
  inventing one would stop a vendor fixing a miscategorised listing. This follows the D-14 precedent
  for user status rather than the task 2.6 admin-transition precedent.
- **A product may only be assigned to an active category.** `requireActiveCategory` refuses a
  deactivated category with a 400. Without it a vendor could keep filing products under a category
  the storefront no longer lists. `categories.active` is already a soft flag (D-18); this is what
  makes it enforceable.
- **`base_price` must be greater than zero**, asserted both by `ProductRequest`'s
  `@DecimalMin(inclusive = false)` (so the client gets a field-level 400) and by a service check
  (so a non-HTTP writer cannot store a zero) — the two-layer rule of D-12.

### Consequences

- The catalog is reachable only by a vendor whose profile is `APPROVED`. A suspended vendor loses it
  on their next request, with no token re-issue, because the guard reads the status per request (D-13).
- Deactivating is idempotent and cheap: a single-column update, so the API needs no confirmation
  state and no cascade bookkeeping.
- `ProductService.delete` was renamed to `deactivate`. A hard delete now has no caller at all; if one
  is ever needed it should be an admin-only operation with its own decision, because it invalidates
  stock-movement history.
- A future `ARCHIVED` distinction ("end of life" vs "temporarily hidden") has no API difference from
  `INACTIVE` today. If storefront queries start to treat them differently, revisit whether the update
  endpoint should restrict the values a vendor may choose.

---

## D-23: Optional listing filters are composed Specifications, not a nullable JPQL query

**Status:** Accepted
**Date:** Phase 3c (Task 3.5)

### Context

The vendor product listing takes three optional filters (`status`, `categoryId`, `name`) over a
paged result. Spring Data offers three ways to express that, and the obvious one is a trap.

A hand-written query has to guard each optional parameter:

```
WHERE p.vendor.id = :vendorId
  AND (:status IS NULL OR p.status = :status)
  AND (:categoryId IS NULL OR p.category.id = :categoryId)
```

When Hibernate compiles `:status IS NULL`, the parameter appears on **both** sides of the comparison
— one side against an enum column, the other against a literal `null`. Type inference then has to
come from somewhere, and with no attribute on the null side it resolves from the other one or fails
outright. `status` is a native MySQL `ENUM` (D-20), which makes this worse, not better: the dialect
expects an ENUM-typed parameter and a mis-inferred one surfaces as a Hibernate type error rather
than a useful message.

A derived query method cannot express "absent" at all — Spring Data binds a null argument to
`IS NULL`, which for a filter means "match nothing", the opposite of what an omitted filter should
do. And one derived method per combination would be 2^3 methods to keep in step.

### Decision

Filters are Criteria predicates in `com.flowerconnect.catalog.specification.ProductSpecifications`,
composed onto one mandatory fragment, following the `VendorProfileSpecifications` and
`UserSpecifications` precedent already in the codebase:

- `forVendor(vendorId)` — mandatory, passed first, never optional.
- `withStatus`, `inCategory`, `nameContains` — appended with `Specification#and` only when the caller
  supplied that filter, so an absent filter contributes no predicate at all.
- `nameContains` returns `builder.conjunction()` for a null or blank term, so the predicate is total
  and callers may pass the raw query-string value through unchanged.

`ProductRepository` gains `JpaSpecificationExecutor<Product>`. Sorting stays on the `Pageable`
(`createdAt` then `id`, both descending) rather than in an `ORDER BY` inside a query string, which
keeps pagination stable for rows written in the same instant.

### Consequences

- The predicates carry their own types, so there is no inference to get wrong and no dialect-specific
  ENUM handling in a query string.
- Filter behaviour is Criteria-API behaviour over real SQL, so it is asserted in
  `VendorCatalogIntegrationTest` against MySQL rather than in `ProductServiceTest`. A mocked
  repository cannot demonstrate that `name=TULIP` matches `Yellow Tulip Bunch`; only the database can.
- `nameContains` is a `LIKE '%term%'` and the name column is not indexed, so a broad search scans the
  vendor's own rows. The vendor predicate narrows it first. If search becomes a real requirement, this
  wants a full-text index, not a different query shape.
- A blank `name=` narrows nothing rather than matching nothing. That is a deliberate reading of
  `name=` as a client artefact rather than as an intent to find products with an empty name.

---

## D-24: Stock mutations take a pessimistic row lock, and take it after the ownership check

**Status:** Accepted
**Date:** Phase 3d (Task 3.6)

### Context

Plan section 6.2 prescribes the lock: "pessimistic lock (`SELECT ... FOR UPDATE`) on inventory rows
**ordered by product id** to avoid deadlocks, for every operation above". Task 3.6 is the first phase
that actually writes stock, so the discipline is established here rather than inherited — and the
instruction "unless the existing architecture strongly favors another strategy" asks whether it does.
It does not: this project has no cache in front of the database (Redis was removed in stage 6, plan
v2.2) and no eventual-consistency story, so a read-modify-write against a plain `SELECT` is the only
alternative, and it is a lost-update bug waiting for two concurrent requests.

The concrete race is small and worth naming, because it is what the tests exist to kill. A stock-in
reads `quantity`, adds N, writes `quantity + N`:

```
T1: SELECT quantity -> 10        T2: SELECT quantity -> 10
T1: UPDATE SET quantity = 15     T2: UPDATE SET quantity = 14
```

Final state 14 for two deliveries of 5 units that should have produced 20, and **two** movement rows
each claiming +5. The stock level and its own audit trail would then disagree, permanently, with
nothing to reconcile them. Optimistic locking would detect it (`@Version` and retry) but would still
have written the two movement rows before the collision was noticed, so it moves the problem rather
than removing it.

Three things the plan does not settle, and each one forced a choice here.

**Where the lock sits relative to the ownership check.** A lock must be the *last* thing taken, not
the first. `SELECT ... FOR UPDATE` on another vendor's inventory row would let any authenticated
florist stall that product's stock for as long as it liked, simply by naming a foreign product id in a
loop. Ownership is therefore checked first, from a non-locking read, and the lock is only taken once
the caller is the owner.

**Which writes take it.** The plan says "every operation above" about the stock-movement list, and says
nothing about the two alert settings. They take the lock too, and not for tidiness: Hibernate issues
whole-row `UPDATE` statements, so a threshold write concurrent with a stock change would write back the
*quantity it read before* the change landed, silently undoing a delivery. The lock is what makes a
partial-column write safe here without `@DynamicUpdate`.

**Whether the "ordered by product id" rule applies yet.** Every 3.6 route mutates exactly one product,
so a one-element lock order cannot deadlock. The ordering rule is recorded as binding on the later
multi-product operations (checkout with a multi-shop cart, and the order-accept path) rather than
invented here; there is nothing in this phase for it to order.

### Decision

- **Pessimistic write lock** (`LockModeType.PESSIMISTIC_WRITE`, i.e. `SELECT ... FOR UPDATE`) on the
  inventory row, acquired through the single repository method
  `InventoryRepository.findByProductIdForUpdate`. It was already present from task 3.3; task 3.6 makes
  it the only way the service reaches a row it is about to write.
- **Order inside a mutation is fixed** and identical for every operation, in
  `InventoryService.applyStockChange`: check ownership (unlocked) ? resolve the actor ? lock the
  inventory row ? validate against the locked quantities ? write the new level ? append exactly one
  movement. All five steps are one transaction, so a failure in either write leaves neither.
- **Reads do not lock.** The inventory read, the low-stock list and the movement history are
  `readOnly` transactions: they report a level, they do not change one, and a lock would make a
  dashboard view block the very stock movement it is watching.
- **The two alert settings take the same lock** as the stock mutations, for the whole-row-`UPDATE`
  reason above, even though they write no movement.
- **Availability is recomputed, never clamped.** Every operation computes the candidate
  `quantity` first and refuses it if it would fall below `reserved_quantity`. Clamping would report
  success for a request that was not performed and would leave the level disagreeing with the
  movements that produced it. The refusal is `409 INSUFFICIENT_STOCK`, a dedicated `ErrorCode` rather
  than a generic `CONFLICT`, because "the stock numbers do not allow this" and "this resource already
  exists" call for different client behaviour.
- **The two invariants are checked in the service and in the database** (D-12). The service returns a
  specific 409 before the database is reached; `ck_inventory_reserved_le_quantity` is the backstop for
  any writer that bypasses the service.

### Consequences

- Two stock changes to one product serialise. Two stock changes to *different* products do not: each
  locks only its own row, so the common case (a vendor restocking a whole catalogue) stays parallel.
- Every stock mutation costs one extra `SELECT ... FOR UPDATE`. That is a deliberate payment for
  correctness on the write path, and it is why the read paths stay lock-free.
- `InventoryService` must be reached through the Spring proxy for the lock to be real: a
  self-invocation inside the class would run outside the transaction and the lock would be held by
  nothing. The service is a single bean with no internal call from a `REQUIRES_NEW` path, so this is
  currently safe by construction — a future refactor that calls a `@Transactional` method on `this`
  would silently break the guarantee.
- The lock is verified by `InventoryConcurrencyIntegrationTest`, which runs 6–8 real threads against
  one product and asserts only end state: the final quantity equals the sum of the deltas, the
  movement count equals the number of changes, and with one unit of stock and eight contenders exactly
  one thread succeeds while the other seven receive `INSUFFICIENT_STOCK`. A sequential test cannot
  demonstrate any of this — run one after another, every call sees the previous one's committed level
  and the lock's absence is invisible.
- `reserved_quantity` has no route in this phase (`RESERVE` movements arrive with checkout in Phase 5),
  so the 409 floor is set up in the integration tests by updating the column directly. Without
  reserved units the floor could never bind, and the 409 this API exists to return would be untested.
- A caller that is refused by the floor learns only that the change is impossible, not what the level
  is; it must re-read `GET /inventory` to find out. Returning the current level inside the 409 body
  would help, and is a reasonable follow-on if a client turns out to need it.

---

## D-25: The expiry sweep writes off available stock only, and delists by exclusive transition

**Status:** Accepted
**Date:** Phase 3e (Task 3.7)

### Context

Plan task 3.7 is one line: "@Scheduled job using the injected `Clock`: expired stock ?
`WASTE` movement and product delisted". Four things it does not settle each change what the
job actually writes, and each of them is a place where the obvious implementation silently
corrupts data.

**Which day counts as expired.** Plan section 6.2 defines the sweep's effect but not its
boundary, and the two available readings differ by a day of sellable stock.

**What happens to reserved units.** `inventory` carries `reserved_quantity` for units already
promised to placed-but-unaccepted orders, and section 6.2 requires the vendor's accept to
re-check `quantity >= q` precisely because an expiry write-off may have reduced the level.
`ck_inventory_reserved_le_quantity` (`V10`) forbids `quantity < reserved_quantity`, so a
write-off that took reserved units would either be refused by the database or need the
constraint relaxed.

**Which products get delisted.** `products.status` has four values (D-22) and no transition
table; `ProductService.deactivate` already exists as the vendor-facing `ACTIVE ? INACTIVE`
path, and it resolves a vendor from a JWT subject and is reachable only by an authenticated
caller — neither of which a scheduled job has.

**How a repeated run behaves.** `@Scheduled` runs unattended and repeatedly. A sweep that
selects "every row whose expiry date has passed" re-selects rows it already processed,
forever, and the cheapest way to make it look like progress is to append a `WASTE` movement
per run. The movement log is append-only and is the audit trail of how a level got there
(D-24), so a zero-delta or duplicate movement there is a permanent, wrong claim.

### Decision

- **Expired means `expiryDate < today`, where `today` is `LocalDate.now(clock)` on the
  injected `Clock`.** The stored date is the last day the stock may be used, so stock is
  written off from the following day and is sellable throughout the date itself. Nothing in
  the sweep calls `Instant.now()`; this is the one job whose behaviour has to be provable
  with a fake clock (plan section 3, and the plan's own definition of done for 3.7).
- **Only available stock is written off.** `available = quantity ? reservedQuantity` is
  removed and `quantity` is set to `reservedQuantity`, never below it. Reserved units belong
  to orders that have not been accepted yet; taking them as waste would both break the
  `reserved_quantity <= quantity` invariant and invalidate pending orders the moment they
  were placed. The consequences for an order are already designed for: section 6.2 has the
  vendor's accept re-check the level, and a shortfall is a 409 that the vendor answers by
  rejecting. A write-off that leaves nothing available writes no movement at all — a `WASTE`
  row with a zero delta would be a movement recording no change, which the vendor's own
  `ADJUSTMENT` path refuses to write.
- **Delisting is an exclusive transition: `ACTIVE ? INACTIVE`, and nothing else.** A `DRAFT`,
  `INACTIVE` or `ARCHIVED` product is already off the storefront, so rewriting it would be a
  no-op update that reports a change which did not happen. `ProductService.deactivate` is not
  reused: it needs a JWT subject and it audits a human decision. A vendor who reactivates a
  delisted product while restoring stock and an expiry date in the same request stays listed;
  one who reactivates without replacing the expired date is delisted again by the next sweep,
  because a reachable expiry date means the stock is still expired.
- **The movement has no actor and no reference.** There is no authenticated user behind a
  scheduled job, so `actor_user_id`, `reference_id` and `reference_type` stay null and the
  reason states the expiry date and the sweep date instead. Attributing the write-off to
  whichever admin last touched the product would be a fabricated audit trail.
- **Idempotency comes from the candidate predicate, not from a marker.** Rows are selected
  where the expiry date has passed **and** something still has to change:
  `expiryDate IS NOT NULL AND expiryDate < :today AND (quantity > reservedQuantity OR
  product.status = :activeStatus)`. Processing a row makes it stop matching — the write-off
  drops `quantity` to `reservedQuantity` and the delist moves the product off `ACTIVE`, and
  neither is reversible by the sweep — so the next run has nothing to redo. This is why the
  query joins `products` and compares an enum column: an alternative that selected expired
  rows alone would need a separate "already processed" flag, which is state the sweep would
  have to maintain forever.
- **Selection is unlocked, locking is per row, and every condition is re-checked under the
  lock.** Candidates are read in ascending product-id order and then locked one at a time
  through the existing `InventoryRepository.findByProductIdForUpdate`, which is the lock order
  section 6.2 mandates and the same order the vendor stock path takes (D-24). Between
  selecting a row and locking it a vendor may have corrected the expiry date, adjusted the
  level, or delisted the product, so a row that no longer needs work is skipped rather than
  written to.
- **One sweep is one bounded transaction.** A run handles at most `app.expiry-sweep-max-rows`
  rows and commits once, which keeps the lock window finite; a backlog larger than the cap is
  finished by later runs rather than in one long transaction. This is safe only because of
  the self-clearing predicate above, and it is why the cap is configuration
  (`app.expiry-sweep-max-rows`, `app.expiry-sweep-cron`, overridable as
  `APP_EXPIRY_SWEEP_MAX_ROWS` / `APP_EXPIRY_SWEEP_CRON`) rather than a constant.

### Consequences

- A product's expiry date is the last usable day. A vendor who means "unsellable on this
  date" must store the following day; the boundary is asserted at the service level and
  against MySQL so it cannot drift.
- The sweep never touches reserved units, so an expired product with a pending order shows
  `quantity = reservedQuantity` and `available = 0`. That product is delisted, and its
  in-flight order continues to the accept/reject decision section 6.2 describes — the same
  split D-6 draws for a suspended vendor.
- A single failed row fails the whole sweep's transaction and the next run retries it,
  because the predicate is unchanged. Per-row error isolation was not built: with one row per
  failure per run the sweep converges on its own, and swallowing an exception would hide a
  database-level problem in a job nobody watches.
- Nothing about the sweep is observable over HTTP — no endpoint reports "last swept at" or
  "N products expired". Until one exists, an operator's only signal is the log line. A
  metrics endpoint is a reasonable follow-on, not an omission.
- The test profile disables the schedule (`app.expiry-sweep-cron: "-"`, Spring's
  `CRON_DISABLED`) so a scheduled run cannot fire in the middle of an integration test; the
  sweep is driven directly with the `MutableClock` bean instead. Both the clock and the batch
  cap are restored after each test, because the Spring context is cached and shared across
  the integration suite.

---

## D-26: The storage backend is chosen by profile, and the S3 backend is a declared stub

**Status:** Accepted
**Date:** Phase 3f (Task 3.8)

### Context

Plan task 3.8 asks for "`StorageService` interface: local disk (dev), S3 implementation pluggable by
profile", and plan section 9 repeats it. The interface has to exist before either backend can be
written, and the choice of *how the backend is selected* is not part of the plan. Three mechanisms
were available: a `@ConditionalOnProperty` flag, an abstract factory, or Spring profiles.

The S3 half is the harder question, and the plan does not settle it. This project has no AWS SDK
dependency, no bucket, no region, no credential-resolution story and no environment to test any of
them in. Adding `software.amazon.awssdk:s3` for code that cannot run would bring a large transitive
dependency tree (Netty, the AWS CRT, URL connection clients) into a build whose only purpose at
this point is to be correct.

The failure mode to avoid is specific: an S3 implementation that compiles, activates, logs a put and
stores nothing. A production deployment would then serve `201 Created`, write zero bytes, and the
symptom would surface days later as missing product images on the storefront rather than as a
deploy error.

### Decision

- **`StorageService` knows nothing about images.** It is a flat key/value store —
  `store`, `delete`, `exists`, `describe` — so product images today and any other binary asset later
  share one abstraction, and swapping the backend changes no call site in the domain layer.
- **The key contract belongs to the interface, not to one implementation.** A key is always
  server-generated, `/`-separated and relative, and must resolve inside the backend's root. No
  client-supplied value ever becomes a key: the uploaded filename is kept in
  `product_images.original_filename` for display only.
- **The backend is a profile decision, not a property.** `LocalDiskStorageService` is
  `@Profile("!s3")` and `S3StorageService` is `@Profile("s3")`. A property flag
  (`app.storage.backend=s3`) would be a runtime switch that could point production at a developer's
  disk because an environment variable was wrong; a profile is already the mechanism this project
  uses for dev/prod differences and is set at deploy time.
- **The S3 backend is a declared stub that throws on every method.** It replaces the local bean
  correctly when the profile is active — so the switch is real and every other call site keeps
  working — but `store`/`delete`/`exists`/`describe` each raise `StorageException` with a message
  naming exactly what is missing. Completing it is self-contained: add the SDK v2 S3 dependency, hold
  an `S3Client` bean, implement the four methods against `putObject`/`getObject`/`deleteObject`/
  `headObject`, and add the bucket/region/prefix/credentials properties the stub documents.
- **Local writes are atomic and traversal-checked.** `LocalDiskStorageService` writes to a
  `<name>.part` sibling and moves it into place with `ATOMIC_MOVE`, so a reader never observes a
  half-written object and a crash mid-write leaves a `.part` file rather than a truncated image the
  catalog believes is intact. `resolve` rejects a blank key, a backslash, an absolute or `~`-rooted
  path, and anything that normalises outside the root — four checks because they fail differently,
  and the last one is the check that actually catches `../`.
- **The root is created at startup** (`@PostConstruct`), so a misconfigured or read-only path fails
  on boot rather than on a vendor's first upload.
- **Storage failures are `StorageException` and surface as 500.** They are never converted into a
  4xx: telling a caller "storage failed" invites exactly the retry that an outage makes worse. All
  caller-side validation (ownership, size, format, count) happens before a byte is written, so a
  `StorageException` means the write itself failed and no row should be kept.

### Consequences

- Two of the two backends work in this build. Production runs local disk, and
  `application-prod.yml` documents that the directory does not survive a redeploy — a volume must be
  mounted before real uploads are stored there.
- Activating `s3` in this build fails loudly on the first upload with a 500 naming the gap, instead
  of silently storing nothing. `S3StorageServiceTest` asserts every method throws.
- The interface contract is checked in two places: `LocalDiskStorageServiceTest` covers each
  rejection case against a real temporary directory, and `ProductImageIntegrationTest` covers the
  upload/delete path end to end through HTTP.
- Storage tests touch the filesystem under `backend/target/`, never the configured `uploads`
  directory — the test profile points `app.storage.local-directory` at `target/test-uploads/...`.
- A future backend (S3, CloudFront+CDN, anything) implements four methods and touches nothing else.

---

## D-27: Every accepted image is decoded, scaled and re-encoded by the server

**Status:** Accepted
**Date:** Phase 3f (Task 3.8)

### Context

Plan task 3.8 requires a "type whitelist (JPEG/PNG/WebP) checked by content sniffing, max size,
random filenames, no path traversal, resize/compress". Each of those is a requirement; none of them
says whether the bytes are *stored* or *transformed*, and the plan's word "compress" sits next to
"resize" as if both were optional tidy-ups.

The gap between validating and trusting is where image uploads go wrong. Magic-byte sniffing alone
accepts a polyglot — a file with a valid PNG header and a hostile payload. Decoding alone lets the
JDK's pluggable `ImageIO` readers decide what is acceptable, including formats (BMP, TIFF, GIF, PDF-
rendered-to-bitmap) that have no business in a product catalog. And whatever passes is then stored
verbatim, re-serving whatever metadata the original carried: EXIF GPS coordinates, camera serials,
embedded thumbnails.

Compression matters for a second reason specific to this codebase: `products` and `product_images`
live in MySQL, but the bytes live on disk or in S3, and the storefront will serve them from a
different process than the one that wrote them. Storing bytes the server did not produce means the
stored size, the stored dimensions and the declared `mime_type` can each disagree with reality,
and every consumer of the object has to re-derive that for itself.

The WebP requirement adds a codec problem. `ImageIO` reads JPEG and PNG natively but ships no WebP
reader, so a WebP upload cannot be decoded at all without a plugin — and an upload pipeline that
cannot decode a format the plan requires to accept is not validating anything.

### Decision

- **Format is decided by content, never by the client.** `ImageTypeDetector` reads only the leading
  bytes: `FF D8 FF` for JPEG, the eight-byte PNG signature, and `RIFF....WEBP` for WebP (a plain
  RIFF container such as a WAV is rejected). Neither the filename nor the part's declared
  `Content-Type` is consulted or trusted. An unrecognised format is `415 UNSUPPORTED_MEDIA_TYPE`,
  which is distinct from `VALIDATION_FAILED` precisely so a client can tell "wrong format" from
  "broken file".
- **Detection is a candidate, not an acceptance.** `ImageProcessor` then decodes the file, which is
  what rejects a ZIP wearing a PNG header and a truncated file wearing a WebP header. Both checks
  are kept deliberately: neither one alone is sufficient.
- **Nothing is stored as it arrived.** Every accepted upload is decoded and re-encoded by the
  server, so metadata is dropped rather than re-served later, and the stored bytes, the stored
  dimensions and the stored `mime_type` are all the pipeline's own output.
- **The pixel ceiling is checked from the header, before the pixels exist.** `max-pixels`
  (40,000,000) is read from `reader.getWidth/getHeight` before `reader.read(0)`, because a few
  kilobytes can claim 20000×20000 and cost 1.6 GB to decode. The file-size limit is not a memory
  limit; this is the decompression-bomb bound, and it is rejected with `413` rather than scaled,
  because an image that large is never legitimate.
- **Largest-edge images are scaled down; smaller ones are not upscaled.** `max-dimension` (1600)
  with bilinear interpolation and antialiasing. This is a scaling rule rather than a rejection
  because a vendor photographing a bouquet on a 48-megapixel phone should get a catalog image, not
  an error.
- **The output format follows the input, with one documented exception.** PNG output keeps its alpha;
  JPEG output is re-encoded at `jpeg-quality` (0.85). WebP is **decoded and stored as JPEG**: the
  JDK cannot encode WebP and the plugin registered here (`com.twelvemonkeys.imageio:imageio-webp`)
  is a reader only, so keeping the format would mean either shipping the file undecoded (losing
  every guarantee above) or adding a second codec. Alpha is composited onto white in that case.
  The stored extension and `mime_type` both come from the *storage* format, never the client's name.
- **Limits are configuration, not constants** (`app.image-upload.*`), because each encodes a
  product or operational decision rather than an implementation detail. The container ceiling
  (`spring.servlet.multipart.max-file-size: 5MB`) and the service ceiling
  (`max-file-size-bytes`) are set to the same value and must be raised together — the container
  rejects first, before the request reaches the controller, and `GlobalExceptionHandler` overrides
  `handleMaxUploadSizeExceededException` so that refusal carries the same `ErrorResponse` envelope
  and the same `PAYLOAD_TOO_LARGE` code as the service-level one.
- **Image count is bounded per product** (`max-images-per-product`, default 8) and the check happens
  under the same row lock as the primary rule, so concurrent uploads cannot both squeeze past the
  limit.

### Consequences

- Stored images are smaller than uploaded ones and carry no EXIF. A vendor's photo location data
  does not survive; this is intentional.
- A WebP upload comes back as JPEG, so its `mime_type` and extension differ from what was sent. The
  response DTO reports what was actually stored, and the test suite asserts the conversion rather
  than hiding it.
- `ImageProcessorTest` and `ImageTypeDetectorTest` cover the polyglot, truncated, renamed,
  oversized-pixel and scaling cases against real ImageIO rather than a mocked decoder, because the
  behaviour under test *is* what ImageIO does with those bytes.
- Two new `ErrorCode` values exist: `UNSUPPORTED_MEDIA_TYPE` (415) and `PAYLOAD_TOO_LARGE` (413).
  The latter is reused for the pixel budget, which is a ceiling breach in the same sense the byte
  ceiling is.
- A WebP-only build would need a WebP encoder (`imageio-webp` is reader-only); that is a deliberate
  follow-on, not a bug in this pipeline.

---

## D-28: The one-primary image rule is enforced under a product-scoped row lock

**Status:** Accepted
**Date:** Phase 3f (Task 3.8)

### Context

D-21 already settled that "exactly one primary image per product" is a **service-level** invariant,
because MySQL has no filtered unique index and the generated-column workaround cannot coexist with
the required `product_id` foreign key (error 1215). Task 3.8 is the first task to write
`is_primary`, so it is the first task to have to make that invariant hold under concurrency rather
than in a single-threaded test.

The race is small and worth naming, because it is what the integration test exists to kill. Two
concurrent "make this the cover" requests on a product with images A (primary) and B:

```
T1: SELECT images -> [A(primary), B]   T2: SELECT images -> [A(primary), B]
T1: UPDATE B SET is_primary = true     T2: UPDATE A SET is_primary = false
T1: COMMIT                            T2: COMMIT
```

Depending on interleaving the product ends with two primaries, none, or a primary that no longer
belongs to the row the client just set. The catalog then has two rows claiming to be the cover, and
the storefront picks one arbitrarily — a state the invariant exists to prevent and which no single
request can detect, because each one's own write succeeded.

Two secondary questions follow from D-21's promise that the service is the sole enforcement point:
what happens to the primary when it is deleted, and whether the invariant is also *asserted* after
the fact or only maintained by construction.

### Decision

- **Every write path that can change `is_primary` runs inside one transaction that first takes a
  pessimistic write lock on the product's image rows.**
  `ProductImageRepository.findByProductIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`,
  `ORDER BY sortOrder, id`) is the single way the image service reads a set it is about to modify —
  the same discipline D-24 applies to inventory, including the same ordering rule.
- **The ownership check comes before the lock.** As in D-24, a lock taken before the ownership check
  would let any authenticated florist stall another vendor's image set for as long as it liked, just
  by naming a foreign `productId` in a loop. `ProductService.requireOwnedProduct` (now
  package-private for exactly this reuse) resolves ownership from an unlocked read first, so one
  definition of "yours" serves both the catalog and the image routes.
- **Promotion is clear-then-set within the transaction.** `setPrimary` clears every current primary,
  sets the target, flushes, and only then verifies. Same for the implicit promotion on upload.
- **The first image of a product becomes primary automatically.** A product with images and no
  cover is the failure state worth preventing at the source, and requiring a separate
  `PUT /{imageId}/primary` before a product is usable would be a rule a vendor would trip over
  constantly. An explicit `primary=true` on a later upload takes the cover; without it the existing
  cover is left alone.
- **Deleting the cover promotes the next image** in display order rather than leaving the product
  with images and no cover. Deleting the last image leaves it with none, which is allowed —
  `expected` is computed as `images == 0 ? 0 : 1` rather than being hard-coded to one.
- **Reorder requires an exact permutation.** `PUT /order` must name every image of the product
  exactly once; a partial list, a repeated id, or an id from another product is a 400. Guessing
  where an omitted image belongs is not something the server can do honestly.
- **An unknown image id is 404, not 403.** The caller asked for an image *of this product* and this
  product does not have it; 403 would imply the image exists and belongs to someone else. The image
  is resolved from the already-locked set, so the check cannot race.
- **The invariant is also asserted, not only maintained.** `requireSinglePrimary` re-counts primaries
  and images after each mutation and raises `IllegalStateException` on a violation. It is a
  post-condition, not the mechanism — the clear-then-set above is what enforces the rule — but it
  turns a silent corruption into a failed request.
- **File and row are not atomic, and the asymmetry is deliberate.** Uploads store the object first
  and delete it again if the row cannot be written; deletes remove the row first and treat a failed
  object removal as a logged orphan. Neither direction can leave a visible row pointing at bytes
  that are not there — which is the failure a storefront user would actually notice.

### Consequences

- Two concurrent primary changes on one product serialise; changes on *different* products do not,
  because each locks only its own rows. The common case (uploading a catalogue) stays parallel.
- The lock is verified by `ProductImageIntegrationTest` with real threads released together by a
  `CountDownLatch`, asserting only end state — exactly the method that `InventoryConcurrencyIntegrationTest`
  uses for D-24. A sequential test cannot observe the absence of a lock: every request would see the
  previous one's committed rows.
- `requireSinglePrimary` costs two `COUNT` queries per mutation. That is a deliberate price for
  turning a silent invariant violation into a 500 that names the product id.
- The image count limit is enforced under the same lock as the primary rule, so two concurrent
  uploads at the limit cannot both pass the budget check.
- `ProductImageService` must be reached through the Spring proxy for the lock to be real, exactly as
  in D-24 — a self-invocation would run outside the transaction and the lock would be held by
  nothing. There is no internal call today, so this holds by construction.
- Because the invariant is service-level only, a writer that bypasses `ProductImageService` (a
  scheduled job, admin tooling, a direct data fix) can still leave two primaries. `requireSinglePrimary`
  is the only backstop, and it fires only on paths that go through the service. This is the accepted
  cost recorded in D-21.

---

## D-29: Frontend approval gating reuses the cached vendor profile and withholds the links

**Status:** Accepted
**Date:** Phase 3g (Task 3.9)

### Context

D-13 put approval enforcement on the backend, per handler, through
`@RequiresApprovedVendor`. Task 3.9 is the first screen set behind that gate, so it is the first
time the question of what the *client* should do with the same rule actually arises — and the
options are not equivalent.

The naive frontend versions of the rule all have the same defect: they become a second copy of an
authority that already exists. A gate that reads the JWT cannot know about an approval that happened
after the token was issued. A gate that fetches the vendor's own approval state from a *different*
endpoint can disagree with the backend on the same request. And a nav that renders a Catalog link to a
pending vendor offers a button that can only ever come back `403 VENDOR_NOT_APPROVED`.

There is also a subtlety worth naming, because it cuts against the obvious implementation. The
approval-gated screens are children of `VendorLayout`, and the layout already reads
`GET /api/v1/vendors/profile` to render `VendorStatusBanner`. So the state the gate needs is already
in the TanStack cache under `["vendor","profile"]`. A gate that issues its own request would be a
second read of data that is one navigation away from being on screen.

### Decision

- **`ApprovedVendorGate` is a wrapper, and it reads the existing `useVendorProfile()` query.** The
  three catalog routes (`catalog`, `catalog/new`, `catalog/:productId`) and `inventory` are wrapped in
  it in `router.tsx`. It adds no request: the whole vendor area still costs one
  `GET /api/v1/vendors/profile`, which is the property
  `ApprovedVendorGate.test.tsx` asserts explicitly rather than leaving implicit.
- **The gate is UX, not authorization, and it says so in its own doc comment.** Authority stays
  exactly where D-13 put it. Approval state is never copied into the JWT and never inferred client-side,
  so an admin's approval takes effect on the vendor's next request with no re-login — the same
  guarantee the backend gives, and the reason the gate is allowed to exist at all.
- **A non-approved vendor sees the layout's own banner inside the gate.** It is the same
  `VendorStatusBanner` component reading the same cache entry, not a second banner with its own state,
  so the panel shown when a vendor follows a direct catalog URL explains the state and keeps the rest
  of the vendor area (profile, delivery settings, hours) reachable.
- **`VendorLayout` withholds the Catalog and Inventory nav links unless `profile.status === "APPROVED"`.**
  The nav follows the rule the route follows, so an unapproved vendor is neither shown a dead link nor
  left wondering whether it would work. The dashboard's catalog card is gated the same way.
- **`@RequiresApprovedVendor` is still the review checklist item.** Omitting it from a future vendor
  route fails *open* on the backend; omitting the gate fails only on the client. The gate is therefore
  a usability layer that may be forgotten without a security consequence — deliberately the asymmetry.

### Consequences

- Any future vendor feature needing approval composes `gated(element)` in `router.tsx`. It is a one-line
  change and needs no new state, no new request and no new test fixture beyond asserting the children
  are withheld.
- An unapproved vendor loading `/vendor/catalog` sees no catalog data at all, because no gated query is
  enabled. `router.test.tsx` asserts `fetchProducts` was never called, which is the difference between
  "an empty table" and "an explanation".
- If the approval state ever needs to be consulted by a screen outside the vendor area, the shared
  query is the place to reach for — not a second endpoint.
- The pending-state banner inside the gate duplicates a visible banner for a vendor who arrived at the
  URL directly. That is intentional and is asserted by role/text rather than by count, so the duplicate
  cannot become an unnoticed triple.

---

## D-30: One stock dialog serves all four mutations, and the rules live in one table

**Status:** Accepted
**Date:** Phase 3g (Task 3.9)

### Context

`VendorInventoryController` exposes four stock mutations that differ in two ways each:

| Action     | Quantity             | Reason       | Route suffix   |
|------------|----------------------|--------------|----------------|
| `stock-in` | positive             | optional     | `stock-in`     |
| `stock-out`| positive             | optional     | `stock-out`    |
| `adjustment` | **signed, non-zero** | **required** | `adjustments`  |
| `write-off`| positive             | **required** | `write-offs`   |

The plan asks for "stock in/out/adjustment/write-off dialogs" without saying how many components that
is. Four separate dialogs duplicate the quantity field, the reason field, the reserved-floor note and
the error mapping four times over — and the duplication is exactly where the rules drift. A dialog with
a *required* reason on `stock-in` implies the backend stores one; a dialog with an *optional* reason on
`write-off` offers a way to be refused.

There is a second, subtler decision here: what does a blank reason mean on the wire? The vendor log
stores `reason` as nullable, and `InventoryService.normalize` deliberately stores `null` rather than an
empty string, so "no reason given" stays distinguishable from "a reason that happens to be blank".

### Decision

- **One `StockActionDialog`, one `ACTION_CONTENT` table.** Each entry carries the heading, the
  description, the confirm label, the quantity label and hint, `requiresReason`, `signedQuantity` and
  the reason label and hint. `requiresReason` decides both whether a reason box is rendered *and*
  which zod resolver runs, so the visible form and the validation cannot describe different rules.
- **The four request shapes are a discriminated union, not one optional-reason type.**
  `StockActionRequest` is `StockInRequest | StockOutRequest | StockAdjustmentRequest |
  StockWriteOffRequest`, and the mutation variables are `{productId, action} & StockActionRequest`.
  "Send a write-off with no reason" is a compile error rather than a 400 discovered in the browser, and
  the reason-bearing actions cannot be sent through the optional-reason branch.
- **A blank reason is sent as `null`, never `""`.** `stock-in` and `stock-out` translate `""` to
  `null`; `adjustment` and `write-off` trim and send the string, which is already non-blank by then.
- **The adjustment quantity is a text input, not a number input.** `<input type="number">` discards a
  lone `-` as an invalid intermediate value, which makes a signed correction untypeable in several
  browsers. The three positive actions keep the number input, where `min` and `step` belong. The
  validator is a signed-integer pattern for the same reason.
- **Both callers use the same dialog.** The product editor's `InventoryPanel` and the low-stock
  listing's per-row buttons open the identical component, so a vendor correcting a shortfall from
  either place meets identical validation, wording and error handling.
- **Row buttons carry the product in their accessible name** (`Write off stock for Red Rose
  Bouquet`). A column of identically named buttons is unusable with a screen reader, and it also
  collides with the open dialog's own confirm button of the same label — a collision the test suite
  found before it was designed away.

### Consequences

- Adding a fifth stock mutation is a new `ACTION_CONTENT` entry, a new union member and a new branch
  in `useStockAction`. Nothing else changes, and the rules for it are impossible to add in only one of
  the two surfaces.
- The dialog owns no stock state: it renders the level it is handed and reports the payload the form
  resolved. The page owns the mutation, so the same dialog serves the per-product panel and the
  shop-wide list without either knowing about the other.
- `INSUFFICIENT_STOCK` is explained rather than displayed raw, because the reserved-unit floor (D-24)
  is the one refusal whose cause a vendor cannot see. Any other backend message is passed through
  untouched.
- The union means a caller cannot omit `reason` for `stock-in` at the type level either: it must be
  `string | null` explicitly.

---

## D-31: Product images are listed as metadata because no route serves the bytes

**Status:** Accepted
**Date:** Phase 3g (Task 3.9)

### Context

`ProductImageResponse` carries `storageKey`, `originalFilename`, `mimeType`, `fileSize`, `sortOrder`
and `primary`. The bytes exist — task 3.8 wrote them through `StorageService` — but the API has no
endpoint that returns an image's content, and `storageKey` is an opaque backend key
(`product-images/{productId}/{uuid}.{ext}`) that a client cannot construct a URL for.

The obvious frontend rendering is a grid of `<img>` elements. With no delivery route every one of them
404s, and the vendor sees broken-image icons where their photos should be — which reads as "my upload
failed" rather than "this build has no image endpoint".

### Decision

- **The media section lists metadata, not thumbnails.** Filename (or the storage key when the original
  filename was not retained), MIME type, file size, position in the display order and whether it is the
  cover. No `<img>` is rendered, and the API client never builds a URL from `storageKey`.
- **The reason is stated in the component, not hidden.** A vendor is told the accepted formats and
  the 5 MB ceiling, and the gap is recorded in `docs/known-issues.md` as a missing route rather than
  presented as a UI choice.
- **Everything else about images works normally.** Upload, cover selection, reorder and delete are all
  exercised, and the reorder sends the full id permutation the endpoint requires (D-28) — the move
  buttons recompute the complete order rather than offering a partial list.

### Consequences

- A storefront-facing product page cannot yet show product photos. Adding the route is a small backend
  task (an authenticated `GET` that resolves a key through `StorageService` and streams it), and this
  UI is ready for it: the metadata rows are where the thumbnails will go.
- Until then, the catalog screen's usefulness rests on name, price, stock and status. That is enough
  to manage a catalogue and is not enough to sell from it, which is the honest trade for not shipping
  a 404 on every image.
- The `ProductImage.storageKey` field stays typed and unread. If a delivery route arrives, the field
  becomes the only thing the client needs — no contract change.

---

## D-32: The default-address lock is the first read in the transaction

**Status:** Accepted
**Date:** Phase 4 (Task 4.1)

### Context

Task 4.1's one-default-per-customer rule is a service-level invariant
(MySQL has no partial unique index — the D-21 finding), so it is only as
good as the transaction that enforces it. D-24's discipline — resolve and
check first, take the lock last — was written for the inventory path,
where the locked row is the inventory row and the ownership check reads a
*different* row (the product). Applied literally to the address book it
produced a real defect, caught only by the concurrency integration test:
8 simultaneous first-address creations all became the default, while
every sequential test passed.

The mechanism is MySQL REPEATABLE READ snapshot semantics. The original
`lockUser` resolved the caller with a plain `findByEmailWithRole` and only
then issued `SELECT ... FOR UPDATE`. The first *plain* read of a
transaction establishes its snapshot; the locking read itself reads the
latest committed data but creates no snapshot. So the later
`findAllByUserId` — the read that decides whether a default already
exists — read a snapshot taken *before* the lock was acquired, and could
not see rows a concurrent transaction had committed in between. Every
thread's snapshot was taken while the address table was still empty for
that user, so every thread saw "no addresses exist" and set the flag.

### Decision

The locking read is the **first** read of the write transaction:
`UserRepository.findByEmailForUpdate(email)` resolves and locks the
caller's row in one statement. A locking read establishes no snapshot, so
the snapshot is created by the first plain read *under the lock* and sees
everything committed before it — including the previous lock holder's
address rows. Create, set-default and delete-with-promotion all read the
address table only after that point.

This inverts D-24's "lock last" ordering deliberately, and that inversion
is safe here for the reason D-24 itself identifies: the locked row is
always the caller's own `users` row, resolved from the JWT subject. No
caller can name another user's row, so the foreign-row stall attack that
D-24's ordering guards against cannot happen. (The inventory path keeps
D-24's ordering, because there the lock is on a row a caller *could*
name through a request parameter.)

The locking query joins nothing. A `JOIN FETCH` of the role would issue
`FOR UPDATE` over the shared `roles` row too, serialising every
customer's address writes against each other; the write path never reads
the role, so the lazy proxy is never touched.

### Consequences

- The concurrency test is the only thing that can prove this: sequential
  calls see the previous transaction's committed rows, so an unlocked
  implementation passes. `AddressConcurrencyIntegrationTest` releases 8
  real threads at once (a `CountDownLatch`, the
  `InventoryConcurrencyIntegrationTest` harness) and asserts only end
  state — exactly one default among the created rows.
- Any future default-mutating address method must keep the lock as the
  first read. A plain read before it — even a "harmless" resolve —
  re-poisons the snapshot.
- The same trap applies to any future REPEATABLE READ read-modify-write
  that reads *other rows* after locking one row: check whether the first
  read in the transaction is plain, and if so whether the snapshot it
  establishes predates the lock.

---

## D-33: Boolean DTO properties are named after the state, not the question

**Status:** Accepted
**Date:** Phase 4 (Task 4.1)

### Context

The address request and response need a "which address is the default"
flag. The intuitive property name is `isDefault`. For a *primitive*
`boolean` field with that name, Lombok generates a getter named
`isDefault()` (it does not double the "is" prefix), and Jackson derives
the wire name by stripping the "is" prefix from the getter — so the API
would silently expose the field as `default`, not `isDefault`. Nothing
fails at compile time or in a mocked unit test; only a test that asserts
the wire name at the HTTP boundary catches it. The entity already hit
this: its property is `defaultAddress` (column `is_default`) precisely so
the JPQL property path and the derived query methods are unambiguous.

### Decision

Boolean properties are named after the state (`defaultAddress`), never
`is` + noun, on any class that carries a primitive boolean. The request
DTO uses the same name as the entity and the response, so request,
response and entity share one vocabulary — `defaultAddress` — on the wire
and in Java.

### Consequences

- The wire field is `defaultAddress` on both the request and the response.
  The naming trap is caught by `AddressApiIntegrationTest`, which asserts
  `$.defaultAddress` at the HTTP boundary, so a rename that reintroduces
  it fails as a contract drift rather than surviving silently.
- Future primitive-boolean DTO fields follow the same rule. (A
  `Boolean` wrapper named `isDefault` would serialize correctly — the
  wrapper gets a `getIsDefault()` getter — but two names for one concept
  across three classes is the trap D-33 exists to close.)
- The `Address` entity's class javadoc already records the property-
  resolution reasoning; the DTOs cite it.

---

## D-34: Geo discovery API is public and uses bounding-box + Haversine

**Status:** Accepted
**Date:** Phase 4 (Task 4.3)

### Context

Plan task 4.3 introduces `GET /api/v1/discover`: a public endpoint that returns approved vendors accepting orders within their delivery radius of a given service location. The endpoint requires a `locationId` parameter (referencing a `service_locations` row), and returns paginated vendor cards with computed `distanceKm` and `estimatedDeliveryFee` (base + per-km × distance).

Two patterns for public data access existed:
- `GET /api/v1/locations` — `permitAll()` (reference data)
- `GET /api/v1/categories` — `permitAll()` (reference data, active roots only)

The discovery endpoint is similar to locations/categories in that it serves reference data for the storefront, but it also computes real-time distances and filters by each vendor's `delivery_radius_km`.

### Decision

- **Public access**: `GET /api/v1/discover` is mapped to `permitAll()` in `SecurityConfig`, mirroring `/api/v1/locations` and `/api/v1/categories`. No authentication required.
- **Required parameter**: `locationId` (Long) is required; missing or unknown returns 400 `VALIDATION_FAILED`.
- **Filtering**: Only vendors with `status = APPROVED` AND `accepting_orders = true` are considered.
- **Distance calculation**: Bounding-box prefilter using `idx_vendor_profiles_status_geo` (composite on `status, latitude, longitude`), then exact Haversine distance check against each vendor's `delivery_radius_km`. The bounding box radius is derived from the maximum `delivery_radius_km` among approved vendors.
- **Sorting**: Results sorted by `distanceKm` ascending, tie-broken by vendor `id`.
- **Pagination**: Standard `page` (0-based) and `size` (1..100, default 20) parameters. Response uses `PageResponse` envelope with `page`, `size`, `totalElements`, `totalPages`, `first`, `last`, `empty`.
- **Fee estimation**: `estimatedDeliveryFee = baseDeliveryFee + perKmFee × distanceKm` (rounded to 2 decimals). `freeDeliveryAbove` is included as informational only.
- **Error handling**: Unknown `locationId` ? 400 `VALIDATION_FAILED` ("Unknown service location"); missing `locationId` ? 400 `VALIDATION_FAILED` via the `MissingServletRequestParameterException` override in `GlobalExceptionHandler` (standard `ErrorResponse` envelope).

### Consequences

- The endpoint is usable by unauthenticated customers for storefront browsing (Phase 4.5+).
- Bounding-box prefilter keeps the Haversine computation bounded even as vendor count grows.
- The composite index `idx_vendor_profiles_status_geo` serves both the status filter and the bounding box.
- `freeDeliveryAbove` is informational; the storefront UI decides whether to show "free delivery above X" badges.
- The discovery query does not consider vendor prep time, slot availability, or max orders per slot — those are order-time concerns.
- If no approved vendors exist, or max radius is null, an empty page is returned immediately without a bounding-box query.

---

## D-35: Full-page cart route accepted over slide-out drawer for multi-vendor layout

### Context

The original project plan (`docs/FlowerConnect_Implementation_Plan_v2.2.md`, task 5.6) specified a slide-out cart drawer:
`| 5.6 | Cart UI | Slide-out drawer grouped by shop, quantity stepper, price breakdown |`

However, the Phase 5 Stage 0 design specification (`docs/phase-5-stage-0-design.md`, Â§6.2 and lines 522â€“530) explicitly allowed:
`CartPage.tsx # full-page cart (optional; drawer may suffice)`

In commit `deccca3`, the cart frontend was implemented as a standalone full-page view mounted directly at `/cart` (`src/features/cart/pages/CartPage.tsx`), without introducing a slide-out drawer component (`CartDrawer.tsx`).

### Rationale

In a hyperlocal multi-vendor marketplace, cart items from different florists have distinct delivery fees, distance constraints, minimum order requirements, and price-drift warnings. In a 360pxâ€“420px slide-out drawer, this multi-vendor information density causes vertical crowding and makes per-vendor subtotals and item adjustments difficult to review. A dedicated full-page cart view at `/cart` provides the horizontal and vertical space necessary to cleanly separate vendor sections, display price drift and unpurchasability reasons, and summarize multi-vendor totals before checkout.

### Decision

- **Full-page cart accepted**: `CartPage.tsx` mounted at `/cart` is accepted as the canonical customer cart implementation for Phase 5.
- **Drawer requirement waived**: A slide-out drawer (`CartDrawer.tsx`) is not required for Phase 5 completion. If a slide-out drawer is introduced in a future phase, it will serve as an optional quick-access preview linking directly to `/cart`.
- **Functional acceptance criteria confirmed**: Inspection of `CartPage.tsx` confirms all functional criteria of task 5.6 are satisfied:
  - Vendor grouping: Items grouped by vendor with per-shop headers and vendor subtotals.
  - Quantity controls: Stepper bounded between 1 and `CART_MAX_QUANTITY` (10), with delete/remove button.
  - Price drift detection: Visual badge and line-item indication when catalog price differs from cart snapshot.
  - Unpurchasable item handling: Reason displayed and "Proceed to checkout" button disabled when any item is not purchasable.
  - Breakdown and summary: Cart subtotal, shop subtotals, and CTA navigating to `/checkout`.

### Consequences

- Eliminates redundant drawer component scaffolding, focus trapping, and synchronized state management across drawer and page.
- Routing to `/cart` remains direct and consistent across navigation headers.
- Task 5.6 acceptance criteria are met and the task can be marked complete in project tracking.

---

## D-36: The customer catalogue's product detail is an overlay over its parent surface, and standalone on direct entry

**Status:** Accepted
**Date:** Phase 4 (task 4.9)

### Context

Plan task 4.9 describes a "Product detail modal". A modal that is not a route has no URL, and a product page that cannot be linked, refreshed or reached from a shared message is not a page at all — so the product detail is a **route**, and the overlay is a *presentation of that route* rather than a separate screen.

The route has to be nested somewhere. Nesting it under the florist's storefront (`/vendors/{vendorId}/products/{productId}`) is what lets the shop stay on screen behind the product, which is the point of an overlay: the customer sees the product in the context of the shop it belongs to.

### Decision

- The product route is nested under the storefront route.
- A product reached **from within the app** renders as a panel **over** the storefront, with the shop still behind it and a close control; the storefront's own content is unchanged and remains visible.
- A product reached **directly** — a typed URL, a pasted link, a shared message — renders on its own, with the storefront not shown.
- The two presentations are mutually exclusive for one URL. There is no state in which the product is both an overlay and alone.
- Every ineligibility — unknown, inactive, sold out, an unapproved or non-accepting florist, or a florist outside the delivery radius — is the **same** non-disclosing 404, so the overlay never reveals whether a product exists.

### Consequences

- The surface behind the overlay is a **live page**, not a static backdrop: it stays in the DOM and stays keyboard-reachable. The overlay declares `aria-modal="true"`, which on its own implements **neither** focus management nor a focus trap. This is a recorded limitation, deferred rather than fixed — see the 4.9 entry in `docs/progress.md`.
- D-36 fixes **what** the two presentations are. **How** the frontend chooses between them is a separate question, and the rule that shipped with Gate E is being replaced — see D-37.
- The product is reachable from the search results as well as from a storefront. What that means for the search context is part of D-37.

---

## D-37: The overlay is distinguished by explicit navigation state and a per-page-load token

**Status:** Accepted
**Date:** Phase 4 (task 4.9) — implemented and verified in the Gate E remediation

> **Implemented and verified.** The mechanism below is what shipped; the
> coverage named under "Verification" is what the automated suite actually
> asserts. The browser-level behaviour was then checked separately, in a real
> Chromium browser, and is recorded under "Verification" below.

### Context

D-36 fixes the two presentations but not the rule that chooses between them. The Gate E implementation inferred the choice from `useNavigationType()` alone: a PUSH renders the overlay, a POP renders the standalone page. Inferring a presentation from a navigation type is fragile in three specific ways.

First, **dismissal and history**. The close control and the Escape key both navigate to the storefront, which **pushes** a history entry. The browser Back button then returns to the product URL with a POP navigation, so the product renders standalone — with no shop behind it and, under that implementation, no close control either.

Second, **the search context**. The product route was nested under the storefront alone, so a product opened from the search results left the search page entirely: the customer landed on a shop they did not open, and the search filters and page number — which were local to the search page — were lost. The app shell keys its route-transition wrapper by the full pathname, so every pathname change remounts the routed subtree. The results survive only because the query cache is held above the router; the filters that select them do not.

Third, **a refresh**. React Router keeps navigation state in the history entry, and the browser preserves that state across a same-document reload, so a marker written in one page load can still be present in the next one. A rule that reads a marker alone therefore cannot distinguish "the customer opened this from the shop" from "the customer reloaded this page".

### Decision

- **The overlay is marked, not inferred.** A product link carries explicit React Router navigation state identifying it as an overlay, together with a **token** identifying the current JavaScript page load. The token is generated once per page load, held in memory only, never persisted and never sent to the server. A marker is honoured only when its token matches the current page load, which is what makes a reload render the standalone page rather than reconstruct an overlay.
- **Ordinary links carry no marker.** React Router writes navigation state only when a link supplies it, so no other route in the app can be mistaken for an overlay.
- **The product route is nested under the search surface as well as the storefront**, giving two public product URLs — `/vendors/{vendorId}/products/{productId}` and `/search/products/{productId}` — that render the same product component. A product opened from the search results therefore keeps the results behind it.
- **Each surface recognises its own product child by an explicit route id** (`storefront-product`, `search-product`), read through `useMatches()`. The ids must be **distinct**: React Router requires route ids to be globally unique within a data router and throws on a collision when the router is created. The same ids let a surface tell a matched product child from any other child, which `useOutlet()` cannot do — it returns `null` both when no child matched and when the matched index child has no element.
- **The search filters and page live in an in-memory store**, not in component state, so they survive the remount that opening a product causes. The store is not persisted: a full reload starts from the defaults, which is the correct reading of "this application session".
- **Dismissal pops the in-app predecessor.** The close control and the Escape key return to the surface the product was opened from, without pushing a duplicate background entry. The predecessor's existence is guaranteed by the marker — the marker is written only by a link that pushes an entry — so no history-index guess is needed. A `window.history.state.idx` guard was considered and rejected: it reads the *browser's* history, whereas the tests run a memory router with an in-memory history, so it would take the wrong branch under test.
- **A standalone product page offers a return path** back to the surface it belongs to, and never calls `navigate(-1)`.

### Consequences

- The overlay/standalone distinction becomes **explicit**, so Forward within a page load restores the overlay rather than silently degrading it to the standalone page, and a reload degrades to standalone on purpose.
- The product route gains a second public URL form. Both render the same component; the difference is which surface is behind it.
- The search page's filter state moves out of the component, which makes it survive navigation and makes returning to the search restore the last filters. The existing "clear filters" action keeps its meaning.
- A surface no longer fetches what it is not going to show: a standalone product entry issues no storefront request, and a standalone search-parented product issues no search request.
- The surfaces require a **data router**, because `useMatches()` is a data-router API. The application already uses `createBrowserRouter`; the tests use `createMemoryRouter`.
- The mechanism is verifiable **except** for one thing. A test that presents a stale token proves only the marker-comparison logic; it does not prove that a browser reload produces a stale token, because the repository has no browser-test infrastructure and adding one is out of scope. The browser-level refresh behaviour is therefore a **manual check**, and this decision must not be read as claiming it is automated.

### Verification

Automated — asserted by the frontend suite (128 customer tests across 12 files; 730 frontend tests across 65 files; typecheck, lint and build all clean):

- Overlay open, close, Escape, Back and Forward from the storefront, and the same from the search surface.
- Closing pops the in-app predecessor rather than pushing a duplicate background entry.
- Search filters and page preserved across the product round-trip and across a remount of the page, including a check that the request that follows carries the preserved filters rather than the defaults.
- Direct entry, and a marker whose token belongs to another page load, both render the standalone page with a return path.
- The storefront query is not issued for a standalone product entry, and the search query is not issued for a standalone search-parented product.
- A malformed florist id and a malformed product id each issue no request and render the non-disclosing unavailable state.
- The two product route ids are distinct, and the router is created without an id collision.

**Browser-level behaviour: checked in a real browser, not by an automated test.** The four frontend navigation checks — refreshing a product URL that was opened as an overlay, refreshing a directly opened standalone product URL, Back and Forward through an overlay opened from the search results and from a storefront, and keyboard traversal through the overlay — were exercised in a real Chromium browser after the automated suite passed. The catalogue API responses were **stubbed in the browser**, because the backend instance available at the time could not serve the uncommitted public-catalogue endpoints. The checks therefore establish the **frontend** navigation, reload and history behaviour; they do **not** establish end-to-end catalogue integration against the real backend or MySQL, which remains unverified. The keyboard traversal confirmed the recorded limitation: focus can leave the overlay, and no focus trap is implemented. The stale-token test still covers only the marker comparison, so the automated suite does not on its own prove these browser results.

---

## D-38: Location-gated catalogue surfaces versus the storefront's non-blocking area advisory

**Status:** Accepted
**Date:** Phase 4 (tasks 4.6–4.9)

### Context

The catalogue endpoints do not all take a delivery area. `GET /api/v1/discover` and `GET /api/v1/search` require `locationId` (D-34, and the search contract from the same phase), and `GET /api/v1/products/{id}` takes one to decide whether the product is deliverable. `GET /api/v1/vendors/{id}/storefront` takes none: the customer has already chosen that florist, and the endpoint applies no delivery-radius filter.

A single rule — "the customer area gates everything" — would therefore contradict the storefront endpoint's own contract and would hide a shop the backend is happy to serve.

### Decision

- **The location-required surfaces gate on a delivery area.** Until the customer chooses one, the browse page, the search page and the product detail show the location prompt and issue **no location-dependent request**; the query stays disabled rather than being sent with a missing id.
- **The storefront's area is a non-blocking advisory.** A storefront renders in full whether or not an area is chosen, and the missing area is stated alongside it. The shop is not hidden, because the backend is not hiding it either.
- The distinction follows the **backend's** contract rather than a UI preference: it is a gate where the query needs a parameter, and an advisory where it does not.

### Consequences

- The customer can read a shop they may not be served by, and the interface says so rather than refusing to show it.
- The location selection is held for the session (D-34, task 4.2), so the prompt appears once per tab rather than once per page.
- "No location-dependent request" is a property the tests assert, not an accident of the query being disabled.

---

## D-39: Customer price bounds are two labelled number inputs, not a range slider

**Status:** Accepted
**Date:** Phase 4 (task 4.7)

### Context

Plan task 4.7 lists "price slider" among the search controls. A two-handle range slider has no native accessible semantics, cannot be typed into, and is awkward to operate with a screen reader or with a keyboard alone. The backend's contract, by contrast, is two independent decimal bounds — `priceMin` and `priceMax` — with `priceMin > priceMax` refused as a validation error.

### Decision

- The price bounds are **two labelled number inputs** — "Minimum price" and "Maximum price" — that mirror the backend's `priceMin` and `priceMax` exactly.
- A minimum greater than the maximum is refused **before** the request is issued, with the error shown against both fields.
- The slider is a documented deviation from the plan's wording, not an omission.

### Consequences

- The controls are precise, typeable and keyboard-friendly, and they map one-to-one onto the query parameters.
- The plan's wording and the implementation differ on this one control, which is why the deviation is recorded here rather than left implicit.
- The bound values themselves are unchanged; only the input control differs.

---

## D-40: An anonymous add-to-cart redirects to login and does not persist a pending add

**Status:** Accepted
**Date:** Phase 4 (task 4.9)

### Context

The catalogue is public; the cart is not. `POST /api/v1/cart/**` requires the `CUSTOMER` role, so an anonymous visitor cannot add anything. The customer frontend nonetheless offers the control, because hiding it would hide the fact that the catalogue leads to a cart.

The tempting implementation is to remember the intended add and replay it after the customer signs in. That replay has a problem: the product's price, its stock and the customer's own intention may all have changed by the time the customer returns from the login page, and a cart the customer never confirmed is a surprise rather than a convenience.

### Decision

- An anonymous visitor's add-to-cart **navigates to the login page**, carrying the current path in the router's `from` state — the convention the route guard and the login page already use — so the customer returns to where they were.
- **No pending add is persisted.** The add is not queued, not stored and not replayed. The customer returns to the product and presses the button again.
- A signed-in customer who is not a `CUSTOMER` sees the control **disabled**, with the reason stated.
- The backend remains the authority on authorization, pricing, stock and cart validation. The frontend sends only the product id, the quantity and the optional gift note, and the price snapshot is taken server-side (decision C-1).

### Consequences

- The customer presses the button twice after signing in. That is the cost of never putting an item in a cart they did not confirm.
- Nothing about the add is written to storage, so there is no stale queue to expire or to replay against a changed catalogue.
- The existing cart implementation is untouched: the customer frontend reuses the cart mutation and the existing `["cart"]` query invalidation rather than introducing a second path.

## D-41: The customer cart is fetched only for a customer, and a 401 without a session does not trigger a refresh

**Status:** Accepted
**Date:** Phase 5 (task 5.T, defect D-1)

### Context

The app shell's `SiteHeader` is on every route, and it read the customer cart
unconditionally so it could show the item count. `/api/v1/cart/**` is CUSTOMER-only, so
an anonymous visitor's read answered 401.

That 401 was not inert. The axios response interceptor treats *any* 401 from a
non-public endpoint as an expired access token, so it called `POST /api/v1/auth/refresh`.
The refresh credential is an HttpOnly cookie, which the frontend cannot inspect, so with
no session the request came back 400 "Refresh token is required" - and the interceptor's
error handler then called `logout()`, whose own `POST /api/v1/auth/logout` came back 400
as well. One request the app had no use for became three failing requests and three
console errors, on every public page, for every visitor who had not signed in.

The same query also raised a 403 for a florist and an admin, who have no cart either.

### Decision

- **The header fetches the cart only for an authenticated customer.** The condition is
  `isAuthenticated && user?.role === "CUSTOMER"` - the same one under which the cart link
  is already rendered, so the query and the link cannot disagree.
- **`useCart()` gained an optional `{ enabled?: boolean }` that defaults to `true`.**
  `CartPage` and `CheckoutPage` call it unchanged and keep their behaviour; they already
  sit behind a CUSTOMER-only route guard, so a gate inside `useCart()` would have added
  nothing but a coupling to the auth store.
- **A 401 with no access token is rejected without refreshing or logging out.** The
  interceptor reads the auth store first, and the store's own `refresh()` already draws
  the same line: a session needs an access token to be worth renewing. The refresh
  credential being an HttpOnly cookie means the frontend cannot tell whether a refresh
  would succeed - it can only tell whether there is a session to refresh.
- **The refresh path for a session with an access token is unchanged.** An expired access
  token still triggers a refresh and a retry of the original request, and a failed refresh
  still logs the session out.
- **No backend contract changed.** `/api/v1/cart/**` keeps its `hasRole("CUSTOMER")`
  rule, and the refresh and logout endpoints keep their current status codes.

### Consequences

- An anonymous visitor, a florist and an admin now issue no cart request at all, so the
  public pages are free of the 401 -> 400 -> 400 cascade and of its console noise.
- A future caller of `useCart()` can still forget the gate; the `enabled` option is the
  mechanism for that, and the cart and checkout pages are the reason it defaults to
  `true`.
- A signed-in user whose refresh cookie has gone still gets one 400 from the refresh and
  one 400 from the logout. That is the server reporting that the token is required, and
  it is deliberately left alone.
- The fix is verified in source, and since the 2026-10-21 deployment it is also verified
  in the running Docker frontend: the six public routes were re-checked in a real browser
  against the deployed build, with zero cart, refresh or logout requests and zero console
  errors. The signed-in customer flow remains unverified in a browser, because no approved
  test credentials are available; the unit and integration suites remain its coverage.
## D-42: Order state machine owns every order status transition

**Status:** Accepted
**Date:** Phase 6 (task 6.1)

### Context

Plan section 6.1 requires a single service for changing order status, validated
against the full state-machine table (illegal transition -> 409), with one
order_status_history row per transition. Phase 5 wrote only the initial
checkout row (rom_status = null, 	o_status = PLACED) and left every
subsequent transition to Phase 6. No order endpoints exist yet; Stage 1 implements
and unit-tests the state machine only.

### Decision

- OrderStateMachine (com.flowerconnect.order.service) is the sole service
  for changing Order#status. No other class may set it.
- Public API: 	ransition(orderId, toStatus, actorType, actorUserId, reason),
  where ctorType is CUSTOMER, VENDOR, ADMIN or SYSTEM and
  ctorUserId is populated for named users and null for SYSTEM.
- The entire section 6.1 (from, to, actor) table is encoded as one immutable
  map (TRANSITIONS). PENDING_PAYMENT transitions are included even though
  online payment is unreachable until Phase 7; customer/admin cancellation
  transitions are included even though their endpoints are reserved for Phase 8.
- DELIVERED, CANCELLED and REJECTED are terminal. A reason is required for
  vendor reject, vendor cancel and admin cancel.
- Every successful transition locks the order row first (indByIdForUpdate),
  re-checks the current status under that lock, writes exactly one
  order_status_history row (from/to, actor type, actor user id, reason,
  timestamp from the injected Clock), updates the order, and publishes
  scalar-only OrderStatusChanged plus OrderCancelled (cancellations) or
  OrderDelivered (delivery) after the transaction commits.
- Stock side effects go through InventoryService (commitReservedStock,
  eleaseReservedStock, estockCancelledOrder), with inventory rows locked
  in ascending product-id order after the order lock:
  PLACED -> CONFIRMED commits reserved stock (quantity -= q,
  eserved_quantity -= q, SALE_ONLINE) and re-checks quantity >= q (409
  otherwise); PLACED -> REJECTED/CANCELLED releases the reservation
  (RELEASE); CONFIRMED -> CANCELLED restocks (RESTOCK); cancellations from
  PREPARING/OUT_FOR_DELIVERY do not restock; OUT_FOR_DELIVERY -> DELIVERED
  sets payment_status = COLLECTED for COD.
- order_status_history gains a nullable ctor_type VARCHAR(16) column via
  additive migration V16__add_order_status_history_actor_type.sql; the Phase 5
  checkout row keeps a null ctor_type, so no second transition is created and
  no applied migration is edited.

### Concurrency

Lock order is order row first, then inventory rows in ascending product id.
Vendor accept/reject, auto-reject and duplicate actions all re-check the order
status under the lock, so a repeated or conflicting transition returns 409 and
REJECTED/CANCELLED orders free their slot capacity automatically (Phase 5
counts only PLACED and CONFIRMED).

### Deferred

Auto-reject configuration (platform_config table), order endpoint security,
customer/admin cancellation endpoints, CancellationService, Phase 7
payments/expiry, Phase 9 notifications and Phase 10 commission/ledger work are
deliberately out of scope for Stage 1.

## D-43: Vendor order endpoints use the FLORIST namespace without approval gating

**Status:** Accepted
**Date:** Phase 6 (Stage 2, task 6.2–6.4)

### Context

Stage 2 exposes the vendor order action endpoints (accept/reject/prepare/out-for-delivery/delivered), the paginated vendor order list, and today's summary. The plan's vendor routes live under `/api/v1/vendors/**` (plural), already mapped to `hasRole("FLORIST")` in `SecurityConfig`, and D-13 places `@RequiresApprovedVendor` per handler on catalog/inventory/order-creation routes. Stage 1's `OrderStateMachine` (D-42) is the sole status-transition service and defers "order endpoint security" to later stages.

Two questions had to be settled before writing the endpoints.

First, the URL namespace. The plan prose names `/api/v1/vendor/products` (singular) in one place, but the codebase and `SecurityConfig` use `/api/v1/vendors/**` (plural). Introducing a second near-identical prefix risks attaching a rule to the wrong one. Stage 2 therefore uses `/api/v1/vendor/orders` (singular) for the order endpoints — a distinct, deliberate namespace — and adds a matching `SecurityConfig` rule `.requestMatchers("/api/v1/vendor/**").hasRole("FLORIST")`.

Second, whether `@RequiresApprovedVendor` applies. D-6 draws a clear line: a suspended vendor is hidden from discovery and blocked from new catalog/inventory/order work, but must still process their own existing in-flight orders to completion. Applying `@RequiresApprovedVendor` to the order-action endpoints would break that guarantee (a suspended vendor could not accept or deliver an in-flight order). So the order endpoints carry the FLORIST namespace rule plus per-handler ownership (missing order 404, foreign order 403) but deliberately **not** `@RequiresApprovedVendor`.

Stock side effects are delegated to `OrderStateMachine` ? `InventoryService` under the agreed lock order: the order row is locked first, then inventory rows in ascending product id.

### Decision

- Vendor order endpoints live under `/api/v1/vendor/orders` (singular): `POST /{orderId}/accept|reject|prepare|out-for-delivery|delivered`, `GET /` (paginated, optional `status`/`fromDate`/`toDate`), `GET /summary` (today's summary in the vendor's timezone).
- Each endpoint resolves the caller's vendor profile from the JWT subject, enforces ownership (404 missing, 403 foreign), and delegates the transition to `OrderStateMachine`.
- The namespace rule is `hasRole("FLORIST")`; `@RequiresApprovedVendor` is deliberately omitted per D-6.
- Additive migration `V17__alter_order_status_history_actor_type_to_enum.sql` converts the committed `V16` `order_status_history.actor_type` column from `VARCHAR(16)` to native MySQL `ENUM('CUSTOMER','VENDOR','ADMIN','SYSTEM')` to match the `ActorType` entity mapping (`@Enumerated(EnumType.STRING)`) and satisfy `ddl-auto: validate` (D-20).

### Consequences

- A suspended vendor can still accept/reject/prepare/deliver their existing orders (D-6 honored), but cannot create new orders or touch catalog/inventory (those routes carry `@RequiresApprovedVendor`).
- Forgetting `@RequiresApprovedVendor` on a future catalog/inventory/order-creation route fails open — the main review checklist item for vendor features.
- `V17` is additive; `V16` is not edited (ADR-002/D-9). The Phase 5 checkout history row keeps a null `actor_type`, compatible with the ENUM column.

## D-45: Customer order response exposes only safe, customer-visible fields

**Status:** Accepted
**Date:** Phase 6 (Stage 3, task 6.3)

### Context

Task 6.3 adds `GET /api/v1/orders` and `GET /api/v1/orders/{id}` for the
authenticated customer. The `Order` entity carries several internal fields
(`commissionRate`, `clientOrderId`, the `user`/`vendor` relations, timestamps)
that must not be leaked to the customer, and the status timeline must show
only actor information that is safe for a customer to see.

### Decision

- The detail response (`CustomerOrderResponse`) exposes: order status, payment
  method/status, vendor name (`businessName`), address snapshot, delivery slot,
  per-item snapshots (product name, captured price, quantity, item subtotal),
  the BigDecimal fee breakdown (subtotal/delivery fee/platform fee/total),
  created/updated timestamps, and the chronological status timeline.
- The timeline (`CustomerOrderTimelineEntry`) exposes `fromStatus`,
  `toStatus`, `actorType` and, for a named user, `actorName` (the display
  name). Actor email, id and any internal reason beyond the stored `reason`
  are deliberately omitted.
- Internal fields such as `commissionRate`, `clientOrderId`, credentials and
  unrelated user data are never exposed. The list response
  (`CustomerOrderListItem`) is deliberately light (id, status, payment
  method/status, vendor name, total, createdAt, slot) so the full item
  snapshots and timeline are reserved for the detail endpoint.
- These endpoints are read-only: they never change order status. Every status
  change continues to go through `OrderStateMachine` (D-42).

### Consequences

- The customer can reconcile an order against what they agreed to pay at
  checkout (price snapshots) and watch its progress through the timeline,
  without learning internal pricing or other customers' data.
- A future decision is needed before any of these read-only fields become
  mutable or before commission/settlement data is exposed to customers.

## D-46: Auto-reject threshold stored in platform_config, not hard-coded

**Status:** Accepted
**Date:** Phase 6 (Stage 4, task 6.5)

### Context

Task 6.5 requires the auto-reject threshold (minutes after which an unaccepted
`PLACED` order is auto-cancelled) to be configurable. The plan places this
setting in `platform_config` (plan section 9, phases 6–10), but Phase 6
precedes Phase 10, so no `platform_config` table existed and `CheckoutProperties`
explicitly defers runtime settings to Phase 10.

### Decision

- Additive migration `V18__create_platform_config_table.sql` creates a generic
  key/value `platform_config` table (`config_key`, `config_value`, `description`)
  and seeds `auto_reject_minutes = 60`. The generic shape lets Phase 10 add
  commission, platform-fee and timeout rows without another migration.
- `AutoRejectService` reads the threshold from `platform_config` at job run time.
  If the row is missing, the documented default (60 minutes) is used and a
  warning is logged — auto-reject is never silently disabled. A non-integer
  value fails fast at service startup (`IllegalStateException`).
- The scheduler cadence (`app.auto-reject-cron`, default hourly) and batch bound
  (`app.auto-reject-batch-size`, default 100) come from `AppProperties`, following
  the `expirySweepCron`/`expirySweepMaxRows` pattern. The age threshold itself
  always comes from the database.
- `platform_config` is not exposed through a runtime configuration API in Phase 6;
  that arrives with Phase 10.

### Consequences

- `V18` is additive; no applied migration is edited (ADR-002/D-9). V16/V17 are
  untouched.
- Phase 10 can extend `platform_config` with commission and platform-fee rows
  without a new migration.
- The auto-reject job is disabled in the test profile
  (`app.auto-reject-cron: "-"`) and driven directly with the `MutableClock` bean,
  following the `InventoryExpiryScheduler` precedent.
## D-47: COD-only payments model for Phase 7 Stage 1

**Status:** Accepted
**Date:** Phase 7 (task 7.1-7.2)

### Context

Phase 7 introduces payments for checkout. Stage 1 supports cash-on-delivery (COD) only; online payment (intents, webhooks, capture, refund) is deferred to a later Phase 7 step. The decisions to settle: where payment status lives, who writes it, how COD collection works at delivery, and what happens to payment status when an order is rejected or cancelled before delivery.

### Decision

- **`PaymentService` is the sole writer of payment status.** No controller, repository, or state-machine code writes `orders.payment_status` or `payments.status` directly; all payment transitions go through `PaymentService`.
- **`PaymentGateway` abstraction with a `CodPaymentProvider` implementation.** The COD provider makes no external call and no network request; it only persists a `PENDING` payment record during checkout. A later online phase adds provider implementations for intent creation, confirmation, webhook handling, capture and refund.
- **One `payments` row per order (1:1).** `payments.order_id` is unique. The row holds method, provider, amount, status and nullable `collected_at`; a nullable `payment_group_id` is reserved for the later shared online payment intent.
- **Additive `V19` migration** creates `payments` and `payment_events`, backfills one payment row per existing order (preserving existing `payment_status`, including `COLLECTED`), and adds `idx_osh_to_status_created_at` so the auto-reject age query can measure age from entry into `PLACED`.
- **Option B for pre-delivery transitions:** a COD order that is rejected or cancelled before delivery keeps both `payments.status` and `orders.payment_status` at `PENDING` --- no collection was attempted, so `PaymentService` makes no change. This avoids writing a payment status that must later be reversed.
- **`collectPayment` transitions `PENDING -> COLLECTED` at delivery.** It updates `payments.status`, `orders.payment_status` and stamps `collected_at` from the injected `Clock` in one transaction, and rejects non-COD orders and non-pending payments with 409 `CONFLICT`.
- **`PENDING_PAYMENT` counts toward slot capacity** (`countBookedOrdersForSlot` includes `PLACED`, `CONFIRMED`, `PENDING_PAYMENT`) so an order awaiting payment still occupies a slot.
- **Auto-reject age is measured from the `PLACED` history entry** (`order_status_history.to_status = PLACED`), not `orders.created_at`, via the new `findPlacedStaleBefore` query, so an order that sat in `PENDING_PAYMENT` before becoming `PLACED` starts its auto-reject clock at the `PLACED` transition.
- **No real online provider, webhook, refund or capture; no card data or secrets stored.** All of that is deferred to a later Phase 7 step.

### Consequences

- Payment status is consistent between `payments` and `orders` because `PaymentService` updates both in one transaction.
- Reject/cancel before delivery is safe under Option B: the payment stays `PENDING` and can be collected later if the order is reinstated, or left as-is if the order is terminal.
- `V19` is additive; no applied migration is edited (ADR-002/D-9).
- `PENDING_PAYMENT` consuming slot capacity means an order awaiting payment blocks a slot until it transitions out, which is intentional (prevents slot hoarding while still reserving capacity).

## D-48: Online checkout creates shared simulated payment intent and PENDING_PAYMENT orders

**Status:** Accepted
**Date:** Phase 7 Stage 2 (task 7.4 part 1)

### Context

Stage 1 of Phase 7 supports COD only. Stage 2 part 1 introduces online checkout without a real payment gateway — the intent is simulated so the frontend can exercise the resume/pay flow end to end in tests. Decisions to settle: where the intent lives, how a shared intent groups multiple orders, how resume transitions work, and RBAC for payment endpoints.

### Decision

- **`PaymentIntent` is a separate entity** (`com.flowerconnect.payment.domain.PaymentIntent`, `IntentStatus` CREATED/PAID/EXPIRED/FAILED) with a `payment_intents` table created by additive migration `V20`. One intent per payment group, keyed by `payment_group_id` (VARCHAR(36), unique).
- **Online checkout creates one shared intent + one `PENDING` payment per order.** All orders in a multi-vendor checkout share the same `payment_group_id` and the same intent reference (`intent_sim_<uuid>`). Orders start `PENDING_PAYMENT`; stock is reserved via `RESERVE` movement but quantity is untouched. Intent expires after `app.intentTtlMinutes` (default 30).
- **`resumePayment` transitions `PENDING_PAYMENT -> PLACED` exclusively through `OrderStateMachine`** (ActorType.SYSTEM, SideEffect.NONE since stock already reserved), then syncs `payments.status` to PAID, `orders.payment_status` to PAID, and intent status to PAID — all in one transaction. Expired intent (past `expiresAt`) is rejected with 409.
- **`fetchPaymentState` and `fetchGroupState` are read-only views** scoped to the owning customer. RBAC: `/api/v1/payments/**` ? `hasRole("CUSTOMER")`. Florist and admin are 403; unauthenticated is 401.
- **`OrderStateMachine` dependency on `PaymentService` is broken via `ObjectProvider`** (circular dependency: `PaymentService ? OrderStateMachine ? PaymentService`). `OrderStateMachineProvider.getObject()` resolves lazily at call time.
- **`PENDING_PAYMENT` is excluded from vendor order list and summary** (`VendorOrderService`) since it's not yet an actionable order — the customer hasn't paid yet.
- **`created_at`/`updated_at` on `PaymentIntent` use `@CreationTimestamp`/`@UpdateTimestamp`** (Hibernate) so manually created intents in tests persist correctly.

### Consequences

- COD path is unchanged from Stage 1; `paymentMethod` defaults to COD in `CheckoutRequest`/`CheckoutQuoteRequest`.
- No external gateway, no card data, no secrets stored — the simulated intent is test-only scaffolding for Stage 2 part 1. A real online provider (intent creation, webhooks, capture) is deferred to a later Phase 7 step.
- `V20` is additive; no applied migration is edited (ADR-002/D-9).
- 7 integration tests + 17 unit tests + 9 slice RBAC tests all green; full backend unit suite 1133 tests pass.


## D-49: Signed webhook and simulated pay/fail provider (Phase 7 Stage 3)

**Status:** Accepted
**Date:** Phase 7 Stage 3 (task 7.4 part 2)

### Context

Stage 3 introduces the webhook endpoint that provider callbacks hit, plus a simulated test-mode provider so the online pay/resume flow can be exercised end-to-end without a real gateway. Decisions to settle: webhook authentication, signature scheme, RBAC ordering, simulator profile gating, prod startup validation, event outcome semantics, and late-event/duplicate handling.

### Decision

- **`POST /api/v1/payments/webhook` is `permitAll` and carries no JWT.** It is authenticated solely by the HMAC-SHA256 signature over `timestamp + raw body`, presented via `X-FlowerConnect-Timestamp` and `X-FlowerConnect-Signature: v1=<hex>` headers. A body-size cap (`app.webhook-max-body-bytes`) rejects oversized payloads before buffering.
- **Signature verified with constant-time comparison** (`MessageDigest.isEqual`) against `app.webhook-secret` (`APP_WEBHOOK_SECRET` env). A tolerance window (`app.webhookToleranceSeconds`) rejects stale timestamps.
- **`SecurityConfig` matcher ordering fixed:** the webhook `permitAll` rule is placed **before** the `/api/v1/payments/**` ? `hasRole("CUSTOMER")` rule so the webhook is reachable without authentication while all other payment endpoints remain CUSTOMER-only.
- **Prod startup validation:** `WebhookSecretValidator` (`@Profile("prod")`) fails application startup if the webhook secret is absent or shorter than 32 bytes, preventing a silently-inert webhook in production.
- **Simulator behind `!prod` profile:** `SimulatedPaymentProvider` + `SimulatorController` expose `POST /api/v1/payments/simulator/{orderId}/pay|fail` (customer auth + ownership required). The simulator generates a correctly signed event and delivers it through the same webhook verification path — no card-like fields, no external calls, no secrets stored.
- **Rate limiting:** webhook is IP-keyed via `RateLimitFilter` (no email available on webhook requests).
- **`PaymentService.processProviderEvent`** applies group transitions atomically via `OrderStateMachine.transitionGroup` (locks order rows ascending id). Success ? PAID/PLACED (ActorType.SYSTEM); failure ? FAILED/CANCELLED with reservation release. Late success after cancel/timeout ? REFUND_PENDING (no resurrection); failure after success ? no regression; duplicate event IDs ? idempotent. Each accepted event persists one `PaymentEvent` row transactionally.
- **`resumePayment` fixed** to not transition payment/order; returns pending state and marks intent EXPIRED on timeout only. Regression test added.
- **Tests:** `WebhookControllerIntegrationTest` 13/13 pass (real HTTP + MySQL + Testcontainers); `WebhookSignatureServiceTest`, `WebhookSecretValidatorTest`, `PaymentServiceTest` unit tests pass; `SimulatorControllerIntegrationTest` (5 tests), `SimulatorProdAbsenceIntegrationTest` (2 tests); full unit suite 1156 tests pass (1 environment-only failure: `AppPropertiesBindingTest` requires local MySQL `test` user).

### Consequences

- Webhook security rests entirely on signature verification; a leaked secret would allow forged callbacks, so the secret is treated as a production credential with startup validation.
- The simulator is compiled but inactive in prod (`!prod` profile), so it cannot leak test endpoints to production.
- Late-event and duplicate handling is explicit rather than implicit, preventing double-collection or resurrection of terminal orders.
- `V19`/`V20` migrations already created the payments tables; no new migration is needed for Stage 3.


## D-50: Customer payment UI polls payment state; resume is a refresh, not a completion (Phase 7 frontend Stage 3 / task 7.6)

**Status:** Accepted
**Date:** Phase 7 frontend stage (task 7.6)

### Context

The backend Stage 3 (webhook + simulator) is complete. The frontend needs to integrate the online checkout response and payment-group state APIs into the customer UI. Decisions to settle: where the payment-method selector lives, how payment state is polled, what the resume action does, and how the simulated pay/fail controls are exposed.

### Decision

- **Payment-method selector at checkout.** `CheckoutPage` gains a payment-method radio group (COD default; ONLINE offered because backend 7.4 is built). The selected method is sent as `paymentMethod` in the `CheckoutRequest`. On COD checkout the confirmation step navigates to `/orders`; on ONLINE checkout it navigates to `/payment/:paymentGroupId`.

- **Payment status page.** `PaymentStatusPage` at `/payment/:paymentGroupId` polls `GET /api/v1/payments/group/{paymentGroupId}` via TanStack Query with `refetchInterval` of 8 s (within plan §9.4's 5â€“10 s). Polling pauses while the tab is hidden (`usePolling`) and stops for terminal states (`PAID`, `FAILED`, `REFUNDED`). The page shows pending â†" paid/failed/expired/refund-pending states using existing design tokens and responsive layouts.

- **Resume is a refresh, not a completion.** The resume action calls `POST /api/v1/payments/{orderId}/resume`. After Stage 3 backend changes, `resumePayment` does *not* transition payment/order to paid â€” it returns the current pending state and marks the intent expired on timeout. Only the verified webhook flow transitions a payment to `PAID`/`PLACED`. The frontend resume button is shown only for pending orders in non-terminal groups.

- **No card-like data anywhere.** The frontend never collects, renders, stores, logs, or submits card numbers, CVV, or other card-like data. Online payment in v1 is a simulated test-mode provider (D-48/D-49).

- **Simulator controls are backend-only.** The frontend does not expose pay/fail controls directly. The simulated pay/fail endpoints (`POST /api/v1/payments/simulator/{orderId}/pay|fail`) are backend-only behind the `!prod` profile. The frontend only polls payment state and shows transitions.

- **Role protection.** `/payment/:paymentGroupId` is guarded by `ProtectedRoute roles={[CUSTOMER_ROLE]}`. Existing API/error-handling conventions (Axios interceptors, `ApiErrorInfo`, TanStack Query) are preserved.

- **COD checkout preserved.** The COD checkout flow is unchanged â€” COD remains the default payment method and navigates to `/orders` on success.

### Consequences

- The payment-method selector is a new checkout control; existing COD tests are updated to include `paymentMethod: "COD"` in the expected `placeCheckout` payload.
- The payment status page is a new route; no existing routes are modified.
- 7 new Vitest/RTL tests cover pendingâ†"terminal transitions, polling stop behavior, resume API call, terminal-state UI, and API error handling.
- Full frontend suite: lint, typecheck, tests, build all clean. Backend unit suite: 1157 tests, 0 failures.


## D-51: Reservation expiry job cancels stale `PENDING_PAYMENT` orders and releases stock (Phase 7 task 7.5)

**Status:** Accepted
**Date:** Phase 7 Stage 3.5 (task 7.5)

### Context

Online orders sit in `PENDING_PAYMENT` while the customer completes payment through the simulated gateway (task 7.4). If the intent expires before the customer pays, the order must be cancelled and its reserved stock released, otherwise reservations can leak forever. Decisions to settle: where the threshold lives, who writes the cancellation, how stock is released, and how failures in one candidate affect the batch.

### Decision

- **`PaymentExpiryService` is the sole owner of payment expiry.** It selects `PENDING_PAYMENT` orders whose `created_at` is strictly older than the configured threshold (`app.payment-expiry-minutes`, default 30) and cancels each through `OrderStateMachine.transition` (`PENDING_PAYMENT -> CANCELLED`, actor `SYSTEM`, reason `PAYMENT_EXPIRED_NO_RESPONSE`). The state machine's `SideEffect.RELEASE` delegates stock release to `InventoryService.releaseReservedStock`, which decrements `reserved_quantity` without touching `quantity` and appends exactly one `stock_movements` row of type `RELEASE`.

- **Each candidate is processed in its own `REQUIRES_NEW` transaction.** One failing order (e.g. insufficient reserved quantity for the release) never rolls back or blocks another candidate. The state machine locks the order row and re-checks its status under the lock, so a customer who resumed the order first leaves it `PLACED` and the expiry is skipped; an already-cancelled order is skipped without a duplicate history row or stock movement.

- **The threshold comes from `AppProperties`, not `platform_config`.** `paymentExpiryMinutes` (default 30) is a simple configuration property because payment expiry is only relevant when online payment exists and does not need the generic key/value table. The cron (`paymentExpiryCron`, default every minute) and batch bound (`paymentExpiryBatchSize`, default 100) follow the same pattern as `autoRejectCron`/`autoRejectBatchSize`.

- **The scheduler is a thin component** (`PaymentExpiryScheduler`) that owns only the cron and delegates all business logic to `PaymentExpiryService`. The cron is disabled in the test profile (`payment-expiry-cron: "-"`) so a scheduled run cannot fire mid-test; the service is driven directly with the injected `Clock` instead.

- **Tests:** `PaymentExpiryServiceTest` (2 unit tests for configuration reading) and `PaymentExpiryIntegrationTest` (13 integration tests vs real MySQL covering cutoff boundary, only `PENDING_PAYMENT` orders changed, reservation released without changing quantity, one history row with `SYSTEM` actor, rerun idempotency, customer resume wins race, one failing order does not block batch, batch bounds, threshold from `AppProperties`).

### Consequences

- Payment-expiry writes go through the same ownership boundaries as all other order status changes: `OrderStateMachine` is the sole writer of `Order#status`, `InventoryService` is the sole writer of stock levels and movements.
- No new migration: the `orders`, `order_status_history`, `inventory`, and `stock_movements` tables already exist and support `PENDING_PAYMENT` and the `RELEASE` movement type.
- Full backend unit suite: 1159 tests, 0 failures (2 new unit tests from `PaymentExpiryServiceTest`).


## D-52: Single cancellation endpoint for CUSTOMER, VENDOR, and ADMIN (Phase 8 Stage 1)

**Status:** Accepted
**Date:** Phase 8 Stage 1 (tasks 8.1, 8.2)

### Context

Plan task 8.2 specifies a single cancellation endpoint `POST /api/v1/orders/{id}/cancel` with a reason. Section 6.1 allows CUSTOMER, VENDOR, and ADMIN to cancel orders in different states, with different target statuses (CANCELLED or REJECTED) and different reason requirements. The existing `SecurityConfig` restricts `/api/v1/orders/**` to CUSTOMER only, but the cancellation endpoint must be reachable by all three roles.

### Decision

- **Single endpoint `POST /api/v1/orders/{id}/cancel` for all three roles.** A specific matcher in `SecurityConfig` permits CUSTOMER, FLORIST, and ADMIN on `POST /api/v1/orders/{orderId}/cancel`, placed before the broad `/api/v1/orders/**` â†’ CUSTOMER rule. The `CancellationService` derives the actor type from the authentication authorities and enforces ownership: CUSTOMER on their own order, VENDOR on their own order (including suspended vendors per D-6), ADMIN on any order.

- **`CancellationService` as orchestrator.** Validates actor, ownership, current state, and reason requirements, then delegates the status change to `OrderStateMachine`. It does not write order status, payment status, or stock directly. Payment status updates go through `PaymentService.markRefundPending`.

- **Target status resolution follows section 6.1 exactly:**
  - VENDOR on PLACED â†’ REJECTED (reason required)
  - CUSTOMER/ADMIN on PLACED â†’ CANCELLED (reason optional for CUSTOMER, required for ADMIN)
  - CUSTOMER/VENDOR/ADMIN on CONFIRMED â†’ CANCELLED (reason required for VENDOR/ADMIN)
  - VENDOR/ADMIN on PREPARING â†’ CANCELLED (reason required)
  - ADMIN on OUT_FOR_DELIVERY â†’ CANCELLED (reason required)
  - Terminal states (DELIVERED, CANCELLED, REJECTED) admit no transition (409).

- **Payment effects:** Paid online orders (paymentMethod=ONLINE, paymentStatus=PAID) become REFUND_PENDING via `PaymentService.markRefundPending`; COD before delivery leaves payment PENDING (Option B, per D-47). No money moves for COD cancellations before delivery.

- **Stock effects** delegated to `OrderStateMachine` â†’ `InventoryService` per section 6.2: RELEASE before acceptance, RESTOCK after acceptance but before PREPARING, NONE from PREPARING onward.

- **`OrderCancelled` event** published after commit with actor, reason, previous status. Slot capacity freed automatically via existing `countBookedOrdersForSlot` query (CANCELLED/REJECTED excluded from PLACED/CONFIRMED/PENDING_PAYMENT count).

- **Suspended vendors** may cancel in-flight orders (D-6 honored).

### Consequences

- No separate vendor/admin cancellation URLs; single endpoint with role-based ownership enforcement.
- `CancellationService` in `com.flowerconnect.order.service` (alongside `OrderStateMachine`), not a separate `dispute` package â€” cancellations are order transitions, not disputes.
- Integration test covers full status Ã— actor matrix, reason validation, exact stock quantities/movements, cancel-vs-accept race, duplicate cancellation, paid-online REFUND_PENDING, COD Option B, slot capacity, suspended vendor, RBAC (401, 403 foreign, 404, 200).


## D-53: CancellationService in order.service package (not dispute package)

**Status:** Accepted
**Date:** Phase 8 Stage 1

### Context

The plan's package layout (section 3) places `CancellationService` in a `dispute` package alongside `DisputeService`. However, cancellations are order state transitions governed by the section 6.1 state machine, not disputes. Disputes (Phase 8 task 8.4) are post-delivery customer complaints with admin resolution.

### Decision

Place `CancellationService` in `com.flowerconnect.order.service` alongside `OrderStateMachine`. This follows the architectural principle that order state transitions live in the order domain. The `dispute` package will be created later for the dispute workflow (customer raises, vendor responds, admin resolves).

### Consequences

- Clean separation: `order.service` owns order lifecycle; future `dispute.service` will own dispute lifecycle.
- No premature package creation; `dispute` package added when disputes are implemented (Stage 2+).


## D-54: PaymentService.markRefundPending for paid-online cancellations

**Status:** Accepted
**Date:** Phase 8 Stage 1

### Context

Plan 8.3: "Paid online orders: REFUND_PENDING â†’ REFUNDED (admin marks in v1...). COD orders cancelled before delivery: no money has moved." Section 6.1 says "refund if already paid" for PLACED â†’ REJECTED/CANCELLED. The state machine does not modify payment status; `PaymentService` is the sole writer of payment status (D-47).

### Decision

Add `PaymentService.markRefundPending(Long orderId)` that transitions both `payments.status` and `orders.payment_status` from PAID to REFUND_PENDING in one transaction. Called by `CancellationService` after `OrderStateMachine.transition` when cancelling a paid online order. Idempotent: if already REFUND_PENDING or REFUNDED, throws 409 CONFLICT.

### Consequences

- Refund state managed by `PaymentService` (sole writer of payment status per D-47).
- Admin refund queue (Stage 2) will use this to mark REFUNDED.
- No money moved in v1; admin manually processes refund or gateway refund if 7.4 exists.


## D-55: Admin refund queue and mark-refunded endpoint (Phase 8 Stage 2, task 8.3)

**Status:** Accepted
**Date:** Phase 8 Stage 2

### Context

Plan 8.3 requires an admin-only refund queue listing REFUND_PENDING payments and an endpoint to mark a refund complete. Section 6.1 says "refund if already paid" for cancellations/refunds. PaymentService is the sole writer of payment status (D-47, D-54). The admin needs to review pending refunds and mark them as completed, with audit trail.

### Decision

- **GET /api/v1/admin/refunds** -- paginated refund queue listing payments with payment_status = REFUND_PENDING. Supports optional vendorId, fromDate, toDate filters. Returns only fields needed to review: orderId, vendorName, paymentMethod, paymentStatus, orderStatus, amount, createdAt, customerEmail. No internal or unrelated customer data exposed. /api/v1/admin/refunds/** mapped to hasRole("ADMIN") in SecurityConfig, placed before the broad /api/v1/admin/** rule.

- **POST /api/v1/admin/refunds/{orderId}/mark** -- marks a REFUND_PENDING payment as REFUNDED. Requires a non-blank, length-limited (<= 500 chars) referenceNote in the request body. Returns RefundMarkedResponse with orderId, paymentStatus, refundedAt, referenceNote.

- **PaymentService.markRefunded(Long orderId, String adminEmail, String referenceNote)** is the sole writer of payment status for refunds. Validates:
  - Reference note non-blank and <= 500 chars (400 otherwise).
  - Order exists (404 otherwise), payment exists (404 otherwise).
  - Only REFUND_PENDING can be marked refunded; any other state returns 409 CONFLICT.
  - Already REFUNDED returns the current result idempotently (no duplicate refundedAt update).
  - Transitions both payments.status and orders.payment_status to REFUNDED in one transaction.
  - Stamps refundedAt from the injected Clock.
  - Writes one audit_log row: actor, action PAYMENT_REFUNDED, entity type PAYMENT, entity id (payment id), reason with before/after values and reference note.

- **Customer order detail** exposes refundedAt and paymentStatus from payment data via CustomerOrderResponse. Internal fields (commission, etc.) not exposed.

- **refundedAt column** added to payments table via additive migration V21__add_refunded_at_to_payments.sql. No commission, ledger, settlement, or dispute logic in this stage.

### Consequences

- Refund state is consistent between payments and orders because PaymentService updates both in one transaction.
- Idempotent mark-refunded prevents duplicate processing; already-refunded payments return current state.
- Audit trail records who marked what refund and when, with before/after status.
- Customer can see refund state and timestamp on their order detail page.
- No money is moved in v1; admin manually processes the refund externally.
- Disputes, dispute UI, ledger entries, and settlement logic belong to later Phase 8 stages.


## D-56: Cancellation UI patterns (Phase 8 Stage 3, task 8.6)

**Status:** Accepted
**Date:** Phase 8 Stage 3

### Context

Stage 1 and Stage 2 of Phase 8 implemented the backend cancellation endpoint (`POST /api/v1/orders/{id}/cancel` for CUSTOMER/VENDOR/ADMIN), the admin refund queue (`GET /api/v1/admin/refunds`), and the mark-refunded endpoint (`POST /api/v1/admin/refunds/{orderId}/mark`). Stage 3 adds the frontend UI for these features across three screens: customer order detail, vendor order board, and admin refunds page.

Three things needed settling:

**Why a customer's reason is optional but a vendor's and admin's are required.** The backend state machine enforces this: CUSTOMER cancellation of PLACED/CONFIRMED does not require a reason, while VENDOR cancellation of CONFIRMED/PREPARING and ADMIN cancellation of any non-terminal state does. The frontend mirrors the backend rather than duplicating the rule — each dialog's `requiresReason` flag drives both the rendered reason box and the zod resolver, so they cannot disagree (following D-30's precedent).

**Conditional dialog rendering to avoid framer-motion role conflicts.** Both `RejectReasonDialog` and `CancelReasonDialog` use `motion.div` with `animate={open ? "visible" : "hidden"}`. When always rendered (even with `open={false}`), testing-library's `findByRole("dialog")` cannot reliably resolve which dialog matches, causing flaky timeouts. Dialogs are therefore conditionally rendered (`{cancelTarget && <CancelReasonDialog ... />}`) so only the open dialog exists in the DOM.

**Dialog state reset on unmount.** When a dialog unmounts (cancel target set to null), its internal reason state resets to empty. This is intentional — each dialog instance starts fresh, so a vendor who opens cancel, types a reason, cancels, then reopens sees an empty reason field rather than stale text.

### Decision

- **Customer cancel (OrderDetailPage):** Cancel button shows for PLACED/CONFIRMED orders. Reason is optional (sent as `null` when blank). Refund status banner shows `REFUND_PENDING`/`REFUNDED` with `refundedAt` date when applicable.
- **Vendor cancel (OrderCard + VendorOrderBoardPage):** Cancel button shows for CONFIRMED/PREPARING orders. Reason is required. Double-click protection via `isPending` from `useVendorOrderAction`. 409 insufficient-stock errors surface a "reject instead" prompt.
- **Admin cancel (AdminOrderLookupPage):** Admin can cancel any non-terminal order. Reason is required.
- **Admin refund queue (AdminRefundsPage):** Paginated listing of `REFUND_PENDING` payments with optional vendor/date filters. Mark-refunded dialog requires a non-blank reference note (<= 500 chars). Idempotent: already-REFUNDED returns current state.
- **Dialogs render conditionally** based on their open state to avoid framer-motion role resolution conflicts with testing-library.
- **No disputes UI yet** — deferred to Phase 8 Stage 4+ per plan.

### Consequences

- The cancellation matrix is enforced by the backend; the frontend only offers the buttons the backend allows for each status/role combination.
- Conditional dialog rendering means `findByRole("dialog")` reliably finds the open dialog.
- Disputes (customer raises, vendor responds, admin resolves) are deliberately out of scope for Stage 3.


## D-57: Disputes backend (Phase 8 Stage 4, task 8.4)

**Status:** Accepted
**Date:** Phase 8 Stage 4

### Context

Phase 8 Stage 4 implements the disputes backend (plan task 8.4): customers raise disputes for delivered orders within a configurable window; vendors respond once; admins queue and review. Resolution (Stage 5) and UI (Stage 6) are deferred.

Key questions:
- **Dispute window**: Measured from the order's entry into `DELIVERED` (per `order_status_history`), not `orders.updated_at`, so a delayed delivery scan doesn't shrink the window. The window (default 7 days) lives in `platform_config` and is read at runtime.
- **One dispute per order ever**: Enforced by `uq_disputes_order` unique constraint on `order_id` at the database level. The service checks first for a clean 409; the constraint backstops concurrent races.
- **Vendor response**: One response per dispute, moving `OPEN` ? `UNDER_REVIEW`. A suspended vendor may still respond (D-6).
- **Reason codes**: Fixed vocabulary (`ITEM_NOT_AS_DESCRIBED`, `ITEM_DAMAGED`, `ITEM_MISSING`, `WRONG_ITEM_DELIVERED`, `QUALITY_COMPLAINT`, `LATE_DELIVERY`, `OTHER`) so the admin queue can be filtered/grouped. The list is in the service, not a DB CHECK, because it may evolve before resolution work.
- **RBAC**: Customers see only their own disputes; vendors see only disputes on their orders; admins see the full queue with status/date filters. Missing ? 404, foreign ? 403.
- **Event**: `DisputeOpened` published after commit (scalar-only payload).

### Decision

- **Migration V22** creates `disputes` table with `UNIQUE(order_id)`, `status` ENUM (`OPEN`, `UNDER_REVIEW`, `RESOLVED_REFUND`, `RESOLVED_PARTIAL`, `RESOLVED_REJECTED`), and seeds `platform_config.dispute_window_days = 7`.
- **Service** reads window from `platform_config`; falls back to 7 if missing. Window measured from `order_status_history` row where `to_status = DELIVERED`, using injected `Clock`.
- **Customer endpoints** (`/api/v1/disputes`): `POST /orders/{orderId}` to raise (validates reason code, description ?2000), `GET` list, `GET /{disputeId}` detail. All CUSTOMER-only, owner-only.
- **Vendor endpoints** (`/api/v1/vendor/disputes`): `GET` list (disputes on vendor's orders), `GET /{disputeId}` detail, `POST /{disputeId}/respond` (required response ?500 chars, moves `OPEN` ? `UNDER_REVIEW`). All FLORIST-only, owner-only. Suspended vendor may respond.
- **Admin endpoints** (`/api/v1/admin/disputes`): `GET` queue with optional `status`, `from`, `to` filters; `GET /{disputeId}` detail (full record). ADMIN-only. Resolution endpoint returns 501 (Stage 5).
- **Event** `DisputeOpened` published after commit with scalar payload (`disputeId`, `orderId`, `customerId`, `reasonCode`, `status`, `createdAt`).
- **SecurityConfig** adds `/api/v1/disputes/**` ? CUSTOMER, `/api/v1/vendor/disputes/**` ? FLORIST, `/api/v1/admin/disputes/**` ? ADMIN.
- **OrderStatusHistoryRepository** gains `findFirstByOrderIdAndToStatusOrderByCreatedAtDesc` for window measurement.

### Consequences

- Concurrency-safe: DB unique constraint + service-level check prevent duplicate disputes even under concurrent raise attempts (tested with 8 threads).
- Window measurement uses `DELIVERED` history entry, not `orders.updated_at`, so delayed scans don't shrink the customer's window.
- Fixed reason-code list in the service (not DB CHECK) allows evolution before Stage 5 resolution.
- Suspended vendors may respond to in-flight disputes (D-6 honored).
- Resolution, UI, and notifications are Stage 5+; `POST /admin/disputes/{id}/resolve` returns 501.


## D-58: Dispute resolution (Phase 8 Stage 5, task 8.5)

**Status:** Accepted
**Date:** Phase 8 Stage 5

### Context

Phase 8 Stage 5 implements dispute resolution (plan task 8.5): admins resolve OPEN or UNDER_REVIEW disputes to RESOLVED_REFUND, RESOLVED_PARTIAL, or RESOLVED_REJECTED. Key questions:

- **Refund amount representation**: The Payment entity previously had no field for the actual refund amount, so marking a payment REFUNDED for a partial refund would lose information about how much was actually refunded. A `refunded_amount` column (V24) was added to `payments` to record the actual refunded amount for both full and partial refunds.
- **Partial refund through PaymentService**: `PaymentService.markRefundedWithAmount` was added to handle partial refunds, accepting a `refundAmount` parameter that must be > 0 and <= payment amount, rounded HALF_UP to 2 decimals (INR). Full refunds default to the payment amount when `refundAmount` is null.
- **RESOLVED_REJECTED has no payment effect**: PaymentService is not called for rejected disputes; payment status is unchanged.
- **COD orders**: Payment status remains COLLECTED; the refund obligation is recorded on the dispute via `refund_amount` for out-of-band handling (Phase 10 settlement).
- **Order status preserved**: `order.status` remains DELIVERED; resolution never changes order state or stock.
- **Concurrency**: Dispute row is locked with `PESSIMISTIC_WRITE` before checking state, ensuring exactly one winner on concurrent resolution attempts.
- **Audit**: `DISPUTE_RESOLVED` audit row written with actor, action, timestamp, before/after resolution values.
- **Event**: Scalar-only `DisputeResolved` Spring event published after commit with Phase 10 hook data (dispute/order/vendor IDs, outcome, refund amount, payment method, resolver ID, occurrence timestamp).

### Decision

- **Migration V23** adds `refund_amount DECIMAL(10,2) NULL` to `disputes`.
- **Migration V24** adds `refunded_amount DECIMAL(10,2) NULL` to `payments`.
- **`Dispute` entity** gains `refundAmount` field mapped to `refund_amount`.
- **`ResolveDisputeRequest` DTO** gains `refundAmount` field with `@DecimalMin("0.01")` validation.
- **`DisputeService.resolveDispute`** locks the dispute row via `findByIdForUpdate`, validates status is OPEN/UNDER_REVIEW, validates refund amount rules per outcome, coordinates with `PaymentService` for online refunds, writes audit log, and publishes `DisputeResolved` event after commit.
- **`AdminDisputeController.resolveDispute`** replaces the 501 stub with the real ADMIN-only endpoint at `POST /api/v1/admin/disputes/{disputeId}/resolve`.
- **`PaymentService.markRefundedWithAmount`** handles partial and full refunds, idempotent when already REFUNDED with matching amount.
- **`Payment.markRefunded`** now delegates to `markRefundedWithAmount` with null amount (defaults to full payment amount).

### Consequences

- Online full/partial refunds go through PaymentService, never direct payment field writes.
- COD payment status stays COLLECTED; refund obligation recorded on dispute for out-of-band handling.
- Rejected disputes have no payment or stock effect.
- Order status (DELIVERED) and stock are never changed by dispute resolution.
- No Phase 10 ledger entries created in this stage.
- 11 new integration tests cover all resolution outcomes, partial amount bounds/rounding, final-state behavior, concurrent resolution, audit contents, event payload, online vs COD payment effects, unchanged order status, and ADMIN-only RBAC.


## D-59: In-app notification read API — recipient derived from the authenticated principal, namespace open to every authenticated role

**Status:** Accepted
**Date:** Phase 9 Stage 2

### Context

Phase 9 Stage 1 persisted notifications and wired event-driven delivery (V25, `Notification`, `NotificationService`, `NotificationListener`, async executor). Stage 2 (plan task 9.2) adds the read API so recipients can list their notifications, see the unread count, and mark items read. Two questions had to be settled before writing the endpoints.

First, who may call the API. Notifications are generated for both customers (order lifecycle, refund, dispute outcomes) and vendors (order lifecycle, dispute opened/resolved, vendor status changes). Restricting the namespace to a single role would either lock out one side or require a second namespace. Since every authenticated account holds exactly one of `CUSTOMER`, `FLORIST`, `ADMIN`, the namespace can be open to every authenticated role without exposing anything a caller isn't entitled to.

Second, how the recipient is identified. Accepting a user id from the request would let a caller probe another user's inbox, so no route takes a user identifier. The recipient is derived from the JWT subject on every call, and ownership is enforced in the repository queries themselves (`findByIdAndUserId`), so a missing or foreign notification id is a 404 and the caller never learns whether it exists.

### Decision

- **`GET /api/v1/notifications`** — one page of the caller's own notifications, newest first, with read state, returned as the shared `PageResponse<NotificationResponse>` envelope.
- **`GET /api/v1/notifications/unread-count`** — the caller's unread count.
- **`PATCH /api/v1/notifications/{id}/read`** — marks one of the caller's notifications read; a missing or foreign id is 404.
- **`POST /api/v1/notifications/read-all`** — marks all of the caller's notifications read, returns the number marked.
- The namespace `/api/v1/notifications/**` is mapped to `hasAnyRole("CUSTOMER", "FLORIST", "ADMIN")` in `SecurityConfig`, placed before the `.anyRequest().authenticated()` catch-all.
- The recipient is derived from `Authentication.getName()` (the JWT subject); no route accepts a user id. `NotificationService` resolves the user from the email and scopes every read/mark query by `userId`.
- `NotificationResponse` exposes only safe recipient-facing fields (category, title, message, optional deep-link target, read/create timestamps); internal event and entity details are omitted.
- SSE real-time delivery remains out of scope for Stage 2 and is deferred to a later stage; the read API is poll-based and does not depend on a live connection.

### Consequences

- A customer, florist, or administrator each sees only their own notifications; cross-user access is not expressible in the request.
- Forgetting `@RequiresApprovedVendor` on future catalog/inventory/order-creation routes still fails open — that review item from D-13 is unchanged; notifications are personal reads, not vendor transactions.
- Because the recipient is read from the JWT per request, an admin approval or suspension takes effect on the next inbox read with no token re-issue, consistent with D-13.


## D-60: Email notifications are async, fire-and-forget, with plain-text templates

**Status:** Accepted
**Date:** Phase 9 Stage 3

### Context

Phase 9 Stage 3 (plan task 9.3) requires email notifications for order events, vendor lifecycle events, refunds, and disputes. The sender must be asynchronous, failures logged but never break the business flow, and MailHog used for local development.

### Decision

- **Templates**: Plain-text only, rendered by `EmailTemplateService`. No HTML/template engine dependency. Templates cover: order placed (customer & vendor), order status changes (customer), refunds (customer), disputes (customer & vendor), vendor lifecycle (approved/rejected/suspended/reinstated). INR formatting, plain text.
- **Listener**: `EmailNotificationListener` with `@Async("notificationExecutor")` + `@TransactionalEventListener(phase = AFTER_COMMIT)` for each event type. Recipients resolved from JWT subject via `UserRepository`. Email failures logged and swallowed — never break the originating transaction.
- **Sender**: `EmailSender` interface with `SmtpEmailSender` (MailHog in dev, real SMTP in prod via `application-prod.yml`) and `NoOpEmailSender` (fallback when no SMTP configured). `EmailSender.send(to, subject, body)` is fire-and-forget.
- **MailHog**: Already in `docker-compose.yml` (port 1025) and `application-dev.yml` (`mailhog:1025`).

### Consequences

- Email failures never roll back orders, payments, vendor actions, or disputes.
- Plain-text templates avoid HTML/encoding issues and keep dependency footprint minimal.
- MailHog in dev means emails are visible at `http://localhost:8025` without real SMTP credentials.
- Real SMTP credentials stay in environment variables (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`) — never committed.

---

## D-61: Polling-based live updates for vendor alerts and customer order tracking

**Status:** Accepted
**Date:** Phase 9 Stage 4

### Context

Plan task 9.4 requires polling-based live updates: vendor new-order alerts (toast + sound) and customer order status tracking. SSE (task 9.5) is deferred. The existing `usePolling` hook (8s interval, respects `document.hidden`, stops on terminal states) is the foundation.

### Decision

- **Vendor new-order alert**: `useNewOrderAlert(orders)` hook tracks seen order IDs in a `Set`. On each poll, newly appeared `PLACED` orders trigger a toast (`NewOrderToast`) and a Web Audio API chime. Deduplication via `Set` of seen order IDs prevents repeat alerts. Sound uses Web Audio API (sine wave chime), unlocked on first user interaction per browser autoplay policy.
- **Customer order status toast**: `useOrderStatusToast(order)` hook compares current vs previous status, shows contextual toast (`StatusToast`) with type-appropriate styling (info/success/warning/error) and human-readable message (e.g., "Order status changed: confirmed ? out for delivery").
- **Toast components**: `NewOrderToast` (vendor, amber, auto-dismiss 8s) and `StatusToast` (generic, info/success/warning/error, auto-dismiss 6s). Both dismissible manually.
- **Polling**: Reuses existing `usePolling` (8s, respects `document.hidden`, stops on terminal states/page hidden). No new polling infrastructure.

### Consequences

- No SSE infrastructure needed for Phase 9; polling is sufficient for v1.
- Sound is best-effort: fails silently if Web Audio API unavailable or autoplay blocked.
- Deduplication prevents alert fatigue; `Set` of seen order IDs resets on page reload (acceptable trade-off).
- Toast components follow existing UI patterns (`PRESSABLE`, `FOCUS_RING`, `animate-slide-up`).
- Polling stops on terminal states (`DELIVERED`, `CANCELLED`, `REJECTED`) and when tab hidden — no wasted requests.
- 18 backend unit tests and 8 backend integration tests cover list pagination/newest-first, unread count, mark-one-read ownership (own success, foreign 404, missing 404), mark-all-read, RBAC (anonymous 401, every authenticated role 200), and the error envelope; the frontend NotificationBell and SiteHeader wiring are covered by 25 frontend tests.


