# FlowerConnect: Agent Rules

These rules apply for the whole project, from Phase 0 to the final phase. Read this file at the start of every session. If a rule here conflicts with the implementation plan, stop and ask; do not guess. The implementation plan (latest revision) remains the source of truth for tasks and domain rules; this file governs **how** you work.

---

## 1. Working process

1. **One phase at a time.** Never start work from a later phase, even "while you're there".
2. **Before each phase**, present a short plan: the tasks you will do, files/packages you will touch, migrations you will add, and open questions. **Wait for explicit confirmation** before writing code.
3. **Before each major phase** (Phases 0, 2, 5, 6, 10 in particular), also explain the architecture and ER changes it introduces.
4. **Follow the plan's task numbers.** Refer to tasks by number (e.g. "1.3") in commits, docs and reports.
5. **Ask, don't assume,** when the plan is ambiguous or contradicts the existing code. State the options and a recommended default; do not silently pick one.
6. **Do not expand scope.** No extra features, refactors, dependency upgrades or "improvements" that the current phase does not call for. Note ideas in `docs/known-issues.md` instead.
7. **Never cut** the protected items: concurrency tests (5.T), the RBAC checklist, and security items 13.1, 13.2 and 13.6. If time is short, follow the plan's cut order (Phase 15, then analytics, POS, SSE, online gateway, reviews).

## 2. Editing and tooling

1. **Do not use Python scripts** (or sed/awk/one-liner scripts) to edit files. Make edits manually or with your edit tool, one file at a time.
2. Read a file before editing it. Re-read it afterwards if the edit was large.
3. Make small, reviewable changes. Keep diffs focused on the task; do not reformat unrelated code.
4. Do not run destructive commands (deleting directories, dropping databases, `git reset --hard`, force-push, `docker compose down -v`) without asking first.
5. Never edit an applied Flyway migration. Add a new `Vn__description.sql`.
6. Never commit secrets, `.env` files, keys or real credentials. Only `.env.example` with placeholders.
7. Use `docker compose` (space syntax). Inside containers use service names (e.g. `mysql:3306`); never read host `.env` values meant for local tooling.
8. Provide both `.sh` and `.ps1` variants of any script; commands should work on Windows as well as Linux/macOS.

## 3. Technology and conventions

| Topic | Rule |
|---|---|
| **Stack** | React 18 + Vite + TypeScript (frontend); Java 17 + Spring Boot 3.x (backend); MySQL 8. Modular monolith; no microservices. No Redis in v1 |
| **API** | Every endpoint under `/api/v1/...` |
| **Roles** | Seeded `roles` table (`CUSTOMER`, `VENDOR`, `ADMIN`); `users.role_id` FK; one role per user. Use the same role names everywhere |
| **Money** | `DECIMAL(10,2)` / `BigDecimal`, INR, `HALF_UP` to 2 decimals. Never `double` or `float` |
| **Time** | Store timestamps in UTC. Inject a `Clock` bean into anything time-dependent (schedulers, expiry, timeouts, tokens). Never call `Instant.now()` / `LocalDateTime.now()` directly in business logic |
| **Errors** | One `ApiError` shape via a global `@RestControllerAdvice`. Use `BusinessException` factories (`badRequest`, `notFound`, `forbidden`, `conflict`); no `new` on static factories. Never expose stack traces |
| **JSON** | Inject Spring's `ObjectMapper`; never construct one manually |
| **Ownership** | Foreign resource returns **403**; nonexistent resource returns **404**. Distinguish them explicitly |
| **Queries** | Named parameters only. No string-concatenated JPQL/SQL |
| **Pagination** | Every list endpoint is paginated |
| **Entities** | Table and entity names in the plan are the design baseline; if the code differs, map names but keep the semantics |
| **Docs** | Every endpoint documented in Swagger UI with its required roles |

## 4. Domain rules (implement each in exactly one service)

### Order state machine (`OrderStateMachine`)
- Every status change goes through this one service, is validated against the plan's transition table, and writes an `order_status_history` row (actor, reason, timestamp). Illegal transition returns 409.
- `DELIVERED`, `CANCELLED`, `REJECTED` are terminal. Post-delivery problems go through disputes.

### Inventory (`StockService`)
- `available = quantity - reserved_quantity`.
- **Reserve at checkout; deduct when the vendor accepts** (`PLACED -> CONFIRMED`). This decision is final.
- Before acceptance: vendor reject, cancel, auto-reject, payment failure or timeout all **release** the reservation only.
- After acceptance: cancelled before `PREPARING` restocks; cancelled from `PREPARING` onward does not restock.
- Accept re-checks `quantity >= q`; if not, return 409.
- Every stock change writes a `stock_movements` row.
- Use pessimistic locks (`SELECT ... FOR UPDATE`) on inventory rows **ordered by product id** for every stock operation. POS uses the same discipline.
- Manual adjustments may not push `quantity` below `reserved_quantity`.
- Multi-shop checkout is atomic: one failure rolls back every shop.

### Pricing and settlement (`PricingService`, billing)
- Delivery reach = `distance <= vendor.delivery_radius_km`. No polygons.
- `delivery_fee = base + per_km x distance`, or 0 when the shop subtotal >= `free_delivery_above`.
- Commission applies to the **item subtotal only**; snapshot the applied rate on the commission row.
- Ledger entries are created only at `DELIVERED`. Online: `+(S - C + D)`. COD: `-(C + P)`. POS: no ledger entry.
- Prices are tax-inclusive; no tax computation (D-3).

