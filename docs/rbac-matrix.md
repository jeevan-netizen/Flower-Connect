# RBAC Matrix — Phase 3 (Catalog & Inventory)

Scope: every endpoint added by Phase 3 tasks 3.1, 3.5, 3.6 and 3.8. Phase 2 endpoints are
out of scope and were verified during the Phase 2 close-out (`docs/progress.md`, task 2.T).

Rules `docs/rules.md` §6.3 requires per endpoint:

| Cell | Meaning |
|---|---|
| **401** | unauthenticated |
| **403 role** | authenticated, wrong role |
| **403 foreign** | another vendor's resource |
| **404** | nonexistent resource |
| **2xx** | correct role and owner |

## The two authorization layers

D-13 splits authorization into two layers that both run, in order. This is why the vendor
routes have two distinct 403 codes and the distinction is asserted rather than assumed:

1. **URL namespace** (`SecurityConfig`, `SecurityConfig.java:94-95`)
   - `/api/v1/vendors/**` → `hasRole("FLORIST")`
   - `/api/v1/admin/**` → `hasRole("ADMIN")`
   - `GET /api/v1/categories` → `permitAll`
   - `POST /api/v1/vendors/register` → `permitAll`
2. **Handler** (`@RequiresApprovedVendor` on the controller class) → `vendor_profiles.status`
   = `APPROVED`, read per request, never cached in the JWT.

So a `CUSTOMER` on a vendor route is `403 FORBIDDEN` (layer 1), while a `PENDING_APPROVAL`
florist is `403 VENDOR_NOT_APPROVED` (layer 2). Both are 403; only the `code` distinguishes
them, which is why the tests assert `$.code` and not only the status.

---

## 3.1 — Categories

| Method | Path | 401 | 403 role | 403 foreign | 404 | 2xx |
|---|---|---|---|---|---|---|
| GET | `/api/v1/categories` | **200** (public by design) | n/a — public | n/a | n/a | 200 |
| GET | `/api/v1/admin/categories` | ⬜ | ✅ 403 `CUSTOMER` | n/a — global | n/a | 200 |
| POST | `/api/v1/admin/categories` | ⬜ | ✅ 403 `CUSTOMER` | n/a — global | n/a | 200 |
| PUT | `/api/v1/admin/categories/{id}` | ⬜ | ⬜ | n/a — global | ⬜ | 200 |
| DELETE | `/api/v1/admin/categories/{id}` | ⬜ | ⬜ | n/a — global | ⬜ | 204 |
| GET | `/api/v1/admin/categories/{id}` | ⬜ | ⬜ | n/a — global | ✅ (after delete) | ⬜ |

Verified by `CategoryApiIntegrationTest`: `shouldBeAccessibleWithoutAuthentication`,
`adminCanCreateCategory`, `adminCanListCategories`, `adminCanUpdateCategory`,
`adminCanDeleteCategory` (asserts the follow-up read is 404),
`nonAdminCannotAccessAdminEndpoints` (GET and POST as `CUSTOMER`).

**Gaps, recorded rather than papered over.** Categories are global reference data, so the
403-foreign column is genuinely N/A — there is no owning vendor. The six ⬜ cells are real
gaps in *this* file, but the authorization itself is not untested: they all route through
the same `hasRole("ADMIN")` namespace rule that `RoleBoundaryTest` and
`AdminUserStatusIntegrationTest` exercise for `/api/v1/admin/**`, and
`AdminCategoryControllerTest` covers the handlers. Closing them here would duplicate the
Phase 2d matrix rather than test new behaviour.

**Note:** `POST /api/v1/admin/categories` returns **200**, not 201. The controller
(`AdminCategoryController.java:44`) is consistent with its own test
(`CategoryApiIntegrationTest.java:93`), so this is a deliberate-looking choice rather than a
defect, but it deviates from the 201 used by the Phase 3.5/3.8 vendor routes. Left unchanged
— changing it is a contract change, not an incidental fix.

---

## 3.5 — Vendor catalog (`/api/v1/vendors/products`)

`@RequiresApprovedVendor` on `VendorProductController` (class level).

| Method | Path | 401 | 403 role | 403 approval | 403 foreign | 404 | 2xx |
|---|---|---|---|---|---|---|---|
| POST | `/api/v1/vendors/products` | ✅ | ✅ `FORBIDDEN` | ✅ `VENDOR_NOT_APPROVED` | n/a | n/a | 201 |
| GET | `/api/v1/vendors/products` | ✅ | ✅ `FORBIDDEN` | ✅ `VENDOR_NOT_APPROVED` | n/a — own listing | n/a | 200 |
| GET | `/api/v1/vendors/products/{id}` | ✅ | ✅ `FORBIDDEN` | ✅ | ✅ | ✅ | 200 |
| PUT | `/api/v1/vendors/products/{id}` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| PATCH | `/api/v1/vendors/products/{id}/deactivate` | ✅ | ✅ | ✅ | ✅ | ✅ | 204 |

Verified by `VendorCatalogIntegrationTest`:

