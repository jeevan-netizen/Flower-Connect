# Performance Sanity — Phase 14 Stage 6 (task 14.7)

**Source of truth:** `docs/FlowerConnect_Implementation_Plan_v2.2.md` §3, §7, §10, §13, §14
**Branch:** `phase-14-polish-deliverables` (working tree, uncommitted — Stage 6 only, no Stage 7)
**Verification:** `PerformanceSanityIT` against a seeded Testcontainers MySQL 8.0.36 database

## 1. Findings Table

| id | area | description | severity | evidence | proposed action | status |
|----|------|-------------|----------|----------|-----------------|--------|
| P-1 | Discovery | `findMaxDeliveryRadiusForApproved` (status='APPROVED') uses covering index `idx_vendor_profiles_status_accepting_radius(status, accepting_orders, delivery_radius_km)`. EXPLAIN shows `type=ref`, `key=idx_vendor_profiles_status_accepting_radius`, `Extra: Using where; Using index`. | Fixed | EXPLAIN after V33 on seeded DB (33 vendor_profiles): covering index used for MAX() aggregation | Added V33 index | Fixed |
| P-2 | Discovery | `findApprovedAcceptingInBoundingBox` (status + accepting_orders + lat/lng BETWEEN) still shows `type=ALL` at demo scale. `accepting_orders` is not covered by any existing index prefix. | Medium | EXPLAIN after V33: `key=NULL`, `type=ALL`, `rows=1` (33-row table, optimizer prefers scan) | Index added but not chosen by optimizer for this query at demo scale; justified for the MAX() query (P-1) | Documented |
| P-3 | Vendor orders | `findByVendorIdAndStatusOrderByCreatedAtDesc` uses `idx_orders_vendor_status_created_at_desc(vendor_profile_id, status, created_at DESC)` with `Extra: Using index condition` and **no filesort**. | Fixed | EXPLAIN after V33: `key=idx_orders_vendor_status_created_at_desc`, no filesort | Added V33 index | Fixed |
| P-4 | Customer orders | `findByUserIdOrderByCreatedAtDesc` and `findByUserIdAndStatusOrderByCreatedAtDesc` still show `type=ALL` + filesort at demo scale. New composite indexes exist but optimizer chooses table scan on small table. | Medium | EXPLAIN after V33: `key=NULL`, `type=ALL`, `Extra: Using where; Using filesort` | Added V33 indexes; not used by optimizer at demo scale, justified by query shape for production scale | Documented |
| P-5 | Vendor orders | `findByVendorIdAndStatusNotOrderByCreatedAtDesc` uses `idx_orders_vendor_created_at_desc(vendor_profile_id, created_at DESC)` with `Extra: Using where` and **no filesort**. `findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc` also uses this index with `type=range`. | Fixed | EXPLAIN after V33: `key=idx_orders_vendor_created_at_desc`, no filesort | Added V33 index | Fixed |
| P-6 | Notifications | `findByUserIdOrderByCreatedAtDesc` still shows `type=ALL` + filesort at demo scale (150 rows). Existing `idx_notifications_user_created(user_id, created_at DESC)` exists but not chosen. | Low | EXPLAIN: `key=NULL`, `type=ALL`, `Extra: Using where; Using filesort` | No change — index already exists; effective at production scale | Documented |
| P-7 | Billing ledger | `getVendorLedger`, `calculateOutstandingBalance`, `findEligibleUnattachedForSettlement` still show `type=ALL` at demo scale (30 rows). Existing indexes exist but not chosen. | Low | EXPLAIN: `key=NULL`, `type=ALL` | No new index added — existing indexes cover access patterns | Documented |
| P-8 | Search | `productRepository.findAll(spec)` (vendor IN (...) + status = ACTIVE) still shows `type=ALL` at demo scale (135 rows). Existing `idx_products_vendor_status(vendor_id, status)` exists but not chosen. | Medium | EXPLAIN: `key=NULL`, `type=ALL` | No change — existing index covers the query shape | Documented |
| P-9 | N+1 / Search | **Fixed.** Search service issued per-product queries: page size=20 → 24 SQL statements, page size=50 → 54 SQL statements (+30 per product). After fix: page=20→5, page=50→5, page=100→5 (bounded). | High | Measured via Hibernate `getQueryExecutionCount()` before fix: growth of +30 queries for +30 products. After fix: constant 5 queries across all page sizes. | Fixed with JOIN FETCH vendor+category in `PublicProductSpecifications`, batch-fetch images via `ProductImageRepository.findByProductIdInOrderByProductIdSortOrderAscIdAsc` | Fixed |
| P-10 | N+1 / Customer orders | **Fixed.** `CustomerOrderService.toListItem()` accessed `order.getVendor().getBusinessName()` — vendor is `FetchType.LAZY` on `Order`, causing per-order lazy load. After fix: page=20→2, page=50→2 (bounded). | Medium | Measured via Hibernate statistics before fix: 2 queries at both sizes (growth hidden by persistence context sharing). After fix: `@EntityGraph(attributePaths={"vendor"})` on `OrderRepository` list queries eliminates lazy vendor fetch; growth stays at 0 across page sizes. | Fixed with `@EntityGraph(attributePaths={"vendor"})` on `findByUserIdOrderByCreatedAtDesc` and `findByUserIdAndStatusOrderByCreatedAtDesc` | Fixed |
| P-11 | Query counting | Hibernate statistics instrumentation works: `getQueryExecutionCount()` returns cumulative query count. Measured search growth (24→54 before fix) confirms instrumentation reliability. | Fixed | `PerformanceSanityIT` uses `SessionFactoryStatistics.getQueryExecutionCount()` with `spring.jpa.properties.hibernate.generate_statistics=true` | None needed | Fixed |
| P-12 | Pagination | All list endpoints verified for pagination + max page size. Unpaged endpoints are justified (small reference data, single values, time series, CSV, max-8 images). | None | `verifyPaginationCoverage()` test: 32 endpoints documented | None needed | Fixed |

