# Architecture

## 1. Purpose

FlowerConnect is a **hyperlocal flower marketplace**. It connects local florists with customers for same-day or scheduled flower delivery within a tight geographic radius. The platform handles browsing, ordering, payments, and delivery coordination.

Current development phase: **Phase 2d (Admin User Management)** — authentication (Phase 1), service
locations (Phase 2a), and vendor registration/approval with the `audit_log` trail (Phase 2b/2c) are
implemented. Phase 2d adds the admin user listing and status-management APIs, backed by the same
`audit_log` table and the existing `users.status` column.

## 2. High-Level Architecture

```
┌──────────────────────────────────────────────────────┐
│                     Browser (SPA)                     │
│  React 18 + Vite + TypeScript + Tailwind CSS           │
│  http://localhost:5173                                │
├──────────────────────────────────────────────────────┤
│                       API Gateway                      │
│  (future)                                              │
├──────────────────────────────────────────────────────┤
│              Backend (Spring Boot 3.2)                │
│  Java 17 · Maven · JPA · Flyway · Lombok · MapStruct │
│  http://localhost:8080                                │
├──────────────────────────────────────────────────────┤
│                    MySQL 8 (docker)                   │
│  Roles + Users (Phase 0/1)                            │
│  Service Locations (Phase 2a)                         │
│  Vendor Profiles + Vendor Hours (Phase 2b)            │
│  Future: Catalog, Orders, Payments, Delivery          │
├──────────────────────────────────────────────────────┤
│                    Redis 7 (docker)                    │
│  Session cache, future: rate limiting, cart state     │
└──────────────────────────────────────────────────────┘
```

### Service Boundaries (Planned)

| Service      | Responsibility                          | Phase    |
|--------------|-----------------------------------------|----------|
| Auth         | JWT issuance, user registration/login   | Phase 1 (done) |
| Geo          | Service locations, pincode/area search  | Phase 2a (done) |
| Catalog      | Florist listings, product inventory     | Phase 2b+ |
| Order        | Cart, checkout, order lifecycle         | Phase 3+ |
| Payment      | Stripe integration                      | Phase 3+ |
| Delivery     | Assignment, tracking, status updates    | Phase 3+ |
| Notification | Email/SMS dispatch                      | Phase 4+ |

## 3. Technology Stack

### Backend

| Concern        | Technology                          |
|----------------|-------------------------------------|
| Framework      | Spring Boot 3.2.5                   |
| Language       | Java 17 (LTS)                       |
| Build          | Maven (via `mvnw` wrapper)          |
| ORM            | Spring Data JPA + Hibernate 6       |
| Database       | MySQL 8 (InnoDB, utf8mb4)           |
| Migrations     | Flyway (baseline V1, additive V2–V5) |
| Cache          | Redis 7 (password-protected)        |
| Codegen        | Lombok 1.18.32, MapStruct 1.5.5     |
| Security       | Spring Security 6 (planned)         |
| Auth           | JWT (Bearer tokens)                 |
| Validation     | Bean Validation 3 (`javax.validation`) |
| API versioning | URL prefix `api/v1`                 |

### Frontend

| Concern        | Technology                          |
|----------------|-------------------------------------|
| Framework      | React 18                            |
| Language       | TypeScript (strict)                 |
| Build          | Vite 5                              |
| Styling        | Tailwind CSS 3                      |
| State (client) | Zustand (persisted auth store)      |
| State (server) | TanStack Query v5                   |
| Forms          | react-hook-form + zod               |
| Routing        | react-router-dom v6                 |
| HTTP           | Axios (with interceptors)           |
| Tests          | Vitest + @testing-library/react     |
| Linting        | ESLint + Prettier                   |

### Infrastructure

| Component        | Technology                  |
|------------------|-----------------------------|
| Container runtime| Docker Compose            |
| DB container     | MySQL 8 (custom Dockerfile) |
| Cache container  | Redis 7 (official image)   |
| Backend container| Multi-stage (Terndrin JRE)  |
| Frontend container| Multi-stage (Node 22)     |

## 4. Directory Structure

