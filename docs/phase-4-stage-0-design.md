# Phase 4 Stage 0 — Design Note (Customer Addresses, Discovery and Search)

**Status:** DRAFT — awaiting explicit approval before Stage 1
**Date:** 2026-10-09
**Plan:** `docs/FlowerConnect_Implementation_Plan_v2.2.md` §7 (tasks 4.1–4.T)
**Branch:** `phase-4-discovery-search` (created from `phase-3-catalog-inventory` @ `cef599b`)

This note records what inspection found and the design decisions Stage 1–5 will implement.
Nothing below is implemented yet. Items marked **[APPROVAL]** need an explicit decision.

---

## A. Preflight evidence

| Check | Result |
|---|---|
| Base branch | `phase-3-catalog-inventory` @ `cef599b` ("chore: initialize GSD project tracking"), up to date with `origin/phase-3-catalog-inventory` |
| New branch | `phase-4-discovery-search` created via `git checkout -b`; `git branch --show-current` confirms; Phase 3 branch untouched |
| Working tree | Clean before and after branch creation; nothing committed, nothing pushed |
| Full verification | `& scripts\validate-all.ps1` → `EXIT_CODE=0`; tail: `✅ All validators passed!` — 27 workflows (0 errors, 0 warnings), 12 skills (0 errors), 5 subagents (0 errors), 24 templates (0 errors, 17 cosmetic warnings: missing 'Last updated' markers), 8 scripts (0 errors) |
| Latest Flyway migration | `V11__stock_movements.sql` → proposed Stage 1 migration: **`V12__create_addresses.sql`** |

Note: the repo has **no** `scripts/verify.ps1` (plan §3 / task 0.6 says it should exist).
`scripts/validate-all.ps1` is the repo's full-verification script, but it validates **GSD
artifacts only** (workflows, skills, subagents, templates, scripts) — it does not run the
backend test suite or the frontend build. **[APPROVAL]** Recommend running `./mvnw test`
(unit) and the frontend `npm run test` / `npm run lint` / `npm run typecheck` /
`npm run build` before Stage 1 close; `./mvnw verify -Pintegration` needs the Docker daemon
(Testcontainers — known issue 018).

---

## B. Stage 1 (task 4.1) — Address book

### Migration `V12__create_addresses.sql`