| Assertion | Test |
|---|---|
| 401 | `anAnonymousCallerIsUnauthenticated` |
| 403 role, `FORBIDDEN` not `VENDOR_NOT_APPROVED` | `aCustomerIsStoppedByTheNamespaceRuleBeforeTheApprovalGuard` |
| 403 role, `ADMIN` also refused | `anAdminCannotUseTheVendorCatalogRoutes` |
| 403 `PENDING_APPROVAL` | `aPendingVendorIsRefusedWithTheApprovalErrorCode` |
| 403 `REJECTED` | `aRejectedVendorIsRefused` |
| 403 `SUSPENDED`, immediately | `aSuspendedVendorLosesCatalogAccessImmediately` |
| reinstate restores access | `reinstatementRestoresCatalogAccess` |
| 403 foreign | `oneVendorCannotSeeAnotherVendorsProduct` |
| 404 | line 259-260 `isNotFound()` |
| 201/200 | `anApprovedVendorCanCreateAndReadBack` |
| 204 | line 390, 408 |

The vendor is resolved from the JWT subject on every call, so **cross-vendor access is not
expressible in a request** — no route accepts a vendor identifier. That is why the
"403 foreign" column is a property of the route shape, not just of a check.

---

## 3.6 — Vendor inventory (`/api/v1/vendors`)

`@RequiresApprovedVendor` on `VendorInventoryController` (class level).

