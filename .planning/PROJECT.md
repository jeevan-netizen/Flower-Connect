# FlowerConnect — Project Specification

## Overview
FlowerConnect is a production-grade hyperlocal flower marketplace connecting local florists/vendors with nearby customers, backed by platform administration and governance.

- **Frontend:** React 18 SPA (Vite, TypeScript strict, Tailwind CSS 3, React Router 6, Zustand, TanStack Query, React Hook Form + Zod, Vitest)
- **Backend:** Spring Boot 3.2.5 (Java 17, Spring Security 6, Spring Data JPA / Hibernate 6, Flyway, MySQL 8, Bucket4j + Caffeine, springdoc OpenAPI)
- **Architecture:** Modular monolith, REST API prefix `/api/v1`, 3-layer architecture (Controller → Service → Repository).
- **Security:** Spring Security 6, JWT Bearer access token (in memory only), httpOnly refresh token cookie with reuse rotation and family revocation, BCrypt passwords, robust role-based access control (`CUSTOMER`, `FLORIST`/`VENDOR`, `ADMIN`).

## Core Domain Principles
1. **Hyperlocal Model:** Centroid-based location matching (`service_locations` table with city/area/pincode centroids) — strictly **no GPS / browser geolocation**. Vendors define `delivery_radius_km`.
2. **Perishable Inventory:** Per-product stock with optional `expiry_date`. Background scheduler sweeps expired stock (`WASTE` movement) and deactivates expired products.
3. **Stock Reservation & Lock Discipline:** Ordered pessimistic locking (`PESSIMISTIC_WRITE`) on inventory rows. Checkout **reserves** stock; stock is committed (`SALE_ONLINE`) when the florist accepts the order.
4. **Order State Machine:** Explicit transitions recorded in `order_status_history`. Terminal states: `DELIVERED`, `CANCELLED`, `REJECTED`.
5. **Dual Revenue Streams & Net Settlement:** Platform charges commission on item subtotal + flat platform fee. COD vs. Online ledger entries net out in weekly settlement cycles.
6. **POS Mode:** Commission-free walk-in sales from the same real-time inventory pool (`SALE_POS`) with idempotency keys.

## Authoritative Documentation
- [AGENTS.md](file:///c:/Projects/FlowerConnect/AGENTS.md) — Coding conventions and agent rules
- [FlowerConnect_Implementation_Plan_v2.2.md](file:///c:/Projects/FlowerConnect/docs/FlowerConnect_Implementation_Plan_v2.2.md) — Master implementation plan
- [progress.md](file:///c:/Projects/FlowerConnect/docs/progress.md) — Detailed historical progress log
- [architecture.md](file:///c:/Projects/FlowerConnect/docs/architecture.md) — System architecture and component structure
- [decisions.md](file:///c:/Projects/FlowerConnect/docs/decisions.md) — Architecture Decision Records (ADRs D-1 through D-31)
- [rbac-matrix.md](file:///c:/Projects/FlowerConnect/docs/rbac-matrix.md) — Full endpoint RBAC access matrix
- [known-issues.md](file:///c:/Projects/FlowerConnect/docs/known-issues.md) — Bug tracker and known limitations
