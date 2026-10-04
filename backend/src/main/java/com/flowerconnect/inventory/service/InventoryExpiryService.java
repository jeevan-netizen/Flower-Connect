package com.flowerconnect.inventory.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Expires perishable stock (plan task 3.7: "expired stock → {@code WASTE}
 * movement and product delisted").
 *
 * <p>The sweep is the only writer in the system with no authenticated
 * caller, so four rules are decided here rather than left to the caller:
 *
 * <ul>
 *   <li><b>Which day counts as expired</b> — {@code expiryDate <
 *       today}, where {@code today} is
 *       {@link LocalDate#now(Clock) LocalDate.now(clock)} on the
 *       injected {@link Clock}. The stored date is the last day the
 *       stock may be used, so stock is written off from the following
 *       day. Nothing calls {@code Instant.now()}: the sweep is the one
 *       job whose behaviour must be provable with a fake clock (plan
 *       section 3, and the plan's own definition of done for task
 *       3.7).</li>
 *   <li><b>Reserved units are not written off</b> — only
 *       {@code available = quantity − reservedQuantity} is removed,
 *       leaving {@code quantity = reservedQuantity}. Units in
 *       {@code reservedQuantity} are promised to orders that have not
 *       been accepted yet, and plan section 6.2 makes the vendor's
 *       accept re-check {@code quantity >= q} precisely because an
 *       expiry write-off may have reduced the level. Taking reserved
 *       units as waste as well would break the
 *       {@code reserved_quantity <= quantity} invariant the database
 *       enforces and would silently invalidate pending orders.</li>
 *   <li><b>Only {@code ACTIVE} is delisted</b> — {@code DRAFT},
 *       {@code INACTIVE} and {@code ARCHIVED} are already off the
 *       storefront, so rewriting them would be a no-op write that
 *       reports a change that did not happen. A vendor who
 *       reactivates a delisted product restores stock and an expiry
 *       date in the same request, and the next sweep takes it out
 *       again: the vendor can therefore re-list an expired product by
 *       replacing its expiry date, which is the only honest way to say
 *       "this is not expired stock".</li>
 *   <li><b>The movement has no actor</b> — there is no authenticated
 *       user behind a scheduled job, so {@code actor_user_id} and the
 *       reference fields stay null and the reason states what
 *       happened. The alternative, attributing the write-off to
 *       whichever admin last touched the product, would be a
 *       fabricated audit trail.</li>
 * </ul>
 *
 * <p><b>Ordering and locking.</b> Candidates are selected in ascending
 * product-id order and then locked one at a time through
 * {@link InventoryRepository#findByProductIdForUpdate(Long)}, which is
 * the lock order plan section 6.2 requires and the same order the
 * vendor stock path takes. The unlocked selection is only a candidate
 * list: between selecting a row and locking it, a vendor may have
 * corrected the expiry date, adjusted the level to zero, or already
 * delisted the product, so every condition is re-checked under the
 * lock before anything is written.
 *
 * <p><b>One bounded transaction.</b> A sweep handles at most
 * {@code app.expiry-sweep-max-rows} rows and commits once. Taking the
 * locks in a single transaction is what makes the selection-then-lock
 * sequence safe against another sweep running concurrently, and the
 * cap bounds how long that transaction can hold row locks. A backlog
 * larger than the cap is finished by later runs rather than in one
 * long transaction, which is safe because the candidate predicate
 * excludes rows that have already been processed — the leftover rows
 * are the ones the next run has to do anyway.
 *
 * <p>Invariants are enforced in the service as well as in the database
 * (D-12): {@code quantity} is set to {@code reservedQuantity}, never
 * below it, so {@code ck_inventory_reserved_le_quantity} is a backstop
 * rather than the mechanism.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryExpiryService {

    /**
     * Written into every {@code WASTE} movement the sweep creates. It
     * states the expiry date that was passed and the day the sweep ran,
     * so a movement is self-describing without a reference document.
     */
    static String expiryReason(LocalDate expiryDate, LocalDate today) {
        return "Expired stock write-off: expiry date " + expiryDate + " has passed (sweep date " + today + ")";
    }

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final Clock clock;
    private final AppProperties appProperties;

    /**
     * What one sweep did. Returned rather than logged only, so the
     * scheduler can report a sweep that found nothing, and so a test
     * can assert the outcome instead of re-deriving it from the
     * database.
     *
     * @param candidates        rows selected for processing
     * @param productsWrittenOff products that lost available stock, one movement each
     * @param productsDelisted  products moved off {@code ACTIVE}
     */
    public record SweepResult(int candidates, int productsWrittenOff, int productsDelisted) {

        static SweepResult empty() {
            return new SweepResult(0, 0, 0);
        }
    }

    /**
     * Writes off every available unit of stock whose expiry date has
     * passed and delists the affected products. Called by
     * {@code InventoryExpiryScheduler}; separated from it so the rule
     * can be exercised directly with a fake clock.
     */
    @Transactional
    public SweepResult sweepExpiredStock() {
        LocalDate today = LocalDate.now(clock);
        List<Inventory> candidates = findCandidates(today);

        if (candidates.isEmpty()) {
            log.debug("Expiry sweep for {} found nothing to do", today);
            return SweepResult.empty();
        }

        int writtenOff = 0;
        int delisted = 0;
        for (Inventory candidate : candidates) {
            Outcome outcome = expire(candidate, today);
            writtenOff += outcome.wroteOff() ? 1 : 0;
            delisted += outcome.delisted() ? 1 : 0;
        }

        if (candidates.size() >= batchSize()) {
            log.warn("Expiry sweep for {} examined the configured maximum of {} row(s); "
                    + "any remaining expired stock is handled by the next run", today, candidates.size());
        }
        log.info("Expiry sweep for {} wrote off {} product(s) and delisted {} product(s)",
                today, writtenOff, delisted);
        return new SweepResult(candidates.size(), writtenOff, delisted);
    }

    // ------------------------------------------------------------------
    // Steps
    // ------------------------------------------------------------------

    /**
     * The unlocked candidate read. Ordered by product id in the query
     * and capped here, so the locks that follow are taken in the order
     * plan section 6.2 mandates and the transaction cannot run away
     * over a large backlog.
     */
    private List<Inventory> findCandidates(LocalDate today) {
        return inventoryRepository.findExpiredNeedingAction(today, ProductStatus.ACTIVE,
                PageRequest.of(0, batchSize()));
    }

    private record Outcome(boolean wroteOff, boolean delisted) {

        private static final Outcome NOTHING = new Outcome(false, false);
    }

    /**
     * Locks the row and re-checks every condition under the lock. A
     * candidate that no longer needs work is skipped rather than
     * written to: reporting a write-off that moved no stock would
     * append a movement row claiming a change that never happened.
     */
    private Outcome expire(Inventory candidate, LocalDate today) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(candidate.getProduct().getId())
                .orElse(null);
        if (inventory == null) {
            log.warn("Inventory row {} vanished between selection and lock; skipping", candidate.getId());
            return Outcome.NOTHING;
        }

        LocalDate expiryDate = inventory.getExpiryDate();
        if (expiryDate == null || !expiryDate.isBefore(today)) {
            log.debug("Product {} is no longer expired ({} vs {}); skipping",
                    candidate.getProduct().getId(), expiryDate, today);
            return Outcome.NOTHING;
        }

        boolean wroteOff = writeOffAvailableStock(inventory, expiryDate, today);
        boolean delisted = delistIfActive(inventory);
        return new Outcome(wroteOff, delisted);
    }

    /**
     * Removes only the available units, leaving {@code quantity} at
     * {@code reservedQuantity}, and appends the single movement that
     * records it. A row with nothing available is left alone: a
     * {@code WASTE} row with a zero delta would be a movement
     * recording no change, which the vendor's own adjustment path
     * refuses to write (see {@code InventoryService.adjust}).
     */
    private boolean writeOffAvailableStock(Inventory inventory, LocalDate expiryDate, LocalDate today) {
        int available = inventory.getAvailable();
        if (available <= 0) {
            return false;
        }
        inventory.setQuantity(inventory.getReservedQuantity());
        inventoryRepository.saveAndFlush(inventory);

        stockMovementRepository.save(StockMovement.builder()
                .product(inventory.getProduct())
                .movementType(MovementType.WASTE)
                .quantityDelta(-available)
                .reason(expiryReason(expiryDate, today))
                .build());

        log.info("Expiry write-off of {} unit(s) on product {} (expiry date {}, reserved {} unit(s) kept)",
                available, inventory.getProduct().getId(), expiryDate, inventory.getReservedQuantity());
        return true;
    }

    /**
     * {@code ACTIVE → INACTIVE} and nothing else. {@code ProductService.deactivate}
     * is the vendor-facing route to the same state and is not reused
     * here: it resolves the vendor from a JWT subject and audits a
     * human decision, neither of which exists for a scheduled sweep.
     */
    private boolean delistIfActive(Inventory inventory) {
        Product product = inventory.getProduct();
        if (product.getStatus() != ProductStatus.ACTIVE) {
            return false;
        }
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        log.info("Delisted product {} because its stock expired", product.getId());
        return true;
    }

    /**
     * At least one row per sweep: a configured zero or negative cap
     * would otherwise silently disable the job, which is a worse
     * failure than processing more rows than intended.
     */
    private int batchSize() {
        return Math.max(1, appProperties.getExpirySweepMaxRows());
    }
}
