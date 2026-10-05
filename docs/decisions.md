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

