# FlowerConnect — Roadmap

Source of truth: [FlowerConnect_Implementation_Plan_v2.2.md](file:///c:/Projects/FlowerConnect/docs/FlowerConnect_Implementation_Plan_v2.2.md) and [progress.md](file:///c:/Projects/FlowerConnect/docs/progress.md).

---

### Phase 0: Scaffolding, Infrastructure and Foundations `[COMPLETED]`
- [x] 0.1 Spring Boot 3.2.5 init (JPA, Security, Validation, MySQL driver, Lombok, MapStruct, Flyway, Bucket4j + Caffeine, springdoc)
- [x] 0.2 React 18 + Vite + TypeScript init (Tailwind, Zustand, TanStack Query, React Router, Vitest)
- [x] 0.3 Docker Compose (MySQL 8 with healthcheck, Mailhog, backend, frontend)
- [x] 0.4 Project package structure and frontend feature layout
- [x] 0.5 Flyway baseline (`V1__baseline.sql`: `roles`, `users`)
- [x] 0.6 CI-ready scripts (`mvnw`, `verify.ps1`/`verify.sh`)
- [x] 0.7 Error framework (`ErrorCode`, `BusinessException`, `@RestControllerAdvice`, `ApiError`)
- [x] 0.8 Profiles (`application-dev.yml`, `application-prod.yml`, `application-test.yml`)
- [x] 0.9 Documentation skeleton (`docs/`)
- [x] 0.T Test baseline (`AbstractIntegrationTest` with singleton MySQL Testcontainer)
- [x] 0.10 `.gitignore` setup

---

### Phase 1: Authentication and User Management `[COMPLETED]`
- [x] 1.1 Roles & users (`CUSTOMER`, `FLORIST`/`VENDOR`, `ADMIN`, status `ACTIVE`/`SUSPENDED`/`DISABLED`)
- [x] 1.2 Security and tokens (JWT access token in memory, httpOnly refresh cookie, rotation + reuse detection)
- [x] 1.3 Account status enforcement (instant suspension check on every request, session revocation)
- [x] 1.4 Auth API (`/register`, `/login`, `/refresh`, `/logout`, `/users/me`)
- [x] 1.5 Password reset (`/forgot-password`, `/reset-password` via Mailhog)
- [x] 1.6 Admin bootstrap (idempotent seed via environment credentials)
- [x] 1.7 React auth pages (Login, Register, Forgot/Reset password)
- [x] 1.8 Route guards (`ProtectedRoute`)
- [x] 1.9 Auth store & interceptors (Zustand, Axios singleton refresh guard)
- [x] 1.10 Role-boundary test utility (`RoleBoundaryTester`)
- [x] 1.11 Rate limiting (Bucket4j + Caffeine on auth endpoints, IP & email hashed buckets)
- [x] 1.12 Profile & password updates (`PATCH /users/me`, `POST /users/me/password`)
- [x] 1.13 Scheduled token cleanup (`RefreshTokenCleanupScheduler`)
- [x] 1.T Phase 1 test suite

---

### Phase 2: Vendor Profiles, Delivery Settings and Admin Approval `[COMPLETED]`
- [x] 2.1 Service locations (`service_locations` table with Bengaluru centroid seeds, `GET /api/v1/locations`)
- [x] 2.2 `vendor_profiles` data model (business info, radius, status `PENDING_APPROVAL`/`APPROVED`/`REJECTED`/`SUSPENDED`)
- [x] 2.3 Delivery settings (fees, radius, min order, prep time, slots)
- [x] 2.4 Opening hours (`vendor_hours` table)
- [x] 2.5 Vendor registration API (`POST /api/v1/vendors/register`, `GET|PUT /api/v1/vendors/profile`)
- [x] 2.6 Admin vendor management (`GET /api/v1/admin/vendors`, approve/reject/suspend/reinstate + `audit_log`)
- [x] 2.7 Approval gating (`@RequiresApprovedVendor`, `VendorApprovalGuard`)
- [x] 2.8 Admin user status (`PATCH /api/v1/admin/users/{id}/status` + token revocation + `audit_log`)
- [x] 2.9 Vendor dashboard shell (Florist navigation, profile/settings/hours editor)
- [x] 2.10 Admin dashboard shell (Vendor approvals with reason dialog, user status management)
- [x] 2.T Phase 2 verification & RBAC tests

---

### Phase 3: Catalog and Inventory Management `[COMPLETED]`
- [x] 3.1 Category entity (`categories` table, hierarchical admin CRUD, active roots public read)
- [x] 3.2 Product entity (`products` table, vendor-scoped, slug generation with suffix retry)
- [x] 3.3 Inventory entity (`inventory` table, auto-created at qty 0, availability calculation)
- [x] 3.4 Stock movement log (`stock_movements` table, append-only movement history)
- [x] 3.5 Vendor catalog API (`VendorProductController`, filtered listing, soft delete)
- [x] 3.6 Vendor inventory API (`VendorInventoryController`, pessimistic locks, 4 stock actions, alert thresholds)
- [x] 3.7 Expiry scheduler (`InventoryExpiryScheduler`, `@Scheduled` sweep on `Clock`, `WASTE` delist)
- [x] 3.8 Image handling (`StorageService` local/S3-stub, content-sniffed pipeline, resize/compress, single-primary lock)
- [x] 3.9 Vendor catalog & inventory UI (Catalog listing/filters, Shopify-style product form, stock dialog, inventory table)
- [x] 3.T Phase 3 verification & RBAC matrix (`docs/rbac-matrix.md`, 508 unit + 362 integration tests)

---

### Phase 4: Customer Addresses, Discovery and Search `[PENDING — NEXT]`
- [ ] 4.1 Address book (`addresses` table: label, line1, line2, `service_location_id`, default flag; owner-only CRUD `/api/v1/addresses`)
- [ ] 4.2 Location picker (session-backed city/area/pincode picker backed by `service_locations`, no GPS)
- [ ] 4.3 Geo discovery API (`GET /api/v1/discover?locationId=`, bounding box pre-filter + Haversine distance ≤ vendor radius)
- [ ] 4.4 Product search API (`GET /api/v1/search`, keyword, category, price bounds, sorting, location-scoped)
- [ ] 4.5 Storefront API (`GET /api/v1/vendors/{id}/storefront`, public vendor profile + active in-stock products)
- [ ] 4.6 Customer home page (Location picker hero, nearby vendor cards with distance/delivery fee/ratings)
- [ ] 4.7 Search and filter UI (Search bar, category chips, price slider, sort dropdown)
- [ ] 4.8 Storefront page (Florist banner, product grid, add-to-cart controls)
- [ ] 4.9 Product detail modal (Image carousel, description, free-text gift note)
- [ ] 4.T Phase 4 test suite (Public endpoints anonymity, empty results handling, radius bounds, address ownership, RBAC)

---

### Phase 5: Cart and Checkout `[PENDING]`
- [ ] 5.1 Multi-shop cart data model (`carts`, `cart_items` with price snapshots and gift notes)
- [ ] 5.2 Cart API (Add, update, remove items; stock availability check)
- [ ] 5.3 Checkout quote API (`POST /api/v1/checkout/quote`: fee calculation, slot validation, minimum orders)
- [ ] 5.4 Checkout transaction (Transactional ordered pessimistic lock, stock reservation, split order per shop)
- [ ] 5.5 Delivery slots (Computation from vendor hours, slot duration, max orders)
- [ ] 5.6 Cart UI (Slide-out drawer grouped by florist shop, quantity stepper)
- [ ] 5.7 Checkout page (Address selector, per-shop delivery slot, payment method selection, order summary)
- [ ] 5.8 Phase 5 verification & concurrency tests

---

### Phase 6: Order Management `[PENDING]`
- [ ] 6.1 Order state machine (`OrderStateMachine`, transitions, status history)
- [ ] 6.2 Vendor actions API (Accept commits reserved stock, reject releases reservation, prepare, out-for-delivery, deliver)
- [ ] 6.3 Customer order API (Order history, detail view, status timeline)
- [ ] 6.4 Vendor order API (Incoming orders dashboard, daily summary)
- [ ] 6.5 Auto-reject scheduler (Unaccepted orders timeout sweeper)
- [ ] 6.6 Customer orders UI (Order tracking stepper, polling)
- [ ] 6.7 Vendor order board (Kanban board: New → Preparing → Out for Delivery → Delivered)
- [ ] 6.T Phase 6 test suite

---

### Phase 7: Payments and COD Settlement `[PENDING]`
- [ ] 7.1 Payments model (`payments` table, payment lifecycle)
- [ ] 7.2 Gateway abstraction (`PaymentGateway`, `CodPaymentProvider`)
- [ ] 7.3 COD collection tracking
- [ ] 7.4 Optional online payment test gateway (Signature webhook, idempotent confirmation)
- [ ] 7.5 Reservation expiry sweeper
- [ ] 7.6 Payment UI
- [ ] 7.T Phase 7 tests

---

### Phase 8: Cancellations, Refunds and Disputes `[PENDING]`
- [ ] 8.1 Cancellation rules engine (`CancellationService`, restock vs no-restock rules)
- [ ] 8.2 Cancel API (`POST /api/v1/orders/{id}/cancel`)
- [ ] 8.3 Refund handling (Online refund transition, COD zero-movement handling)
- [ ] 8.4 Disputes model & API (`disputes` table, customer raise, vendor respond, admin resolve)
- [ ] 8.5 Dispute resolution effects (Ledger adjustments, audit log, notifications)
- [ ] 8.6 Dispute & cancellation UI
- [ ] 8.T Phase 8 tests

---

### Phase 9: Notifications and Real-Time Updates `[PENDING]`
- [ ] 9.1 Transactional domain events (`OrderPlaced`, `OrderStatusChanged`, `DisputeOpened`, etc.)
- [ ] 9.2 In-app notification system (`notifications` table, unread count, bell UI)
- [ ] 9.3 Async email sender (Mailhog templates)
- [ ] 9.4 Polling hooks (TanStack Query refetch interval for live order updates)
- [ ] 9.5 Optional SSE emitter
- [ ] 9.T Phase 9 tests

---

### Phase 10: Billing, Commissions and Settlement Ledger `[PENDING]`
- [ ] 10.1 Commission rate engine (Platform default + vendor override)
- [ ] 10.2 Commission records (Snapshotted upon order delivery)
- [ ] 10.3 Settlement ledger (`settlement_entries`: online credit, COD dues, dispute adjustments)
- [ ] 10.4 Weekly settlement cycles (`settlements` generation, netting dues vs credits)
- [ ] 10.5 Vendor billing UI (Statements, dues, revenue breakdown)
- [ ] 10.6 Admin billing UI (Platform revenue, settlement reconciliation)
- [ ] 10.7 Overdue dues enforcement
- [ ] 10.T Phase 10 tests

---

### Phase 11: POS (Walk-in Sales) `[PENDING]`
- [ ] 11.1 POS entities (`pos_transactions`, `pos_items`)
- [ ] 11.2 POS quick-sale API (Idempotency keys, no cart persistence)
- [ ] 11.3 Shared inventory synchronization (`SALE_POS` movement, locks)
- [ ] 11.4 POS fullscreen tablet UI
- [ ] 11.5 POS sales reports (Commission-free segregation)
- [ ] 11.T Phase 11 tests

---

### Phase 12: Ratings, Reviews and Analytics `[PENDING]`
- [ ] 12.1 Review entity (`reviews` table, verified purchase constraint)
- [ ] 12.2 Review API (Submit, vendor reply, admin moderation, rating aggregation)
- [ ] 12.3 Review UI (Star badges, storefront reviews, post-delivery prompt)
- [ ] 12.4 Admin analytics dashboard
- [ ] 12.5 Vendor analytics dashboard
- [ ] 12.T Phase 12 tests

---

### Phase 13: Security Hardening `[PENDING]`
- [ ] 13.1 Rate-limit audit & tuning
- [ ] 13.2 Upload security verification
- [ ] 13.3 CORS, headers & CSRF audit
- [ ] 13.4 Token storage & cookie flag verification
- [ ] 13.5 Audit log completeness
- [ ] 13.6 Endpoint access sweep & automated RBAC check
- [ ] 13.7 Error sanitization & dependency scan
- [ ] 13.T Security verification suite

---

### Phase 14: Testing, Polish, Production Readiness and Deliverables `[PENDING]`
- [ ] 14.1 Full journey integration test suite
- [ ] 14.2 Frontend comprehensive flow tests
- [ ] 14.3 OpenAPI/Swagger documentation completion
- [ ] 14.4 Responsive design pass (Mobile customer, tablet POS, desktop admin/vendor)
- [ ] 14.5 Realistic demo seed data
- [ ] 14.6 Production profile & container optimization
- [ ] 14.7 Performance & query sanity check
- [ ] 14.8 Final documentation package
- [ ] 14.9 Demo walkthrough script
- [ ] 14.10 Synopsis reconciliation
- [ ] 14.11 Clean-clone rehearsal
- [ ] 14.12 Project report materials

---

### Phase 15: AI Demand Prediction (Optional Stretch) `[PENDING]`
- [ ] 15.1 Sales history aggregation
- [ ] 15.2 Baseline statistical forecasting service
- [ ] 15.3 Forecast API
- [ ] 15.4 Vendor forecast UI
- [ ] 15.5 Evaluation metrics & report
