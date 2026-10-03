package com.flowerconnect.inventory.repository;

import com.flowerconnect.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(Long productId);
}