```
FlowerConnect/
├── .env.example              # Environment variable template
├── .gitignore
├── AGENTS.md                 # Primary project agent instructions
├── CLAUDE.md                 # Compatibility redirect to AGENTS.md
├── kilo.jsonc                # Kilo project configuration
├── docker-compose.yml        # Full-stack orchestration
├── backend/
│   ├── Dockerfile            # Multi-stage: base → build → runtime
│   ├── mvnw / mvnw.cmd       # Maven wrapper
│   ├── .mvn/
│   ├── pom.xml               # Maven POM (Spring Boot 3.2.5)
│   └── src/
│       └── main/
│           ├── java/com/flowerconnect/
│           │   ├── FlowerConnectApplication.java
│           │   ├── domain/               # JPA entities
│           │   ├── repository/           # Spring Data repositories
│           │   ├── service/              # Business logic
│           │   ├── controller/           # REST controllers
│           │   ├── dto/                  # Request/response DTOs
│               │   ├── geo/                  # Service locations feature slice
│               │   ├── vendor/               # Vendor profile + admin feature slice
│               │   │   ├── controller/
│               │   │   ├── dto/
│               │   │   ├── mapper/
│               │   │   ├── security/         # Approval guard + @RequiresApprovedVendor
│               │   │   ├── service/
│               │   │   └── specification/    # Reusable query fragments (approved-only)
│               │   ├── config/               # Security, Jackson, Clock beans
│               │   └── exception/            # Error framework
│           └── resources/
│               ├── application.yml
│               └── db/migration/
│                   ├── V1__baseline.sql         # roles, users
│                   ├── V2__auth_tokens.sql
│                   ├── V3__seed_roles.sql
│                   ├── V4__service_locations.sql
│                   └── V5__vendor_profiles.sql  # vendor_profiles, vendor_hours
│                   └── V6__audit_log_and_vendor_checks.sql  # audit_log, vendor CHECKs
├── frontend/
│   ├── Dockerfile            # Multi-stage: deps → builder → runner
│   ├── index.html
│   ├── package.json
│   ├── postcss.config.js
│   ├── server.mjs            # SPA static server for Docker
│   ├── tailwind.config.ts
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── .eslintrc.cjs
│   ├── .npmrc
│   └── src/
│       ├── main.tsx
│       ├── vite-env.d.ts
│       ├── app/
│       │   ├── router.tsx
│       │   ├── providers/
│       │   │   ├── query-client.ts
│       │   │   └── theme.tsx
│       │   └── styles/
│       │       └── index.css
│       ├── features/
│       │   └── auth/
│       │       └── stores/
│       │           └── auth-store.ts
│       ├── shared/
│       │   └── lib/
│       │       └── api.ts
│       └── test/
│           └── setup.ts
└── docker/
    └── mysql/
        ├── Dockerfile        # Custom MySQL image with init script
        ├── my.cnf            # MySQL config (utf8mb4, bind 0.0.0.0)
        └── init/
            └── 00-init.sql   # Ensures DB exists for Flyway
```

## 5. Database Schema (Phase 0–2b)

Source of truth: `backend/src/main/resources/db/migration/`

### Tables

#### `roles`
| Column | Type         | Constraints              |
|--------|-------------|--------------------------|
| id     | BIGINT      | PK, AUTO_INCREMENT       |
| name   | VARCHAR(64) | NOT NULL, UNIQUE         |

#### `users`
| Column        | Type         | Constraints                            |
|---------------|-------------|----------------------------------------|
| id            | BIGINT      | PK, AUTO_INCREMENT                     |
| role_id       | BIGINT      | FK → roles(id), NOT NULL               |
| email         | VARCHAR(255)| NOT NULL, UNIQUE                       |
| password_hash | VARCHAR(255)| NOT NULL                               |
| full_name     | VARCHAR(128)| NOT NULL                               |
| phone         | VARCHAR(32) | NULL, UNIQUE                           |
| status        | ENUM('ACTIVE', 'SUSPENDED', 'DISABLED') | NOT NULL, DEFAULT 'ACTIVE' |
| created_at    | DATETIME(6)| NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) |
| updated_at    | DATETIME(6)| NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE |

#### `service_locations` (Phase 2a)
| Column      | Type        | Constraints                            |
|-------------|-------------|----------------------------------------|
| id          | BIGINT      | PK, AUTO_INCREMENT                     |
| city        | VARCHAR(128)| NOT NULL                               |
| area        | VARCHAR(128)| NOT NULL                               |
| pincode     | VARCHAR(10) | NOT NULL                               |
| latitude    | DECIMAL(10,8)| NOT NULL                             |
| longitude   | DECIMAL(11,8)| NOT NULL                             |
| created_at  | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) |

- One row per service area, seeded for the Bengaluru demo region (8 areas).
- Coordinates are area-level approximations; there is no GPS or live device location (D-4).
- Unique on `(city, area)`.

