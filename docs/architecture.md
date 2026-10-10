# Architecture

## 1. Purpose

FlowerConnect is a **hyperlocal flower marketplace**. It connects local florists with customers for same-day or scheduled flower delivery within a tight geographic radius. The platform handles browsing, ordering, payments, and delivery coordination.

Current development phase: **Phase 4 (Customer addresses, discovery and search)** — authentication (Phase 1), service
locations (Phase 2a), and vendor registration/approval with the `audit_log` trail (Phase 2b/2c) are
implemented, as are the admin user listing and status-management APIs (Phase 2d), the vendor and
admin frontends (Phase 2d), categories (Phase 3a), the product/inventory/stock-movement data model
(Phase 3b), the vendor catalog and inventory APIs (Phase 3c/3d), the expiry sweep (Phase 3e) and
product image handling (Phase 3f). The bytes of an uploaded image live behind a `StorageService`
abstraction — local disk today, a declared S3 stub behind the `s3` profile.

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
│           │   ├── catalog/              # Categories, products, product images
│           │   ├── inventory/            # Stock levels + append-only movement log
│           │   ├── storage/              # StorageService (local disk / S3 stub) + image pipeline
│           │   │   └── image/            # Type sniffing, decode/scale/encode, upload limits
│           │   ├── geo/                  # Service locations feature slice
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
│       │   ├── auth/
│       │   │   ├── api.ts
│       │   │   ├── stores/auth-store.ts
│       │   │   └── types.ts
│       │   ├── vendor/
│       │   │   ├── api.ts          # GET/PUT /api/v1/vendors/profile
│       │   │   ├── queries.ts      # TanStack Query hooks + cache lifecycle
│       │   │   ├── types.ts        # DTOs, Weekday, PUT payload builder
│       │   │   ├── format.ts       # INR, LocalTime, status/label helpers
│       │   │   ├── form-schema.ts  # Zod string-form schemas mirroring the DTO bounds
│       │   │   ├── components/     # Layout, status banner, error state, form fields
│       │   │   └── pages/          # dashboard, profile, settings, hours
│       │   └── admin/
│       │       ├── api.ts          # /api/v1/admin/vendors + /api/v1/admin/users
│       │       ├── queries.ts      # filter+page keyed queries, whole-listing invalidation
│       │       ├── types.ts        # DTOs, PageResponse, vendorActionsFor
│       │       ├── format.ts       # role/status labels, action labels, dates
│       │       ├── form-schema.ts  # Zod reason schema mirroring @NotBlank + @Size(500)
│       │       ├── components/     # Layout, reason dialog, pagination, error state, badges
│       │       └── pages/          # dashboard, vendors, users
│       ├── shared/
│       │   ├── components/
│       │   │   └── ProtectedRoute.tsx
│       │   └── lib/
│       │       ├── api.ts
│       │       └── api-error.ts    # ErrorResponse -> ApiErrorInfo
│       └── test/
│           ├── setup.ts
│           ├── factories.ts        # vendor profile / hours / admin user / page fixtures
│           ├── api-errors.ts       # AxiosError shaped like ErrorResponse
│           └── render.tsx          # renderWithProviders
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

### Implemented Frontend Routes
Customer routes (`/`, `/browse`, `/cart`, `/orders`, `/login`, `/register`) are placeholders. The
Phase 2d vendor area is the first real screen set:

| Path                | Page                          | Guard                          |
|---------------------|-------------------------------|--------------------------------|
| `/vendor`           | Vendor dashboard              | `ProtectedRoute roles={FLORIST}` |
| `/vendor/profile`   | Business/profile editing      | `ProtectedRoute roles={FLORIST}` |
| `/vendor/settings`  | Delivery settings and capacity | `ProtectedRoute roles={FLORIST}` |
| `/vendor/hours`     | Weekly operating hours        | `ProtectedRoute roles={FLORIST}` |

`ProtectedRoute` sends an unauthenticated visitor to `/login` (preserving `from`) and renders an
access-denied panel for a signed-in non-florist. The three `/vendor/*` pages read the caller's own
profile from `GET /api/v1/vendors/profile`; a `VENDOR_NOT_APPROVED` (403) response renders the
approval banner rather than an error, because the vendor must still be able to see and edit their
application while it is pending.

The whole vendor area is one TanStack Query cache key (`["vendor","profile"]`), cleared on logout so
a different florist cannot see the previous one's data.

The Phase 2d admin area is the second real screen set:

| Path                | Page                          | Guard                          |
|---------------------|-------------------------------|--------------------------------|
| `/admin`            | Admin dashboard (queues + navigation) | `ProtectedRoute roles={ADMIN}` |
| `/admin/vendors`    | Vendor listing, approval transitions | `ProtectedRoute roles={ADMIN}` |
| `/admin/users`      | User listing, status changes   | `ProtectedRoute roles={ADMIN}` |

`ProtectedRoute` renders an access-denied panel for a signed-in non-admin, and
`SecurityConfig`'s `hasRole("ADMIN")` matcher on `/api/v1/admin/**` is the actual authorization
authority on every request. `/admin` previews the two queues an administrator's own work produces
(`PENDING_APPROVAL` vendors, `SUSPENDED` users) rather than inventing analytics, because Phase 2
exposes no dashboard endpoint.