### Location
- **No GPS, no map service.** City / area / pincode come from the seeded `service_locations` table, which is the only source of coordinates. Distance is Haversine between centroids.

## 5. Security rules

1. **Tokens:** access token (15 min) lives in memory only; refresh token (7 days) lives in an httpOnly, Secure, SameSite=Strict cookie scoped to `/api/v1/auth`. Never put tokens in `localStorage` or `sessionStorage`.
2. Refresh tokens are stored hashed, rotated on every use, with reuse detection, and revoked on logout, password reset and status change.
3. Cookie-authenticated endpoints (`/auth/refresh`, `/auth/logout`) require the custom header and an `Origin` check. CORS allows credentials only for the exact configured origin.
4. Account status is enforced at login, refresh and on **every request** (JWT filter status lookup). `SUSPENDED` and `DISABLED` accounts cannot keep using an issued token.
5. Login, register, refresh and forgot/reset password are rate-limited (429 with `Retry-After`); limits live in configuration.
6. Passwords: BCrypt. Reset tokens: single-use, expiring, hashed. Forgot-password gives an identical response whether or not the email exists.
7. Role checks with `@PreAuthorize` on every endpoint; default deny. No endpoint is public unless the plan says so.
8. Uploads: validate content type by sniffing (not extension), enforce size limits, generate random file names, store behind `StorageService`.
9. Admin and settlement actions are written to the audit log.
10. No secrets in the repository; production config comes from environment variables only.

## 6. Testing and Definition of Done

A phase is **closed only when all of these are true, backed by real terminal output**:

1. Backend compiles and the app boots cleanly against MySQL.
2. New unit tests pass (`./mvnw test`) and at least one integration test for the phase's critical flow passes (`./mvnw verify -Pintegration`; Docker must be running).
3. **RBAC checklist** for every new endpoint: one test each for unauthenticated (401), wrong role (403), foreign resource (403), nonexistent resource (404), correct role (2xx).
4. Frontend `npm run lint`, `npm run typecheck`, `npm test`, `npm run build` pass (when the phase has UI).
5. A manual end-to-end check was performed with real requests or the UI.
6. `docs/progress.md` and `docs/known-issues.md` are updated and the work is committed.

Additional testing rules:
- Tests are part of the phase, not deferred. Write test tasks (marked **T**) in the same phase.
- Concurrency-sensitive code (checkout, stock, POS) requires real concurrent tests, not just mocks.
- Time-dependent code is tested with a fake/injected `Clock`.
- If Docker is unavailable, do **not** mark the phase done; record it in `docs/known-issues.md`.
- `scripts/verify.sh` / `scripts/verify.ps1` must pass before a phase is closed.

## 7. Reporting honestly

1. **Never claim something works without running it.** Paste or summarise the actual command and its output.
2. Report failures, skipped tests and unverified items plainly. Do not hide them, and do not mark them done.
3. If you could not run something (Docker down, tool missing), say so and log it in `docs/known-issues.md`.
4. When finishing a phase, give a report: tasks completed (by number), tests added and results, manual checks done, known issues, and the questions for the next phase.
5. Do not describe code you have not read or changes you have not made.

## 8. Frontend rules

1. TypeScript strict; no `any` without a comment explaining why.
2. Validate forms with react-hook-form + Zod. Server state through TanStack Query; client state through Zustand.
3. Axios uses `withCredentials`. The 401 interceptor refreshes once (single-flight), retries, and skips public endpoints (a 401 from `/auth/refresh` must not trigger another refresh).
4. The "initial load finished" flag is set on session-restore success **and** failure; no stuck spinners.
5. Route guards use `<ProtectedRoute roles={[...]} />`.
6. Responsive targets: customer (mobile), vendor dashboard (desktop/tablet), POS (tablet).
7. Status updates use polling in v1; SSE is optional and must use a fetch-based client (`EventSource` cannot send an `Authorization` header).

## 9. Git and documentation

1. Conventional commit messages (`feat:`, `fix:`, `test:`, `docs:`, `chore:`), referencing the task number.
2. One logical change per commit; do not commit broken builds.
3. Keep `docs/decisions.md` current (D-1 onward). Record every decision that changes the plan.
4. Keep `docs/progress.md` (what is done, per phase) and `docs/known-issues.md` (what is not) current at every phase close.
5. Keep the README, demo script and ER diagram accurate as the project evolves; the demo must run from a clean clone.

## 10. Decisions already made (do not reopen without asking)

| # | Decision |
|---|---|
| D-1 | COD only in v1; online gateway (7.4) is optional |
| D-2 | Vendor self-delivery; no delivery-partner role |
| D-3 | Prices are tax-inclusive; no tax computation |
| D-4 | No GPS; seeded `service_locations` for city/area/pincode |
| D-5 | Access token in memory; refresh token in httpOnly cookie |
| D-6 | Suspended vendor is hidden from discovery immediately; in-flight orders continue |
| D-7 | AI demand prediction is stretch only (Phase 15), first to be cut |
| — | Stock is deducted on vendor accept, not at PLACED |

## 11. Do-not list

- Do not use Python scripts to edit files.
- Do not start the next phase without confirmation.
- Do not edit applied migrations.
- Do not use `double`/`float` for money.
- Do not store tokens in browser storage.
- Do not build GPS, Redis, delivery polygons or a delivery-partner role.
- Do not skip tests, the RBAC checklist or the security items to save time.
- Do not mark a phase complete without real verification output.
- Verification output means the verbatim command output (test summaries, BUILD result, `git status`), pasted into the report. A prose summary of results does not count. Never commit, amend, or push unless the prompt says to.
- Do not commit secrets.