#### `vendor_profiles` (Phase 2b)
| Column                   | Type          | Constraints                                  |
|--------------------------|---------------|----------------------------------------------|
| id                       | BIGINT        | PK, AUTO_INCREMENT                           |
| user_id                  | BIGINT        | FK → users(id), NOT NULL, UNIQUE             |
| business_name            | VARCHAR(160)  | NOT NULL                                     |
| description              | VARCHAR(1000) | NULL                                         |
| address_line1            | VARCHAR(255)  | NOT NULL                                     |
| address_line2            | VARCHAR(255)  | NULL                                         |
| service_location_id      | BIGINT        | FK → service_locations(id), NOT NULL         |
| latitude                 | DECIMAL(10,8) | NOT NULL (copied from service_location)      |
| longitude                | DECIMAL(11,8) | NOT NULL (copied from service_location)      |
| delivery_radius_km       | DECIMAL(5,2)  | NOT NULL, DEFAULT 5.00                       |
| logo_url                 | VARCHAR(512)  | NULL                                         |
| status                   | ENUM('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SUSPENDED') | NOT NULL, DEFAULT 'PENDING_APPROVAL' |
| commission_rate          | DECIMAL(5,4)  | NULL (platform default applies when NULL)    |
| avg_rating               | DECIMAL(3,2)  | NULL                                         |
| review_count             | INT           | NOT NULL, DEFAULT 0                          |
| min_order_amount         | DECIMAL(10,2) | NOT NULL, DEFAULT 0.00                       |
| base_delivery_fee        | DECIMAL(10,2) | NOT NULL, DEFAULT 0.00                       |
| per_km_fee               | DECIMAL(10,2) | NOT NULL, DEFAULT 0.00                       |
| free_delivery_above      | DECIMAL(10,2) | NULL                                         |
| prep_time_minutes        | INT           | NOT NULL, DEFAULT 30                         |
| slot_duration_minutes    | INT           | NOT NULL, DEFAULT 60                         |
| max_orders_per_slot      | INT           | NOT NULL, DEFAULT 10                         |
| accepting_orders         | BIT(1)        | NOT NULL, DEFAULT b'1'                       |
| created_at               | DATETIME(6)   | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)       |
| updated_at               | DATETIME(6)   | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE |

- One profile per user (`uq_vendor_profiles_user`).
- `service_location_id` is the single source of the vendor's coordinates; `latitude`/`longitude`
  are denormalized copies so geo queries can use a single index (D-4).
- Delivery settings live on the profile: minimum order amount, base delivery fee, per-km fee,
  free-delivery threshold, prep time, slot duration, orders per slot, and an accept-orders flag.
- Money fields use `DECIMAL` so scale is preserved (no floating-point round-off).

#### `vendor_hours` (Phase 2b)
| Column            | Type     | Constraints                                |
|-------------------|----------|--------------------------------------------|
| id                | BIGINT   | PK, AUTO_INCREMENT                         |
| vendor_profile_id | BIGINT   | FK → vendor_profiles(id), NOT NULL, ON DELETE CASCADE |
| weekday           | ENUM('MONDAY'…'SUNDAY') | NOT NULL                 |
| open_time         | TIME     | NULL                                       |
| close_time        | TIME     | NULL                                       |
| closed            | BIT(1)   | NOT NULL, DEFAULT b'0'                     |
| created_at        | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) |
| updated_at        | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE |

- At most one row per profile per weekday (`uq_vendor_hours_profile_weekday`).
- A closed day stores NULL `open_time` / `close_time`.
- Deleting a vendor profile cascades to its hours.
- `ck_vendor_hours_times` (V6) enforces: a closed day has both times NULL, and an open day
  has both present with `close_time > open_time`.

#### `audit_log` (Phase 2c)
| Column        | Type        | Constraints                          |
|---------------|-------------|--------------------------------------|
| id            | BIGINT      | PK, AUTO_INCREMENT                   |
| actor_user_id | BIGINT      | FK → users(id), NOT NULL             |
| action_type   | VARCHAR(64) | NOT NULL                             |
| entity_type   | VARCHAR(64) | NOT NULL                             |
| entity_id     | BIGINT      | NOT NULL                             |
| reason        | VARCHAR(500)| NULL (required for reject/suspend)   |
| created_at    | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|

- Append-only: every admin vendor transition writes one row; no update or delete path exists.
- Indexed on `(entity_type, entity_id)` and `(actor_user_id, created_at)`.

### Entity Relationships

```
roles  1 ──< users  1 ──1 vendor_profiles  1 ──< vendor_hours
                              │
                              └──< service_locations
```

- `VendorProfile.user` is a one-to-one with `User` (unique on `user_id`).
- `VendorProfile.serviceLocation` is a many-to-one with `ServiceLocation`.
- `VendorHours.vendorProfile` is a many-to-one with `VendorProfile` (cascade delete).

