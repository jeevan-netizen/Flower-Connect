# FlowerConnect — End-to-End Demonstration Script

This document provides a timed, numbered, step-by-step walkthrough of the FlowerConnect hyperlocal flower marketplace across **Customer**, **Vendor (Florist)**, and **Admin** roles.

---

## 1. Environment & Pre-Demonstration Setup

### 1.1 Operator Credentials & Configuration

All demo accounts are seeded dynamically at boot when running in the `dev` profile with seed data enabled. 
**No credentials or passwords are committed to version control.**

The operator configures credentials in the local `.env` file prior to starting the demo environment:

```env
SPRING_PROFILES_ACTIVE=dev
ADMIN_EMAIL=admin@flowerconnect.test
ADMIN_PASSWORD=<configured-admin-password>
DEMO_PASSWORD=<configured-demo-password>
```

- **Admin Account:** `admin@flowerconnect.test` (password from `ADMIN_PASSWORD`)
- **Customer Accounts:** `demo-customer-1@flowerconnect.test`, `demo-customer-2@flowerconnect.test`, `demo-customer-3@flowerconnect.test` (password from `DEMO_PASSWORD`)
- **Vendor (Florist) Accounts:**
  - `vendor-blossom@flowerconnect.test` (Blossom Haven, Approved, Koramangala)
  - `vendor-petalcraft@flowerconnect.test` (Petal Craft Florals, Approved, Indiranagar)
  - `vendor-gardenia@flowerconnect.test` (Green Gardenia, Approved, HSR Layout)
  - `vendor-oasis@flowerconnect.test` (Orchid Oasis, Pending Approval, Jayanagar)
  - `vendor-boutique@flowerconnect.test` (Flora Boutique, Suspended, Whitefield)
  *(All vendor account passwords from `DEMO_PASSWORD`)*

### 1.2 Known Limitations & Operator Notes

