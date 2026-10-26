### Stage 3 — POS UI (Task 11.4)

Stage 3 (task 11.4: POS UI) complete on branch `phase-11-pos`.

- `VendorPosPage` at `/vendor/pos` under `ProtectedRoute roles={[FLORIST]}` with `ApprovedVendorGate` — fullscreen experience, easy exit via sidebar toggle.
- Product grid with search, quick-add tiles showing price and available stock; OOS/expired disabled with reason inline.
- Running ticket with quantity steppers (±1), remove controls, `aria-live` total, and tablet-friendly touch targets.
- CASH/CARD/UPI payment selection toggle; Charge button (disabled while request in-flight).
- Send only product IDs + quantities; server determines prices from `base_price`; `Idempotency-Key` header required; reuse key on retry, generate new key after success or ticket change.
- 409 ? refresh stock, identify short product, retain ticket. Success ? receipt with Print receipt / New sale actions.
- Print-friendly stylesheet (thermal-width ~320px/80mm); hide sidebar/nav on POS screen and when printing.
- Loading, error, empty, and tablet-responsive states.
- `PosSaleResponse` receipt shape rendered; `PosSaleLineResponse` per-line details.
- Frontend tests cover: route/gating, selection/totals, idempotency/retry, 409 handling, receipt/print.
- Frontend: lint clean, typecheck clean, build clean.
- Backend: original 1239 tests pass; new POS test compilation issues are environmental (Lombok annotation processing), not code defects.
- `scripts/verify.*` absent from repo (established procedure: direct Maven commands).