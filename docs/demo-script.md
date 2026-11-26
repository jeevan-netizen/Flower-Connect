# FlowerConnect Demo Script (8–10 minutes)

**Branch:** `b2b-module` (not merged to `main`)  
**Stack:** backend on `http://localhost:8080`, frontend on `http://localhost:5173`, MySQL on `localhost:3306` (`3307` host-mapped), MailHog on `1025/8025`.

## Seeded demo accounts

All four accounts are verified to log in through the browser in separate Chromium contexts:

| Role | Email | Password | Portal / Owns |
|------|-------|----------|---------------|
| Admin | `admin@flowerconnect.local` | *(from `.env`)* | `/admin` — vendor & user management |
| Farmer | `farmer.demo@flowerconnect.test` | `FarmerDemo123!` | `/farmer` — linked `ACTIVE` to Demo Seller Florist + Demo Buyer Florist |
| Florist (Seller) | `seller.demo@flowerconnect.test` | `SellerDemo123!` | `/vendor` — Demo Seller Florist (`APPROVED`); product "Demo Red Roses" ₹199.00/kg |
| Florist (Buyer) | `buyer.demo@flowerconnect.test` | `BuyerDemo123!` | `/vendor` — Demo Buyer Florist (`APPROVED`) |

---

## Part 1: Farmer & Passbook Flow (~4 minutes)

Use two separate browser contexts: **Farmer** (`farmer.demo@flowerconnect.test`, or a fresh farmer when demonstrating join from scratch) and **Vendor** (an approved florist, e.g. `seller.demo@flowerconnect.test` or a freshly registered+approved florist).

| Step | Account | Action | Expected On-Screen Result |
|------|---------|--------|---------------------------|
| 1 | Farmer | Log in at `/login`, open `/farmer/vendors`, search for the target vendor, click **Request to join**. | Button updates immediately to **Requested** (pending link created). |
| 2 | Vendor | Log in in a separate context, open `/vendor/farmers`, click **Accept** on the pending farmer request. | Link status changes to **ACTIVE**; farmer now appears in active linked farmers list. |
| 3 | Vendor | Open the farmer's passbook (`/vendor/farmer-links/:linkId/passbook`). Add 4 deposits at **₹100/kg**: `9.5 kg`, `20 kg`, `32 kg`, `55 kg`. | Live deduction preview updates as you type (`9.5→0→9.5 / ₹950`, `20→2→18 / ₹1,800`, `32→3→29 / ₹2,900`, `55→5→50 / ₹5,000`). Saved rows appear and outstanding balance reaches **₹10,650.00**. |
| 4 | Farmer | Open that vendor's passbook (`/farmer/links/:linkId/passbook`). | Table shows **Gross**, **Deduction**, **Net**, **Rate**, **Amount**, and **Running Balance** for every row. Opening a second vendor's passbook shows separate entries and balance. |
| 5 | Farmer | Submit a collection request above the outstanding balance (e.g. `₹99,999`). | Request is refused with a visible alert message; balance remains unchanged. |
| 6 | Farmer | Submit a valid collection request (e.g. `₹1,000`, `CASH`). | Request appears as **PENDING**; outstanding balance remains **₹10,650.00**. |
| 7 | Vendor | Refresh/view `/vendor/farmer-links/:linkId/passbook` and click **Confirm** on the pending ₹1,000 collection. | Collection status becomes **CONFIRMED**; outstanding balance decreases to **₹9,650.00** on both vendor and farmer screens. |
| 8 | Farmer & Vendor | Farmer requests another collection (e.g. `₹500`); Vendor clicks **Reject**. | Collection status becomes **REJECTED**; balance remains **₹9,650.00**. |
| 9 | Vendor | Open `/vendor/farmers` and click **Suspend** on the farmer link. | Link status becomes **SUSPENDED**. Trying to record a deposit shows a readable blocked message; on the farmer screen, the passbook remains visible while **New collection requests are blocked**. |
| 10 | Vendor | Click **Reactivate** on the farmer link, then add a deposit. | Link returns to **ACTIVE** and new deposits succeed again. |
| 11 | Farmer | Navigate directly to another farmer's passbook URL (`/farmer/links/:otherLinkId/passbook`). | Access is refused with an error state (`403`/`404`). |

---

## Part 2: B2B Order, E-Bill & Statement Flow (~4–5 minutes)

Use two separate browser contexts: **Buyer** (`buyer.demo@flowerconnect.test`) and **Seller** (`seller.demo@flowerconnect.test`).

| Step | Account | Action | Expected On-Screen Result |
|------|---------|--------|---------------------------|
| 1 | Buyer | Open `/vendor/b2b/place-order`, search and select **Demo Seller Florist**, enter quantity `2` for **Demo Red Roses** (₹199.00/kg), click **Place order**. | Redirects to `/vendor/b2b/orders/:id` showing status **Placed** and total **₹398.00** (or catalog total). |
| 2 | Seller | Open `/vendor/b2b/orders/:id`, click **Accept order**. | Status updates to **Accepted**; checking `/vendor/b2b/place-order` as Buyer confirms available stock dropped by `2 kg`. |
| 3 | Seller | Click **Ship order**, enter shipment reference (e.g. `TRACK-DEMO-1`), submit, then click **Mark delivered**. | Status transitions **Accepted → Shipped → Delivered**; status timeline updates on both Seller and Buyer screens. |
| 4 | Buyer | On the delivered order detail page, click **Download e-bill**. | Valid PDF downloads containing bill number, order number, seller & buyer names, item rows, quantities, rates, and grand total matching the on-screen total. *(Pre-delivery requests return `409`; third-vendor requests return `403`.)* |
| 5 | Seller | Open `/vendor/b2b/statements`, select **Demo Buyer Florist** with the default 15-day range, click **Preview**. | Preview lists only delivered, unpaid orders for that buyer with the matching combined total. |
| 6 | Seller | Click **Create statement**, then open it from **Statements list** and click **Record payment** (e.g. `UPI`). | Statement status changes to **PAID** and included orders show **PAID**. |
| 7 | Seller & Buyer | Seller re-previews the same 15-day range; Buyer opens `/vendor/b2b/statements` → **Statements list** → **As buyer**. | Paid orders no longer appear in Seller's unpaid preview; Buyer sees the **PAID** statement in read-only mode. |
| 8 | Buyer & Seller | Buyer places a `3 kg` order; Seller accepts it (stock drops by `3 kg`); Seller or Buyer cancels it with a reason. | Order status becomes **Cancelled** and **Demo Red Roses** stock is restored by `3 kg`. |

---

## Automated E2E & Regression Suites

- `frontend/tests/e2e/b2b-full-flow.test.ts` — full B2B lifecycle + e-bill PDF + statements + cancellation/stock-restore.
- `frontend/tests/e2e/farmer-full-flow.test.ts` — full farmer/vendor link, 4 deposits/deductions, passbook, collections, suspend/reactivate, isolation.
- `frontend/tests/e2e/test-seeded-logins.test.ts` — browser login verification for all 4 seeded accounts.