### Indexes
- `idx_users_role` on `users(role_id)`
- `idx_service_locations_city_area` on `service_locations(city, area)`
- `idx_service_locations_pincode` on `service_locations(pincode)`
- `idx_vendor_profiles_status_geo` on `vendor_profiles(status, latitude, longitude)` — supports
  "approved vendors near a location" lookups. `VendorProfileSpecifications.approved()` predicates on
  the leading `status` column, so discovery queries stay index-served.

### Role Hierarchy
| Role       | Description                         |
|------------|-------------------------------------|
| CUSTOMER   | End-user browsing and ordering      |
| FLORIST    | Local florist managing inventory  |
| ADMIN      | Platform administrator            |

## 6. API Structure

### Base URL
- Development: `http://localhost:8080/api/v1`
- Frontend proxy: `VITE_API_BASE_URL` env var (defaults to `http://localhost:8080/api/v1`)

### Authentication
- JWT Bearer tokens in the `Authorization` header
- Access token (default 15 min TTL) + refresh token (default 7 days TTL)
- Tokens stored in Zustand (persisted) and `localStorage` (`fc-access-token` key)

### Implemented Endpoints
Phase 1 (auth), Phase 2a (service locations), and Phase 2c (vendor registration, admin
approval, and approval gating) endpoints are implemented. No catalog or order endpoint exists
yet, so `@RequiresApprovedVendor` currently guards no production route — see
`docs/decisions.md` (D-13).

| Method | Path                | Description                        | Phase  |
|--------|---------------------|------------------------------------|--------|
| POST   | `/api/v1/auth/register`| Register new user                | Phase 1|
| POST   | `/api/v1/auth/login`| Authenticate and issue JWT         | Phase 1|
| POST   | `/api/v1/auth/refresh` | Issue new access token (rotation) | Phase 1|
| POST   | `/api/v1/auth/logout` | Invalidate refresh token           | Phase 1|
| POST   | `/api/v1/auth/forgot-password` | Request a password reset link | Phase 1|
| POST   | `/api/v1/auth/reset-password` | Reset password with a valid token | Phase 1|
| GET    | `/api/v1/users/me`   | Get current user profile            | Phase 1|
| PATCH  | `/api/v1/users/me`   | Update current user profile         | Phase 1|
| POST   | `/api/v1/users/me/password` | Change current password      | Phase 1|
| GET    | `/api/v1/locations`  | List or search service locations    | Phase 2a|
| POST   | `/api/v1/vendors/register` | Register a vendor account + `PENDING_APPROVAL` profile (public) | Phase 2c|
| GET    | `/api/v1/vendors/profile` | Get the caller's own vendor profile (FLORIST) | Phase 2c|
| PUT    | `/api/v1/vendors/profile` | Update the caller's own vendor profile (FLORIST) | Phase 2c|
| GET    | `/api/v1/admin/vendors` | List/filter vendor profiles by status (ADMIN) | Phase 2c|
| POST   | `/api/v1/admin/vendors/{id}/approve`  | Approve a pending vendor (ADMIN) | Phase 2c|
| POST   | `/api/v1/admin/vendors/{id}/reject`   | Reject a pending vendor; `reason` required (ADMIN) | Phase 2c|
| POST   | `/api/v1/admin/vendors/{id}/suspend`   | Suspend an approved vendor; `reason` required (ADMIN) | Phase 2c|
| POST   | `/api/v1/admin/vendors/{id}/reinstate` | Reinstate a suspended vendor (ADMIN) | Phase 2c|
| GET    | `/api/v1/admin/users`   | List/filter users by role and status (ADMIN) | Phase 2d|
| PATCH  | `/api/v1/admin/users/{id}/status` | Change a user's status; reason required, tokens revoked (ADMIN) | Phase 2d|
| GET    | `/actuator/health`   | Health check (no auth)              | Phase 0|

## 7. Configuration

### Environment Variables

All configuration is externalized via environment variables. Copy `.env.example` to `.env` for local development.