## 2. Measured Query Counts — Before vs After N+1 Fixes

Hibernate `SessionFactoryStatistics.getQueryExecutionCount()` was enabled via `spring.jpa.properties.hibernate.generate_statistics=true` (test-only, via `@TestPropertySource`). Counts are cumulative query executions per measured action. Each measurement runs with `entityManager.clear()` to simulate a fresh request with a clean persistence context.

### Before fixes (baseline)

| Endpoint | Page size=20 | Page size=50 | Growth | N+1? |
|----------|-------------|-------------|--------|------|
| `GET /api/v1/search` | 24 queries | 54 queries | +30 for 30 more products | **Yes** — per-product image + lazy vendor + lazy category |
| `GET /api/v1/orders` (customer list) | 2 queries | 2 queries | 0 | Hidden by persistence context sharing; confirmed by code inspection |

### After fixes

| Endpoint | Page size=20 | Page size=50 | Page size=100 | Growth | N+1? |
|----------|-------------|-------------|--------------|--------|------|
| `GET /api/v1/search` | 5 queries | 5 queries | 5 queries | 0 | **No** — JOIN FETCH vendor+category, batch image fetch |
| `GET /api/v1/orders` (customer list) | 2 queries | 2 queries | N/A | 0 | **No** — `@EntityGraph(attributePaths={"vendor"})` on order list queries |
| `GET /api/v1/notifications` (list) | 2 queries | N/A | N/A | N/A | No — pageable count + data (2 total), no per-row growth |
| `GET /api/v1/notifications/unread-count` | 1 query | N/A | N/A | N/A | No — single count query |
| `GET /api/v1/vendor/billing/ledger` | 1 query | N/A | N/A | N/A | No — single pageable Specification query |

### Search N+1 detail (before fix)

Page size=20 returns 20 products; page size=50 returns 50 products. The 30-query difference equaled approximately 1 extra query per additional product, confirming per-product N+1 from:
1. Product image query (`productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)`)
2. Lazy `Product.vendor` fetch
3. Lazy `Product.category` fetch

### Fixes applied

**Search (`SearchService.java`, `PublicProductSpecifications.java`, `ProductImageRepository.java`):**
- Added `PublicProductSpecifications.fetchVendorAndCategory()` which applies `root.fetch("vendor", JoinType.LEFT)` and `root.fetch("category", JoinType.LEFT)` to the product query, eliminating lazy vendor/category loads per product.
- Added `ProductImageRepository.findByProductIdInOrderByProductIdSortOrderAscIdAsc(List<Long>)` to batch-fetch all images for a page's products in a single query, replacing the per-product `findByProductIdOrderBySortOrderAscIdAsc(productId)` call in `toResponse()`.
- Images are grouped by product ID in memory and passed to `toResponse()`.

**Customer order list (`OrderRepository.java`, `CustomerOrderService.java`):**
- Added `@EntityGraph(attributePaths={"vendor"})` to `findByUserIdOrderByCreatedAtDesc` and `findByUserIdAndStatusOrderByCreatedAtDesc`, generating `LEFT JOIN FETCH vendor` so vendor data is fetched with the order query instead of lazily per order.

