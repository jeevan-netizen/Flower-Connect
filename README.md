# FlowerConnect

Hyperlocal flower marketplace connecting local artisan florists with customers for same-day or scheduled delivery within a tight geographic radius.

**Spring Boot 3.2 (Java 17) backend** + **React 18 / TypeScript / Vite / Tailwind CSS frontend** + **MySQL 8**.

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.2.5, Java 17, Spring Data JPA + Hibernate, Flyway, Lombok, MapStruct |
| Database | MySQL 8 (InnoDB, utf8mb4) |
| Frontend | React 18, TypeScript (strict), Vite 5, Tailwind CSS 3 |
| State | Zustand (auth), TanStack Query (server data) |
| Forms | react-hook-form + zod |
| HTTP | Axios with interceptors |
| Tests | JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + @testing-library/react (frontend) |
| Build | Maven (backend), npm/Vite (frontend) |

## Architecture

```
Browser (React SPA, :5173)
    │ HTTPS / HTTP (single-origin reverse proxy in prod)
    ▼
Backend (Spring Boot, :8080) ───► MySQL 8 (Docker)
REST /api/v1 ─────────────────────► Flyway migrations
```

The backend exposes a REST API under `/api/v1`. Role-based access is enforced in `SecurityConfig` (URL layer) and, where applicable, by per-handler method security (`@RequiresApprovedVendor`). The OpenAPI/Swagger UI is available in `dev` only and disabled in `prod`.

See [`docs/architecture.md`](docs/architecture.md) for the final module diagram and [`docs/er-diagram.md`](docs/er-diagram.md) for the entity-relationship diagram derived from the Flyway migrations.

## Feature list by role

### Customer
- Register / login, manage profile and password
- Choose a delivery area from seeded `service_locations` (no GPS)
- Browse approved vendors via discovery (`GET /api/v1/discover`), search (`GET /api/v1/search`), and storefronts (`GET /api/v1/vendors/{id}/storefront`)
- View product detail, add items to cart, checkout (COD or simulated online payment)
- View order history and detail; raise disputes on delivered orders
- View notifications (in-app bell), payment status page for online orders

### Vendor (FLORIST)
- Register as a vendor; manage own profile, delivery settings, operating hours
- Manage catalog (products, images, stock), record stock movements
- Accept / reject / prepare / out-for-delivery / deliver orders
- View order board, today's summary, analytics overview and daily trends
- View disputes on own orders and respond once; view billing ledger, outstanding balance, settlements, statement CSV
- Record commission-free POS walk-in sales

### Admin
- Manage vendors (approve / reject / suspend / reinstate) and users (status change)
- Manage categories; set platform default and per-vendor commission rates
- View refund queue and mark refunds; view dispute queue and resolve disputes
- Run and finalize weekly settlement cycles; view overdue vendors; download statement CSV

## Prerequisites

- **Docker Desktop** with Compose v2+ (Windows: Docker Desktop with WSL2 backend recommended)
- **Node 22 LTS** (required for local frontend development; the frontend Dockerfile uses `node:22-alpine` and `.nvmrc` pins `22`)
- **Git** (line endings: the repo enforces LF on wrapper scripts via `.gitattributes`; on Windows, check out with `core.autocrlf=input` to avoid CRLF breaking `./mvnw` inside Linux containers)
- A terminal (PowerShell, Git Bash, or WSL)

### Ports
| Service | Port | Notes |
|---------|------|-------|
| Frontend | 5173 | |
| Backend API | 8080 | |
| MySQL | 3306 (container) | Host exposure defaults to 3307 to avoid colliding with a local MySQL |
| MailHog | 8025 | Dev email viewer |

If port `3306` is already bound by a local MySQL, set `DB_PORT=3307` in `.env` (host side) so Compose does not collide. The backend always connects to MySQL via the internal Docker network at `mysql:3306`.

## Quick start from a clean clone

```bash
# 1. Clone
git clone <repo> FlowerConnect
cd FlowerConnect

# 2. Configure (copy the template; fill in dev-only secrets)
cp .env.example .env
# Edit .env: ADMIN_EMAIL, ADMIN_PASSWORD, DEMO_PASSWORD, JWT_SECRET,
# APP_CHECKOUT_QUOTE_SIGNING_SECRET, DB_PORT (3307 if 3306 is in use)

# 3. Build and start
docker compose up --build -d

# 4. Wait for health
docker compose ps   # all four containers should be healthy

# 5. Open
# Frontend:  http://localhost:5173
# Backend:   http://localhost:8080/api/v1
# Swagger:   http://localhost:8080/swagger-ui.html  (dev only)
# MailHog:   http://localhost:8025
```

### Dev demo credentials (do not publish passwords)

Demo customer and vendor accounts are seeded automatically on the `dev` profile when `DEMO_PASSWORD` (or `DEMO_USER_PASSWORD` / `app.seed.demo-password`) is set in `.env`. The admin account uses `ADMIN_EMAIL` / `ADMIN_PASSWORD`. Email patterns are illustrative (e.g. `customer-alice@flowerconnect.test`, `vendor-blossom@flowerconnect.test`, `admin@flowerconnect.test`); passwords are **not** committed or printed. If `DEMO_PASSWORD` is unset, the seed runner logs a warning and skips, so boot still succeeds without demo data.

