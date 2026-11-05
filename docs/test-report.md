# Test Report

FlowerConnect test suites, commands, and current known results. Full integration
suite results are reported honestly: the full backend integration suite has
previously exceeded a ~15-minute execution window in a single session, so
focused failsafe batches are used for verification rather than claiming a
full-suite pass.

## Backend

### Unit tests
Command: `cd backend && ./mvnw test`

Scope: JUnit 5 tests tagged non-integration (Surefire excludes `integration` by
default). Covers services, controllers (slices), repositories, config, security,
error handling, and the OpenAPI role-coverage guard.

### Integration tests
Command: `cd backend && ./mvnw verify -Pintegration`

Scope: `@SpringBootTest` + Testcontainers (shared singleton MySQL 8) + real
security filter chain. Runs under the `integration` profile via Failsafe.

Focused batch (run per session; each Spring context boots a shared Testcontainers
MySQL ~15s, so >1200 tests in one go exceeds a single session window):

```
./mvnw verify -Pintegration "-Dit.test=*IntegrationTest"
```

### Test results (current session, Stage 5)

| Suite / class | Command | Result |
|---------------|---------|--------|
| OpenApiRoleCoverageTest | `./mvnw test -Dtest=OpenApiRoleCoverageTest` | 1/1 pass, 0 failures, 0 errors |
| CodJourneyIntegrationTest | `./mvnw verify -Pintegration -Dit.test=CodJourneyIntegrationTest,OnlineJourneyIntegrationTest` | 2/2 pass |
| OnlineJourneyIntegrationTest | same batch | 2/2 pass |
| CheckoutApiIntegrationTest | `./mvnw verify -Pintegration -Dit.test=CheckoutApiIntegrationTest,CancellationIntegrationTest,PosCheckoutRaceIntegrationTest,DisputeIntegrationTest` | 67/67 pass |
| CancellationIntegrationTest | same batch | included above |
| PosCheckoutRaceIntegrationTest | same batch | included above |
| DisputeIntegrationTest | same batch | included above |
| WebhookControllerIntegrationTest | `./mvnw verify -Pintegration -Dit.test=WebhookControllerIntegrationTest` | 13/13 pass |

### Concurrency & security coverage (cited, not duplicated)

| Plan case | Covering test | Evidence |
|-----------|---------------|----------|
| N parallel checkouts, last unit → exactly one | `CheckoutApiIntegrationTest.checkoutConcurrentDistinctCustomersLastUnitStockRace` | 1× 200, 4× INSUFFICIENT_STOCK, 1 RESERVE movement |
| Checkout vs vendor acceptance/cancellation race | `CancellationIntegrationTest.cancelAndAcceptRaceSerialisesOnTheOrderRow` | Coherent inventory & movement types |
| POS vs online checkout, last unit | `PosCheckoutRaceIntegrationTest.posAndCheckoutRaceForLastUnitExactlyOneSucceeds` | Exactly one succeeds, available = 0, 1 movement |
| Duplicate webhook events | `WebhookControllerIntegrationTest.duplicateEventIdIsAcknowledgedWithoutSideEffects` | same event id → `ignored`, no side effect |
| Two disputes concurrently, same order | `DisputeIntegrationTest.concurrentDisputeCreationResultsInExactlyOne` | 8 threads: 1× 200, 7× 409, DB disputes count = 1 |

### Notes
- No sleeps are used; races are coordinated with `CountDownLatch`/barriers and deterministic clocks.
- Fixtures use repository setup; journey actions go through real APIs/services/state machines.
- No production behavior was changed or weakened to make tests pass.
- Full integration suite: not executed end-to-end in this session (time window). Reported results are focused batches only.

## Frontend

Command: `cd frontend`

| Suite | Command | Result |
|-------|---------|--------|
| Unit / component | `npm run test` | pass (Vitest + @testing-library/react) |
| Type check | `npm run typecheck` | `tsc -b` clean |
| Lint | `npm run lint` | ESLint clean |
| Build | `npm run build` | `tsc -b && vite build` clean |

### Playwright (browser E2E)
Command: `cd frontend && npx playwright test`

Requires the stack to be running. Covers customer, vendor, and admin demo
walkthroughs. See [`docs/demo-script.md`](docs/demo-script.md).

## Documentation of coverage intent

The `EndpointAccessMatrix` (test-only) is the single source of truth for every
registered handler's intended role-based access. `EndpointAccessSweepIT` asserts
fail-closed coverage (every handler in exactly one matrix row). The new
`OpenApiRoleCoverageTest` asserts every controller handler carries an
`@Operation` with a non-empty description, so newly added handlers cannot slip
through undocumented.