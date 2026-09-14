# Progress

Tracks what has been implemented and what remains. Updated after each session.

## Current Phase

**Phase 1 — Authentication** (complete)

JWT-based authentication system with access/refresh token rotation, BCrypt password hashing, and Spring Security filter chain. All backend endpoints implemented and unit-tested (51 tests pass). Integration tests require Docker running.

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

#### Phase 1 — Authentication (Complete)
- [x] V2 migration: `refresh_tokens` table with SHA-256 token hashes
- [x] V3 migration: Seed CUSTOMER, FLORIST, ADMIN roles
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
- [x] Current user endpoint (`GET /api/v1/users/me`)
- [x] DTOs with Bean Validation + MapStruct mappers
- [x] Global exception handler (@RestControllerAdvice)
- [x] Scheduled cleanup job for expired/revoked refresh tokens
- [x] Unit tests: 51 tests pass (JWT, auth service, controllers, mappers)
- [x] Integration tests: 4 tests (require Docker + Testcontainers MySQL 8)
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
| 2026-09-14 | Fixed unit test failures across all test classes | `JwtService.java`, `AuthControllerTest.java`, `UserControllerTest.java`, `AuthServiceTest.java`, `RefreshTokenServiceTest.java` |
| 2026-09-14 | Completed Phase 1 authentication backend | V2/V3 migrations, auth services, controllers, 51 unit tests |

## Session Notes

- Working on Windows; use PowerShell paths (e.g., `./mvnw` works, `.\mvnw` also works).
- `mvnw.cmd` is gitignored — Windows users should use `./mvnw` which delegates to the wrapper.
- `package-lock.json` is gitignored — use `npm install`, not `npm ci`, for local dev.
- All Kilo configuration lives in `kilo.jsonc` (validated). Agent and command `.md` files in `.kilo/` directories fail YAML validation in this Kilo CLI build.
- `docs/progress.md` is the ground truth for unfinished work — always check before starting new tasks.
- Phase 1 backend complete with 51 unit tests passing. Integration tests (4 tests) require Docker running for Testcontainers MySQL 8.