Reset the database to a pristine seeded state:

```bash
docker compose down -v      # destroys containers AND named volumes (destructive)
docker compose up -d
```

The `down -v` removes the MySQL data volume, so the next boot re-applies all Flyway migrations from scratch and re-seeds. This is destructive: any data not in migrations/seeds is lost.

## Running tests

### Backend

Unit tests (no Docker required):

```bash
cd backend
./mvnw test
```

Integration tests (require Docker Desktop; Testcontainers starts a shared MySQL):

```bash
cd backend
./mvnw verify -Pintegration
```

Run a focused subset (recommended; the full suite previously exceeded a 15-minute window in a single session):

```bash
cd backend
./mvnw verify -Pintegration "-Dit.test=*IntegrationTest"
```

### Frontend

```bash
cd frontend
npm ci          # requires Node 22 (see .nvmrc / engines in package.json)
npm run test    # Vitest
npm run typecheck  # tsc -b
npm run lint       # ESLint
npm run build      # tsc -b && vite build
```

### Playwright (browser E2E)

```bash
cd frontend
npx playwright test
```

Requires the stack to be running. See [`docs/demo-script.md`](docs/demo-script.md) and [`docs/deployment.md`](docs/deployment.md) for the rehearsal and production-readiness context.

## Project structure

```
FlowerConnect/
├── backend/                 # Spring Boot 3.2 (Java 17)
│   ├── src/main/java/com/flowerconnect/
│   │   ├── catalog/         # Categories, products
│   │   ├── cart/            # Customer cart
│   │   ├── checkout/        # Quote + checkout (COD / simulated online)
│   │   ├── customer/        # Addresses, orders
│   │   ├── dispute/         # Customer/vendor/admin disputes
│   │   ├── inventory/       # Stock levels, movements, expiry sweep
│   │   ├── notification/    # In-app notifications, email, SSE
│   │   ├── order/           # State machine, cancellation, auto-reject
│   │   ├── payment/         # COD / online intents, webhook, simulator
│   │   ├── pos/             # Commission-free point-of-sale
│   │   ├── review/          # Customer reviews, vendor replies, admin moderation
│   │   ├── security/        # JWT, filter chain, CORS, rate limiting, headers
│   │   ├── settlement/      # Commission, ledger, settlements, statements, overdue
│   │   ├── slot/            # Delivery slot discovery
│   │   ├── vendor/          # Profiles, hours, catalog, inventory, orders, analytics, billing
│   │   └── ...              # auth, geo, demo
│   ├── src/main/resources/
│   │   ├── application.yml / -dev / -prod / -staging / -test
│   │   └── db/migration/    # Flyway V1__baseline … V32__
│   └── src/test/            # Unit + integration tests
├── frontend/                # React 18 + Vite
│   ├── src/
│   │   ├── app/             # Router, providers, styles
│   │   ├── features/        # auth, cart, checkout, dispute, order, payment,
│   │   │                   # review, vendor, admin, notification, billing
│   │   └── shared/          # ProtectedRoute, api client, api-error
│   └── tests/e2e/           # Playwright
├── docs/                    # architecture, progress, decisions, known-issues, api-summary, er-diagram, test-report, deployment, demo-script, credits
├── docker-compose.yml       # Dev stack (MySQL, MailHog, backend, frontend)
├── docker-compose.prod.yml  # Prod-like stack (single-origin HTTPS proxy)
├── .env.example             # Environment template (no real secrets)
└── README.md
```

## Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| `./mvnw: not found` inside Linux container | CRLF line endings on `mvnw`; the Dockerfile strips them and `.gitattributes` enforces LF |
| MySQL connection refused | Docker Desktop not running; start it before `verify -Pintegration` |
| Port 3306 collision | Set `DB_PORT=3307` in `.env` |
| Flyway checksum mismatch | Local schema drifted from rewritten V1; drop `flyway_schema_history` and dependent tables, then reboot |
| Swagger UI 404 in prod | Expected — `springdoc` is disabled in `application-prod.yml` (Phase 13 security decision) |
| Refresh cookie not sent | Cookie path is `/api/v1/auth`; call that path, not `/` |

## Security notes

- Access tokens are memory-only (not persisted to `localStorage`); sessions restore via the HttpOnly refresh cookie (`SameSite=Strict`, `Secure` in prod).
- Refresh token is issued via HttpOnly cookie only, never in the response body.
- CORS allows explicit headers (`Authorization`, `Content-Type`, `X-FlowerConnect-Client`) — no wildcard with credentials.
- Rate limiting is active in dev and prod (per-user/IP Caffeine buckets, configurable).
- Security headers (`nosniff`, `DENY`, `strict-origin-when-cross-origin`, HSTS) are added by `SecurityHeadersFilter`.
- Swagger/OpenAPI is available in `dev` and disabled in `prod` and `staging`.

## License

Apache 2.0. See [`docs/credits.md`](docs/credits.md) for demo image generator licensing.