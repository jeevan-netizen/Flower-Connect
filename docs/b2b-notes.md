# B2B Module — Stage 1: B2B Orders

Status: Complete (branch `b2b-module`, commit from `farmer-module` at `f596764`)

## Endpoint list

All endpoints live under the existing vendor namespace
`/api/v1/vendors/b2b/...`, covered first by the production
`hasRole("FLORIST")` rule in `SecurityConfig` and then by
`@RequiresApprovedVendor` (D-13) on `B2BOrderController`. The vendor
caller is resolved from the JWT subject on every call; no route accepts
a vendor identifier for the caller.

| Method | Path | Description | Role |
|---|---|---|---|
| GET | `/api/v1/vendors/b2b/sellers?query=` | Search other approved vendors, excluding self. Optional substring match on business name. | FLORIST |
| GET | `/api/v1/vendors/b2b/sellers/{sellerId}/products` | List a seller's ACTIVE orderable products with catalog price and available stock. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders` | Place a B2B order (sellerId + items + optional note). Computes prices/totals server-side; snaps name/unit/price at placement. | FLORIST |
| GET | `/api/v1/vendors/b2b/orders?role=&status=&page=&size=` | List the caller's orders as BUYER (default) or SELLER, newest first, optional status filter. | FLORIST |
| GET | `/api/v1/vendors/b2b/orders/{orderId}` | Read one B2B order. Only buyer or seller may read it. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/accept` | Seller only. PLACED → ACCEPTED; deducts seller stock through InventoryService in the same transaction. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/reject` | Seller only. PLACED → REJECTED with optional reason. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/cancel` | Buyer cancels PLACED; either party cancels ACCEPTED (restores seller stock). No cancel after SHIPPED. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/ship` | Seller only. ACCEPTED → SHIPPED; requires shipment reference. | FLORIST |
| POST | `/api/v1/vendors/b2b/orders/{orderId}/deliver` | Seller only. SHIPPED → DELIVERED. Payment stays UNPAID. | FLORIST |

## Implementation decisions

- **Migration**: `V38__create_b2b_orders.sql` (additive). Creates `b2b_orders` (order_no unique, buyer_id/seller_id FKs to `vendor_profiles`, status ENUM, payment_status UNPAID default, total DECIMAL(12,2), order_note, shipment_reference, shipped_at/delivered_at, bill_number, placed_at/updated_at) and `b2b_order_items` (order_id/product_id FKs, product_name/unit snapshots, quantity INT, unit_price/line_total DECIMAL, CHECK quantity>0 and prices>=0). Adds CHECK `buyer_id <> seller_id` and indexes `(buyer_id,status)`, `(seller_id,status)`, `(seller_id,buyer_id,delivered_at)`. No existing column/constraint/index is altered (ADR-002).
- **Entities**: `B2BOrder` (inner `Status`/`PaymentStatus` enums) and `B2BOrderItem`, mapped to the V38 tables with `@Enumerated(EnumType.STRING)` (D-20 convention). `items` uses `@Builder.Default` so Lombok builder doesn't leave the list null.
- **Service**: `B2BOrderService` owns all validation and transitions. Resolves callers via `vendorProfileRepository.findByUserEmail` (JWT subject). Every order mutation checks participant ownership (403 FORBIDDEN) and current status (409 CONFLICT for invalid transitions). Uses injected `Clock` (never `Instant.now()`).
- **Stock**: `InventoryService.deductForB2BAccept(List<StockDelta>, User, reason)` deducts seller `quantity` (not `reserved_quantity`) with `MovementType.SALE_POS` under pessimistic row locks (ascending product-id order), one movement row per product, plus 409 `INSUFFICIENT_STOCK` if `quantity < q`. `InventoryService.restockCancelledOrder` restores accepted-order stock on cancel. Buyer inventory is never touched.
- **Transitions enforced**:
  - PLACED → ACCEPTED/REJECTED: seller only
  - PLACED → CANCELLED: buyer only
  - ACCEPTED → CANCELLED: buyer or seller (stock restored)
  - ACCEPTED → SHIPPED: seller only (shipment reference required)
  - SHIPPED → DELIVERED: seller only
  - All other transitions → 409 CONFLICT
- **Payment status**: stays `UNPAID` in Stage 1. Settlement ledger is not integrated (deferred to Stage 2).
- **Price snapshots**: product name, unit ("kg" default), and unit price are read from `Product` at placement and stored on `B2BOrderItem`; later catalog changes never alter the order.
- **DTOS**: `PlaceB2BOrderRequest`, `B2BOrderItemRequest`, `B2BOrderResponse`, `B2BOrderItemResponse`, `B2BOrderPageResponse`, `B2BProductSummary`, `SellerSummary`, `B2BOrderActionRequest`. BigDecimal wire format drops trailing zeros (e.g. `199.0`), which integration tests account for.
- **Validation**: `@NotBlank`/`@Positive`/`@Size` on request DTOs; service-level checks for self-order, foreign product, inactive product, insufficient stock, missing shipment reference.
- **Tests**: `B2BOrderServiceTest` (24 unit tests, Mockito LENIENT), `B2BOrderIntegrationTest` (22 integration tests against real MySQL via Testcontainers + production filter chain), `B2BOrderControllerTest` (6 slice tests with role boundary).

## Shortcuts / known gaps

- `Product` has no `unit` field in Stage 1, so B2B units default to `"kg"` for all flowers. A real unit field would require a separate decision/migration.
- `B2BOrderPageResponse.listOrders` does all filtering in memory (List-based repository methods) rather than via paginated `Pageable` queries. This is acceptable for Stage 1 volume but should be revisited for scale.
- Search/filter results are limited to what the repository returns; no database-level `LIKE` for seller search (in-memory substring match).
- `orderNo` generation uses epoch seconds + UUID suffix; collision probability negligible for Stage 1.

## Notes

- **Buyer inventory is not increased on delivery.** Only the seller's stock is deducted on accept and restored on cancel-after-accept. Delivery does not change any inventory row.
- **The settlement ledger is not integrated.** Payment stays `UNPAID`; e-bills, commission recording, and settlement cycles are Stage 2 and deliberately use dedicated B2B tables, not the Phase 10 settlement ledger.
- **Vendor role is `FLORIST`** (D-11). `FARMER` (Phase 11) is a distinct role and cannot act as a B2B buyer or seller.
- **Approval is `VendorProfile.status == APPROVED`**, enforced per-handler via `@RequiresApprovedVendor` where transactions require approval (D-13).
