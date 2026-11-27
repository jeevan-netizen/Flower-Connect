# Merge Review: `b2b-module`

> **Status: Ready for merge review — do not merge or push without explicit approval.**

Prepared: 2026-11-26. Branch has not been merged or pushed.

---

## Branch State

| Item | Value |
|---|---|
| Branch | `b2b-module` |
| HEAD | `acb9c74 chore(docker): add opt-in B2B demo seed env passthroughs` |
| Commits ahead of `main` | 24 |
| Modified (unstaged) | `docs/known-issues.md` |
| Untracked (unstaged) | `docs/b2b-module-merge-review.md` |
| Excluded locally | 3 scratch E2E files excluded via `.git/info/exclude` (see §Local Scratch Files) |

---

## Commits Ahead of `main` (24 total)

Listed oldest-to-newest. All commits are on `b2b-module`.

### Farmer module (Phases 9.1–9.3)

| Hash | Message |
|---|---|
| `cefdc86` | `feat(farmer): add collection requests, passbooks, and farmer/vendor screens` |
| `9f25002` | `feat(farmer): add FARMER role and farmer/vendor link lifecycle` |
| `6bdf00b` | `feat(farmer): add vendor-entered flower deposits and weight deduction rule` |
| `7be8921` | `feat(farmer): tighten deposit validation and future-timestamp guard` |
| `f67537c` | `test(farmer): add E2E test for farmer module flow` |
| `f596764` | `fix(farmer): vendor list loads on empty search query` |

### B2B module (Stages 1–4)

| Hash | Message |
|---|---|
| `5dabecb` | `feat(b2b): implement Stage 1 B2B orders between approved vendors` |
| `b8cc4f2` | `feat(b2b): implement Stage 2 B2B e-bills` |
| `07dbfb2` | `feat(b2b): implement Stage 3 B2B statements and payment recording` |
| `9331c14` | `feat(b2b): implement Stage 4 frontend, demo seed, and final verification` |
| `69046d5` | `test: B2B demo flow - buyer places order, seller accepts, stock drops` |
| `ce87e24` | `fix: B2B frontend - API paths under vendors namespace, place-order hook crash, e-bill on delivered` |
| `7190957` | `fix: B2B demo seeder - distinct phone numbers + create missing inventory row` |

### Documentation

| Hash | Message |
|---|---|
| `37aea10` | `docs: add demo script + record B2B frontend harness fixes` |

### Bug fixes

| Hash | Message |
|---|---|
| `9948c6a` | `fix(b2b): fix dialog backdrop overlay and enable dialog submit button` |
| `262325c` | `fix(b2b): add Statements list tab and fetch buyers via useSellers(null)` |
| `7405c31` | `fix(farmer): wire numeric form inputs, passbook CollectionForm + weight columns, and query invalidation` |

### Test fixes and verification

| Hash | Message |
|---|---|
| `6b2bd70` | `test(frontend): fix B2B unit tests and include playwright.demo.config.ts in tsconfig` |
| `bb68aeb` | `test(e2e): add B2B, farmer/passbook, and seeded-login demo verification tests` |
| `bc8276f` | `docs: update demo-script, progress, known-issues, and decisions after browser verification` |
| `b2bfbfa` | `test(payment): use deterministic fixed clock in WebhookControllerStandaloneTest` |
| `514c1b4` | `docs: reconcile backend test fix and docker-compose local config status` |

### Housekeeping (this session)

| Hash | Message |
|---|---|
| `03f1363` | `chore: ignore local certs/ directory` |
| `acb9c74` | `chore(docker): add opt-in B2B demo seed env passthroughs` |

---

## Audit: Last Two Commits

### `03f1363` — `chore: ignore local certs/ directory`

Scope: `.gitignore` only. Three lines added:

```
# Local TLS dev certificates
certs/
```

Findings: No secrets. No unrelated changes. Prevents local TLS development certificates from being accidentally staged.

### `acb9c74` — `chore(docker): add opt-in B2B demo seed env passthroughs`

Scope: `docker-compose.yml` only. Twelve lines added to the `backend` service `environment` block:

