package com.flowerconnect.inventory.repository;

import com.flowerconnect.inventory.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stock movement log persistence (plan task 3.4).
 *
 * <p>The log is append-only: rows are written once and never
 * updated or deleted, so the repository exposes reads and inserts
 * only. The {@code (product_id, created_at)} index backs the hot
 * path — the per-product history, newest first.
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("SELECT m FROM StockMovement m WHERE m.product.id = :productId ORDER BY m.createdAt DESC, m.id DESC")
    List<StockMovement> findByProductIdOrderByCreatedAtDescIdDesc(Long productId);

    long countByProductId(Long productId);
}
