# API Summary

Method, path, access rule, and purpose grouped by module. Access rules reflect
`SecurityConfig` (URL layer) plus per-handler method security
(`@RequiresApprovedVendor`, ownership checks). Cross-checked against
`docs/rbac-matrix.md` and the `EndpointAccessMatrix` test
(`backend/src/test/java/com/flowerconnect/security/EndpointAccessMatrix.java`).
Swagger/OpenAPI (`springdoc`) is available in `dev` and disabled in `prod`/`staging`.

## Authentication (`/api/v1/auth`)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/api/v1/auth/register` | Public | Create a CUSTOMER account; returns access token + HttpOnly refresh cookie |
| POST | `/api/v1/auth/login` | Public, rate-limited | Validate credentials; issue access token + HttpOnly refresh cookie |
| POST | `/api/v1/auth/refresh` | Authenticated (cookie + `X-FlowerConnect-Client` + exact `Origin`) | Rotate access + refresh token |
| POST | `/api/v1/auth/logout` | Authenticated (cookie + headers) | Revoke refresh token + clear cookie |
| POST | `/api/v1/auth/forgot-password` | Public, rate-limited | Request password-reset token (generic response, no user enumeration) |
| POST | `/api/v1/auth/reset-password` | Public, rate-limited | Reset password with single-use token; revoke all refresh tokens |

## Current user (`/api/v1/users`)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/users/me` | Authenticated | Caller's own profile |
| PATCH | `/api/v1/users/me` | Authenticated | Update full name / phone |
| POST | `/api/v1/users/me/password` | Authenticated | Change password (verify current); revoke refresh tokens |

## Service locations (`/api/v1/locations`)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/locations` | Public | Hierarchical list or paginated search (pincode/area) |

## Vendor registration & profile (`/api/v1/vendors`)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/api/v1/vendors/register` | Public | Create FLORIST account + PENDING_APPROVAL profile |
| GET | `/api/v1/vendors/profile` | FLORIST | Caller's own vendor profile |
| PUT | `/api/v1/vendors/profile` | FLORIST | Full-replacement update of own profile |

## Vendor approval (`/api/v1/admin/vendors`)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/admin/vendors` | ADMIN | List/filter vendors by status |
| POST | `/api/v1/admin/vendors/{id}/approve` | ADMIN | Approve pending vendor (+ audit row) |
| POST | `/api/v1/admin/vendors/{id}/reject` | ADMIN, reason required | Reject pending vendor (+ audit row) |
| POST | `/api/v1/admin/vendors/{id}/suspend` | ADMIN, reason required | Suspend approved vendor (+ audit row) |
| POST | `/api/v1/admin/vendors/{id}/reinstate` | ADMIN | Reinstate suspended vendor (+ audit row) |

## Admin users (`/api/v1/admin/users`)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/admin/users` | ADMIN | List/filter users by role & status |
| PATCH | `/api/v1/admin/users/{id}/status` | ADMIN, reason required, no self-target | Change user status; revoke tokens (+ audit row) |

## Discovery / search / storefront (public)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/discover` | Public, `locationId` required | Approved, accepting vendors within radius |
| GET | `/api/v1/search` | Public, `locationId` required | Product search with filters & pagination |
| GET | `/api/v1/products/{id}` | Public, `locationId` required | Public product detail (non-disclosing 404 if ineligible) |
| GET | `/api/v1/vendors/{id}/storefront` | Public | Vendor profile, hours, paginated products |
| GET | `/api/v1/categories` | Public | Active top-level categories |

## Categories (admin)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/admin/categories` | ADMIN | All categories, paginated |
| POST | `/api/v1/admin/categories` | ADMIN | Create (slug auto-generated) |
| PUT | `/api/v1/admin/categories/{id}` | ADMIN | Update (slug regenerates on rename) |
| DELETE | `/api/v1/admin/categories/{id}` | ADMIN | Hard delete (409 if has children) |