| Method | Path | 401 | 403 role | 403 approval | 403 foreign | 404 | 2xx |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/vendors/products/{id}/inventory` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| POST | `.../inventory/stock-in` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| POST | `.../inventory/stock-out` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| POST | `.../inventory/adjustments` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| POST | `.../inventory/write-offs` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| PUT | `.../inventory/low-stock-threshold` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| PUT | `.../inventory/expiry-date` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| GET | `.../inventory/movements` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| GET | `/api/v1/vendors/inventory/low-stock` | ✅ | ✅ | ✅ | n/a — own listing | n/a | 200 |

Verified by `VendorInventoryIntegrationTest`:

| Assertion | Test / line |
|---|---|
| 401, both the per-product read and the low-stock list | line 197-198 `isUnauthorized()` |
| 403 role, `CUSTOMER` on every mutation | lines 208-262, nine `isForbidden()` |
| 403 approval, `PENDING_APPROVAL` | lines 218-232 |
| suspend loses access, reinstate restores | lines 139-167 |
| 403 foreign | lines 235-258 |
| 404 nonexistent product | lines 271-276 |
| 409 `INSUFFICIENT_STOCK` | lines 487, 526, 543 |

The movement's actor is taken from the same authenticated identity, so **the actor is not
client-supplied** either. The two alert settings (`low-stock-threshold`, `expiry-date`) take
the same pessimistic row lock as the stock mutations even though they write no movement —
Hibernate issues whole-row `UPDATE`s, so a concurrent stock change would otherwise write
back a stale `quantity` (D-24).

---

## 3.8 — Product images (`/api/v1/vendors/products/{productId}/images`)

`@RequiresApprovedVendor` on `VendorProductImageController` (class level).

| Method | Path | 401 | 403 role | 403 approval | 403 foreign | 404 | 2xx |
|---|---|---|---|---|---|---|---|
| POST | `/images` (multipart) | ✅ | ✅ | ✅ | ✅ | ✅ | 201 |
| GET | `/images` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| PUT | `/images/{imageId}/primary` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| PUT | `/images/order` | ✅ | ✅ | ✅ | ✅ | ✅ | 200 |
| DELETE | `/images/{imageId}` | ✅ | ✅ | ✅ | ✅ | ✅ | 204 |

Verified by `ProductImageIntegrationTest`:

| Assertion | Test / line |
|---|---|
| 401, all five routes | lines 494-497 `isUnauthorized()` |
| 403 role, `CUSTOMER` | lines 509-536 `isForbidden()` |
| 403 approval, suspend/reinstate | lines 542-549 |
| 403 foreign | lines 437-452, 516 |
| 404 nonexistent product | lines 464-483 |
| 415 unsupported format | lines 262-277 |
| 413 payload/pixel ceiling | lines 335, 418 |
| 409 image-count limit | line 418 |
| 201 / 200 / 204 | lines 155, 580, 652 |

An **unknown image id is 404, not 403** — the caller asked for an image *of this product*
and this product does not have it. The image is resolved from the already-locked row set, so
the check cannot race (D-28).

---

## Non-RBAC Phase 3 cells

Rules `docs/rules.md` §6 also required these; all are covered by the same suites.

| Requirement | Verified by | Notes |
|---|---|---|
| Ownership checks | `VendorCatalogIntegrationTest.oneVendorCannotSeeAnotherVendorsProduct`, `VendorInventoryIntegrationTest`, `ProductImageIntegrationTest` | Lock is taken **after** the ownership check, so a florist cannot stall another vendor's row by naming a foreign id (D-24). |
| Slug collision | `ProductServiceTest`, `ProductInventoryIntegrationTest` | Numeric suffix retry; concurrent creates can deadlock on the unique index (error 1213), so the whole create is retried in a fresh transaction. |
| Expiry job with a fake clock | `InventoryExpiryServiceTest`, `InventoryExpiryIntegrationTest` | Driven directly against the `MutableClock` bean; `application-test.yml` sets `app.expiry-sweep-cron: "-"` so no scheduled run can fire mid-test. |
| Upload rejection cases | `ProductImageIntegrationTest`, `ImageProcessorTest`, `ImageTypeDetectorTest` | Empty part, oversized, wrong magic bytes, polyglot, truncated, decompression-bomb pixel count, over the image-count limit. |
| Concurrency | `InventoryConcurrencyIntegrationTest`, `ProductImageIntegrationTest` | Real threads released together by a `CountDownLatch`, asserting **end state only**. A sequential test cannot observe the absence of a lock. |

## Why `@WebMvcTest` cannot cover any of this

D-13 records the trade: `@RequiresApprovedVendor` is a composed `@PreAuthorize` enabled by
`@EnableMethodSecurity` on the production `SecurityConfig`. A `@WebMvcTest` slice supplies its
own `SecurityFilterChain` and never loads that class, so the annotation is **inert** and a
gated route returns 200 instead of 403. Every cell in this document therefore comes from a
`@SpringBootTest` (`@AutoConfigureMockMvc`) running against the real production filter chain.
The slice tests carry an explicit Javadoc note saying so, so a reader does not assume the
gate is verified there.

---

## 4.1 — Customer address book (`/api/v1/addresses`)

One authorization layer only: `SecurityConfig` maps `/api/v1/addresses/**` to
`hasRole("CUSTOMER")`. There is no `@RequiresApprovedVendor` — the address book is a
customer surface, not a vendor transaction surface. The caller is resolved from the JWT
subject on every call, so cross-customer access is not expressible in a request: an
address that belongs to another customer and one that does not exist are the same 404.

| Method | Path | 401 | 403 role | 403 foreign | 404 | 2xx |
|---|---|---|---|---|---|---|
| GET | `/api/v1/addresses` | ✅ | ✅ `FORBIDDEN` (FLORIST, ADMIN) | n/a — own listing | n/a | 200 |
| POST | `/api/v1/addresses` | ✅ | ✅ | n/a | n/a | 201 |
| GET | `/api/v1/addresses/{id}` | ✅ | ✅ | ✅ (indistinguishable from 404) | ✅ | 200 |
| PUT | `/api/v1/addresses/{id}` | ✅ | ✅ | ✅ | ✅ | 200 |
| DELETE | `/api/v1/addresses/{id}` | ✅ | ✅ | ✅ | ✅ | 204 |

Verified by `AddressApiIntegrationTest` (real JWTs minted through the login endpoint,
so the whole production filter chain runs):

| Assertion | Test |
|---|---|
| 401, GET and POST | `anUnauthenticatedRequestIsRefused` |
| 403 role, FLORIST and ADMIN | `aFloristAccountCannotReachTheAddressBook`, `anAdminAccountCannotReachTheAddressBook` |
| 404 foreign and nonexistent, GET/PUT/DELETE | `anotherCustomersAddressIs404`, `aMissingAddressIs404` |
| 400 validation (blank label, unknown service location, non-positive id, bad page/size) | `aBlankLabelIsRefused`, `anUnknownServiceLocationIsRefused`, `updatingWithAnUnknownServiceLocationIs400`, `aNonPositiveAddressIdIsRefused`, `invalidPaginationParametersAreRefused` |
| Default rules at the HTTP boundary (first-address auto-default, explicit default clears previous, omitted flag keeps it, delete promotes oldest, delete of only address leaves none) | `theFirstAddressBecomesTheDefaultWhateverTheRequestSays`, `anExplicitDefaultOnALaterAddressClearsThePreviousDefault`, `aLaterAddressWithoutExplicitDefaultStaysNonDefault`, `updateIsAFullReplacementThatKeepsTheStoredDefault`, `updateSettingAnotherDefaultClearsThePreviousDefault`, `updateExplicitlyClearingTheDefaultLeavesNoDefault`, `deletingTheDefaultPromotesTheOldestRemainingAddress`, `deletingANonDefaultAddressLeavesTheDefaultAlone`, `deletingTheOnlyAddressLeavesTheBookEmpty` |
| Listing order (default first, then id ASC) and pagination totals | `listsTheCallersAddressesDefaultFirstThenIdAscending`, `listingIsPaginatedAndReportsTotals` |
| Coordinates are the server-copied centroid (create and on service-location change) | `theFirstAddressBecomesTheDefaultWhateverTheRequestSays`, `updateRecopiesTheCentroidWhenTheServiceLocationChanges` |

Concurrency is covered by `AddressConcurrencyIntegrationTest`: 8 simultaneous first-address
creations leave exactly one default, and 8 competing default switches leave exactly one —
the user-row lock is the serialization point (D-32), and only a real-threads test can
prove it.

**Note:** the 403-foreign cell is a 404 by design — "yours" is defined by the principal,
so a client cannot distinguish "someone else's address" from "no such address". That is
the intended information-hiding property, not a gap.