# FlowerConnect Demo Script

Step-by-step walkthrough of the full order flow for all three roles (CUSTOMER, FLORIST, ADMIN) with expected results. (Phase 14 deliverable.)

## Note — simulated online payments (Phase 7)

Simulated online payments (`SimulatedPaymentProvider` / `SimulatorController`) are registered only under the `dev` profile (`@Profile("!prod")`). On a prod-like run (`prod` profile), the simulator endpoints are not registered and the demo must use COD only; online checkout will not be completable via the simulator. Orders rejected before delivery keep payment status `PENDING` (COD rule, D-47 Option B).