## Vendor catalog (`/api/v1/vendors/products`) — approval-gated

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/api/v1/vendors/products` | FLORIST + approved | Create product |
| GET | `/api/v1/vendors/products` | FLORIST + approved | Paginated own products (filters) |
| GET | `/api/v1/vendors/products/{id}` | FLORIST + approved, ownership | Read one product |
| PUT | `/api/v1/vendors/products/{id}` | FLORIST + approved, ownership | Update product |
| PATCH | `/api/v1/vendors/products/{id}/deactivate` | FLORIST + approved, ownership | Soft-delete (INACTIVE) |

## Vendor images (`/api/v1/vendors/products/{productId}/images`) — approval-gated

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `.../images` | FLORIST + approved, ownership | Upload image (content-sniffed, decoded/scaled/re-encoded) |
| GET | `.../images` | FLORIST + approved, ownership | List image metadata (no byte route — see known issue 020) |
| PUT | `.../images/{imageId}/primary` | FLORIST + approved, ownership | Set cover image |
| PUT | `.../images/order` | FLORIST + approved, ownership | Reorder (exact permutation) |
| DELETE | `.../images/{imageId}` | FLORIST + approved, ownership | Delete image (promote next if cover) |

## Vendor inventory (`/api/v1/vendors/.../inventory`) — approval-gated

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/vendors/products/{id}/inventory` | FLORIST + approved, ownership | Read inventory |
| POST | `.../stock-in` | FLORIST + approved, ownership | Add stock |
| POST | `.../stock-out` | FLORIST + approved, ownership | Remove stock |
| POST | `.../inventory/adjustments` | FLORIST + approved, ownership, reason required | Signed correction |
| POST | `.../inventory/write-offs` | FLORIST + approved, ownership, reason required | Record waste |
| PUT | `.../low-stock-threshold` | FLORIST + approved, ownership | Set threshold |
| PUT | `.../expiry-date` | FLORIST + approved, ownership | Set/clear expiry |
| GET | `.../movements` | FLORIST + approved, ownership | Paged movement history |
| GET | `/api/v1/vendors/inventory/low-stock` | FLORIST + approved | Vendor-wide low-stock list |

## Slots (`/api/v1/vendors/{id}/slots`)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/vendors/{vendorId}/slots` | CUSTOMER | Delivery slot discovery (timezone-aware) |

## Cart (`/api/v1/cart`) — CUSTOMER

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/cart` | CUSTOMER | View cart (vendor-grouped) |
| POST | `/api/v1/cart/items` | CUSTOMER | Add/increment item |
| PATCH | `/api/v1/cart/items/{id}` | CUSTOMER, ownership | Update quantity |
| DELETE | `/api/v1/cart/items/{id}` | CUSTOMER, ownership | Remove item |
| DELETE | `/api/v1/cart` | CUSTOMER | Clear cart |

## Checkout (`/api/v1/checkout`) — CUSTOMER

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/api/v1/checkout/quote` | CUSTOMER | Read-only quote (price drift detection) |
| POST | `/api/v1/checkout` | CUSTOMER, idempotent | Place order (COD / ONLINE); reserve stock |

## Customer orders (`/api/v1/orders`) — CUSTOMER

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/orders` | CUSTOMER | Paginated own orders (status filter) |
| GET | `/api/v1/orders/{id}` | CUSTOMER, ownership | Full order detail + timeline |
| POST | `/api/v1/orders/{orderId}/cancel` | CUSTOMER / FLORIST / ADMIN, ownership | Cancel order (reason rules per role/state) |
| POST | `/api/v1/orders/{orderId}/reviews` | CUSTOMER | Raise review for DELIVERED order |

## Vendor orders (`/api/v1/vendor/orders`) — FLORIST (no approval gate — D-6)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/api/v1/vendor/orders/{orderId}/accept` | FLORIST, ownership | Accept PLACED order |
| POST | `/api/v1/vendor/orders/{orderId}/reject` | FLORIST, ownership, reason | Reject order |
| POST | `/api/v1/vendor/orders/{orderId}/prepare` | FLORIST, ownership | Mark preparing |
| POST | `/api/v1/vendor/orders/{orderId}/out-for-delivery` | FLORIST, ownership | Mark out for delivery |
| POST | `/api/v1/vendor/orders/{orderId}/delivered` | FLORIST, ownership | Deliver + collect COD |
| GET | `/api/v1/vendor/orders` | FLORIST | Paginated own orders (filters) |
| GET | `/api/v1/vendor/orders/summary` | FLORIST | Today's summary (vendor timezone) |

