# FlowerConnect — Project-Wide Agent Instructions

Hyperlocal flower marketplace. Spring Boot 3.2 (Java 17) backend + React 18/TS/Vite frontend.
Currently Phase 0 (scaffold: user/role DB schema, placeholder UI, no business logic).

> **Full reference:** See `docs/architecture.md`, `docs/progress.md`, `docs/decisions.md`, `docs/known-issues.md`, and `kilo.jsonc`.

## Quick Summary

```
frontend/ (React 18, TS, Vite)     backend/ (Spring Boot 3.2, Java 17)
│   src/app/                      │   src/main/java/com/flowerconnect/
│     router.tsx                  │     FlowerConnectApplication.java
│     providers/                  │   src/main/resources/
│       query-client.ts           │     application.yml
│       theme.tsx                 │     db/migration/V1__baseline.sql
│     styles/index.css            │
│   src/features/auth/            │
│     stores/auth-store.ts        │
│   src/shared/lib/api.ts         │
└─► :8080/api/v1 └─► MySQL 8 (Docker)
```

## Technology Stack

| Layer  | Tech                              |
|--------|-----------------------------------|
| Backend| Spring Boot 3.2.5, Java 17, Maven |
| DB     | MySQL 8 (InnoDB, utf8mb4)         |
| Cache  |None in v1 (Redis dropped by plan v2.2; removed in stage 6)|
| ORM    | Spring Data JPA + Hibernate       |
| Mig.   | Flyway (baseline V1)              |
| Codegen| Lombok, MapStruct                 |
| Auth   | Spring Security (planned), JWT    |
| Frontend| React 18, TS, Vite, Tailwind 3   |
| State  | Zustand (persist), TanStack Query |
| Forms  | react-hook-form + zod             |
| HTTP   | Axios + interceptors              |
| Tests  | Spring Boot Test, Vitest (jsdom)  |

## Session Startup Checklist

1. Read `AGENTS.md` → this file
2. Read `docs/progress.md` (primary source for unfinished work)
3. Read `docs/architecture.md`, `docs/decisions.md`, `docs/known-issues.md`
4. Run `git status` and `git log --oneline -10`
5. Inspect code relevant to the current task

## Autonomous Workflow (Optimized for Limited Models)

When given a clear task, operate as an autonomous senior engineer. **Keep tasks small and incremental — break complex work into multiple sessions.**

1. **Understand** — Read only the relevant docs + code for this task. Avoid re-reading files already explored (log paths in `docs/progress.md`).
2. **Plan** — Create a concise todo list. Note affected files. Estimate complexity.
3. **Implement** — Write minimal, focused changes following conventions. Commit discoveries to `docs/` before context fills up.
4. **Test** — Run the specific test that covers your change. `./mvnw test` (backend), `npm run test` (frontend).
5. **Validate** — Run lint, typecheck, or build for the affected layer only.
6. **Review** — Check for regressions in the changed code.
7. **Document** — Update `docs/progress.md` (what changed), `docs/decisions.md` (if a decision was made), `docs/known-issues.md` (if a bug was found). Update immediately — don't wait until context is full.
8. **Report** — Summarize what was done, blockers, and next steps.

**Critical rules for limited/free models:**
- **Never claim success without running tests or build checks.**
- **If a test fails, diagnose and fix it before continuing.** Do not skip or work around failures.
- **Prevent infinite loops:** The autonomous-engineer agent has `steps: 20` max iterations. If iterations exhaust, record the blocker in `docs/known-issues.md` and report.
- **Write discoveries early:** Before context grows, write decisions, bugs, and progress to `docs/`. This persists state across sessions.
- **Use `docs/progress.md` as ground truth** for what's done vs. pending. Always check it before starting new work.
- **Read only what you need:** Use Grep/Glob to narrow down files. Don't re-read entire files you've already seen this session.

## Self-Maintenance

After any significant change:

| Change type             | Update file               |
|-------------------------|---------------------------|
| Architecture changes    | `docs/architecture.md`    |
| New technical decisions | `docs/decisions.md`       |
| Completed/pending work  | `docs/progress.md`        |
| Bugs or limitations     | `docs/known-issues.md`    |
| New permanent coding rules | `AGENTS.md` (needs user approval) |

## Coding Standards

### Backend (Java / Spring Boot)

- Java 17, `@Slf4j` (Lombok), `@Validated` on controllers.
- Three-layer: Controller → Service → Repository. Controllers thin (DTOs via MapStruct).
- Database via Spring Data JPA repositories; Flyway for schema. Migrations additive (`V2__`, `V3__`...). Never edit applied migrations.
- REST: `api/v1` prefix, `ResponseEntity` with proper HTTP status.
- Error handling: `@RestControllerAdvice` with structured JSON.
- Validation: `javax.validation` on DTO fields.
- Security: Spring Security 6, BCrypt passwords, JWT Bearer tokens. All endpoints except `/actuator/health` require auth.
- Tests: `*ServiceTest`, `*ControllerTest`, `*RepositoryTest`.

### Frontend (React / TypeScript / Vite)

- TypeScript strict. All new files: `.ts` or `.tsx`.
- React 18 function components, hooks-based.
- Tailwind CSS utility classes. Zustand (auth state), TanStack Query (server data).
- Forms: `react-hook-form` + `zod`. Routing: `react-router-dom` v6 (`src/app/router.tsx`).
- API calls through `src/shared/lib/api.ts`. Feature-sliced layout in `src/features/`.
- Tests: Vitest + @testing-library/react. Lint: `npm run lint`. Build: `npm run build`.
- 2-space indent (Prettier). ESLint: `@typescript-eslint/recommended`.

### Git

- Check `git status` before changes. Do not commit `.env`, `target/`, `node_modules/`, `dist/`.
- Atomic commits with Conventional Commits style. Commit only when asked.

## Security

- Never commit secrets. `.env` is gitignored; `.env.example` is the template.
- JWT secret: 256-bit, via `JWT_SECRET` env var. DB credentials via `DB_USER`/`DB_PASS`/`DB_URL`.
- Backend validates all input. CORS restricted to `APP_CORS_ORIGINS`. Passwords hashed with BCrypt.
- Kilo config denies read access to `.env*` and build artifacts. Git push/rebase/reset require confirmation.

## Common Command Reference

| Task              | Command                                      |
|-------------------|----------------------------------------------|
| Backend dev       | `cd backend && ./mvnw spring-boot:run`       |
| Backend build     | `cd backend && ./mvnw clean package`         |
| Backend tests     | `cd backend && ./mvnw test`                  |
| Backend migrations| `cd backend && ./mvnw flyway:info`           |
| Frontend dev      | `cd frontend && npm run dev`                 |
| Frontend build    | `cd frontend && npm run build`               |
| Frontend lint     | `cd frontend && npm run lint`                |
| Frontend tests    | `cd frontend && npm run test`                |
| Full stack        | `docker compose up --build`                  |
| Backend health    | `curl http://localhost:8080/actuator/health` |
| Frontend health   | `curl http://localhost:5173/`                |
| Kilo commands     | `/build-check`, `/self-maintain`             |
