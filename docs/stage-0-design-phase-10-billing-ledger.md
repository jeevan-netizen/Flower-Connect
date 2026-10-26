# Stage 0 Design Note — Phase 10: Billing, Commissions, Settlement Ledger
## 1. Core design principles (carried from Phase 0–8)

- **Money is immutable and signed.** Ledger amounts are `BigDecimal`, scale 2, `HALF_UP`. Sign convention: **`+` = platform owes vendor; `-` = vendor owes platform.** No stored derived balances — outstanding is always recomputed from ledger rows.
- **Atomicity with delivery.** Commission row + matching ledger row are created *inside* the same transaction that moves an order to `DELIVERED` (hook in `OrderStateMachine`, after `saveAndFlush(order)`, before commit). `OrderDelivered` stays published after commit exactly as today. Idempotent retries are handled by a unique constraint on `commissions(order_id)` plus a pre-check (`existsByOrderId`) + `DataIntegrityViolationException` backstop (same pattern as the `NotificationService` dedupe fix). Delivery is never rolled back by a commission/ledger failure.
- **Dispute adjustments run inside the resolve transaction.** Ledger rows for resolved disputes are created inside `DisputeService.resolveDispute` before commit, keyed by `(dispute_id)` unique constraint for idempotency. The payment-side refund already exists (`PaymentService.markRefundedWithAmount`); the ledger row is the commission-side counterpart.
- **Settlement attaches, never mutates.** A settlement run attaches existing ledger rows (via `settlement_entries`) and records a net amount on the `settlements` row. Ledger rows are never updated or deleted.
- **Everything audited.** Commission-rate changes write `audit_log` rows with before/after values (new nullable columns `previous_value`/`new_value`).
- **All endpoints authenticated;** vendor endpoints scoped to the vendor linked to the JWT subject; admin endpoints `ADMIN` only (`SecurityConfig` already maps `/api/v1/admin/**` → `hasRole("ADMIN")`).


**Status:** Proposed — awaiting confirmation  
**Branch:** `phase-10-billing-ledger` (created from `main` at `effd6a3`)  
**Plan reference:** `docs/FlowerConnect_Implementation_Plan_v2.2.md`, tasks 10.1–10.7 + 10.T  
**Verification:** `scripts/verify.sh` / `scripts/verify.ps1` **do not exist** in the repo (only `search_repo.ps1`/`search_repo.sh` in `scripts/`). The plan references them (section 0.6, task 0.T). Verification could not be run — see section 14.

**Related code already present:** `Order.commissionRate` snapshotted at checkout (`CheckoutService`), `VendorProfile.commission_rate` (dormant column), `platform_config` table + `PlatformConfig` entity/repository (seeded rows exist; no service/API yet), `OrderDelivered` published after commit via transaction synchronization, `DisputeResolved` event, `AuditLog` (no before/after columns yet), `Payment.collectPayment`/`markRefundedWithAmount`, `VendorAdminService` (suspend/reinstate + audit pattern), `AppProperties` cron convention (`app.auto-reject-cron` / `APP_AUTO_REJECT_CRON`), `TestClockConfig` (`Clock.fixed`).

## 2. Rate resolution and snapshot (Task 10.1)

**Rate representation (plan §6.3):** Commission rate is stored and exposed as a **percentage 0–100** with **at most 2 decimal places** (`DECIMAL(5,2)` on `vendor_profiles.commission_rate` and `platform_config.commission_rate_default`). The API accepts and returns values like `10.00` (meaning 10%). Internally, `CommissionRateService.resolveRate(vendor)` returns a `BigDecimal` multiplier in `0–1` scale (divide stored percentage by 100) for use in `commissionAmount = subtotal × rate`, scale 2 `HALF_UP`. Validation: `0.00 <= rate <= 100.00`, max 2 decimal places; multiplier precision handled at 4 decimals internally before the final `× subtotal` rounding.

Resolution order for an order's commission rate:

1. `vendor_profiles.commission_rate` (per-vendor override, `DECIMAL(5,2)`, nullable)
2. `platform_config` row `commission_rate_default` (seeded `"10.00"` in migration V26, meaning 10%)
3. `CheckoutProperties.defaultCommissionRate` (`0.10`) — ultimate fallback, keeps existing unit tests green

