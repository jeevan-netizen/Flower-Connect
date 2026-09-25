# Progress

Tracks what has been implemented and what remains. Updated after each session.

## Current Phase

**Phase 1 — Authentication** (Realigning to plan v2.2)

JWT-based authentication system with access/refresh token rotation, BCrypt password hashing, and Spring Security filter chain. Backend implemented and tested (67 unit tests + 32 integration tests pass). Frontend authentication implemented with login/register UI, protected routes, token refresh/retry interceptor, and 41 frontend tests passing. Currently being realigned to plan v2.2 on branch `plan-v2-2-alignment`.

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
- [x] Run available frontend Vitest tests
- [x] Run TypeScript type checks
- [x] Run ESLint
- [x] Verify backend + frontend health endpoints
- [x] Validate Flyway migrations (V1 baseline applied and validated)
- [x] **0.7 Error framework**: `ErrorCode` enum (VALIDATION_FAILED, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, RATE_LIMITED, ACCOUNT_SUSPENDED), `BusinessException` base with factory methods, `@RestControllerAdvice` handling with injected `ObjectMapper` + `Clock` bean, bean-validation and malformed-JSON error handling, Spring Security `AuthenticationEntryPoint`/`AccessDeniedHandler` returning structured `ErrorResponse` (401/403), all 4 existing exceptions refactored to extend `BusinessException`
- [x] **0.8 Profiles**: `application-dev.yml` created, added `forward-headers-strategy`, `app.base-url`, `jwt.refresh-grace-seconds` to `application.yml`/`application-prod.yml`/`application-test.yml`, `AppProperties` registered in `@EnableConfigurationProperties`
- [x] **0.T Test baseline**: Surefire excludes `integration` tag by default; Maven `integration` profile runs tagged tests via `mvn verify -Pintegration`; `IntegrationTestBase` renamed to `AbstractIntegrationTest` with singleton Testcontainers MySQL (started in static initializer, NOT `@Container`); 67 unit tests + 32 integration tests pass
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
- [x] Current user endpoint (`GET /api/v1/users/me`)
- [x] DTOs with Bean Validation + MapStruct mappers
- [x] Global exception handler (@RestControllerAdvice)
- [x] Scheduled cleanup job for expired/revoked refresh tokens
- [x] Unit tests: 72 tests pass (JWT, auth service, controllers, mappers, error handling, AppProperties binding)
- [x] Integration tests: 34 tests pass (all pass, single Testcontainers MySQL 8 container)
- [x] Frontend types (`types.ts`): RegisterRequest, LoginRequest, RefreshRequest, AuthResponse, UserResponse, ErrorResponse
- [x] Auth API client (`api.ts`): register, login, refresh, logout, fetchCurrentUser wrappers
- [x] Zustand auth store (`auth-store.ts`): login, register, refresh, logout, loadCurrentUser, setAuth with persistence
- [x] Axios interceptors (`shared/lib/api.ts`): request interceptor (Bearer token on non-public endpoints), response interceptor (401 handling with singleton refresh promise, retry-once, `/auth/refresh` excluded from refresh logic to prevent deadlock)
- [x] Login page (`LoginPage.tsx`): email/password form with zod validation, loading state, backend errors, navigation with `from` redirect
- [x] Register page (`RegisterPage.tsx`): fullName/email/phone/password/confirmPassword form with zod validation, CUSTOMER role only
- [x] Auth hooks (`useAuth.tsx`): RequireAuth/RequireUnauth guards, useInitAuth for initial user load, hasLoadedInitial edge case fix (sets true even when /users/me fails)
- [x] Router (`router.tsx`): /browse, /cart, /orders protected; /login, /register require unauth
- [x] Frontend tests: 41 tests pass (auth-store, LoginPage, RegisterPage, useAuth, api interceptors) — smoke test plus 40 new tests

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
| 2026-09-25 | Stage 4c: cookie-authenticated refresh/logout CSRF mitigation | `AuthController.java`, `AuthApiIntegrationTest.java`, `AuthControllerTest.java`, `shared/lib/api.ts`, `shared/lib/api.test.ts`, `docs/progress.md` |
| 2026-09-22 | Stage 2c: ddl-auto validate, shared TestClockConfig replaces @MockBean Clock, test property cleanup | `application-test.yml`, `TestClockConfig.java`, `AuthControllerTest.java`, `UserControllerTest.java`, `RoleBoundaryTest.java` |

## Session Notes

- Working on Windows; use PowerShell paths (e.g., `./mvnw` works, `.\mvnw` also works).
- `mvnw.cmd` is gitignored — Windows users should use `./mvnw` which delegates to the wrapper.
- `package-lock.json` is gitignored — use `npm install`, not `npm ci`, for local dev.
- All Kilo configuration lives in `kilo.jsonc` (validated). Agent and command `.md` files in `.kilo/` directories fail YAML validation in this Kilo CLI build.
- `docs/progress.md` is the ground truth for unfinished work — always check before starting new tasks.
- Phase 1 backend implemented, realigning to plan v2.2: 76 unit tests and 37 integration tests pass, with a single Testcontainers MySQL 8 container (singleton pattern).
- Phase 1 frontend auth implemented, realigning to plan v2.2: login/register UI, auth API client, Zustand store, route guards, token refresh/retry interceptor; 41 frontend tests pass (including the smoke test).
