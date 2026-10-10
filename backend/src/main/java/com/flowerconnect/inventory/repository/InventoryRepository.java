package com.flowerconnect.inventory.repository;

import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Inventory persistence (plan task 3.3).
 *
 * <p>The unique constraint on {@code product_id} guarantees exactly
 * one row per product; the CHECK constraints in
 * {@code V10__inventory.sql} guarantee the non-negativity and
 * {@code reserved_quantity <= quantity} invariants for every writer.
 *
 * <p>{@link #findByProductIdForUpdate} takes a pessimistic write
 * lock on the row. Plan section 6.2 requires locked, ordered
 * access for every stock mutation (checkout, accept, adjustment,
 * POS sale) so concurrent operations on the same product serialise
 * instead of interleaving; the lock is acquired here, and callers
 * lock rows in ascending product-id order to avoid deadlocks.
 *
 * <p>{@code JpaSpecificationExecutor} backs the vendor-scoped
 * low-stock listing added in task 3.6. That listing selects rows
 * where {@code quantity − reserved_quantity <= low_stock_threshold},
 * which is an expression over three columns rather than a field, so
 * a derived query method cannot express it; see
 * {@link com.flowerconnect.inventory.specification.InventorySpecifications}.
 *
 * <p>{@link #findExpiredNeedingAction} backs the expiry sweep (task
 * 3.7). See {@code InventoryExpiryService} for why the predicate is
 * shaped the way it is.
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long>,
        JpaSpecificationExecutor<Inventory> {

    Optional<Inventory> findByProductId(Long productId);

    /**
     * Loads the stock rows of several products in one query, so a read that
     * reports availability for a whole page of products — the public
     * storefront's {@code inStock} flag (plan task 4.5) — costs one lookup
     * instead of one per row.
     */
    List<Inventory> findByProductIdIn(Collection<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(Long productId);

    /**
     * Rows the expiry sweep has work to do: an expiry date that has
     * passed <em>and</em> something still left to change — available
     * stock to write off, or a product still listed as
     * {@code ACTIVE} to delist.
     *
     * <p>The second condition is what makes the sweep idempotent.
     * A derived method cannot express "expired date AND (an
     * expression over two columns OR a status on the associated
     * product)", and a query that simply selected every expired row
     * would re-select the rows the previous run already processed
     * forever. With this predicate a processed row stops matching
     * the moment it is written: the write-off drops
     * {@code quantity} to {@code reserved_quantity}, and the delist
     * moves the product off {@code ACTIVE}. Neither is reversible
     * by the sweep, so the second run has nothing to redo.
     *
     * <p>{@code expiryDate < :today} rather than {@code <=}: the
     * stored date is the last day the stock may be used, so the
     * write-off starts on the following day.
     *
     * <p>{@code :activeStatus} is a parameter rather than a literal so
     * the enum constant is bound with its own type, matching the
     * ENUM column (D-20).
     *
     * <p>The caller supplies the row cap through the {@link Pageable}. The ordering
     * stays in the query because it is not a presentation sort: it is the lock order
     * plan section 6.2 requires of every writer, so it must not be something a
     * caller can override.
     */
    @Query("""
            SELECT i FROM Inventory i
            WHERE i.expiryDate IS NOT NULL
              AND i.expiryDate < :today
              AND (i.quantity > i.reservedQuantity OR i.product.status = :activeStatus)
            ORDER BY i.product.id ASC
            """)
    List<Inventory> findExpiredNeedingAction(@Param("today") LocalDate today,
                                             @Param("activeStatus") ProductStatus activeStatus,
                                             Pageable pageable);
}