`CommissionRateService.resolveRate(vendor)` encapsulates this. Returns a `BigDecimal` multiplier in `0–1` scale (percentage ÷ 100). Validation: `0 <= multiplier <= 1` (100% cap).

**Snapshot:** `CheckoutService` already sets `order.commissionRate` at order creation. Change its default branch from `checkoutProperties.getDefaultCommissionRate()` to `commissionRateService.resolveRate(vendor)`. Behavior is unchanged in tests (vendor rate set → used; vendor rate null → platform_config missing in unit tests → fallback to the same mocked `0.10`; integration DB seeded with `0.10` = same value).

Commission base = **order subtotal only** (not delivery fee, not platform fee). `commissionAmount = subtotal × rate`, scale 2 `HALF_UP`.

**Rate timing question (requires decision):** `CheckoutService` snapshots `commissionRate` at **order creation** (PLACED). The commission row (and its snapshotted `rate`) is created when the order becomes `DELIVERED` (plan §10.2). If the rate changes between order placement and delivery, the rate on the commission row is whichever `resolveRate(vendor)` returns **at delivery time** — NOT the order-time snapshot. This matches the plan's "the rate in force is snapshotted per order" on the commission row. However, this diverges from `CheckoutService` already storing `commissionRate` on the order at creation time. **Decision needed:** (a) rate at delivery time (as proposed, matches commission-row snapshot semantics), or (b) rate at order creation (use `order.commissionRate` in the commission row).