## Payments (`/api/v1/payments`) — CUSTOMER

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/payments/{orderId}` | CUSTOMER, ownership | Payment state |
| GET | `/api/v1/payments/group/{paymentGroupId}` | CUSTOMER, ownership | Group payment state |
| POST | `/api/v1/payments/{orderId}/resume` | CUSTOMER, ownership | Resume pending (refresh only) |
| POST | `/api/v1/payments/webhook` | Signature (HMAC), no JWT | Provider callback (permitAll, before CUSTOMER rule) |
| POST | `/api/v1/payments/simulator/{orderId}/pay\|fail` | CUSTOMER, ownership, `!prod` | Simulated pay/fail (test mode) |

## Admin refunds (`/api/v1/admin/refunds`) — ADMIN

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/admin/refunds` | ADMIN | Paginated REFUND_PENDING queue (filters) |
| POST | `/api/v1/admin/refunds/{orderId}/mark` | ADMIN, reference note required | Mark refund complete (+ audit row) |

## Disputes

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/api/v1/disputes/orders/{orderId}` | CUSTOMER | Raise dispute (window, reason code) |
| GET | `/api/v1/disputes` | CUSTOMER | Caller's own disputes |
| GET | `/api/v1/disputes/{id}` | CUSTOMER, ownership | Dispute detail |
| GET | `/api/v1/vendor/disputes` | FLORIST | Disputes on vendor's orders |
| GET | `/api/v1/vendor/disputes/{id}` | FLORIST, ownership | Dispute detail |
| POST | `/api/v1/vendor/disputes/{id}/respond` | FLORIST, ownership, response required | Respond (OPEN → UNDER_REVIEW) |
| GET | `/api/v1/admin/disputes` | ADMIN | Dispute queue (filters) |
| GET | `/api/v1/admin/disputes/{id}` | ADMIN | Full dispute record |
| POST | `/api/v1/admin/disputes/{id}/resolve` | ADMIN | Resolve (REFUND/PARTIAL/REJECTED; amount rules) |

## Reviews

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/vendors/{vendorId}/reviews` | Public | Visible (non-hidden) reviews |
| POST | `/api/v1/orders/{orderId}/reviews` | CUSTOMER | Submit review |
| GET | `/api/v1/reviews/{id}` | CUSTOMER, ownership | Caller's own review (incl. hidden) |
| PATCH | `/api/v1/vendors/reviews/{id}/reply` | FLORIST, ownership | Vendor reply |
| GET | `/api/v1/vendors/reviews` | FLORIST | Vendor's own reviews (incl. hidden) |
| GET | `/api/v1/vendors/reviews/summary` | FLORIST | Aggregate rating + distribution |
| PATCH | `/api/v1/admin/reviews/{id}/hide` | ADMIN, reason required | Hide review (+ audit row) |
| PATCH | `/api/v1/admin/reviews/{id}/unhide` | ADMIN | Unhide review (+ audit row) |
| GET | `/api/v1/admin/reviews` | ADMIN | Paginated admin listing (hidden filter) |