```yaml
SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-}

# B2B demo seed (opt-in; credentials supplied only at runtime, never committed).
APP_B2B_DEMO_SEED_ENABLED: ${APP_B2B_DEMO_SEED_ENABLED:-false}
DEMO_FARMER_EMAIL:    ${DEMO_FARMER_EMAIL:-}
DEMO_FARMER_PASSWORD: ${DEMO_FARMER_PASSWORD:-}
DEMO_SELLER_EMAIL:    ${DEMO_SELLER_EMAIL:-}
DEMO_SELLER_PASSWORD: ${DEMO_SELLER_PASSWORD:-}
DEMO_BUYER_EMAIL:     ${DEMO_BUYER_EMAIL:-}
DEMO_BUYER_PASSWORD:  ${DEMO_BUYER_PASSWORD:-}
```

Findings: All values are shell default-substitution expressions evaluating to empty string or `false` when no environment variable is set. No hardcoded credentials, tokens, keys, or machine-local paths. The seeder is a no-op in a default `docker compose up`.

---

## Secret Scan

Scope checked: full diffs of the housekeeping and fix commits inspected in this review (`03f1363`, `acb9c74`, `b2bfbfa`), `docker-compose.yml`, `.gitignore`, `docs/known-issues.md`, `docs/demo-script.md`, and the committed Playwright demo scripts (`b2b-full-flow.test.ts`, `farmer-full-flow.test.ts`, `test-seeded-logins.test.ts`).

Findings:
- **Demo account credentials** (seeded farmer, seller, buyer, and local demo test accounts) are **intentionally documented** in `docs/demo-script.md` and used in the committed Playwright demo scripts (`b2b-full-flow.test.ts`, `farmer-full-flow.test.ts`, `test-seeded-logins.test.ts`).
- **No production secrets, private keys, or tokens** were found in the audited diffs (`03f1363`, `acb9c74`, `b2bfbfa`).

The three local scratch files (`debug-login.test.ts`, `demo-verification.test.ts`, `smoke.test.ts`) are **not committed** and are excluded locally via `.git/info/exclude` (see §Local Scratch Files).

---

## Test and Browser Verification Results

All recorded results correspond to committed code on `b2b-module`; no tests were rerun after `514c1b4` (backend) or `bc8276f` (Playwright). Backend and frontend unit/build checks ran locally, while Playwright ran against the Docker Compose full stack (`mysql`, `mailhog`, `backend`, `frontend`).

### Backend

| Suite | Result | Commit in force |
|---|---|---|
| `./mvnw test` — full unit suite | ✅ **1,491 / 1,491 passed** — BUILD SUCCESS | `b2bfbfa` / `514c1b4` |

**Clock-race fix (`b2bfbfa`):** `WebhookControllerStandaloneTest.validSignatureCallsServiceAndReturns200` intermittently returned `401` instead of `200`. Root cause: `@PostConstruct init()` does not run when the class is instantiated directly (outside Spring), leaving `toleranceSeconds == 0`; a 1-second wall-clock drift between `Clock.fixed(Instant.now(), …)` and `Instant.now().getEpochSecond()` then caused the signature check to fail. Fix: a shared `static final Instant FIXED_NOW = Instant.parse("2026-11-26T12:00:00Z")` constant used for both the fixed clock and the request timestamp, eliminating all wall-clock dependency. Full suite re-run after the fix: **1,491 / 1,491 passed**.

### Frontend

| Check | Result | Source state covered |
|---|---|---|
| `npm run test` — Vitest unit suite | ✅ **890 / 890 passed** — 89 test files | Recorded at `6b2bd70` (`frontend/src/` and `tsconfig.json`) |
| `npm run typecheck` | ✅ Pass | Recorded at `6b2bd70` (`frontend/src/` and `tsconfig.json`) |
| `npm run lint` | ✅ Pass — 0 errors | Recorded at `6b2bd70` |
| `npm run build` | ✅ Pass | Recorded at `6b2bd70` |

