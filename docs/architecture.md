# Architecture

## 1. Purpose

FlowerConnect is a **hyperlocal flower marketplace**. It connects local florists with customers for same-day or scheduled flower delivery within a tight geographic radius. The platform handles browsing, ordering, payments, and delivery coordination.

Current development phase: **Phase 0 (Scaffold)** — the project has an initialized repository with basic directory layout, database schema for users/roles, and a placeholder frontend. No business logic or REST API endpoints are implemented yet.

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
│  Roles + Users (Phase 0)                              │
│  Future: Catalog, Orders, Payments, Delivery          │
├──────────────────────────────────────────────────────┤
│                    Redis 7 (docker)                    │
│  Session cache, future: rate limiting, cart state     │
└──────────────────────────────────────────────────────┘
```

### Service Boundaries (Planned)

| Service      | Responsibility                          | Phase    |
|--------------|-----------------------------------------|----------|
| Auth         | JWT issuance, user registration/login   | Phase 1+ |
| Catalog      | Florist listings, product inventory     | Phase 2+ |
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
| Migrations     | Flyway (baseline V1)                |
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
│           │   └── FlowerConnectApplication.java
│           └── resources/
│               ├── application.yml
│               └── db/migration/
│                   └── V1__baseline.sql
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

## 5. Database Schema (Phase 0)

Source of truth: `backend/src/main/resources/db/migration/V1__baseline.sql`

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
| phone         | VARCHAR(32) | NULL                                   |
| is_active     | TINYINT(1)  | NOT NULL, DEFAULT 1                    |
| created_at    | DATETIME(6)| NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) |
| updated_at    | DATETIME(6)| NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE |

### Indexes
- `idx_users_role` on `users(role_id)`
- `idx_users_active` on `users(is_active)`

### Role Hierarchy (Planned, Phase 0)
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

### Endpoints (Phase 0 — none implemented yet)
All routes are placeholders. Phase 1 will implement:

| Method | Path                | Description                        | Phase  |
|--------|---------------------|------------------------------------|--------|
| POST   | `/api/v1/auth/login`| Authenticate and issue JWT         | Phase 1|
| POST   | `/api/v1/auth/refresh` | Issue new access token            | Phase 1|
| POST   | `/api/v1/auth/register` | Register new user                | Phase 1|
| POST   | `/api/v1/auth/logout` | Invalidate refresh token           | Phase 1|
| GET    | `/api/v1/users/me`   | Get current user profile            | Phase 1|
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