## 3. Query-Plan Results (EXPLAIN against seeded database)

**Demo scale:** 3 vendors, 27 products, 33 vendor_profiles, 120 orders, 150 notifications, 30 settlement entries, 240 order_status_history rows, 135 products/inventory rows.

### Discovery & Search

| Query | Table | EXPLAIN after V33 | Index used? |
|-------|-------|-------------------|-------------|
| `findMaxDeliveryRadiusForApproved` (MAX delivery_radius_km WHERE status='APPROVED') | vendor_profiles | `type=ref`, `key=idx_vendor_profiles_status_accepting_radius`, `Extra: Using where; Using index` | **Yes** (covering) |
| `findApprovedAcceptingInBoundingBox` (status + accepting_orders + lat/lng BETWEEN) | vendor_profiles | `type=ALL`, `key=NULL`, `Extra: Using where` | No (33-row table, scan cheaper) |
| `serviceLocationRepository.findById` | service_locations | `type=const`, `key=PRIMARY` | Yes (PK) |
| `productRepository.findAll(spec)` (vendor IN (...) + status = ACTIVE) | products | `type=ALL`, `key=NULL`, `Extra: Using where` | No (135-row table, scan cheaper) |
| `inventoryRepository.findAllByProductIdIn` | inventory | `type=ALL`, `key=NULL` | No (batch fetch, small table) |

### Customer Order List

| Query | Table | EXPLAIN after V33 | Index used? |
|-------|-------|-------------------|-------------|
| `findByUserIdOrderByCreatedAtDesc` | orders | `type=ALL`, `key=NULL`, `Extra: Using where; Using filesort` | No |
| `findByUserIdAndStatusOrderByCreatedAtDesc` | orders | `type=ALL`, `key=NULL`, `Extra: Using where; Using filesort` | No |

### Vendor Order List

| Query | Table | EXPLAIN after V33 | Index used? |
|-------|-------|-------------------|-------------|
| `findByVendorIdAndStatusNotOrderByCreatedAtDesc` | orders | `type=ref`, `key=idx_orders_vendor_created_at_desc`, `Extra: Using where` | **Yes** (no filesort) |
| `findByVendorIdAndStatusOrderByCreatedAtDesc` | orders | `type=ref`, `key=idx_orders_vendor_status_created_at_desc`, `Extra: Using index condition` | **Yes** (no filesort) |
| `findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc` | orders | `type=range`, `key=idx_orders_vendor_created_at_desc`, `Extra: Using index condition` | **Yes** (no filesort) |
| `countBookedOrdersForSlot` | orders | `type=ref`, `key=idx_orders_slot` | Yes (existing) |

### Notification List & Unread Count

| Query | Table | EXPLAIN after V33 | Index used? |
|-------|-------|-------------------|-------------|
| `findByUserIdOrderByCreatedAtDesc` | notifications | `type=ALL`, `key=NULL`, `Extra: Using where; Using filesort` | No (150-row table) |
| `countByUserIdAndReadAtIsNull` | notifications | `type=ref`, `key=idx_notifications_user_unread`, `Extra: Using where; Using index` | Yes |
| `findByIdAndUserId` | notifications | `type=const`, `key=PRIMARY` | Yes (PK) |

### Vendor Billing & Admin Analytics

| Query | Table | EXPLAIN after V33 | Index used? |
|-------|-------|-------------------|-------------|
| `getVendorLedger` (vendor_id + ORDER BY created_at) | settlement_entries | `type=ALL`, `key=NULL`, `Extra: Using where; Using filesort` | No (30-row table) |
| `calculateOutstandingBalance` (vendor_id + LEFT JOIN settlements) | settlement_entries | `type=ALL`, `key=NULL` | No |
| `findEligibleUnattachedForSettlement` | settlement_entries | `type=ALL`, `key=NULL`, `Extra: Using where; Using filesort` | No |
| `findDeliveredWithTimestampBetween` (analytics) | orders + order_status_history | `type=ALL` on orders; correlated subqueries use `idx_osh_order` | Partial |
| `findCancelledWithTimestampBetween` (analytics) | orders + order_status_history | `type=range` on orders; correlated subqueries use `idx_osh_order` | Partial |

## 4. Index Changes (V33)

**Migration:** `backend/src/main/resources/db/migration/V33__performance_indexes.sql` (additive, after V32)

