# FlowerConnect — Project State

**Milestone:** Milestone 1 — Core Hyperlocal Marketplace
**Phase:** Phase 4 — Customer Addresses, Discovery and Search
**Status:** ready
**Plan:** None

## Last Action
Phase 3 (Catalog and Inventory Management) completed, verified, and closed out with 508 unit tests, 362 integration tests, and 514 frontend tests passing.

## Next Steps
- [ ] 4.1 Address book (`addresses` entity and owner CRUD `/api/v1/addresses`)
- [ ] 4.2 Location picker (session-backed, no GPS)
- [ ] 4.3 Geo discovery API (`GET /api/v1/discover`)
- [ ] 4.4 Product search API (`GET /api/v1/search`)
- [ ] 4.5 Storefront API (`GET /api/v1/vendors/{id}/storefront`)

## Blockers
- [ ] None

## Phase Progress Summary
- **Phase 0:** Complete (Scaffold, Spring Boot + React skeleton, Flyway)
- **Phase 1:** Complete (Auth, JWT in-memory + httpOnly cookie refresh, rate limiting)
- **Phase 2:** Complete (Service locations, vendor profile, opening hours, approval gate)
- **Phase 3:** Complete (Categories, product catalog, inventory locks, expiry sweep, image pipeline, vendor UI)
- **Phase 4:** Next up (Customer addresses, discovery, search, storefront)
- **Phases 5–15:** Pending per `docs/FlowerConnect_Implementation_Plan_v2.2.md`