## Vendor analytics (`/api/v1/vendor/analytics`) — FLORIST

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/vendor/analytics/overview` | FLORIST | Marketplace + POS overview (date range) |
| GET | `/api/v1/vendor/analytics/daily-trends` | FLORIST | Daily time series (vendor-local timezone) |

## POS (`/api/v1/pos`) — FLORIST + approved

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/api/v1/pos/sales` | FLORIST + approved, idempotency key | Record commission-free walk-in sale |
| GET | `/api/v1/pos/sales/{id}` | FLORIST, ownership | Retrieve sale receipt |
| GET | `/api/v1/pos/summary` | FLORIST + approved | Today's summary |
| GET | `/api/v1/pos/reports` | FLORIST + approved | Daily/weekly/range report |

## Billing / settlements

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/vendor/billing/rate` | FLORIST | Vendor's effective commission rate |
| GET | `/api/v1/vendor/billing/ledger` | FLORIST | Vendor's ledger entries |
| GET | `/api/v1/vendor/billing/outstanding` | FLORIST | Vendor's outstanding balance |
| GET | `/api/v1/vendor/billing/settlements` | FLORIST | Vendor's settlements |
| GET | `/api/v1/vendor/billing/settlements/{id}` | FLORIST, ownership | Settlement detail |
| GET | `/api/v1/vendor/billing/statement` | FLORIST | CSV statement download |
| GET | `/api/v1/admin/platform-settings/commission-rate` | ADMIN | Platform default rate |
| PATCH | `/api/v1/admin/platform-settings/commission-rate` | ADMIN | Update default rate (+ audit row) |
| GET | `/api/v1/admin/vendors/{id}/commission-rate` | ADMIN | Vendor override rate |
| PATCH | `/api/v1/admin/vendors/{id}/commission-rate` | ADMIN | Set/clear vendor override (+ audit row) |
| GET | `/api/v1/admin/billing/ledger` | ADMIN | All-vendor ledger |
| GET | `/api/v1/admin/billing/outstanding/{vendorId}` | ADMIN | Vendor outstanding balance |
| GET | `/api/v1/admin/billing/overdue` | ADMIN | Overdue vendors (advisory) |
| GET | `/api/v1/admin/billing/statement` | ADMIN | Vendor CSV statement download |
| POST | `/api/v1/admin/billing/settlements/run` | ADMIN | Run settlement cycle (vendor or all) |
| GET | `/api/v1/admin/billing/settlements` | ADMIN | Paginated settlements |
| GET | `/api/v1/admin/billing/settlements/{id}` | ADMIN | Settlement detail |
| PATCH | `/api/v1/admin/billing/settlements/{id}/finalize` | ADMIN, reference note required | Finalize PENDING → SETTLED (+ audit row) |

## Notifications (`/api/v1/notifications`) — any authenticated role

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/api/v1/notifications` | CUSTOMER\|FLORIST\|ADMIN | Caller's own notifications (paginated) |
| GET | `/api/v1/notifications/unread-count` | CUSTOMER\|FLORIST\|ADMIN | Unread count |
| PATCH | `/api/v1/notifications/{id}/read` | CUSTOMER\|FLORIST\|ADMIN, ownership | Mark one read |
| POST | `/api/v1/notifications/read-all` | CUSTOMER\|FLORIST\|ADMIN | Mark all read |
| GET | `/api/v1/notifications/stream` | CUSTOMER\|FLORIST\|ADMIN | SSE stream (best-effort, post-commit) |

## Infrastructure

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/actuator/health` | Public | Health (liveness/readiness groups) |

## Notes

- Approval gating (`@RequiresApprovedVendor`) is per-handler on catalog/inventory/order-creation routes; order reads and the vendor's own profile remain reachable while not approved (D-6, D-13).
- A suspended vendor is hidden from discovery and blocked from new catalog/inventory/order work but may still process in-flight orders (D-6).
- The payment simulator and `/api/v1/pos/**` report endpoints are present in dev but return 404 in prod (`!prod` profile / `SimulatorProdAbsenceIntegrationTest`).
- Image bytes are not served by any route (known issue 020); images are listed as metadata only.