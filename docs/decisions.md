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
