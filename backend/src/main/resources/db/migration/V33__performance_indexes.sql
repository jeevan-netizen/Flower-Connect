-- V33__performance_indexes.sql
-- Phase 14 Stage 6 (task 14.7): performance sanity indexes.
--
-- Justification (measured via EXPLAIN against a seeded demo database in PerformanceSanityIT):
--
-- 1. vendor_profiles(status, accepting_orders, delivery_radius_km)
--    Discovery (GET /api/v1/discover) and search (GET /api/v1/search) run
--    findMaxDeliveryRadiusForApproved (status='APPROVED') and
--    findApprovedAcceptingInBoundingBox (status + accepting_orders + lat/lng range).
--    Existing idx_vendor_profiles_status_geo(status, latitude, longitude) does not
--    cover accepting_orders or delivery_radius_km, producing type=ALL full table scans.
--    New composite index serves the status filter, the accepting_orders filter, and
--    the MAX(delivery_radius_km) aggregation as a covering index.
--
-- 2. orders(user_id, created_at DESC)
--    Customer order list (GET /api/v1/orders) uses findByUserIdOrderByCreatedAtDesc.
--    Existing idx_orders_user(user_id) serves the user filter but not the
--    ORDER BY created_at DESC, producing Using filesort. New index eliminates the
--    filesort.
--
-- 3. orders(user_id, status, created_at DESC)
--    Customer order list with status filter uses findByUserIdAndStatusOrderByCreatedAtDesc.
--    Composite index serves user_id + status filter plus created_at ordering.
--
-- 4. orders(vendor_profile_id, created_at DESC)
--    Vendor order list (GET /api/v1/vendor/orders) uses
--    findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc and
--    findByVendorIdAndStatusNotOrderByCreatedAtDesc. New index serves vendor filter
--    plus created_at ordering without filesort.
--
-- 5. orders(vendor_profile_id, status, created_at DESC)
--    Vendor order list with status filter uses findByVendorIdAndStatusOrderByCreatedAtDesc.
--    Composite index serves vendor_id + status filter plus created_at ordering.
--
-- All indexes are additive; no existing migration or column is modified.
--
-- Measured improvement (EXPLAIN before vs after V33, seeded demo database):
--   findMaxDeliveryRadiusForApproved: type=ALL/key=NULL -> type=ref/key=idx_vendor_profiles_status_accepting_radius
--     Extra: "Using where; Using index" (covering index, no table lookup)
--   findByVendorIdAndStatusOrderByCreatedAtDesc: key=idx_orders_vendor_status + filesort
--     -> key=idx_orders_vendor_status_created_at_desc, no filesort
--   Other order/notification/billing queries still show type=ALL at demo scale
--   (33 vendor_profiles, 56 orders, 150 notifications, 30 settlement entries) because
--   MySQL's optimizer prefers table scans on small tables; these indexes become
--   effective at production scale.

CREATE INDEX idx_vendor_profiles_status_accepting_radius
    ON vendor_profiles (status, accepting_orders, delivery_radius_km);

CREATE INDEX idx_orders_user_created_at_desc
    ON orders (user_id, created_at DESC);

CREATE INDEX idx_orders_user_status_created_at_desc
    ON orders (user_id, status, created_at DESC);

CREATE INDEX idx_orders_vendor_created_at_desc
    ON orders (vendor_profile_id, created_at DESC);

CREATE INDEX idx_orders_vendor_status_created_at_desc
    ON orders (vendor_profile_id, status, created_at DESC);
