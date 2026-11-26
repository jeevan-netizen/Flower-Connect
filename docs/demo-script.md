# FlowerConnect Demo Script (8–10 minutes)

**Demo date:** Friday, 2026-11-27  
**Branch:** `b2b-module` (not merged to `main`)  
**Stack:** backend on `http://localhost:8080`, frontend on `http://localhost:5173`, MySQL on `localhost:3306`, MailHog on `1025/8025`.

## Seeded demo accounts

Created by the opt-in development seed (`APP_B2B_DEMO_SEED_ENABLED=true` with `DEMO_*_EMAIL/PASSWORD`). All three accounts verified to exist in the database and to authenticate against `/api/v1/auth/login`.

| Role | Email | Password | Owns |
|------|-------|----------|------|
| Farmer | farmer.demo@flowerconnect.test | FarmerDemo123! | ACTIVE link to Demo Seller Florist + Demo Buyer Florist; outstanding balance ₹13,600.00 |
| Florist (seller) | seller.demo@flowerconnect.test | SellerDemo123! | Demo Seller Florist (APPROVED); product "Demo Red Roses" ₹199.00/kg |
| Florist (buyer) | buyer.demo@flowerconnect.test | BuyerDemo123! | Demo Buyer Florist (APPROVED) |

## Demo sequence

### 1. Seed the demo data (optional if already seeded)

Start the stack (backend + frontend + MySQL + MailHog), then start the backend with `APP_B2B_DEMO_SEED_ENABLED=true` and the `DEMO_*_EMAIL/PASSWORD` credentials. The `B2BDemoSeeder` runs on startup and is idempotent: a second start detects the `DEMO-STM-001` statement marker and skips, so no data is duplicated.

Verified: backend starts, Flyway migrates V1→V40, three demo accounts exist in the database and authenticate.

### 2. Two-vendor B2B order flow (verified in real Chromium)

**Accounts:** buyer.demo@flowerconnect.test (buyer) and seller.demo@flowerconnect.test (seller), in separate browser contexts so sessions cannot mix.

1. Buyer logs in to `http://localhost:5173`, opens `/vendor/b2b/place-order`.
2. Buyer searches "Demo Seller", selects "Demo Seller Florist".
3. Buyer sees "Demo Red Roses" at ₹199.00/kg with available stock (94 kg at seed time).
4. Buyer enters quantity `4`, confirms the line total ₹796.00, clicks "Place order".
5. Buyer is taken to the order page showing status **Placed** and total **₹796.00**.
6. Seller logs in (separate browser context), opens `/vendor/b2b/orders/<orderId>`.
7. Seller clicks **Accept order** → order status becomes **Accepted**.
8. Buyer re-opens the place-order page for the same seller: available stock has dropped by exactly 4 (e.g. 94 → 90).

Verified end-to-end in real Chromium: order #6 (#B2B-1795678766-8cc0f6b7), status Placed, total ₹796.00, stock dropped 94 → 90 after accepting a 4-kg order.

### Not verified in this session (real-browser)

The following parts of the demo script could not be completed in a real browser during this session because the Playwright E2E test harness hit timing issues unrelated to the production code (async view rendering, input element re-mounting on the deposit form, selector timing). They are listed here for completeness but are **not claimed as verified**:

- **Farmer flow:** farmer login, vendor browse + request to join, vendor accept, deposit entry + deduction preview (3 kg / 29 kg / ₹2,900.00 for 32 kg @ ₹100), farmer passbook + running balance, collection request/confirm/reject, suspend/reactivate, passbook isolation between farmers.
- **B2B flow (post-acceptance):** ship with shipment reference, deliver, e-bill download + PDF verification (bill number, order number, seller/buyer, item rows, quantities, rates, grand total), access refused before delivery / by a third party.
- **Statements:** preview delivered unpaid orders, generate statement, record payment, statement marked PAID, orders marked paid, re-preview excludes paid orders, buyer read-only view.
- **Browser logins for all seeded accounts** (API-level login verified via curl; real-browser login not completed).

**Root cause:** the frontend B2B module had two real bugs that blocked the demo flow — (1) all B2B API calls used `/api/v1/b2b/*` instead of `/api/v1/vendors/b2b/*` (404), and (2) `B2BPlaceOrderPage` called a hook conditionally, violating React's rules of hooks and crashing the page. Both are fixed (commits `ce87e24`, `7190957`). With those fixed, order placement works end-to-end in the browser. The remaining unverified flows were not reached because the E2E test harness timing (async view rendering, input re-mounting) consumed the available session time; they are recommended for a follow-up session before the demo.

## Accounts & data summary (verified in database)

| Account | Email | Role | Data |
|---------|-------|------|------|
| Farmer | farmer.demo@flowerconnect.test | FARMER | Linked ACTIVE to seller + buyer vendors; outstanding balance ₹13,600.00 |
| Seller | seller.demo@flowerconnect.test | FLORIST | Demo Seller Florist (APPROVED); Demo Red Roses ₹199.00/kg, stock 94 kg at seed |
| Buyer | buyer.demo@flowerconnect.test | FLORIST | Demo Buyer Florist (APPROVED) |

## Regression tests added

- `frontend/tests/e2e/demo-b2b-flow.test.ts` — real-browser test: buyer places order, seller accepts, stock drops by exactly the ordered quantity.
