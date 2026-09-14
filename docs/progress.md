# Progress

Tracks what has been implemented and what remains. Updated after each session.

## Current Phase

**Phase 0 — Scaffold** (complete)

The project has a working repository scaffold with database schema and placeholder UI. No business logic or API endpoints are yet implemented. All Phase 0 verification checks pass: backend builds, Flyway V1 applies and validates, frontend builds, tests pass, type checks pass, lint passes, and the full stack boots against a local MySQL instance with health endpoints responding.

## Completed Work

### Phase 0 — Scaffold (Complete)

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
  - `docker-compose.yml`: MySQL 8, Redis 7, backend, frontend
  - `.env.example` with all environment variable templates
  - `.gitignore` for Maven, Node, env files, IDE artifacts
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
- [x] Validate Flyway migrations (V1 baseline applied and validated)
- [x] Run available frontend Vitest tests
- [x] Run TypeScript type checks
- [x] Run ESLint
- [x] Verify backend + frontend health endpoints

#### Phase 1 — Authentication (Not Started)
- [ ] User registration endpoint (`POST /api/v1/auth/register`)
- [ ] Login endpoint (`POST /api/v1/auth/login`) — issue JWT access + refresh tokens
- [ ] Token refresh endpoint (`POST /api/v1/auth/refresh`)
- [ ] Logout endpoint (`POST /api/v1/auth/logout`)
- [ ] Current user endpoint (`GET /api/v1/users/me`)
- [ ] Spring Security 6 filter chain with JWT validation
- [ ] Password hashing with BCrypt
- [ ] Frontend login/register UI with react-hook-form + zod
- [ ] Protected routes and auth guard in React Router
- [ ] Token storage and refresh logic in `api.ts`

#### Phase 2 — Catalog (Not Started)
- [ ] Florist entity and catalog CRUD
- [ ] Product browsing UI
- [ ] Search and filtering

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
| 2026-09-14 | Initial Phase 0 scaffold established     | All files (initial commits)                          |
| 2026-09-14 | Created persistent project memory (optimized for limited models) | `AGENTS.md`, `CLAUDE.md`, `docs/*`, `kilo.jsonc` |

## Session Notes

- Working on Windows; use PowerShell paths (e.g., `./mvnw` works, `.\mvnw` also works).
- `mvnw.cmd` is gitignored — Windows users should use `./mvnw` which delegates to the wrapper.
- `package-lock.json` is gitignored — use `npm install`, not `npm ci`, for local dev.
- The project is currently in a clean git state with no uncommitted changes.
- All Kilo configuration lives in `kilo.jsonc` (validated). Agent and command `.md` files in `.kilo/` directories fail YAML validation in this Kilo CLI build.
- `docs/progress.md` is the ground truth for unfinished work — always check before starting new tasks.