| Variable            | Default                                          | Description                    |
|---------------------|--------------------------------------------------|--------------------------------|
| `DB_HOST`           | `mysql`                                          | Database host                  |
| `DB_PORT`           | `3306`                                           | Database port                  |
| `DB_NAME`           | `flowerconnect`                                  | Database name                  |
| `DB_USER`           | `flowerconnect`                                  | Database user                  |
| `DB_PASS`           | —                                                | Database password              |
| `DB_URL`            | `jdbc:mysql://...`                               | Full JDBC URL                  |
| `REDIS_HOST`        | `redis`                                          | Redis host                     |
| `REDIS_PORT`        | `6379`                                           | Redis port                     |
| `REDIS_PASSWORD`    | —                                                | Redis password                 |
| `JWT_SECRET`        | — (must be set)                                  | 256-bit JWT signing key        |
| `JWT_ACCESS_TTL_MS` | `900000` (15 min)                                | Access token TTL               |
| `JWT_REFRESH_TTL_MS`| `604800000` (7 days)                             | Refresh token TTL              |
| `APP_BASE_URL`      | `http://localhost:5173`                          | Frontend origin (CORS, links)  |
| `APP_CORS_ORIGINS`  | `http://localhost:5173`                          | Allowed CORS origins           |
| `SMTP_HOST`         | `localhost`                                      | SMTP server (notifications)    |
| `SMTP_PORT`         | `587`                                            | SMTP port                      |
| `SMTP_USER`         | —                                                | SMTP username                  |
| `SMTP_PASS`         | —                                                | SMTP password                  |
| `STRIPE_SECRET_KEY` | —                                                | Stripe secret (billing)        |
| `STRIPE_WEBHOOK_SECRET` | —                                            | Stripe webhook signing secret  |

## 8. Build & Deployment

### Local Development

```bash
# Backend
cd backend
./mvnw spring-boot:run

# Frontend (separate terminal)
cd frontend
npm install
npm run dev

# Full stack via Docker
docker compose up --build
```

### Production Build

```bash
# Backend
cd backend
./mvnw clean package

# Frontend
cd frontend
npm install
npm run build

# Docker image
docker compose up --build -d
```

### Health Checks

```bash
curl http://localhost:8080/actuator/health   # Backend
curl http://localhost:5173/                    # Frontend
```

## 9. Data Flow

```
1. User opens frontend at http://localhost:5173
2. Frontend loads React SPA (served by Node static server)
3. On API calls, frontend sends requests to http://localhost:8080/api/v1
4. Backend validates JWT, processes request via Controller → Service → Repository
5. Repository queries MySQL via JPA/Hibernate
6. Redis caches session/lookup data as needed
7. Flyway manages DB schema migrations on startup
```

## 10. Deployment Architecture (Production)

```
[Load Balancer / CDN]
       │
       ├── Frontend (Docker: Node 22 static + SSR fallback)
       │    ├── Serves index.html, JS, CSS from dist/
       │    └── SPA fallback: index.html for client-side routes
       │
       └── Backend (Docker: Java 17 JRE)
            ├── REST API at /api/v1/*
            ├── Actuator health at /actuator/health
            ├── Connects to MySQL (remote or Docker volume)
            └── Connects to Redis (remote or Docker volume)

External services (Phase 3+):
├── Stripe (payments)
└── SMTP provider (notifications)
```

## 11. Future Architecture Decisions

See `docs/decisions.md` for the full ADR log. Key decisions made so far:

| ADR-001 | Maven wrapper + thin Dockerfile | Accepted |
|---------|---------------------------------|----------|
| ADR-002 | Flyway baseline + additive migrations | Accepted |
| ADR-003 | JWT access/refresh token pair | Accepted |
| ADR-004 | Feature-sliced frontend directory layout | Accepted |

## 12. Security Model

- **Passwords**: BCrypt hashing via `BCryptPasswordEncoder`
- **JWT**: HS256 signed, 256-bit secret via `JWT_SECRET` env var
- **CORS**: Restricted to `APP_CORS_ORIGINS`
- **Secrets**: Never committed; all via `.env` (gitignored)
- **API**: All endpoints except `/actuator/health` will require JWT once auth is implemented
- **Input validation**: Backend validates all requests (frontend validation is convenience only)

### Authorization layering

Authorization is two independent layers that both run, in order:

| Layer                          | Rule                                                        | Failure response              |
|--------------------------------|-------------------------------------------------------------|-------------------------------|
| URL namespace (`SecurityConfig`) | `/api/v1/vendors/**` → `hasRole("FLORIST")`                 | 403 `FORBIDDEN`               |
| Handler (`@RequiresApprovedVendor`) | `vendor_profiles.status = APPROVED`, read from the DB per request | 403 `VENDOR_NOT_APPROVED` |

The first answers "is this a vendor account?"; the second answers "may this vendor transact?".
A `PENDING_APPROVAL`, `REJECTED` or `SUSPENDED` vendor passes the first and fails the second, which
is why the vendor's own `GET|PUT /api/v1/vendors/profile` stays reachable while the profile is not
approved. Approval state is never cached in the JWT, so an admin approval or suspension takes effect
on the vendor's very next request. See `docs/decisions.md` (D-13).
