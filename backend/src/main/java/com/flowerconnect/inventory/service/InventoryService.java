package com.flowerconnect.inventory.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.dto.InventorySummary;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockDelta;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import com.flowerconnect.inventory.dto.ExpiryDateRequest;
import com.flowerconnect.inventory.dto.LowStockPageResponse;
import com.flowerconnect.inventory.dto.LowStockThresholdRequest;
import com.flowerconnect.inventory.dto.StockAdjustmentRequest;
import com.flowerconnect.inventory.dto.StockInRequest;
import com.flowerconnect.inventory.dto.StockMovementPageResponse;
import com.flowerconnect.inventory.dto.StockOutRequest;
import com.flowerconnect.inventory.dto.StockWriteOffRequest;
import com.flowerconnect.inventory.mapper.StockMovementMapper;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.inventory.specification.InventorySpecifications;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vendor inventory operations (plan task 3.6).
 *
 * <p>Five rules are owned here, and each of them is load-bearing for a
 * later phase:
 *
 * <ul>
 *   <li><b>Availability</b> — {@code available = quantity −
 *       reservedQuantity}, computed by {@link Inventory#getAvailable()}
 *       and never stored. Stock is <em>not</em> deducted at checkout
 *       (plan section 6.2): it moves into {@code reservedQuantity}, so
 *       an operation that would take {@code quantity} below
 *       {@code reservedQuantity} would consume units already promised
 *       to pending orders. Every operation that computes a new
 *       quantity checks it first and refuses with 409
 *       ({@code INSUFFICIENT_STOCK}) rather than clamping, because a
 *       silent clamp would leave the vendor's books disagreeing with
 *       the orders they have taken.</li>
 *   <li><b>One movement per change</b> — each stock mutation updates
 *       the level and appends exactly one {@code stock_movements} row
 *       in the same transaction. The log records <em>changes</em>, so
 *       the two writes are inseparable: a level without its movement
 *       is unauditable history, and a movement without its level is a
 *       claim that never happened. Changing the alert threshold or the
 *       expiry date is not a stock change and writes nothing.</li>
 *   <li><b>The actor is the authenticated identity</b> — the movement's
 *       {@code actor_user_id} is resolved from the JWT subject inside
 *       the service. No route accepts an actor, so a vendor cannot
 *       attribute a write-off to a colleague, and a system-initiated
 *       movement (the expiry scheduler, task 3.7) is the only way to
 *       get a null actor.</li>
 *   <li><b>Ownership</b> — every method resolves the vendor from the
 *       JWT subject, never from a parameter. A foreign product is 403
 *       and a missing one 404 (plan section 3).</li>
 *   <li><b>Locking</b> — every write takes the inventory row's
 *       pessimistic write lock. See
 *       {@link InventoryRepository#findByProductIdForUpdate(Long)} and
 *       docs/decisions.md (D-24).</li>
 * </ul>
 *
 * <p>The two invariants are enforced in two layers (D-12): Bean
 * Validation on the request DTOs for the client-facing bounds, and a
 * service check for every rule a non-HTTP writer could bypass — a
 * non-positive quantity, a zero adjustment, and the reserved-quantity
 * floor. The MySQL CHECK constraints in {@code V10__inventory.sql} are
 * the backstop, but the service returns a specific 409 before the
 * database is reached, so the caller learns what was wrong.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final StockMovementMapper mapper;

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /**
     * Current stock level of a product the calling vendor owns.
     */
    @Transactional(readOnly = true)
    public InventorySummary get(String vendorEmail, Long productId) {
        Product product = requireOwnedProduct(vendorEmail, productId);
        return mapper.toSummary(requireInventory(product));
    }

    /**
     * One page of the vendor's low-stock products: those whose
     * availability has reached or fallen below their own threshold.
     * Newest first is not useful here (the condition, not the age, is
     * the signal), so rows are ordered by the inventory id — stable,
     * index-served, and one row per product by construction.
     */
    @Transactional(readOnly = true)
    public LowStockPageResponse listLowStock(String vendorEmail, int page, int size) {
        VendorProfile vendor = requireVendor(vendorEmail);
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                clampSize(size),
                Sort.by("id").ascending());

        Specification<Inventory> spec = InventorySpecifications.forVendor(vendor.getId())
                .and(InventorySpecifications.atOrBelowThreshold());
        Page<Inventory> result = inventoryRepository.findAll(spec, pageable);

        return LowStockPageResponse.builder()
                .content(result.getContent().stream().map(mapper::toLowStockEntry).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    /**
     * One page of a product's movement history, newest first. The
     * product must belong to the calling vendor.
     */
    @Transactional(readOnly = true)
    public StockMovementPageResponse listMovements(String vendorEmail, Long productId, int page, int size) {
        requireOwnedProduct(vendorEmail, productId);
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                clampSize(size),
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));

        Page<StockMovement> result = stockMovementRepository.findByProductId(productId, pageable);

        return StockMovementPageResponse.builder()
                .content(result.getContent().stream().map(mapper::toMovementResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    // ------------------------------------------------------------------
    // Stock movements
    // ------------------------------------------------------------------

    /**
     * Records units received ({@code STOCK_IN}, positive delta).
     */
    @Transactional
    public InventorySummary stockIn(String vendorEmail, Long productId, StockInRequest request) {
        return applyStockChange(vendorEmail, productId,
                requirePositive(request.getQuantity(), "Quantity must be greater than zero"),
                MovementType.STOCK_IN,
                request.getReason());
    }

    /**
     * Records units removed at the vendor's request ({@code STOCK_OUT},
     * negative delta). Refused with 409 if the removal would drop
     * {@code quantity} below the reserved quantity.
     */
    @Transactional
    public InventorySummary stockOut(String vendorEmail, Long productId, StockOutRequest request) {
        return applyStockChange(vendorEmail, productId,
                -requirePositive(request.getQuantity(), "Quantity must be greater than zero"),
                MovementType.STOCK_OUT,
                request.getReason());
    }

    /**
     * Applies a signed correction to the recorded quantity
     * ({@code ADJUSTMENT}). The delta is signed rather than absolute
     * because that is what the movement log stores, so an adjustment
     * reads in the history exactly like every other movement. A zero
     * correction is refused: it would append a row recording no change.
     */
    @Transactional
    public InventorySummary adjust(String vendorEmail, Long productId, StockAdjustmentRequest request) {
        int delta = requireNonZero(request.getQuantity());
        requireReason(request.getReason());
        return applyStockChange(vendorEmail, productId, delta, MovementType.ADJUSTMENT, request.getReason());
    }

    /**
     * Writes off stock that exists in the books and not on the shelf
     * ({@code WASTE}, negative delta) — the same loss the expiry
     * scheduler records automatically, entered by hand.
     */
    @Transactional
    public InventorySummary writeOff(String vendorEmail, Long productId, StockWriteOffRequest request) {
        requireReason(request.getReason());
        return applyStockChange(vendorEmail, productId,
                -requirePositive(request.getQuantity(), "Quantity must be greater than zero"),
                MovementType.WASTE,
                request.getReason());
    }

    // ------------------------------------------------------------------
    // Alert settings (not stock changes)
    // ------------------------------------------------------------------

    /**
     * Replaces the low-stock alert threshold. This is a setting, not a
     * stock change: no movement is written, and the same row lock is
     * taken anyway, because Hibernate updates whole rows — without the
     * lock a concurrent stock change could be overwritten with the
     * quantity this transaction read before it.
     */
    @Transactional
    public InventorySummary updateLowStockThreshold(String vendorEmail, Long productId,
                                                    LowStockThresholdRequest request) {
        Product product = requireOwnedProduct(vendorEmail, productId);
        Inventory inventory = requireLockedInventory(product);
        inventory.setLowStockThreshold(request.getLowStockThreshold());
        inventoryRepository.saveAndFlush(inventory);
        log.info("Updated low-stock threshold of product {} to {}", productId,
                request.getLowStockThreshold());
        return mapper.toSummary(inventory);
    }

    /**
     * Replaces the expiry date, or clears it with {@code null}. As
     * above: no movement is written, and the expiry scheduler (task
     * 3.7) is what turns a reached date into a {@code WASTE} movement.
     */
    @Transactional
    public InventorySummary updateExpiryDate(String vendorEmail, Long productId, ExpiryDateRequest request) {
        Product product = requireOwnedProduct(vendorEmail, productId);
        Inventory inventory = requireLockedInventory(product);
        LocalDate expiryDate = request.getExpiryDate();
        inventory.setExpiryDate(expiryDate);
        inventoryRepository.saveAndFlush(inventory);
        log.info("Updated expiry date of product {} to {}", productId, expiryDate);
        return mapper.toSummary(inventory);
    }

    // ------------------------------------------------------------------
    // The stock mutation template
    // ------------------------------------------------------------------

    /**
     * The single write path every stock mutation goes through, so the
     * five steps are impossible to vary per operation:
     *
     * <ol>
     *   <li><b>check ownership, before locking</b> — the product is
     *       resolved and compared against the calling vendor first, so a
     *       foreign vendor is refused without ever taking a row lock on
     *       someone else's inventory. Locking first would let any vendor
     *       stall any product's stock by holding its row.</li>
     *   <li><b>lock the inventory row</b> —
     *       {@link InventoryRepository#findByProductIdForUpdate(Long)}
     *       issues {@code SELECT ... FOR UPDATE}. Concurrent mutations of
     *       the same product then run one after another instead of
     *       reading the same level and losing one another's write.</li>
     *   <li><b>validate against the locked quantities</b> — the floor
     *       check reads the level the lock just serialised, not one read
     *       before it.</li>
     *   <li><b>write the new level</b>.</li>
     *   <li><b>append exactly one movement</b>, carrying the signed
     *       delta, the caller-supplied reason and the authenticated
     *       actor.</li>
     * </ol>
     *
     * <p>Steps 4 and 5 are one transaction, so a failure in either
     * leaves neither.
     *
     * <p>{@code referenceId}/{@code referenceType} stay null: they point
     * at the document that caused a movement (an order, a purchase
     * order), and a manual stock call has no such document. Writing the
     * product id there would put a self-reference into a field whose
     * whole purpose is to identify something else.
     */
    private InventorySummary applyStockChange(String vendorEmail, Long productId, int delta,
                                              MovementType type, String reason) {
        Product product = requireOwnedProduct(vendorEmail, productId);
        User actor = requireActor(vendorEmail);
        Inventory inventory = requireLockedInventory(product);

        int newQuantity = inventory.getQuantity() + delta;
        requireQuantityNotBelowReserved(inventory, newQuantity);
        inventory.setQuantity(newQuantity);
        inventoryRepository.saveAndFlush(inventory);

        stockMovementRepository.save(StockMovement.builder()
                .product(product)
                .movementType(type)
                .quantityDelta(delta)
                .reason(normalize(reason))
                .actor(actor)
                .build());

        log.info("{} of {} unit(s) on product {} by {} (quantity {} -> {})",
                type, delta, productId, vendorEmail, inventory.getQuantity() - delta, newQuantity);
        return mapper.toSummary(inventory);
    }

    // ------------------------------------------------------------------
    // Order state machine stock side effects (plan Phase 6, task 6.1)
    // ------------------------------------------------------------------

    /**
     * Commits reserved stock for a vendor accepting an order
     * ({@code PLACED &#8594; CONFIRMED}): for every item,
     * {@code quantity -= q} and {@code reserved_quantity -= q} with a
     * {@code SALE_ONLINE} movement. Re-checks {@code quantity >= q} under
     * the lock and refuses with 409 if stock was written off or adjusted
     * since checkout. Inventory rows are locked in ascending product-id
     * order to avoid deadlocks.
     */
    @Transactional
    public void commitReservedStock(List<StockDelta> deltas, User actor) {
        Map<Long, Inventory> locked = lockInventoriesAscending(deltas);
        validateQuantityAboveReserved(locked, deltas);
        applyStockChanges(locked, deltas, MovementType.SALE_ONLINE, actor,
                "Order accepted - reserved stock committed");
    }

    /**
     * Releases reserved stock for an order cancelled or rejected before
     * acceptance ({@code PLACED &#8594; CANCELLED / REJECTED}): for every
     * item, {@code reserved_quantity -= q} with a {@code RELEASE}
     * movement. {@code quantity} is unchanged.
     */
    @Transactional
    public void releaseReservedStock(List<StockDelta> deltas, User actor) {
        Map<Long, Inventory> locked = lockInventoriesAscending(deltas);
        validateReservedAboveDelta(locked, deltas);
        applyStockChanges(locked, deltas, MovementType.RELEASE, actor,
                "Order cancelled/rejected - reservation released");
    }

    /**
     * Restocks a cancelled accepted order
     * ({@code CONFIRMED &#8594; CANCELLED}): for every item,
     * {@code quantity += q} with a {@code RESTOCK} movement.
     * {@code reserved_quantity} is unchanged.
     */
    @Transactional
    public void restockCancelledOrder(List<StockDelta> deltas, User actor) {
        Map<Long, Inventory> locked = lockInventoriesAscending(deltas);
        applyStockChanges(locked, deltas, MovementType.RESTOCK, actor,
                "Order cancelled - stock restocked");
    }

    /**
     * Deducts available stock for a POS walk-in sale (plan Phase 11, task 11.3).
     * For every item, {@code quantity -= q} with a {@code SALE_POS} movement.
     * Validates {@code available = quantity - reserved_quantity >= q} under the
     * lock so the sale never consumes units reserved for pending orders.
     * Inventory rows are locked in ascending product-id order to avoid
     * deadlocks, consistent with every other stock writer (D-24).
     *
     * @param deltas     one entry per product to deduct, quantity is positive
     * @param referenceId the POS transaction id, stored on each movement
     * @param referenceType {@code pos-transaction}
     * @param actor      the authenticated vendor
     */
    @Transactional
    public void deductPosStock(List<StockDelta> deltas, Long referenceId, String referenceType, User actor) {
        Map<Long, Inventory> locked = lockInventoriesAscending(deltas);
        validatePosAvailability(locked, deltas);
        applyPosStockChanges(locked, deltas, referenceId, referenceType, actor);
    }

    private void validatePosAvailability(Map<Long, Inventory> locked, List<StockDelta> deltas) {
        for (StockDelta delta : deltas) {
            Inventory inventory = locked.get(delta.productId());
            int available = inventory.getAvailable();
            if (available < delta.quantity()) {
                Product product = requireProduct(delta.productId());
                throw BusinessException.insufficientStock(
                        "Only " + available + " unit(s) of '" + product.getName() +
                                "' are available, " + delta.quantity() + " requested");
            }
        }
    }

    private void applyPosStockChanges(Map<Long, Inventory> locked, List<StockDelta> deltas,
                                      Long referenceId, String referenceType, User actor) {
        for (StockDelta delta : deltas) {
            Inventory inventory = locked.get(delta.productId());
            Product product = requireProduct(delta.productId());

            inventory.setQuantity(inventory.getQuantity() - delta.quantity());
            inventoryRepository.saveAndFlush(inventory);

            stockMovementRepository.save(StockMovement.builder()
                    .product(product)
                    .movementType(StockMovement.MovementType.SALE_POS)
                    .quantityDelta(-delta.quantity())
                    .reason("POS walk-in sale")
                    .referenceId(referenceId)
                    .referenceType(referenceType)
                    .actor(actor)
                    .build());
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /**
     * The reserved-quantity floor (plan section 6.2). Because
     * {@code reservedQuantity} is itself non-negative, this single check
     * also rejects any result below zero: there is no separate
     * "quantity must stay positive" test to keep in step.
     *
     * <p>Deliberately not a clamp. Clamping would report success for a
     * request that was not performed, and would leave the vendor's
     * recorded level disagreeing with the movements that led to it.
     */
    private void requireQuantityNotBelowReserved(Inventory inventory, int newQuantity) {
        if (newQuantity < inventory.getReservedQuantity()) {
            throw BusinessException.insufficientStock(String.format(
                    "Stock change refused: it would leave quantity at %d, below the %d reserved unit(s)",
                    newQuantity, inventory.getReservedQuantity()));
        }
    }

    private int requirePositive(Integer quantity, String message) {
        if (quantity == null || quantity <= 0) {
            throw BusinessException.badRequest(message);
        }
        return quantity;
    }

    private int requireNonZero(Integer quantity) {
        if (quantity == null || quantity == 0) {
            throw BusinessException.badRequest("Adjustment quantity must not be zero");
        }
        return quantity;
    }

    /**
     * Enforced in the service as well as on the DTO: the reason is the
     * only explanation an adjustment or a write-off will ever have, so a
     * non-HTTP writer must not be able to omit it (D-12).
     */
    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("A reason is required for a stock adjustment or write-off");
        }
    }

    /** Stores null rather than an empty string, so an absent reason is unambiguous in the log. */
    private String normalize(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /**
     * A missing product is a 404 and a foreign one a 403, checked in that
     * order, so the two stay distinguishable to the client (plan section
     * 3).
     */
    private Product requireOwnedProduct(String vendorEmail, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found"));
        VendorProfile vendor = requireVendor(vendorEmail);
        if (!product.getVendor().getId().equals(vendor.getId())) {
            throw BusinessException.forbidden("Product does not belong to this vendor");
        }
        return product;
    }

    private VendorProfile requireVendor(String vendorEmail) {
        return vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));
    }

    private User requireActor(String vendorEmail) {
        return userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
    }

    /**
     * A data-integrity backstop rather than an expected path: every
     * product gets its inventory row in the product's own transaction
     * (task 3.3), so a missing row means the invariant was broken
     * elsewhere.
     */
    private Inventory requireInventory(Product product) {
        return inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> BusinessException.notFound("Inventory not found"));
    }

    /**
     * The pessimistic write lock. Every write path goes through here,
     * including the two that change no quantity, because Hibernate issues
     * a whole-row {@code UPDATE}: an unlocked threshold write could
     * silently roll back a quantity change committed a moment earlier.
     */
    private Inventory requireLockedInventory(Product product) {
        return inventoryRepository.findByProductIdForUpdate(product.getId())
                .orElseThrow(() -> BusinessException.notFound("Inventory not found"));
    }

    /**
     * Locks the distinct product ids in the deltas in ascending order, exactly
     * as plan section 6.2 requires of every stock writer, so concurrent state
     * machine transitions on the same products serialise instead of deadlocking.
     */
    private Map<Long, Inventory> lockInventoriesAscending(List<StockDelta> deltas) {
        List<Long> productIds = deltas.stream()
                .map(StockDelta::productId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Inventory> locked = new LinkedHashMap<>(productIds.size());
        for (Long productId : productIds) {
            locked.put(productId, requireLockedInventoryById(productId));
        }
        return locked;
    }

    private Inventory requireLockedInventoryById(Long productId) {
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> BusinessException.notFound("Inventory not found for product " + productId));
    }

    private void validateQuantityAboveReserved(Map<Long, Inventory> locked, List<StockDelta> deltas) {
        for (StockDelta delta : deltas) {
            Inventory inventory = locked.get(delta.productId());
            if (inventory.getQuantity() < delta.quantity()) {
                Product product = requireProduct(delta.productId());
                throw BusinessException.insufficientStock(
                        "Cannot accept order for product '" + product.getName() +
                                "': " + inventory.getQuantity() + " unit(s) on hand, " +
                                delta.quantity() + " required");
            }
        }
    }

    private void validateReservedAboveDelta(Map<Long, Inventory> locked, List<StockDelta> deltas) {
        for (StockDelta delta : deltas) {
            Inventory inventory = locked.get(delta.productId());
            if (inventory.getReservedQuantity() < delta.quantity()) {
                Product product = requireProduct(delta.productId());
                throw BusinessException.insufficientStock(
                        "Cannot release reservation for product '" + product.getName() +
                                "': only " + inventory.getReservedQuantity() + " unit(s) reserved, " +
                                delta.quantity() + " requested");
            }
        }
    }

    private void applyStockChanges(Map<Long, Inventory> locked, List<StockDelta> deltas,
                                   StockMovement.MovementType type, User actor, String reason) {
        for (StockDelta delta : deltas) {
            Inventory inventory = locked.get(delta.productId());
            Product product = requireProduct(delta.productId());

            switch (type) {
                case SALE_ONLINE -> {
                    inventory.setQuantity(inventory.getQuantity() - delta.quantity());
                    inventory.setReservedQuantity(inventory.getReservedQuantity() - delta.quantity());
                }
                case RELEASE -> inventory.setReservedQuantity(inventory.getReservedQuantity() - delta.quantity());
                case RESTOCK -> inventory.setQuantity(inventory.getQuantity() + delta.quantity());
                default -> throw new IllegalArgumentException(
                        "Unsupported order stock movement type: " + type);
            }
            inventoryRepository.saveAndFlush(inventory);

            stockMovementRepository.save(StockMovement.builder()
                    .product(product)
                    .movementType(type)
                    .quantityDelta(deltaFor(type, delta.quantity()))
                    .reason(reason)
                    .actor(actor)
                    .build());
        }
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found: " + productId));
    }

    private int deltaFor(StockMovement.MovementType type, int quantity) {
        return switch (type) {
            case SALE_ONLINE, RELEASE -> -quantity;
            case RESTOCK -> quantity;
            default -> quantity;
        };
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