```sql
CREATE TABLE addresses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  label VARCHAR(128) NOT NULL,
  line1 VARCHAR(255) NOT NULL,
  line2 VARCHAR(255),
  service_location_id BIGINT NOT NULL,
  latitude DECIMAL(10,8) NOT NULL,
  longitude DECIMAL(11,8) NOT NULL,
  is_default BIT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_addresses_service_location FOREIGN KEY (service_location_id) REFERENCES service_locations(id),
  INDEX idx_addresses_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- `latitude`/`longitude` are **copied from `service_locations` at write time** — the D-4
  precedent (`vendor_profiles` does the same; any code changing `service_location_id`
  re-copies the centroid in the same statement).
- No unique constraint on `is_default`: MySQL has no partial/filtered unique index, and the
  generated-column trick cannot coexist with the required `user_id` FK (MySQL error 1215 —
  the D-21 finding). The one-default-per-user rule is therefore a **service-level invariant**
  (D-21 precedent), enforced transactionally.

### API — `AddressController` at `/api/v1/addresses`

| Method | Path | Behaviour |
|---|---|---|
| GET | `/api/v1/addresses` | List the caller's addresses; default-first, then `id ASC` |
| POST | `/api/v1/addresses` | Create; the first address becomes the default automatically |
| GET | `/api/v1/addresses/{id}` | Read one; 404 for missing **or foreign** id |
| PUT | `/api/v1/addresses/{id}` | Full replacement (D-15 precedent); 404 for missing/foreign |
| DELETE | `/api/v1/addresses/{id}` | Delete; 404 for missing/foreign |

Rules:

- **Ownership comes from the JWT principal**, never the request body — the same pattern as
  `ProductService.requireOwnedProduct`. A foreign id is **404, not 403**: "yours" is defined
  by the principal, so a foreign id is indistinguishable from a missing one (catalog precedent).
- **Unknown `serviceLocationId` → 400 `VALIDATION_FAILED`** — the
  `VendorService.resolveServiceLocation` precedent (`BusinessException.badRequest("Unknown service location")`).
- **Default-address mutations run in one `@Transactional`** that first takes a
  `PESSIMISTIC_WRITE` lock on the caller's address rows ordered by `id`
  (`ORDER BY id FOR UPDATE`), so concurrent create/set-default/delete cannot produce zero or
  two defaults. Setting a new default clears the previous one inside the lock.
- **Deleting the default promotes the oldest remaining address (`MIN(id)`)** — proposed.
  **[APPROVAL]** Alternative: promote none and leave the user with no default until they set one.
- **[APPROVAL] Which roles may hold addresses?** The plan says "owner-only CRUD" but does not
  name a role. Proposal: any authenticated role (the endpoint is `anyRequest().authenticated()`
  territory; ownership is the real boundary). Alternative: `hasRole("CUSTOMER")` only.
- **[APPROVAL] Label uniqueness:** not imposed (a user may have two "Home" entries); the
  plan is silent. Flagging rather than inventing a constraint.

### RBAC (task 4.T)

`RoleBoundaryTester.assertAllRoles(endpoint, method, customerStatus, vendorStatus, adminStatus)`
maps the vendor cell to **FLORIST** (D-11). For `/api/v1/addresses`: all three authenticated
roles behave identically (200 on their own empty set), unauthenticated → 401. If the
CUSTOMER-only alternative is chosen, FLORIST/ADMIN cells become 403 `FORBIDDEN`.

---

## C. Stages 2–3 (tasks 4.2–4.4) — Location picker, discovery, search

### Task 4.2 — Location picker (frontend)

- `GET /api/v1/locations` is already **public** (`SecurityConfig` line 82) and returns the
  hierarchical shape (city → areas → pincodes) with the row id on every area, plus paginated
  search by `pincode` (`^[0-9]{6}$`) / `area`. The picker needs no new backend endpoint.
- The chosen `service_location_id` is held in a **Zustand session store**
  (sessionStorage — location is not a secret, but session-only keeps it out of durable
  storage; contrast known issue #022, where the *auth* store persists tokens to
  localStorage). No GPS, no Geolocation API, no geocoder (D-4).
- `HeroSearch` currently navigates to `/browse?area=` (a placeholder route); the picker
  replaces the free-text hint and threads `locationId` into `/browse` and `/search`.

### Task 4.3 — Discovery: `GET /api/v1/discover`

**New public endpoint — `SecurityConfig` gains `.requestMatchers("/api/v1/discover/**").permitAll()`**
alongside the existing `/api/v1/locations/**` line.

- Signature: `GET /api/v1/discover?locationId=&page=&size=`. **`locationId` is proposed
  required → 400 `VALIDATION_FAILED` when absent or unknown.** **[APPROVAL]** The plan shows
  `locationId` in the signature but never says "required"; an omitted-parameter default
  (e.g. first seeded location) would silently answer for the wrong place.
- Candidate predicate: `vendor_profiles.status = 'APPROVED' AND accepting_orders = 1`
  **and** haversine distance ≤ `delivery_radius_km`. Suspended/pending/rejected vendors are
  excluded (D-6: hidden from discovery immediately).
- Distance math — native `@Query` with named parameters only (Criteria API cannot express a
  formula projection cleanly at this shape):
  ```sql
  2 * 6371 * ASIN(SQRT(
    POWER(SIN(RADIANS(:lat - latitude) / 2), 2) +
    COS(RADIANS(:lat)) * COS(RADIANS(latitude)) *
    POWER(SIN(RADIANS(:lng - longitude) / 2), 2)))
  ```
  run against `vendor_profiles`' own denormalized `latitude`/`longitude` (D-4) — no join to
  `service_locations` needed for the distance.
- **Bounding-box prefilter**: one cheap aggregate (`MAX(delivery_radius_km)` over APPROVED
  profiles) computed in Java, then `latitude BETWEEN :lat±maxR/111 AND ...` narrows the scan
  before haversine refines. The existing `idx_vendor_profiles_status_geo (status, latitude,
  longitude)` serves the prefilter; **no new indexes proposed** at the demo region's scale.
- Sorted by distance ASC, paginated via the existing `PageResponse<T>` envelope (defaults
  `page=0`, `size=20`, max 100 — matching `LocationService`).
- Response row: vendor id, `businessName`, `description`, service area (city/area/pincode),
  `deliveryRadiusKm`, delivery settings (`minOrderAmount`, `baseDeliveryFee`, `perKmFee`,
  `freeDeliveryAbove`, `prepTimeMinutes`), `avgRating`, `reviewCount`, `acceptingOrders`,
  and the computed **`distanceKm`** + **estimated delivery fee**
  (`baseDeliveryFee + perKmFee × distanceKm`; the free-delivery waiver needs a cart subtotal
  that does not exist yet — **[APPROVAL]** show the base fee only, per rules.md §4).
- Zero results = **empty page, not 404** (a location with no delivering vendors is a valid
  answer).

### Task 4.4 — Search: `GET /api/v1/search`

**New public endpoint — `SecurityConfig` gains `.requestMatchers("/api/v1/search/**").permitAll()`.**

- Signature: `GET /api/v1/search?q=&category=&priceMin=&priceMax=&sort=&vendorId=&locationId=`.
  **`locationId` proposed required → 400 when absent/unknown** (it drives both the
  delivery-reach filter and the default `distance` sort). **[APPROVAL]**
- Product predicates (all must hold):
  - `products.status = 'ACTIVE'` (DRAFT/INACTIVE/ARCHIVED are never storefront-visible —
    `Product.ProductStatus` javadoc).
  - `inventory.quantity - inventory.reserved_quantity > 0` — inner join `inventory`
    (a row always exists; `ProductService.create` writes it in the same transaction).
  - The product's vendor is APPROVED, `accepting_orders = 1`, and within
    `delivery_radius_km` of the location (same haversine/bounding-box machinery as
    discovery, joined through `products.vendor_id`).
- `q`: case-insensitive `name LIKE %q%` (matching `ProductSpecifications.nameContains`
  semantics). A leading wildcard is unindexable by b-tree — acceptable at demo scale; a
  FULLTEXT index is the plan's Phase 14.7 follow-up, **not** Stage 3.
- `category`: by **id** (proposed). **[APPROVAL]** The public category read returns roots
  only (known issue #025), so a client can discover only top-level ids from public APIs, but
  admin-created child categories exist (reachable via `GET /api/v1/admin/categories?parentId=`).
  Proposal: accept **any active category id, including children**. Alternative: roots only.
  `slug` as the filter key is rejected (slugs are unique, but the public API does not expose
  them for children).
- `priceMin`/`priceMax`: decimal bounds on `base_price`; `priceMin > priceMax` → 400.
- `sort`: whitelist `distance | price_asc | price_desc | name`; **unknown value → 400
  `VALIDATION_FAILED`** (fail closed, never silently default). Default = `distance` when
  `locationId` is supplied (always, under the required proposal).
- `vendorId`: restrict results to one vendor (still subject to the approval/reach filters).
- Zero results = empty page, not 404.

---

## D. Stages 4–5 (tasks 4.5–4.9) — Storefront API and customer UI

### Task 4.5 — Storefront: `GET /api/v1/vendors/{id}/storefront`

**Security conflict that must be resolved in `SecurityConfig`:** `/api/v1/vendors/**` is
matched by `hasRole("FLORIST")` (line 94), so a *public* storefront needs an explicit
exception matcher **placed before it** (Spring evaluates `requestMatchers` in declaration
order):

```java
.requestMatchers(HttpMethod.GET, "/api/v1/vendors/{id}/storefront").permitAll()
.requestMatchers("/api/v1/vendors/**").hasRole("FLORIST")
```

- Returns the vendor's public profile (business name, description, service area, delivery
  settings, weekly hours from `vendor_hours`) plus its **ACTIVE** products, paginated.
- **404 for an unknown id and 404 for a non-approved vendor** (the plan's wording) — a
  pending/rejected/suspended vendor's storefront does not exist as far as the public API is
  concerned (D-6), and 404 does not leak its existence.
- No new migration; reads `vendor_profiles`, `vendor_hours`, `products`, `inventory`.

### Tasks 4.6–4.9 — Customer frontend

New `src/features/customer/` slice (`api.ts`, `queries.ts`, `types.ts`, `format.ts`,
`form-schema.ts`, `components/`, `pages/`), following the vendor/admin slice conventions.

Routes (all inside the **existing root `Layout`** — see mismatch M4):

| Path | Replaces | Contents |
|---|---|---|
| `/browse` | `Placeholder` | Location picker + vendor cards from `/api/v1/discover` |
| `/search` | — (new) | Search + filter UI over `/api/v1/search` |
| `/vendors/:vendorId` | — (new) | Storefront page over `/api/v1/vendors/{id}/storefront` |
| `/vendors/:vendorId/products/:productId` | — (new) | Product detail modal |
| `/addresses` | — (new) | Address book CRUD (task 4.1) |

Reuse: `ProtectedRoute` (auth guard, UX-only — the backend stays the authority),
`shared/lib/api.ts` (axios instance, Bearer interceptor, 401 refresh), `PageResponse<T>`
(`shared/types.ts`), TanStack Query filter-and-page-keyed hooks with whole-listing
invalidation, `api-error` normalisation, `Pagination`, motion tokens (`motion/tokens.ts`),
design tokens, and the `test/render.tsx` + `test/factories.ts` test patterns.

Deliberate limits:

- **Cart stays a placeholder.** The "add to cart" button renders but writes nothing — no
  cart store, no cart API, no state (cart is Phase 7+). **[APPROVAL]** UX choice: render the
  button disabled, or a "coming soon" affordance.
- **Product-modal image carousel is metadata-only.** No route serves image bytes
  (known issue #020, D-31): the modal lists filename/type/size/position rather than rendering
  `<img>` elements that would 404.
- **Gift note is collect-only in Phase 4** — the checkout that persists it is Phase 5+.
- **No `usePolling` hook exists** (plan §9 mentions polling for later phases); none is built
  here.

---

## E. Plan-vs-code mismatches and open questions

| # | Mismatch / question | Proposal |
|---|---|---|
| M1 | `scripts/verify.ps1` does not exist; `validate-all.ps1` validates GSD artifacts only, not app builds/tests | Run backend + frontend suites manually before Stage 1 close; integration needs Docker (issue 018) |
| M2 | Plan says role `VENDOR`; seeded role is `FLORIST` | Consistent via D-11; `RoleBoundaryTester`'s vendor cell already maps to FLORIST |
| M3 | Plan §5 describes a `geo/` layer with `GeoService`/Haversine/`LocationUtils` — none exists | Built in Stage 3 as part of the discovery/search repositories (native `@Query` haversine); no separate `GeoService` class is needed for the two read endpoints |
| M4 | Plan §5 names a `CustomerLayout`; none exists — customers currently share the root `Layout` + `SiteHeader` | Customer pages live under the existing root `Layout`; a dedicated shell is a design call **[APPROVAL]** |
| M5 | `GET /api/v1/vendors/{id}/storefront` (public) conflicts with `/api/v1/vendors/**` → `hasRole("FLORIST")` | permitAll exception matcher **before** the namespace matcher (shown in §D) |
| M6 | Task 4.T's RBAC checklist (401/403/404 cells via `RoleBoundaryTester`) vs. plan-mandated *public* endpoints (`/discover`, `/search`, storefront) | For public endpoints the meaningful cells are anonymous-2xx and validation-400/404; the 401/403 cells apply to `/api/v1/addresses` only |
| M7 | Address book: plan says "owner-only" but never names the eligible role | Any authenticated role (ownership is the boundary); CUSTOMER-only is the alternative **[APPROVAL]** |
| M8 | `locationId` on `/discover` and `/search` never stated as required | Required → 400 when absent **[APPROVAL]** |
| M9 | Category search filter: id vs slug; children visible or not | id, any active category including children **[APPROVAL]** |
| M10 | Delivery-fee estimate pre-cart (free-delivery waiver needs a subtotal) | Show `baseDeliveryFee + perKmFee × distance` without the waiver **[APPROVAL]** |
| M11 | Delete-default promotion policy | Promote oldest remaining (`MIN(id)`) **[APPROVAL]** |
| M12 | Address-label uniqueness | Not imposed **[APPROVAL]** |
| M13 | Migration numbering | Latest applied is `V11__stock_movements.sql`; Stage 1 writes `V12__create_addresses.sql` (additive only — D-9/ADR-002) |

---

## Out of scope (hard boundaries)

No cart tables, no checkout, no orders, no payments (Phase 5+). No Redis changes. No GPS /
browser Geolocation / geocoder (D-4). No editing of applied migrations. No implementation in
this stage.
