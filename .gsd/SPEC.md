# FlowerConnect

> **Status**: `FINALIZED`
>
> 🔒 **Planning Lock**: Master project specification baseline for FlowerConnect.

## Vision
FlowerConnect is a production-grade hyperlocal flower marketplace connecting local florists with nearby customers, backed by platform administration, inventory management, and net settlement governance.

## Goals
1. **Hyperlocal Centroid Matching** — Location-scoped discovery using seeded service location centroids (no GPS / browser geolocation).
2. **Perishable Inventory & Real-Time Stock** — Per-product inventory with automated expiration sweeps (`WASTE`), pessimistic write locks, and stock reservations.
3. **Multi-Shop Orders & Net Settlement** — Robust order state machine with dual revenue streams (commission + platform fee) and COD/Online net settlement cycles.
4. **Florist POS Mode** — Commission-free walk-in sales sharing the real-time catalog inventory pool.

## Non-Goals (Out of Scope)
- Browser/device GPS tracking (all discovery relies on `service_locations` centroid matching).
- Distributed multi-region clustering in v1 (designed as modular monolith with Spring Boot + MySQL).
- Third-party courier driver dispatch (deliveries handled directly by florists).

## Constraints
- **Backend**: Java 17, Spring Boot 3.2.5, Spring Security 6, Spring Data JPA, Flyway, MySQL 8.
- **Frontend**: React 18 SPA, Vite, TypeScript strict, Tailwind CSS 3, Zustand, TanStack Query.
- **Security**: In-memory JWT access token + httpOnly refresh token cookie with rotation; BCrypt passwords.
- **Architecture**: Modular monolith with REST API prefix `/api/v1` and 3-layer architecture.

## Success Criteria
- [x] Phase 0: Project scaffold, build pipelines, testcontainers, baseline Flyway schema.
- [x] Phase 1: Authentication, user management, JWT tokens, rate limiting.
- [x] Phase 2: Vendor profiles, service locations, opening hours, admin approval gating.
- [x] Phase 3: Category CRUD, product catalog, inventory locking, expiry sweeper, image pipeline, vendor UI.
- [ ] Phase 4: Customer addresses, discovery API, search, storefront, and home page UI.
- [ ] Phases 5–14: Cart, checkout, orders, payments, cancellations, billing, POS, analytics, and hardening.

## Core Domain Principles
1. **Centroid-Based Geo Matching:** Customers select city/area/pincode matched against vendor delivery radius.
2. **Lock Discipline:** Ordered pessimistic locking (`PESSIMISTIC_WRITE`) on inventory rows for all stock mutations and reservations.
3. **Order Lifecycle:** Explicit state transitions recorded in `order_status_history`.
4. **Ledger Integrity:** Net settlement calculations for COD dues vs online credits.

---

*Authoritative references: `docs/FlowerConnect_Implementation_Plan_v2.2.md`, `docs/architecture.md`, `docs/decisions.md`, `AGENTS.md`.*