Both admin listings are **filter-and-page-keyed** queries (`["admin","vendors","list",status,page]`
and `["admin","users","list",role,status,page]`), and every mutation invalidates the whole listing
under `["admin","vendors"]` / `["admin","users"]` rather than patching one row into one page's
cache — a transition can move a record out of the active filter or off the current page. The admin
cache is cleared on logout alongside the vendor cache, so the next account to sign in on the same tab
sees neither list. See `docs/decisions.md` (D-16).

### Implemented Endpoints
Phase 1 (auth), Phase 2a (service locations), Phase 2c (vendor registration and admin approval),
Phase 3a (admin categories), Phase 3c (vendor catalog), Phase 3d (vendor inventory) and Phase 3f
(product images) endpoints are implemented. `@RequiresApprovedVendor` guards the catalog, inventory
and image routes; no order or payment endpoint exists yet — see `docs/decisions.md` (D-13).

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
| POST   | `/api/v1/vendors/products` | Create a product (FLORIST, APPROVED) | Phase 3c|
| GET    | `/api/v1/vendors/products` | List the vendor's own products (paged; `status` / `categoryId` / `name` filters) | Phase 3c|
| GET    | `/api/v1/vendors/products/{id}` | Read one of the vendor's products | Phase 3c|
| PUT    | `/api/v1/vendors/products/{id}` | Update a product; a rename regenerates the slug | Phase 3c|
| PATCH  | `/api/v1/vendors/products/{id}/deactivate` | Soft delete: move the product to `INACTIVE` | Phase 3c|
| GET    | `/api/v1/vendors/inventory/low-stock` | List this vendor's low-stock products (paged) | Phase 3d|
| GET    | `/api/v1/vendors/products/{id}/inventory` | Read one product's inventory | Phase 3d|
| POST   | `/api/v1/vendors/products/{id}/inventory/stock-in` | Add stock (`STOCK_IN`) | Phase 3d|
| POST   | `/api/v1/vendors/products/{id}/inventory/stock-out` | Remove stock (`STOCK_OUT`) | Phase 3d|
| POST   | `/api/v1/vendors/products/{id}/inventory/adjustments` | Signed correction (`ADJUSTMENT`); reason required | Phase 3d|
| POST   | `/api/v1/vendors/products/{id}/inventory/write-offs` | Record a write-off (`WASTE`); reason required | Phase 3d|
| PUT    | `/api/v1/vendors/products/{id}/inventory/low-stock-threshold` | Set the low-stock threshold | Phase 3d|
| PUT    | `/api/v1/vendors/products/{id}/inventory/expiry-date` | Set or clear the expiry date | Phase 3d|
| GET    | `/api/v1/vendors/products/{id}/inventory/movements` | Paged movement history for one product | Phase 3d|
| POST   | `/api/v1/vendors/products/{productId}/images` | Upload an image (multipart `file`, optional `primary`); decoded, scaled and re-encoded by the server | Phase 3f|
| GET    | `/api/v1/vendors/products/{productId}/images` | List a product's images in display order | Phase 3f|
| PUT    | `/api/v1/vendors/products/{productId}/images/{imageId}/primary` | Set the product cover; clears the previous one | Phase 3f|
| PUT    | `/api/v1/vendors/products/{productId}/images/order` | Reorder images; must be an exact permutation | Phase 3f|
| DELETE | `/api/v1/vendors/products/{productId}/images/{imageId}` | Delete an image and its stored object; promotes the next when the cover is removed | Phase 3f|
| GET    | `/api/v1/addresses` | List the caller's saved addresses (paged, default first) | Phase 4.1 |
| POST   | `/api/v1/addresses` | Save an address; the first becomes the default automatically | Phase 4.1 |
| GET    | `/api/v1/addresses/{id}` | Read one of the caller's addresses | Phase 4.1 |
| PUT    | `/api/v1/addresses/{id}` | Full-replacement update of one address | Phase 4.1 |
| DELETE | `/api/v1/addresses/{id}` | Delete an address; deleting the default promotes the oldest remaining | Phase 4.1 |
| GET    | `/api/v1/discover` | Public: approved, order-accepting vendors within delivery radius of `locationId` (public by design, D-34) | Phase 4.3 |
| GET    | `/api/v1/search` | Public: ACTIVE, in-stock products near `locationId` with q/category/price/vendor filters and four sort modes (public by design, D-35) | Phase 4.4 |
| GET    | `/api/v1/vendors/{id}/storefront` | Public: one vendor's public profile plus its ACTIVE products, paginated; 404 for an unknown or non-approved vendor (public by design, D-36) | Phase 4.5 |
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
| `STORAGE_LOCAL_DIR` | `uploads` (dev) / `/var/lib/flowerconnect/uploads` (prod) | Root directory for local-disk uploads (D-26) |
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
- **Uploads**: The format is decided by the file's leading bytes, never the filename or the declared
  `Content-Type`; every accepted image is decoded, scaled and re-encoded by the server, so EXIF/GPS is
  dropped and the stored object is never the bytes the client sent. Keys are server-generated. See
  `docs/decisions.md` (D-27, D-26)

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

Because the two layers produce two different 403 bodies, tests assert `$.code` and not only the
HTTP status — `FORBIDDEN` means "not a vendor account", `VENDOR_NOT_APPROVED` means "a vendor
that may not transact yet". `docs/rbac-matrix.md` records every Phase 3 endpoint with each cell
(401, 403 role, 403 approval, 403 foreign, 404, 2xx) mapped to the integration test that asserts
it, and explains why `@WebMvcTest` slices cannot cover any of it.