**Who sets the per-vendor override (open decision #1):** proposed **admin-only** via `PATCH /api/v1/admin/vendors/{id}/commission-rate` (body: `rate` in `0.00–100.00`; audit row with previous/new value). Vendors can view their rate (`GET /api/v1/vendor/billing/rate`) but not set it. Alternative: vendor may lower their own rate. No vendor self-service in v1 unless confirmed.

## 3. Ledger sign formula table with worked examples (plan §6.4, plan §10.3 types)

Constants per order: `S` = subtotal, `D` = delivery fee, `P` = platform fee, `C` = commission amount (`S × rate`).

| Entry type | When created | Signed amount (platform + = owes vendor) | Meaning |
|---|---|---|---|
| `ONLINE_ORDER_CREDIT` | Order → `DELIVERED`, online payment | `+(S − C + D)` | Platform pays vendor subtotal + delivery − commission |
| `COD_DUES` | Order → `DELIVERED`, COD | `-(C + P)` | Vendor collected cash (`S + D + P`); owes platform commission + platform fee |
| `DISPUTE_ADJUSTMENT` | Dispute resolved (full or partial) | `−refundAmount` (full refund: `−(S − C + D)`, i.e. negate the original credit) | Claw back the refunded amount from vendor credit |
| `SETTLEMENT_PAYMENT` | Settlement run (carried on `settlements.net_amount`, not a ledger row) | `Σ attached signed amounts` | Net payable for the period |

**Worked example** — `S = 100.00`, `D = 20.00`, `P = 0.00`, rate `10%` → `C = 10.00`:

- Online order delivered: ledger `ONLINE_ORDER_CREDIT +110.00`. Platform keeps `C + P = 10.00` of the `120.00` collected.
- COD order delivered: ledger `COD_DUES −10.00`. Vendor keeps `120.00` cash and owes platform `10.00`.
- Partial dispute refund `30.00` on the online order: ledger `DISPUTE_ADJUSTMENT −30.00`. Outstanding = `110.00 − 30.00 = +80.00` (platform still owes vendor `80.00`).

## 4. Dispute adjustment semantics (plan §10.3, plan §10.5 tie-in)

Inside `DisputeService.resolveDispute` (same transaction, before commit):

- `RESOLVED_FULL` → one ledger row `DISPUTE_ADJUSTMENT`, amount = negated original `ONLINE_ORDER_CREDIT` of the order (full reversal of vendor credit).
- `RESOLVED_PARTIAL` → one ledger row `DISPUTE_ADJUSTMENT`, amount = `−refundAmount` (the amount already passed to `markRefundedWithAmount`).
- `REJECTED` → no ledger row.
- Idempotency: unique constraint `uq_dispute_ledger_dispute_id(dispute_id)`; pre-check + backstop as in section 1. A re-resolve of the same dispute creates no second row.

## 5. Settlement attachment and netting (plan §10.4)

**Cadence (plan §10.4):** **Weekly** scheduled generation per vendor into `settlements` (not monthly as originally proposed). `period` is a weekly window `[periodStart, periodEnd)` aligned to a fixed week boundary (e.g. Monday 00:00 UTC). The scheduler runs weekly.

`SettlementService.runSettlement(vendorId, periodStart, periodEnd)`:

1. Reject if a `SETTLED` settlement already exists for this vendor+period (409). If a `PENDING` settlement exists for the period, return it (idempotent re-run).
2. Select unattached ledger rows for the vendor with `occurred_at` in `[periodStart, periodEnd)`: types `ONLINE_ORDER_CREDIT`, `COD_DUES`, `DISPUTE_ADJUSTMENT`, not present in `settlement_entries`.
3. `net = Σ signed amounts`. `direction` = `PLATFORM_PAYS_VENDOR` if `net > 0`, `VENDOR_PAYS_PLATFORM` if `net < 0`, `ZERO` if `0`.
4. Create `settlements` row (`status = PENDING`) + `settlement_entries` attaching each ledger row. `(settlement_id, ledger_entry_id)` unique → a ledger row attaches to at most one settlement.
5. Admin lifecycle: `PENDING` → `SETTLED` (finalize run, admin reference note) — no `PAID` status in v1 (payout confirmation is off-platform). The plan (§10.4) specifies `PENDING → SETTLED` only.

`SettlementScheduler`: `@Scheduled(cron = "#{@appProperties.settlementCron}")`, default weekly `0 0 0 * * 0` (Sunday midnight UTC), property `app.settlement.cron` / env `APP_SETTLEMENT_CRON`, value `-` disables (same convention as `AutoRejectScheduler`). Thin scheduler component delegating to `SettlementService` (clock-injectable, testable without waiting for the schedule).

## 6. Outstanding balance and overdue policy (plan §10.7)

`vendorOutstandingBalance(vendorId) = Σ signed amount of ledger rows **not attached to any settlement.**`

Treatment by attachment state:

| Settlement status of attached ledger row | Counts toward outstanding balance? | Rationale |
|---|---|---|
| Not attached | **Yes** | Still open — owed but not yet netted into a settlement |
| Attached to `PENDING` settlement | **Yes** | Netted into a settlement but not yet finalized — amount still outstanding |
| Attached to `SETTLED` settlement | **No** | Settlement finalized — amount is due; tracked via settlement row, not raw balance |
| Attached to `PAID` settlement | **No** | Payout confirmed — fully discharged |

- Positive balance ⇒ platform owes vendor. Negative balance ⇒ vendor owes platform.
- Overdue rule: balance `≠ 0` **and** oldest unattached (or attached-to-PENDING) ledger row older than `app.billing.overdueDays` (default `30`, env `APP_BILLING_OVERDUE_DAYS`).
- `GET /api/v1/admin/billing/overdue` lists vendors with outstanding balance and days-overdue. **No automatic vendor suspension for non-payment in v1** (flag only; admin decides) — open decision #2 if the plan intends harder enforcement. Plan §10.7 says "Admin can flag or suspend vendors with dues overdue beyond a configured period (audit-logged)" — suspension is an allowed option, not default behavior.

## 7. Statement library + CSV injection protection (plan §10.5, plan §10.T)

`StatementService.generate(vendorId, periodStart, periodEnd, format)`:

- Format v1: **CSV** (plan §10.5 lists both CSV/PDF; PDF is **deferred** as a follow-up — no PDF library in `pom.xml`, no frontend PDF component in scope).
- Rows: ledger entries for the vendor in the period, plus a total row.
- CSV injection protection (OWASP): every cell is double-quoted; internal quotes doubled; any cell starting with `=`, `+`, `-`, `@`, or tab is prefixed with a single apostrophe inside the quotes (e.g. `"'=1+1"`), so spreadsheet software renders it as literal text, never a formula.
- Endpoint: `GET /api/v1/vendor/billing/statement` (vendor, own period) and admin equivalent; `Content-Type: text/csv`, `Content-Disposition: attachment`.

## 8. Backfill strategy (one-off, before scheduler is enabled)

For all existing `DELIVERED` orders lacking a commission row: create commission + `ONLINE_ORDER_CREDIT`/`COD_DUES` ledger row, one transaction per order, idempotent via the same unique constraints. Shipped as a disabled-by-default `ApplicationRunner` (`app.billing.backfill.enabled=false`, env `APP_BILLING_BACKFILL_ENABLED`); operators enable it for one deploy after migration V27, then disable. Log summary (created/skipped). Not part of the scheduler.

## 9. Schema plan (migrations)

**V26** — settings:
- `INSERT platform_config (config_key, config_value) VALUES ('commission_rate_default','10.00')` (percentage, meaning 10%)
- `ALTER TABLE audit_log ADD (previous_value VARCHAR(500), new_value VARCHAR(500))` (nullable, additive)

**V27** — billing tables:
- `commissions` (`id` PK, `order_id` UK `uq_commissions_order`, `vendor_id` FK `vendor_profiles(id)`, `customer_id` FK `users(id)`, `commission_rate DECIMAL(5,2)` (percentage 0–100, 2 dp), `subtotal DECIMAL(12,2)`, `delivery_fee DECIMAL(12,2)`, `platform_fee DECIMAL(12,2)`, `commission_amount DECIMAL(12,2)`, `created_at`); index `(vendor_id, created_at)`.
- `ledger_entries` (`id` PK, `vendor_id` FK, `order_id` FK nullable, `dispute_id` FK UK `uq_dispute_ledger_dispute_id`, `type` enum `ONLINE_ORDER_CREDIT/COD_DUES/DISPUTE_ADJUSTMENT`, `amount DECIMAL(12,2) signed`, `occurred_at`, `reference VARCHAR(255)`, `created_at`); index `(vendor_id, occurred_at)`.
- `settlements` (`id` PK, `vendor_id` FK, `period_start DATE`, `period_end DATE`, `status` enum `PENDING/SETTLED`, `net_amount DECIMAL(12,2)`, `direction` enum `PLATFORM_PAYS_VENDOR/VENDOR_PAYS_PLATFORM/ZERO`, `settled_at`, `admin_reference_note VARCHAR(500)`); UK `(vendor_id, period_start, period_end)`. No `PAID` status in v1.
- `settlement_entries` (`id` PK, `settlement_id` FK, `ledger_entry_id` FK UK `uq_settlement_entries_ledger`).

All `created_at`/`occurred_at` use the app clock (`java.time.Clock`, injected; tests use `TestClockConfig`).

## 10. API plan

| Endpoint | Role | Purpose |
|---|---|---|
| `GET /api/v1/vendor/billing/rate` | VENDOR | Own commission rate (percentage `0.00–100.00`) |
| `PATCH /api/v1/admin/vendors/{id}/commission-rate` | ADMIN | Set per-vendor override (body: `rate` in `0.00–100.00`); audit row |
| `GET /api/v1/admin/platform-settings/commission-rate` | ADMIN | Read platform default (percentage) |
| `PATCH /api/v1/admin/platform-settings/commission-rate` | ADMIN | Update `platform_config.commission_rate_default` (percentage); audit row |
| `GET /api/v1/vendor/billing/ledger` | VENDOR | Own ledger page (type/date filters) |
| `GET /api/v1/admin/billing/ledger` | ADMIN | All-vendor ledger page (vendor filter) |
| `GET /api/v1/vendor/billing/statement` | VENDOR | CSV statement for a period |
| `GET /api/v1/admin/billing/statement` | ADMIN | CSV statement for any vendor/period |
| `GET /api/v1/vendor/billing/outstanding` | VENDOR | Own unattached balance |
| `POST /api/v1/admin/billing/settlements/run` | ADMIN | Run settlement for vendor+period (idempotent) |
| `GET /api/v1/admin/billing/settlements` | ADMIN | List settlements (vendor/status filters) |
| `PATCH /api/v1/admin/billing/settlements/{id}/finalize` | ADMIN | `PENDING → SETTLED` |
| `GET /api/v1/admin/billing/overdue` | ADMIN | Vendors with overdue outstanding balances |

## 11. Frontend (Stage 5–7 of Phase 10)

- Vendor: `/vendor/billing` — rate view, ledger table, outstanding balance, statement CSV download.
- Admin: `/admin/billing` — platform commission rate, vendor commission-rate override, ledger browser, settlement run/finalize/pay, overdue list.
- Routes added under `vendor` and `admin` roots in `src/app/router.tsx` (same `ProtectedRoute` pattern as existing pages); API client under `features/vendor/billing/api.ts` and `features/admin/billing/api.ts`; types in each feature's `types.ts`; Zod form schemas for the rate forms.
- Follow existing conventions: TanStack Query, `react-hook-form` + zod, Tailwind, `ADMIN_PAGE_SIZE`-style paging constants.

## 12. Plan vs. code mismatches (documented, not changed in v1)

1. **Platform-fee source (open decision #4):** Plan §6.3 / plan §8 say `platform_fee` comes from `platform_config` (default 0). Code reads `CheckoutProperties.getPlatformFee()` and `CheckoutServiceTest` mocks it. `platform_config` has no `platform_fee_default` row seeded. **Recommended: keep `CheckoutProperties`** for v1 (no migration, no service change, tests stay green). Document that if platform fee becomes admin-tunable later, add a `platform_fee_default` row to `platform_config` and route through `PlatformConfigService`.
2. **Rate-setting authority:** Plan §10.1 phrasing suggests vendors may manage their own rate. **Proposed: admin-only** (open decision #1).
3. `PlatformConfig` entity/repository exist but have no service — `PlatformConfigService` (read + admin update for `commission_rate_default`) is added in this phase.
4. Plan §10.5 lists both CSV/PDF for statements. v1 scope is CSV with injection protection; PDF is a documented follow-up.
5. Plan §10.4 specifies weekly settlement cadence and `PENDING → SETTLED` lifecycle; original note proposed monthly and `OPEN → SETTLED → PAID`. Revised to match plan (weekly, `PENDING → SETTLED`).

## 13. Testing

- Unit: `CommissionRateServiceTest` (resolution order, bounds), `CommissionServiceTest` (idempotent create, ledger sign math), `SettlementServiceTest` (netting, direction, idempotent re-run, attach-once), `StatementServiceTest` (CSV injection cases: cells starting `=+−@|`), `PlatformConfigServiceTest`.
- Controller slice: MockMvc + `RoleBoundaryTester` — 403 for wrong role, 404/empty for cross-vendor isolation, 409 on duplicate settlement run.
- Integration batch (Testcontainers, `./mvnw verify -Pintegration`, `@Tag("integration")` following Phase 8 pattern): end-to-end COD delivery → commission+ledger → dispute clawback → settlement run → overdue list → statement CSV.

## 14. Verification result (preflight)

**`scripts/verify.sh` / `scripts/verify.ps1` does not exist.** The plan references these scripts (section 0.6, task 0.T: "Everything | `scripts/verify.sh` (or `scripts/verify.ps1` on Windows): backend unit → backend integration → frontend lint, typecheck, test, build; stops at the first failure"). The `scripts/` directory contains only `search_repo.ps1` and `search_repo.sh`. Because the verify script is missing, the green baseline could not be established. This is reported as a preflight blocker; no implementation has begun and none will until the design is confirmed and the baseline (or an equivalent command sequence: `./mvnw test` backend, `npm run lint && npm run typecheck && npm test && npm run build` frontend) is run.

### Open decisions for confirmation

1. **Rate-setting authority:** admin-only (proposed) vs. vendors may lower their own commission rate.
2. **Overdue enforcement:** flag + admin list only (proposed) vs. automatic payout block / vendor suspension when overdue. Plan §10.7 says "Admin can flag or suspend vendors with dues overdue beyond a configured period (audit-logged)" — suspension is an allowed option, not default behavior.
3. **Statement formats:** CSV v1 (proposed), PDF as follow-up. Plan §10.5 lists both CSV/PDF.
4. **Platform fee source:** remain `CheckoutProperties` (documented mismatch #1) vs. route through `platform_config` as the plan states.
5. **Rate timing:** rate at delivery time (proposed, matches commission-row snapshot semantics) vs. rate at order creation (use `order.commissionRate`).

No application code has been changed. Branch `phase-10-billing-ledger` exists at `main`/`effd6a3` with only the untracked design note present. Nothing has been committed, pushed, or merged. Awaiting your confirmation of this design before Stage 1 (task 10.1) begins.
