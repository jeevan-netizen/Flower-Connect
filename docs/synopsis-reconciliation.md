# Synopsis Reconciliation — Phase 14 Stage 9 (task 14.10)

## Status

- **Original submitted synopsis:** Not present in the repository. At the user's request, an implementation-accurate replacement synopsis was authored at `docs/synopsis.md` using the 12 module names the user approved.
- **Reconciliation status:** Technology wording below is aligned to the implemented project and the authored synopsis. The original submitted synopsis, if one exists, has not been modified.
- **Branch:** `phase-14-polish-deliverables`, commit `734dd36` (Stage 8 committed and pushed). Working tree clean at start of Stage 9.

## Reconciliation rows

Each row maps one synopsis technology/claim to the implemented reality evidenced in the current codebase, and proposes replacement wording suitable for the synopsis once the source text is available. Rows marked **PENDING** require the user-supplied synopsis text and/or amendment decision.

| # | Synopsis claim (as referenced in plan §10) | Implemented reality (evidence) | Proposed replacement wording | Status |
|---|---|---|---|---|
| 1 | Stripe payments | COD only in v1. Online payments exist only as an optional test-mode gateway behind `PaymentGateway` (`CodPaymentProvider`, `SimulatedPaymentProvider`, `SimulatorController` gated to `!prod` profile). No Stripe integration, no card data, no real payment processing. Decisions D-47, D-48, D-49. | "COD payments in v1; real online payments are future scope. A simulated test-mode gateway is available outside production." | Included in authored synopsis |
| 2 | Twilio / SendGrid notifications | In-app notifications (persisted `notifications` table, read API, SSE stream) + SMTP email via `SmtpEmailSender` (MailHog in dev, port 1025; `NoOpEmailSender` fallback). No Twilio (SMS) integration, no SendGrid relay. Decision D-60. | "Notifications use in-app alerts and SMTP email; SMS integration is future scope." | Included in authored synopsis |
| 3 | Mapbox geo | Haversine distance computed over seeded `service_locations` area centroids (`DiscoveryController`, `SearchService`). No browser Geolocation API, no GPS, no map service, no Mapbox. Decisions D-4, D-34. | "Distance is estimated with the Haversine formula using service-area centroids; GPS and map services are not used." | Included in authored synopsis |
| 4 | Redis 7 caching | Not provisioned in v1 (Redis removed in Stage 6 per plan v2.2). Rate limiting uses per-instance Caffeine buckets. No Redis service, config, env vars, or volume. Decision D-68. | "Redis is not used in v1; rate limits use per-instance in-memory Caffeine buckets." | Evidenced — no Redis in v1 |
| 5 | Flyway | As planned. Additive migrations V1–V33 (`V1__baseline.sql` … `V33__performance_indexes.sql`), `ddl-auto: validate`, baseline-on-migrate. ADR-002 / D-9. | Matches synopsis; no change needed | Evidenced — no change |
| 6 | Docker Compose | As planned. `docker-compose.yml` orchestrates MySQL 8, MailHog, backend, frontend. Multi-stage Dockerfiles. | Matches synopsis; no change needed | Evidenced — no change |
| 7 | Zustand | As planned. `src/features/auth/stores/auth-store.ts` (persisted auth state) plus feature stores. ADR-004. | Matches synopsis; no change needed | Evidenced — no change |
| 8 | TanStack Query | As planned. Server-state queries/mutations across features (vendor, admin, cart, checkout, orders, billing, notification, etc.). | Matches synopsis; no change needed | Evidenced — no change |
| 9 | Zod | As planned. Form schemas mirror backend DTO bounds (`form-schema.ts` in vendor, admin, cart, checkout, auth, dispute features). | Matches synopsis; no change needed | Evidenced — no change |
| 10 | Spring Boot 3.2 / Java 17 backend | Implemented. `FlowerConnectApplication.java`, Spring Boot 3.2.5, Java 17, Maven wrapper, JPA/Hibernate, Lombok, MapStruct. | Matches synopsis; no change needed | Evidenced — no change |
| 11 | MySQL 8 | Implemented. Flyway migrations V1–V33 against MySQL 8 (Testcontainers for tests; Docker Compose locally). | Matches synopsis; no change needed | Evidenced — no change |
| 12 | React 18 + TypeScript + Vite frontend | Implemented. React 18, TypeScript strict, Vite 5, Tailwind CSS 3, react-router-dom v6, Axios. | Matches synopsis; no change needed | Evidenced — no change |

## 12 functional modules

The user approved using the following plan-derived names. They appear in the authored replacement synopsis at `docs/synopsis.md` and are mapped to implementation phases below. The original submitted synopsis is not in the repository and was not modified.

| # | Approved working module name | Implemented phase(s) | Evidence / note |
|---|---|---|---|
| 1 | Authentication | Phase 1 | Registration, login, refresh/logout, and account security |
| 2 | Vendor profiles and approval | Phase 2 | Vendor registration, profile, admin approval/status |
| 3 | Catalog and inventory | Phase 3 | Product catalog, images, stock and inventory controls |
| 4 | Addresses, discovery, and search | Phase 4 | Service locations, customer addresses, discovery/search |
| 5 | Cart and checkout | Phase 5 | Cart, quote, checkout, multi-vendor ordering |
| 6 | Order management | Phase 6 | Order lifecycle and vendor/customer order views |
| 7 | Payments and COD | Phase 7 | COD plus non-production simulated online gateway; no real processor |
| 8 | Cancellations, refunds, and disputes | Phase 8 | Cancellation, refund and dispute flows |
| 9 | Notifications and real-time updates | Phase 9 | In-app notifications, email and best-effort SSE/polling |
| 10 | Billing and settlements | Phase 10 | Commission, ledger, statements and settlement workflows |
| 11 | POS | Phase 11 | Vendor point-of-sale transactions, separate from marketplace billing |
| 12 | Reviews and analytics | Phase 12 | Customer reviews, vendor analytics and admin analytics |

**Status:** All 12 user-approved module names appear in the authored synopsis and map to implemented phases.

## Items awaiting user input

1. The original submitted synopsis is not in the repository. If it must also be reconciled, provide that source text. The authored replacement synopsis is complete at `docs/synopsis.md`.

No original synopsis was edited. The new synopsis is based on implemented functionality and the module list approved by the user.