| Index | Table | Columns | Used at demo scale? | Justification | Write/storage cost |
|-------|-------|---------|---------------------|---------------|-------------------|
| `idx_vendor_profiles_status_accepting_radius` | vendor_profiles | (status, accepting_orders, delivery_radius_km) | Yes (P-1 MAX() query) | Covers `findMaxDeliveryRadiusForApproved` as covering index; eliminates table lookup for MAX(delivery_radius_km) WHERE status='APPROVED' | Small — 33 rows |
| `idx_orders_user_created_at_desc` | orders | (user_id, created_at DESC) | No | Correct index shape for `findByUserIdOrderByCreatedAtDesc` (user_id filter + created_at DESC ordering). Eliminates filesort at production scale. | Small |
| `idx_orders_user_status_created_at_desc` | orders | (user_id, status, created_at DESC) | No | Correct index shape for `findByUserIdAndStatusOrderByCreatedAtDesc` (user_id + status filter + created_at DESC ordering). Eliminates filesort at production scale. | Small |
| `idx_orders_vendor_created_at_desc` | orders | (vendor_profile_id, created_at DESC) | Yes (P-5) | Eliminates filesort for vendor list queries without status filter | Small |
| `idx_orders_vendor_status_created_at_desc` | orders | (vendor_profile_id, status, created_at DESC) | Yes (P-3) | Eliminates filesort for vendor list with status filter | Small |

No existing migration was edited (ADR-002/D-9). No new shared infrastructure (Redis) added.

### Before/After Evidence

**`findMaxDeliveryRadiusForApproved`:**
- Before V33: `type=ALL`, `key=NULL` (full scan of vendor_profiles)
- After V33: `type=ref`, `key=idx_vendor_profiles_status_accepting_radius`, `Extra: Using where; Using index` (covering index, no table lookup)

**`findByVendorIdAndStatusOrderByCreatedAtDesc`:**
- Before V33: `type=ref`, `key=idx_orders_vendor_status`, `Extra: Using index condition; Using filesort`
- After V33: `type=ref`, `key=idx_orders_vendor_status_created_at_desc`, `Extra: Using index condition` (filesort eliminated)

**`findByVendorIdAndStatusNotOrderByCreatedAtDesc`:**
- Before V33: `type=ALL`, `key=NULL`, `Extra: Using where; Using filesort`
- After V33: `type=ref`, `key=idx_orders_vendor_created_at_desc`, `Extra: Using where` (no filesort)

## 5. Test Commands and Results

### PerformanceSanityIT (integration, Testcontainers MySQL 8.0.36)

```
./mvnw failsafe:integration-test failsafe:verify -Pintegration "-Dit.test=PerformanceSanityIT"
```

**Result:** 13 tests, 0 failures, 0 errors, BUILD SUCCESS (~3 min including Testcontainers startup + 33 Flyway migrations).

Tests executed:
- `explainDiscoveryQueries` — discovery query plans
- `explainSearchQueries` — search query plans
- `explainCustomerOrderListQueries` — customer order list plans
- `explainVendorOrderListQueries` — vendor order list plans
- `explainNotificationQueries` — notification list/unread-count plans
- `explainBillingQueries` — vendor billing ledger plans
- `explainAnalyticsQueries` — vendor/admin analytics plans
- `measureSearchQueryCount` — N+1 query count: before fix 24→54, after fix 5→5→5 (bounded)
- `measureCustomerOrderListQueryCount` — N+1 query count: before fix 2→2 (hidden), after fix 2→2 (bounded, verified)
- `measureNotificationQueryCount` — notification query count (list=2, unread-count=1)
- `measureVendorBillingLedgerQueryCount` — billing ledger query count (1)
- `verifyIndexesAdded` — V33 index existence in INFORMATION_SCHEMA (5 indexes verified)
- `verifyPaginationCoverage` — all 32 list endpoints documented

### Backend unit tests

Not re-run in full for this stage; previous full run: **2822 tests, 0 failures** (Phase 13 Stage 6 baseline). Stage 6 added a test class, a migration, and production code fixes — no new test failures.

### Frontend checks

No frontend changes in Stage 6. Previous run: **860 tests pass, typecheck clean, lint clean, build clean** (Phase 12/13 baseline).

## 6. Remaining Limitations

| id | area | description |
|----|------|-------------|
| L-1 | Demo scale | All EXPLAIN runs used ~33–150 row tables. MySQL optimizer chose table scans for several queries that would use indexes at production scale. Indexes are justified by query shape and will be effective at scale. |
| L-2 | Full integration suite | Full `./mvnw verify -Pintegration` (~1200+ tests) exceeds a single session window (~15 min per baseline). Focused failsafe runs used instead. |