1. **Product Image Serving (Known Issue #020):** 
   Seeded products have valid procedural floral images uploaded and stored in the backend storage service, but no public endpoint currently streams image bytes to browsers. The UI deliberately renders styled image metadata cards rather than broken `<img>` tags (Decision D-31).
2. **Notification Bell & Live Updates:**
   The notification bell uses background polling fallback alongside SSE streams. If an update takes a few seconds to appear, clicking the bell icon or refreshing triggers an immediate query fetch.
3. **Delivery Radius Enforcement:**
   Checkout enforces geometric delivery distance between vendor centroid and customer address. Customer 1 (Alice) is located in Koramangala and HSR Layout; Customer 2 (Bob) in Indiranagar; Customer 3 (Charlie) in HSR Layout. Always select the matching address at checkout.

### 1.3 Clean Reset Procedure

To return the environment to a clean, newly seeded baseline state at any point:

```bash
docker compose down -v
docker compose up -d
```

---

## 2. Walkthrough Schedule & Time Budget

| Section | Role | Focus | Target Duration |
|---|---|---|---|
| **Part 1** | Customer | Discovery, Multi-Shop Cart, Gift Note, COD & Online Checkout | 6 minutes |
| **Part 2** | Vendor | Live Order Board, Status Progression, Inventory & Counter POS | 6 minutes |
| **Part 3** | Admin | Vendor Approval, Refund Queue, Dispute Resolution, Settlements | 4 minutes |
| **Part 4** | Secondary | Analytics, Reviews & Replies, Suspended Vendor State (Tier 2) | 4 minutes |
| **Total** | | | **20 minutes** |

---

## 3. Step-by-Step Demonstration Script

### Part 1: Customer Marketplace Journey (Tier 1)

#### Step 1: Customer Authentication
- **Tier:** Tier 1 (Must Show)
- **Actor:** Customer (`demo-customer-1@flowerconnect.test`)
- **Route:** `/login`
- **Action:**
  1. Navigate to `http://localhost:5173/login`.
  2. Enter Email: `demo-customer-1@flowerconnect.test` and Password: `<configured-demo-password>`.
  3. Click **"Sign in"**.
- **Expected Result:** Successful authentication, JWT issued into session/memory, redirected to home page.
- **Visible Confirmation:** Header displays "Orders", "Browse", "Cart" links, delivery location badge, and user initials/menu.

#### Step 2: Set Hyperlocal Delivery Location
- **Tier:** Tier 1 (Must Show)
- **Actor:** Customer (`demo-customer-1@flowerconnect.test`)
- **Route:** `/` or `/browse`
- **Action:**
  1. In the header, click the **Location Menu** (default text: "Set your delivery location" or previous area).
  2. From the dropdown list of Bengaluru service locations, select **"Koramangala"**.
- **Expected Result:** Location selection is stored in session storage and applied to discovery filters.
- **Visible Confirmation:** Header button updates to display **"Koramangala"** with a green pin icon.

#### Step 3: Browse Catalog & Filter by Category
- **Tier:** Tier 1 (Must Show)
- **Actor:** Customer (`demo-customer-1@flowerconnect.test`)
- **Route:** `/browse`
- **Action:**
  1. Click **"Browse"** in the top navigation.
  2. View available active florists delivering within radius of Koramangala.
  3. Click category chips (e.g. **"Roses"** or **"Bouquets"**).
- **Expected Result:** The product grid filters reactively without page reloads.
- **Visible Confirmation:** Products from "Blossom Haven" and "Green Gardenia" appear in the results grid with prices, descriptions, and stock tags.

#### Step 4: Product Detail & Gift Note
- **Tier:** Tier 1 (Must Show)
- **Actor:** Customer (`demo-customer-1@flowerconnect.test`)
- **Route:** `/vendors/1` or `/search/products/1`
- **Action:**
  1. Click on **"Velvet Red Roses"** by Blossom Haven.
  2. In the product detail drawer/modal, set Quantity to `2`.
  3. Enter Gift Note: `"Happy Anniversary! With all my love."`
  4. Click **"Add to Cart"**.
- **Expected Result:** Item is added to customer cart with price snapshot and custom gift note.
- **Visible Confirmation:** Header Cart badge updates to show item count `(1)`. A confirmation toast appears.

#### Step 5: Multi-Shop Cart & Quantity Adjustment
- **Tier:** Tier 1 (Must Show)
- **Actor:** Customer (`demo-customer-1@flowerconnect.test`)
- **Route:** `/cart`
- **Action:**
  1. Click the **Cart** icon in the header.
  2. View items grouped by florist shop ("Blossom Haven").
  3. Increase quantity or verify item subtotals.
  4. Click **"Proceed to Checkout"**.
- **Expected Result:** Cart loads server-side price snapshots; navigates to checkout.
- **Visible Confirmation:** Checkout screen loads showing address selector, delivery slot options, and price breakdown.

#### Step 6: Checkout with Cash on Delivery (COD)
- **Tier:** Tier 1 (Must Show)
- **Actor:** Customer (`demo-customer-1@flowerconnect.test`)
- **Route:** `/checkout`
- **Action:**
  1. Select Saved Address: **"Home - 123, 4th Cross, 5th Block, Koramangala"**.
  2. Select Earliest Available Delivery Slot.
  3. Select Payment Method: **"Cash on Delivery (COD)"**.
  4. Click **"Place Order"**.
- **Expected Result:** Order quote hash validated; order created in `PLACED` status; cart cleared.
- **Visible Confirmation:** Redirected to `/orders` or order confirmation with Order ID displayed and status badge **"PLACED"**.

#### Step 7: Simulated Online Payment Checkout
- **Tier:** Tier 1 (Must Show)
- **Actor:** Customer (`demo-customer-2@flowerconnect.test`)
- **Route:** `/checkout` $\to$ `/payment/:paymentGroupId`
- **Action:**
  1. Switch/login as Customer 2: `demo-customer-2@flowerconnect.test`.
  2. Location: **"Indiranagar"**. Add a bouquet from **"Petal Craft Florals"** to cart.
  3. Go to `/checkout`, select Saved Address: **"Home - 77, 100 Feet Road, Indiranagar"**.
  4. Choose Payment Method: **"Online Payment (Simulated)"**.
  5. Click **"Pay & Place Order"**.
  6. On the simulated payment screen (`/payment/:id`), click **"Simulate Successful Payment"**.
- **Expected Result:** Webhook simulation triggers backend `PaymentService.processProviderEvent`, order transitions to `PLACED`, payment marked `PAID`.
- **Visible Confirmation:** Payment status shows green checkmark with "Payment Confirmed"; order tracking link appears.

#### Step 8: Order Tracking, Timeline & Cancellation
- **Tier:** Tier 1 (Must Show)
- **Actor:** Customer (`demo-customer-1@flowerconnect.test`)
- **Route:** `/orders/:orderId`
- **Action:**
  1. Open the order placed in Step 6.
  2. View the visual status timeline (`Placed` $\to$ `Confirmed` $\to$ `Preparing` $\to$ `Out for Delivery` $\to$ `Delivered`).
  3. Click **"Cancel Order"**.
  4. In the dialog, select reason `"Change of plans"` and confirm.
- **Expected Result:** Order transitions to `CANCELLED`; reserved stock is restored to available inventory.
- **Visible Confirmation:** Status pill turns red **"CANCELLED"**; timeline records cancellation entry with timestamp.

---

### Part 2: Vendor Store & Fulfilment Operations (Tier 1)

#### Step 9: Vendor Sign-In & Dashboard Overview
- **Tier:** Tier 1 (Must Show)
- **Actor:** Florist (`vendor-blossom@flowerconnect.test`)
- **Route:** `/login` $\to$ `/vendor`
- **Action:**
  1. Log out from customer account.
  2. Sign in as `vendor-blossom@flowerconnect.test` with `<configured-demo-password>`.
- **Expected Result:** Redirected to Vendor Dashboard with florist navigation items.
- **Visible Confirmation:** Welcome heading **"Blossom Haven"** with status pill **"APPROVED"**, today's summary metrics, and order alert bell.

#### Step 10: Vendor Order Board & Order Acceptance
- **Tier:** Tier 1 (Must Show)
- **Actor:** Florist (`vendor-blossom@flowerconnect.test`)
- **Route:** `/vendor/orders`
- **Action:**
  1. Click **"Orders"** in the vendor navigation.
  2. In the **"New Orders"** column, locate the pending `PLACED` order (from pre-seeded Order 3 or fresh placement).
  3. Click **"Accept Order"**.
- **Expected Result:** Order transitions from `PLACED` to `CONFIRMED`.
- **Visible Confirmation:** Order card moves from "New Orders" to "Accepted / Preparing" column.

#### Step 11: Fulfilment Progression (Preparing to Delivered)
- **Tier:** Tier 1 (Must Show)
- **Actor:** Florist (`vendor-blossom@flowerconnect.test`)
- **Route:** `/vendor/orders`
- **Action:**
  1. On the accepted card, click **"Start Preparing"** (`PREPARING`).
  2. Once prepared, click **"Out for Delivery"** (`OUT_FOR_DELIVERY`).
  3. When delivery agent confirms handoff, click **"Mark Delivered"** (`DELIVERED`).
- **Expected Result:** State machine validates transition sequence; if COD, marks payment `COLLECTED`; creates commission and billing ledger entry.
- **Visible Confirmation:** Order card moves to "Delivered Today" column with timestamp; today's revenue summary increments.

#### Step 12: Real-time Stock Management & Thresholds
- **Tier:** Tier 1 (Must Show)
- **Actor:** Florist (`vendor-blossom@flowerconnect.test`)
- **Route:** `/vendor/inventory`
- **Action:**
  1. Click **"Inventory"** in the sidebar.
  2. Filter by status or inspect low-stock badges.
  3. On **"Coral Miniature Rose Posy"**, click **"Stock In"**.
  4. Enter Quantity: `10`, Reason: `"Fresh morning arrival from nursery"`.
  5. Click **"Confirm Stock In"**.
- **Expected Result:** Inventory service increments `quantity` and `available`; audit event logged.
- **Visible Confirmation:** Available quantity updates from 3 to 13; low stock warning badge disappears.

#### Step 13: Walk-in Counter Sale (POS)
- **Tier:** Tier 1 (Must Show)
- **Actor:** Florist (`vendor-blossom@flowerconnect.test`)
- **Route:** `/vendor/pos`
- **Action:**
  1. Click **"POS"** in the sidebar.
  2. Click product tiles to add items to the ticket (e.g. 1x Velvet Red Roses, 1x Sunny Meadow).
  3. Select Payment Method: **"CASH"** (or UPI).
  4. Click **"Complete Sale"**.
- **Expected Result:** POS transaction recorded atomically; stock decremented immediately; transaction marked commission-free per D-66.
- **Visible Confirmation:** Success banner displays with Receipt Number and Total Amount; ticket resets; receipt print modal appears.

---

### Part 3: Administrative Governance & Settlements (Tier 1)

#### Step 14: Admin Authentication & Dashboard
- **Tier:** Tier 1 (Must Show)
- **Actor:** Administrator (`admin@flowerconnect.test`)
- **Route:** `/login` $\to$ `/admin`
- **Action:**
  1. Sign in as `admin@flowerconnect.test` with `<configured-admin-password>`.
- **Expected Result:** Admin layout loaded; unrestricted governance navigation visible.
- **Visible Confirmation:** Admin Dashboard displays platform stats, active vendor count, pending applications badge.

#### Step 15: Vendor Application Approval
- **Tier:** Tier 1 (Must Show)
- **Actor:** Administrator (`admin@flowerconnect.test`)
- **Route:** `/admin/vendors`
- **Action:**
  1. Click **"Vendors"** in admin sidebar.
  2. Filter by status **"Pending Approval"**.
  3. Locate **"Orchid Oasis"** (`vendor-oasis@flowerconnect.test`).
  4. Click **"Approve"**.
  5. In the confirmation dialog, click **"Confirm Approval"**.
- **Expected Result:** Vendor status transitions to `APPROVED`; audit log recorded; vendor is now permitted to manage catalog and accept orders.
- **Visible Confirmation:** Status badge turns green **"APPROVED"**; action buttons change to "Suspend".

#### Step 16: Inspect Refund Queue & Mark Refunded
- **Tier:** Tier 1 (Must Show)
- **Actor:** Administrator (`admin@flowerconnect.test`)
- **Route:** `/admin/refunds`
- **Action:**
  1. Click **"Refunds"** in the sidebar.
  2. Locate Order 7 (cancelled online order in `REFUND_PENDING` status).
  3. Click **"Mark Refunded"**.
  4. Enter Reference: `"NEFT-REF-2026-9901"`.
  5. Click **"Confirm Refund"**.
- **Expected Result:** Payment status transitions from `REFUND_PENDING` to `REFUNDED`; `refundedAt` stamped; audit log created.
- **Visible Confirmation:** Item drops out of the pending refund queue; customer order detail reflects refunded status.

#### Step 17: Dispute Resolution with Partial Refund
- **Tier:** Tier 1 (Must Show)
- **Actor:** Administrator (`admin@flowerconnect.test`)
- **Route:** `/admin/disputes`
- **Action:**
  1. Click **"Disputes"** in the sidebar.
  2. Locate Order 8 dispute (Customer Charlie Rao vs Green Gardenia, reason `ITEM_DAMAGED`).
  3. Review dispute notes and vendor reply.
  4. Select Resolution: **"Partial Refund"**, enter Refund Amount: `₹264.50`, Note: `"Damaged stem photo verified; 50% refund approved"`.
  5. Click **"Resolve Dispute"**.
- **Expected Result:** Dispute status becomes `RESOLVED_PARTIAL`; billing ledger creates `DISPUTE_ADJUSTMENT` entry deducting ₹264.50 from vendor balance.
- **Visible Confirmation:** Dispute status badge updates to **"RESOLVED_PARTIAL"**; resolution summary is recorded.

#### Step 18: Platform Commission & Settlement Cycle
- **Tier:** Tier 1 (Must Show)
- **Actor:** Administrator (`admin@flowerconnect.test`)
- **Route:** `/admin/billing`
- **Action:**
  1. Click **"Billing"** in admin sidebar.
  2. Inspect the **Platform Commission Rate** card (default 10%).
  3. Inspect the **Settlements Table** showing Blossom Haven (`SETTLED`) and Petal Craft Florals (`PENDING`).
  4. Click **"Run Settlement"** modal to trigger an on-demand settlement calculation for the current cycle.
- **Expected Result:** Platform aggregates unattached ledger entries into net payable vendor balances.
- **Visible Confirmation:** Settlement table refreshes showing calculated credits, dues, and net payout amounts.

---

### Part 4: Extended Capabilities (Tier 2 — If Time Permits)

#### Step 19: Customer Reviews & Vendor Reply
- **Tier:** Tier 2 (Optional)
- **Actor:** Florist (`vendor-blossom@flowerconnect.test`)
- **Route:** `/vendor/reviews`
- **Action:**
  1. Sign in as Blossom Haven vendor.
  2. Go to `/vendor/reviews`.
  3. View 5-star customer review with existing owner response.
  4. Demonstrate rating breakdown card (Average rating, total reviews).
- **Expected Result:** Verified review score matches customer feedback.
- **Visible Confirmation:** StarRating component shows 5 stars, review text, and florist reply badge.

#### Step 20: Vendor & Admin Analytics
- **Tier:** Tier 2 (Optional)
- **Actor:** Florist or Admin
- **Route:** `/vendor/analytics` or `/admin/analytics`
- **Action:**
  1. Open analytics dashboard.
  2. Select date range (Last 30 Days).
- **Expected Result:** Aggregated metrics display total revenue, marketplace vs POS breakdown, order count, and AOV.
- **Visible Confirmation:** Visual trend cards and financial summaries render correctly.

---

## 4. Troubleshooting & Fallback Procedures

| Incident | Cause | Fallback Action |
|---|---|---|
| **Notification badge does not increment** | SSE stream timed out or disconnected | Click the notification bell to force a React Query polling refetch. |
| **"Address outside delivery radius" error** | Customer address centroid does not match vendor service area | Change selected delivery address to an area within vendor radius (e.g. Koramangala for Blossom Haven). |
| **Order placement fails with 409 Quote Expired** | Booking horizon or price change | Re-click "Place Order"; checkout auto-refreshes quote hash with `confirmPriceChanges=true`. |
| **Cannot log in with demo account** | Database reset occurred without setting `DEMO_PASSWORD` | Ensure `DEMO_PASSWORD` is set in `.env` and restart backend with `SPRING_PROFILES_ACTIVE=dev`. |

---

## 5. Automated Test Coverage vs. Manual Walkthrough

The Playwright rehearsal suite (`frontend/tests/e2e/demo-rehearsal.test.ts`) automates **page navigation and screenshot capture** for all three roles against the live stack. It validates that pages load correctly with seeded data and that navigation between roles works. The suite does **not** click through business actions (e.g. "Place Order", "Accept Order", "Mark Delivered", "Cancel Order", "Mark Refunded", "Resolve Dispute", "Simulate Successful Payment") because those require modal/dialog interaction and are rate-limited (5 logins/hour).

| Tier 1 Flow | Automated in Playwright? | Manual Walkthrough Required? |
|---|---|---|
| Customer discovery & browse | Yes (pages load) | No |
| Product detail & gift note | Yes (dialog opens) | No |
| Cart management | Yes (page loads) | No |
| COD checkout — complete order | **Yes — verified end-to-end in real browser** (login, location, browse, add to cart, COD checkout, order placed, cart cleared) | No |
| Simulated online payment | Payment page loads ("Awaiting payment"); simulator is backend-only (no frontend button per D-49/D-50) | **Yes** — trigger `POST /api/v1/payments/simulator/{orderId}/pay` with bearer token, or verify manually |
| Order tracking timeline | Yes (orders page loads) | No |
| Customer cancellation | **Yes — verified end-to-end in real browser** (open PLACED order, submit cancel dialog → `CANCELLED`) | No |
| Vendor order acceptance | Order board loads with PLACED orders visible; full accept/deliver click-through blocked by framer-motion dialog click-interception in automation | **Yes** — click "Accept Order" manually |
| Vendor fulfilment to delivery | No | **Yes** — click through Preparing → Out for Delivery → Delivered |
| COD collection on delivery | No | **Yes** — verify payment_status=COLLECTED |
| Inventory stock-in | No — navigates to inventory page only | **Yes** — click "Stock In" manually |
| Counter POS sale | Yes (POS page loads) | No — complete sale manually for full flow |
| Admin vendor approval | No — navigates to vendor list only | **Yes** — click "Approve" manually |
| Admin refund queue & mark refunded | No — navigates to refunds page only | **Yes** — click "Mark Refunded" manually |
| Admin dispute resolution | No — navigates to disputes page only | **Yes** — click "Resolve Dispute" manually |
| Settlement cycle | No — navigates to billing page only | **Yes** — click "Run Settlement" manually |
| Customer reviews & vendor reply | Tier 2 — not automated | **Yes** (optional) |
| Vendor & admin analytics | Tier 2 — not automated | **Yes** (optional) |

**To run the automated smoke test:**
```bash
cd frontend
DEMO_PASSWORD=<your-demo-password> ADMIN_PASSWORD=<your-admin-password> \
  npx playwright test tests/e2e/demo-rehearsal.test.ts
```

**To run the Tier 1 COD + cancel business-action rehearsal:**
The Tier 1 test (`frontend/tests/e2e/demo-rehearsal-tier1.test.ts`) was executed against an isolated clean-clone rehearsal stack. It is not committed to this branch in this Stage 2 docs-only update; run it from an isolated clone after Stage 1 seed data is loaded.
```bash
cd frontend
DEMO_PASSWORD=<your-demo-password> ADMIN_PASSWORD=<your-admin-password> \
  npx playwright test tests/e2e/demo-rehearsal-tier1.test.ts -g "Customer: COD checkout complete"
```

**To complete the full Tier 1 walkthrough manually:** Follow Steps 1–18 in Section 3, performing the actions marked "Manual Walkthrough Required" above. Screenshots for the automated steps are captured in `docs/rehearsal-materials/screenshots/`.

