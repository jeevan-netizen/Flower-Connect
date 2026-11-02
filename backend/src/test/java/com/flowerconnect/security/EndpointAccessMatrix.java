package com.flowerconnect.security;

import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * Single reviewed source of truth for endpoint access control (plan task 13.6).
 *
 * <p>One row per logical handler group. The {@code pathPattern} is an Ant-style
 * path pattern written with {@code {}} placeholders (matching the handler's
 * {@code {varName}} after normalization) so that it ant-matches the handler
 * paths registered in {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}.
 * The {@code rule} is the URL-layer access decision; method-level approval
 * gating ({@code @RequiresApprovedVendor}) is noted in {@code note} and enforced
 * separately by method security.
 *
 * <p>{@code testPath} is a concrete example URL used for live MockMvc testing
 * against the real filter chain; it is not used by the structural sweep.
 *
 * <p>Role names use the application's actual role constant {@code FLORIST}
 * (not the plan's {@code VENDOR}); see decision D-11.
 *
 * <p>Maintenance: when adding a controller or route, add a matching row here
 * and re-run {@link EndpointAccessSweepIT}. The test fails if any registered
 * handler is missing a row, if any row matches no registered handler, if rows
 * overlap ambiguously, or if a row has no explicit access rule. See
 * {@code docs/decisions.md} for the procedure.
 */
public final class EndpointAccessMatrix {

    private EndpointAccessMatrix() {
    }

    /**
     * The access decision applied at the URL layer.
     */
    public enum AccessRule {
        /** permitAll: no authentication required. */
        PUBLIC,
        /** hasRole("CUSTOMER"). */
        CUSTOMER,
        /** hasRole("FLORIST"). */
        FLORIST,
        /** hasRole("ADMIN"). */
        ADMIN,
        /** hasAnyRole("CUSTOMER", "FLORIST", "ADMIN"). */
        ANY_AUTH,
        /** hasAnyRole over a specific non-uniform set (see note). */
        MULTI_ROLE,
        /** permitAll but authenticated by HMAC signature (no JWT). */
        SIGNATURE,
        /** Registered only outside the prod profile. */
        SIMULATOR_NONPROD,
    }

    /**
     * One matrix row.
     *
     * @param method          HTTP method, or {@code null} to match any method.
     * @param pathPattern     Ant-style path pattern as in SecurityConfig, with
     *                        {@code {}} placeholders matching handler variables.
     * @param rule            URL-layer access rule.
     * @param anonymousStatus expected status for an unauthenticated call, or {@code -1}.
     * @param customerStatus  expected status for a CUSTOMER caller.
     * @param floristStatus   expected status for a FLORIST caller (unapproved, per the sweep's generic user).
     * @param adminStatus     expected status for an ADMIN caller.
     * @param note            special-case explanation.
     * @param testPath        concrete URL used for live MockMvc testing.
     */
    public record Entry(
            HttpMethod method,
            String pathPattern,
            AccessRule rule,
            int anonymousStatus,
            int customerStatus,
            int floristStatus,
            int adminStatus,
            String note,
            String testPath
    ) {
    }

    /** The complete, reviewed access matrix. */
    public static final List<Entry> ENTRIES = List.of(
            // ============================================================ PUBLIC (auth flows)
            new Entry(HttpMethod.POST, "/api/v1/auth/register", AccessRule.PUBLIC, 400, 400, 400, 400,
                    "Vendor/customer registration; body validation runs before any auth decision.", "/api/v1/auth/register"),
            new Entry(HttpMethod.POST, "/api/v1/auth/login", AccessRule.PUBLIC, 400, 400, 400, 400,
                    "Login issues access token + httpOnly refresh cookie.", "/api/v1/auth/login"),
            new Entry(HttpMethod.POST, "/api/v1/auth/refresh", AccessRule.PUBLIC, 400, 400, 400, 400,
                    "Refresh consumes the httpOnly cookie; body-token variant also accepted.", "/api/v1/auth/refresh"),
            new Entry(HttpMethod.POST, "/api/v1/auth/logout", AccessRule.PUBLIC, 400, 400, 400, 400,
                    "Logout revokes the refresh token; clears the cookie.", "/api/v1/auth/logout"),
            new Entry(HttpMethod.POST, "/api/v1/auth/forgot-password", AccessRule.PUBLIC, 400, 400, 400, 400,
                    "No user enumeration: identical response for registered/unknown email.", "/api/v1/auth/forgot-password"),
            new Entry(HttpMethod.POST, "/api/v1/auth/reset-password", AccessRule.PUBLIC, 400, 400, 400, 400,
                    "Single-use reset token; generic failure response.", "/api/v1/auth/reset-password"),

            // ============================================================ PUBLIC (reference data & discovery)
            new Entry(HttpMethod.GET, "/api/v1/locations", AccessRule.PUBLIC, 200, 200, 200, 200,
                    "Service-area reference data; public permitAll.", "/api/v1/locations"),
            new Entry(HttpMethod.GET, "/api/v1/categories", AccessRule.PUBLIC, 200, 200, 200, 200,
                    "Active top-level categories only (D-17).", "/api/v1/categories"),
            new Entry(HttpMethod.GET, "/api/v1/discover", AccessRule.PUBLIC, 400, 400, 400, 400,
                    "Geo discovery; locationId required (400 if missing/unknown).", "/api/v1/discover?locationId=1"),
            new Entry(HttpMethod.GET, "/api/v1/search", AccessRule.PUBLIC, 400, 400, 400, 400,
                    "Product search; GET only, locationId required.", "/api/v1/search?locationId=1"),
            new Entry(HttpMethod.GET, "/api/v1/products/{}", AccessRule.PUBLIC, 404, 404, 404, 404,
                    "Public product detail; locationId required, ineligible -> non-disclosing 404.", "/api/v1/products/1?locationId=1"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/{}/storefront", AccessRule.PUBLIC, 404, 404, 404, 404,
                    "Public storefront; unknown/unapproved/non-accepting vendor -> non-disclosing 404 (D-6).",
                    "/api/v1/vendors/1/storefront"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/{}/reviews", AccessRule.PUBLIC, 404, 404, 404, 404,
                    "Public visible reviews for an approved vendor (Phase 12).", "/api/v1/vendors/1/reviews"),
            new Entry(HttpMethod.POST, "/api/v1/vendors/register", AccessRule.PUBLIC, 400, 400, 400, 400,
                    "Public vendor registration entry point; creates FLORIST account + PENDING_APPROVAL profile.",
                    "/api/v1/vendors/register"),

            // ============================================================ PUBLIC (signature-auth webhook)
            new Entry(HttpMethod.POST, "/api/v1/payments/webhook", AccessRule.SIGNATURE, 401, 401, 401, 401,
                    "No JWT; authenticated by HMAC-SHA256 signature + timestamp (no secret in response).",
                    "/api/v1/payments/webhook"),

            // ============================================================ CUSTOMER
            new Entry(HttpMethod.GET, "/api/v1/addresses", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Caller's own addresses only; florist/admin -> 403 (D-15).", "/api/v1/addresses"),
            new Entry(HttpMethod.POST, "/api/v1/addresses", AccessRule.CUSTOMER, 401, 201, 403, 403,
                    "Create address; full replacement PUT semantics.", "/api/v1/addresses"),
            new Entry(HttpMethod.GET, "/api/v1/addresses/{}", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Read one address; foreign id -> 404.", "/api/v1/addresses/1"),
            new Entry(HttpMethod.PUT, "/api/v1/addresses/{}", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Full replacement of one address.", "/api/v1/addresses/1"),
            new Entry(HttpMethod.DELETE, "/api/v1/addresses/{}", AccessRule.CUSTOMER, 401, 204, 403, 403,
                    "Delete one address; promotes oldest remaining as default.", "/api/v1/addresses/1"),
            new Entry(HttpMethod.GET, "/api/v1/users/me", AccessRule.ANY_AUTH, 401, 200, 200, 200,
                    "Current user profile; resolved from JWT subject (self-service, any authenticated role).", "/api/v1/users/me"),
            new Entry(HttpMethod.PATCH, "/api/v1/users/me", AccessRule.ANY_AUTH, 401, 200, 200, 200,
                    "Update current user profile (fullName/phone only); self-service.", "/api/v1/users/me"),
            new Entry(HttpMethod.POST, "/api/v1/users/me/password", AccessRule.ANY_AUTH, 401, 204, 204, 204,
                    "Change current password; self-service.", "/api/v1/users/me/password"),
            new Entry(HttpMethod.GET, "/api/v1/cart", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Customer cart; fetched only for an authenticated customer (D-41).", "/api/v1/cart"),
            new Entry(HttpMethod.POST, "/api/v1/cart/items", AccessRule.CUSTOMER, 401, 201, 403, 403,
                    "Add cart item.", "/api/v1/cart/items"),
            new Entry(HttpMethod.PATCH, "/api/v1/cart/items/{}", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Update cart item quantity.", "/api/v1/cart/items/1"),
            new Entry(HttpMethod.DELETE, "/api/v1/cart/items/{}", AccessRule.CUSTOMER, 401, 204, 403, 403,
                    "Remove one cart item.", "/api/v1/cart/items/1"),
            new Entry(HttpMethod.DELETE, "/api/v1/cart", AccessRule.CUSTOMER, 401, 204, 403, 403,
                    "Clear the entire cart.", "/api/v1/cart"),
            new Entry(HttpMethod.POST, "/api/v1/checkout/quote", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Read-only checkout quote (price drift detection).", "/api/v1/checkout/quote"),
            new Entry(HttpMethod.POST, "/api/v1/checkout", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Transactional checkout (COD, idempotent).", "/api/v1/checkout"),
            new Entry(HttpMethod.GET, "/api/v1/orders", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Customer order list; ownership per handler.", "/api/v1/orders"),
            new Entry(HttpMethod.GET, "/api/v1/orders/{}", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Order detail; missing -> 404, foreign -> 403.", "/api/v1/orders/1"),
            new Entry(HttpMethod.GET, "/api/v1/reviews/{}", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Read one of the customer's own reviews.", "/api/v1/reviews/1"),
            new Entry(HttpMethod.POST, "/api/v1/orders/{}/reviews", AccessRule.CUSTOMER, 401, 201, 403, 403,
                    "Submit review for a delivered order (absolute path on ReviewController).",
                    "/api/v1/orders/1/reviews"),
            new Entry(HttpMethod.GET, "/api/v1/disputes", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Customer raises/views own disputes (Phase 8).", "/api/v1/disputes"),
            new Entry(HttpMethod.GET, "/api/v1/disputes/{}", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Read one of the customer's own disputes.", "/api/v1/disputes/1"),
            new Entry(HttpMethod.POST, "/api/v1/disputes/orders/{}", AccessRule.CUSTOMER, 401, 201, 403, 403,
                    "Raise dispute for a delivered order (absolute path on CustomerDisputeController).",
                    "/api/v1/disputes/orders/1"),
            new Entry(HttpMethod.GET, "/api/v1/payments/{}", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Customer payment state for an order (Phase 7).", "/api/v1/payments/1"),
            new Entry(HttpMethod.GET, "/api/v1/payments/group/{}", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Customer payment group state (Phase 7).", "/api/v1/payments/group/1"),
            new Entry(HttpMethod.POST, "/api/v1/payments/{}/resume", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Resume a pending payment group (refresh only, not completion).", "/api/v1/payments/1/resume"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/{}/slots", AccessRule.CUSTOMER, 401, 200, 403, 403,
                    "Slot discovery; address ownership + vendor approval checked in handler.", "/api/v1/vendors/1/slots?addressId=1"),

            // ============================================================ FLORIST (vendor namespace)
            new Entry(HttpMethod.GET, "/api/v1/vendors/profile", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Own profile; reachable while pending/suspended (D-13).", "/api/v1/vendors/profile"),
            new Entry(HttpMethod.PUT, "/api/v1/vendors/profile", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Full-replacement profile update.", "/api/v1/vendors/profile"),
            new Entry(HttpMethod.POST, "/api/v1/vendors/products", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Create product; approval-gated (@RequiresApprovedVendor).", "/api/v1/vendors/products"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/products", AccessRule.FLORIST, 401, 403, 403, 403,
                    "List own products; approval-gated.", "/api/v1/vendors/products"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/products/{}", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Read one own product; approval-gated.", "/api/v1/vendors/products/1"),
            new Entry(HttpMethod.PUT, "/api/v1/vendors/products/{}", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Update one own product; approval-gated.", "/api/v1/vendors/products/1"),
            new Entry(HttpMethod.PATCH, "/api/v1/vendors/products/{}/deactivate", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Soft-deactivate one own product; approval-gated.", "/api/v1/vendors/products/1/deactivate"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/products/{}/inventory", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Read one product's inventory; approval-gated (D-24).", "/api/v1/vendors/products/1/inventory"),
            new Entry(HttpMethod.POST, "/api/v1/vendors/products/{}/inventory/stock-in", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Stock in; approval-gated.", "/api/v1/vendors/products/1/inventory/stock-in"),
            new Entry(HttpMethod.POST, "/api/v1/vendors/products/{}/inventory/stock-out", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Stock out; approval-gated.", "/api/v1/vendors/products/1/inventory/stock-out"),
            new Entry(HttpMethod.POST, "/api/v1/vendors/products/{}/inventory/adjustments", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Signed adjustment; approval-gated.", "/api/v1/vendors/products/1/inventory/adjustments"),
            new Entry(HttpMethod.POST, "/api/v1/vendors/products/{}/inventory/write-offs", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Write-off; approval-gated.", "/api/v1/vendors/products/1/inventory/write-offs"),
            new Entry(HttpMethod.PUT, "/api/v1/vendors/products/{}/inventory/low-stock-threshold", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Set low-stock threshold; approval-gated.", "/api/v1/vendors/products/1/inventory/low-stock-threshold"),
            new Entry(HttpMethod.PUT, "/api/v1/vendors/products/{}/inventory/expiry-date", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Set/clear expiry date; approval-gated.", "/api/v1/vendors/products/1/inventory/expiry-date"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/products/{}/inventory/movements", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Movement history; approval-gated.", "/api/v1/vendors/products/1/inventory/movements"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/inventory/low-stock", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Shop-wide low-stock list; approval-gated.", "/api/v1/vendors/inventory/low-stock"),
            new Entry(HttpMethod.POST, "/api/v1/vendors/products/{}/images", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Upload image; approval-gated (D-28).", "/api/v1/vendors/products/1/images"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/products/{}/images", AccessRule.FLORIST, 401, 403, 403, 403,
                    "List images; approval-gated.", "/api/v1/vendors/products/1/images"),
            new Entry(HttpMethod.PUT, "/api/v1/vendors/products/{}/images/{}/primary", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Set cover image; approval-gated.", "/api/v1/vendors/products/1/images/1/primary"),
            new Entry(HttpMethod.PUT, "/api/v1/vendors/products/{}/images/order", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Reorder images (exact permutation); approval-gated.", "/api/v1/vendors/products/1/images/order"),
            new Entry(HttpMethod.DELETE, "/api/v1/vendors/products/{}/images/{}", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Delete image; promotes next when cover removed; approval-gated.", "/api/v1/vendors/products/1/images/1"),
            new Entry(HttpMethod.POST, "/api/v1/pos/sales", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Record a POS sale; approval-gated, commission-free (D-66).", "/api/v1/pos/sales"),
            new Entry(HttpMethod.GET, "/api/v1/pos/sales/{}", AccessRule.FLORIST, 401, 403, 403, 403,
                    "Read one POS sale; approval-gated.", "/api/v1/pos/sales/1"),
            new Entry(HttpMethod.GET, "/api/v1/pos/summary", AccessRule.FLORIST, 401, 403, 403, 403,
                    "POS summary; approval-gated.", "/api/v1/pos/summary"),
            new Entry(HttpMethod.GET, "/api/v1/pos/reports", AccessRule.FLORIST, 401, 403, 403, 403,
                    "POS reports; approval-gated.", "/api/v1/pos/reports"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/analytics/overview", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor analytics; suspended vendor may read history (D-6).", "/api/v1/vendor/analytics/overview"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/analytics/daily-trends", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor daily-trends analytics (D-6).", "/api/v1/vendor/analytics/daily-trends"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/orders", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor order list; deliberately NO approval gate so suspended vendors process in-flight orders (D-6/D-43).",
                    "/api/v1/vendor/orders"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/orders/summary", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor today's summary (D-6).", "/api/v1/vendor/orders/summary"),
            new Entry(HttpMethod.POST, "/api/v1/vendor/orders/{}/accept", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Accept order; commits reserved stock (needs valid order/state).", "/api/v1/vendor/orders/1/accept"),
            new Entry(HttpMethod.POST, "/api/v1/vendor/orders/{}/reject", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Reject order; reason required.", "/api/v1/vendor/orders/1/reject"),
            new Entry(HttpMethod.POST, "/api/v1/vendor/orders/{}/prepare", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Mark order preparing.", "/api/v1/vendor/orders/1/prepare"),
            new Entry(HttpMethod.POST, "/api/v1/vendor/orders/{}/out-for-delivery", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Mark order out for delivery.", "/api/v1/vendor/orders/1/out-for-delivery"),
            new Entry(HttpMethod.POST, "/api/v1/vendor/orders/{}/delivered", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Mark order delivered; collects COD payment.", "/api/v1/vendor/orders/1/delivered"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/disputes", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor views disputes on own orders (Phase 8).", "/api/v1/vendor/disputes"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/disputes/{}", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Read one dispute on own order.", "/api/v1/vendor/disputes/1"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/reviews", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor's own reviews (including hidden), newest first (Phase 12).", "/api/v1/vendor/reviews"),
            new Entry(HttpMethod.GET, "/api/v1/vendors/reviews/summary", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor review aggregate rating and distribution (Phase 12).", "/api/v1/vendor/reviews/summary"),
            new Entry(HttpMethod.POST, "/api/v1/vendor/disputes/{}/respond", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor responds to a dispute (once).", "/api/v1/vendor/disputes/1/respond"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/billing/rate", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor effective commission rate (Phase 10).", "/api/v1/vendor/billing/rate"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/billing/ledger", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor billing ledger (Phase 10).", "/api/v1/vendor/billing/ledger"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/billing/outstanding", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor outstanding balance (Phase 10).", "/api/v1/vendor/billing/outstanding"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/billing/settlements", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor settlements (Phase 10).", "/api/v1/vendor/billing/settlements"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/billing/settlements/{}", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Read one vendor settlement (Phase 10).", "/api/v1/vendor/billing/settlements/1"),
            new Entry(HttpMethod.GET, "/api/v1/vendor/billing/statement", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor statement CSV download (Phase 10).", "/api/v1/vendor/billing/statement"),
            new Entry(HttpMethod.PATCH, "/api/v1/vendors/reviews/{}/reply", AccessRule.FLORIST, 401, 403, 200, 403,
                    "Vendor reply to a review (absolute path on ReviewController).", "/api/v1/vendors/reviews/1/reply"),

            // ============================================================ MULTI_ROLE (order cancellation)
            new Entry(HttpMethod.POST, "/api/v1/orders/{}/cancel", AccessRule.MULTI_ROLE, 401, 200, 200, 200,
                    "CUSTOMER/VENDOR/ADMIN may cancel; target status + reason rules per section 6.1.",
                    "/api/v1/orders/1/cancel"),

            // ============================================================ ANY_AUTH (notifications)
            new Entry(HttpMethod.GET, "/api/v1/notifications", AccessRule.ANY_AUTH, 401, 200, 200, 200,
                    "In-app notifications; every authenticated role sees own inbox.", "/api/v1/notifications"),
            new Entry(HttpMethod.GET, "/api/v1/notifications/unread-count", AccessRule.ANY_AUTH, 401, 200, 200, 200,
                    "Unread-count badge.", "/api/v1/notifications/unread-count"),
            new Entry(HttpMethod.PATCH, "/api/v1/notifications/{}/read", AccessRule.ANY_AUTH, 401, 200, 200, 200,
                    "Mark one notification read; missing/foreign -> 404.", "/api/v1/notifications/1/read"),
            new Entry(HttpMethod.POST, "/api/v1/notifications/read-all", AccessRule.ANY_AUTH, 401, 200, 200, 200,
                    "Mark all notifications read.", "/api/v1/notifications/read-all"),
            new Entry(HttpMethod.GET, "/api/v1/notifications/stream", AccessRule.ANY_AUTH, 401, 200, 200, 200,
                    "SSE stream; bearer token via Authorization header (fetch, not EventSource).", "/api/v1/notifications/stream"),

            // ============================================================ ADMIN
            new Entry(HttpMethod.GET, "/api/v1/admin/users", AccessRule.ADMIN, 401, 403, 403, 200,
                    "User listing with role/status filters.", "/api/v1/admin/users"),
            new Entry(HttpMethod.PATCH, "/api/v1/admin/users/{}/status", AccessRule.ADMIN, 401, 403, 403, 200,
                    "User status change; self-targeting refused (D-14).", "/api/v1/admin/users/1/status"),
            new Entry(HttpMethod.GET, "/api/v1/admin/vendors", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Vendor listing by status.", "/api/v1/admin/vendors"),
            new Entry(HttpMethod.POST, "/api/v1/admin/vendors/{}/approve", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Approve vendor; writes audit row.", "/api/v1/admin/vendors/1/approve"),
            new Entry(HttpMethod.POST, "/api/v1/admin/vendors/{}/reject", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Reject vendor; reason required.", "/api/v1/admin/vendors/1/reject"),
            new Entry(HttpMethod.POST, "/api/v1/admin/vendors/{}/suspend", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Suspend vendor; reason required.", "/api/v1/admin/vendors/1/suspend"),
            new Entry(HttpMethod.POST, "/api/v1/admin/vendors/{}/reinstate", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Reinstate vendor.", "/api/v1/admin/vendors/1/reinstate"),
            new Entry(HttpMethod.GET, "/api/v1/admin/categories", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Admin category listing (all categories, active and inactive).", "/api/v1/admin/categories"),
            new Entry(HttpMethod.POST, "/api/v1/admin/categories", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Create category.", "/api/v1/admin/categories"),
            new Entry(HttpMethod.GET, "/api/v1/admin/categories/{}", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Read one category.", "/api/v1/admin/categories/1"),
            new Entry(HttpMethod.PUT, "/api/v1/admin/categories/{}", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Update one category.", "/api/v1/admin/categories/1"),
            new Entry(HttpMethod.DELETE, "/api/v1/admin/categories/{}", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Delete one category (refused if has children).", "/api/v1/admin/categories/1"),
            new Entry(HttpMethod.GET, "/api/v1/admin/analytics/overview", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Platform analytics (Phase 12).", "/api/v1/admin/analytics/overview"),
            new Entry(HttpMethod.GET, "/api/v1/admin/analytics/daily-trends", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Platform daily-trends analytics (Phase 12).", "/api/v1/admin/analytics/daily-trends"),
            new Entry(HttpMethod.GET, "/api/v1/admin/refunds", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Refund queue (Phase 8).", "/api/v1/admin/refunds"),
            new Entry(HttpMethod.POST, "/api/v1/admin/refunds/{}/mark", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Mark refund complete; reference note required.", "/api/v1/admin/refunds/1/mark"),
            new Entry(HttpMethod.GET, "/api/v1/admin/disputes", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Admin dispute queue (Phase 8).", "/api/v1/admin/disputes"),
            new Entry(HttpMethod.GET, "/api/v1/admin/reviews", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Admin review listing for moderation (Phase 12).", "/api/v1/admin/reviews"),
            new Entry(HttpMethod.GET, "/api/v1/admin/disputes/{}", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Read one dispute (full record).", "/api/v1/admin/disputes/1"),
            new Entry(HttpMethod.POST, "/api/v1/admin/disputes/{}/resolve", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Resolve dispute; refund amount rules per outcome.", "/api/v1/admin/disputes/1/resolve"),
            new Entry(HttpMethod.PATCH, "/api/v1/admin/reviews/{}/hide", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Admin hides a review; reason required (Phase 12).", "/api/v1/admin/reviews/1/hide"),
            new Entry(HttpMethod.PATCH, "/api/v1/admin/reviews/{}/unhide", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Admin unhides a review (Phase 12).", "/api/v1/admin/reviews/1/unhide"),
            new Entry(HttpMethod.GET, "/api/v1/admin/billing/ledger", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Admin billing ledger (Phase 10).", "/api/v1/admin/billing/ledger"),
            new Entry(HttpMethod.GET, "/api/v1/admin/billing/outstanding/{}", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Admin vendor outstanding balance (Phase 10).", "/api/v1/admin/billing/outstanding/1"),
            new Entry(HttpMethod.GET, "/api/v1/admin/billing/overdue", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Admin overdue dues report (Phase 10).", "/api/v1/admin/billing/overdue"),
            new Entry(HttpMethod.GET, "/api/v1/admin/billing/statement", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Admin statement CSV download (Phase 10).", "/api/v1/admin/billing/statement"),
            new Entry(HttpMethod.GET, "/api/v1/admin/platform-settings/commission-rate", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Platform default commission rate (Phase 10).", "/api/v1/admin/platform-settings/commission-rate"),
            new Entry(HttpMethod.PATCH, "/api/v1/admin/platform-settings/commission-rate", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Update platform default commission rate (Phase 10).", "/api/v1/admin/platform-settings/commission-rate"),
            new Entry(HttpMethod.GET, "/api/v1/admin/vendors/{}/commission-rate", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Vendor commission rate override (Phase 10).", "/api/v1/admin/vendors/1/commission-rate"),
            new Entry(HttpMethod.PATCH, "/api/v1/admin/vendors/{}/commission-rate", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Set vendor commission rate override (Phase 10).", "/api/v1/admin/vendors/1/commission-rate"),
            new Entry(HttpMethod.DELETE, "/api/v1/admin/vendors/{}/commission-rate", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Clear vendor commission rate override (Phase 10).", "/api/v1/admin/vendors/1/commission-rate"),
            new Entry(HttpMethod.POST, "/api/v1/admin/billing/settlements/run", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Run settlement cycle(s) (Phase 10).", "/api/v1/admin/billing/settlements/run"),
            new Entry(HttpMethod.GET, "/api/v1/admin/billing/settlements", AccessRule.ADMIN, 401, 403, 403, 200,
                    "List settlements (Phase 10).", "/api/v1/admin/billing/settlements"),
            new Entry(HttpMethod.GET, "/api/v1/admin/billing/settlements/{}", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Read one settlement (Phase 10).", "/api/v1/admin/billing/settlements/1"),
            new Entry(HttpMethod.PATCH, "/api/v1/admin/billing/settlements/{}/finalize", AccessRule.ADMIN, 401, 403, 403, 200,
                    "Finalize a settlement (Phase 10).", "/api/v1/admin/billing/settlements/1/finalize"),

            // ============================================================ ACTUATOR
            new Entry(HttpMethod.GET, "/actuator/health", AccessRule.PUBLIC, 200, 200, 200, 200,
                    "Health check; no auth.", "/actuator/health"),
            new Entry(HttpMethod.GET, "/actuator/info", AccessRule.PUBLIC, 200, 200, 200, 200,
                    "Info endpoint; no auth.", "/actuator/info"),
            new Entry(HttpMethod.POST, "/api/v1/payments/simulator/{}/pay", AccessRule.SIMULATOR_NONPROD, 401, 200, 403, 403,
                    "Simulated pay; customer auth + ownership, !prod profile (test scaffold).",
                    "/api/v1/payments/simulator/1/pay"),
            new Entry(HttpMethod.POST, "/api/v1/payments/simulator/{}/fail", AccessRule.SIMULATOR_NONPROD, 401, 200, 403, 403,
                    "Simulated fail; customer auth + ownership, !prod profile (test scaffold).",
                    "/api/v1/payments/simulator/1/fail"),
            new Entry(HttpMethod.GET, "/swagger-ui.html", AccessRule.PUBLIC, -1, -1, -1, -1,
                    "Springdoc dev docs endpoint (permitAll).", "/swagger-ui.html"),
            new Entry(HttpMethod.GET, "/v3/api-docs", AccessRule.PUBLIC, -1, -1, -1, -1,
                    "OpenAPI docs endpoint (permitAll).", "/v3/api-docs"),
            new Entry(HttpMethod.GET, "/v3/api-docs.yaml", AccessRule.PUBLIC, -1, -1, -1, -1,
                    "OpenAPI docs YAML endpoint (permitAll).", "/v3/api-docs.yaml"),
            new Entry(HttpMethod.GET, "/v3/api-docs/swagger-config", AccessRule.PUBLIC, -1, -1, -1, -1,
                    "OpenAPI swagger-config endpoint (permitAll).", "/v3/api-docs/swagger-config"),

            // ============================================================ CATCH-ALL
            new Entry(null, "/**", AccessRule.ANY_AUTH, 401, 401, 401, 401,
                    "anyRequest().authenticated() catch-all for unmatched routes.", "/api/v1/definitely-not-a-real-endpoint")
    );

    /** Rows whose handler is registered only in the dev profile (SpringDoc). */
    public static final List<String> DEV_ONLY_PATH_PATTERNS = List.of("/swagger-ui/**", "/v3/api-docs/**");
}