**Provenance note:** Application source under `frontend/src/` has not changed since `6b2bd70`, but `bb68aeb` later added three E2E test files under `frontend/tests/e2e/`. The recorded unit test, typecheck, lint, and build runs above predate `bb68aeb` (or their exact post-`bb68aeb` rerun is not confirmed in repository records) and therefore cover the `6b2bd70` frontend state rather than the later `bb68aeb` E2E additions.

### Browser / Playwright

Playwright tests ran in real Chromium via `playwright.demo.config.ts` against the live Docker stack. Credentials were supplied via `.env` at runtime and are not committed.

| Test file | Tests | Result |
|---|---|---|
| `b2b-full-flow.test.ts` | — | ✅ Pass |
| `farmer-full-flow.test.ts` | — | ✅ Pass |
| `test-seeded-logins.test.ts` | 4 accounts | ✅ Pass |
| **Total** | **6 tests** | ✅ Pass (~1 m 42 s) |

**Provenance note:** Covers the three E2E test files committed in `bb68aeb` running against the `frontend/src/` application code (unchanged since `6b2bd70`) and the Dockerized backend, with browser verification documented in `bc8276f` (`docs/progress.md`). Not rerun after `acb9c74` (`docker-compose.yml` env passthroughs).

---

## Known Limitations and Unverified Flows

Drawn from `docs/known-issues.md`. Items marked **Open** are pre-existing and not regressions introduced on this branch.

| ID | Area | Status | Summary |
|---|---|---|---|
| 020 | Backend / Frontend | Open | No route serves product image bytes; `storageKey` is opaque to clients |
| 022 | Frontend / Security | Open | `auth-store.ts` persists tokens to `localStorage` contrary to memory-only intent |
| 024 | Test / Dev | Open | Approved-vendor browser walkthrough not completed — Docker became unavailable mid-session during Phase 3h; equivalent behaviour is covered by integration tests |
| 025 | Backend / Frontend | Open | Public category read is top-level only; sub-categories unreachable via public API |
| 026 | Backend | Open | Deleting a category with products returns 500 instead of 409 |
| 027 | Backend | Open | Frontend orders UI (Phase 6.T) not yet implemented |
| 030 | Test / Backend | **Fixed** | `WebhookControllerStandaloneTest` clock race — fixed in `b2bfbfa`; full suite 1,491/1,491 |

None of the open items are introduced or worsened by the commits on `b2b-module`.

---

## Local Scratch Files

Three untracked files exist in the working tree at paths under `frontend/tests/e2e/`:

- `debug-login.test.ts` — Playwright login debug trace. Contains hardcoded demo credentials in plain text. **Must not be committed or pushed.**
- `demo-verification.test.ts` — Earlier Playwright verification draft with a local absolute screenshot path. Local-only.
- `smoke.test.ts` — 5-line smoke draft. Local-only, no sensitive content.

**How they are protected:** All three paths have been added to `.git/info/exclude` in this checkout. This file is never committed, so the exclusion is local only. The three scratch files no longer appear in `git status` and cannot be accidentally staged by `git add .`, while the files themselves remain untouched on disk. (The working tree still has modified `docs/known-issues.md` and untracked `docs/b2b-module-merge-review.md`.)

**No credential values were read, displayed, or copied** as part of this review.

---

## Merge-Readiness Assessment

| Criterion | Status |
|---|---|
| All backend tests pass (1,491/1,491) | ✅ |
| All frontend unit tests pass (890/890) | ✅ |
| Frontend typecheck, lint, and build pass | ✅ |
| Playwright demo flows pass in real Chromium | ✅ |
| No production secrets, private keys, or tokens in audited diffs (demo credentials intentionally in `docs/demo-script.md`) | ✅ |
| No unrelated tracked changes | ✅ |
| `certs/` ignored via `.gitignore` | ✅ |
| `docker-compose.yml` has no hardcoded credentials | ✅ |
| Scratch files excluded via `.git/info/exclude`, not committed | ✅ |
| Docs consistent with verified test output | ✅ |
| Branch not merged or pushed | ✅ |

**`b2b-module` is ready for merge review.**

This document is not committed. It is an untracked file in `docs/` and will remain so unless explicitly staged and committed by the reviewer.
