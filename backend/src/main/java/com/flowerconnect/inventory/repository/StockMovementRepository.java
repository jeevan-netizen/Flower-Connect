package com.flowerconnect.inventory.repository;

import com.flowerconnect.inventory.domain.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 *
 * <p>{@link #findByProductId(Long, Pageable)} is the paginated form
 * of that history (task 3.6). The sort is supplied by the caller as
 * a {@link Pageable} rather than written into an {@code ORDER BY}, so
 * pages stay stable for rows written in the same instant; the
 * unpaged {@link #findByProductIdOrderByCreatedAtDescIdDesc(Long)}
 * remains for callers that want the whole ordered list.
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("SELECT m FROM StockMovement m WHERE m.product.id = :productId ORDER BY m.createdAt DESC, m.id DESC")
    List<StockMovement> findByProductIdOrderByCreatedAtDescIdDesc(Long productId);

    Page<StockMovement> findByProductId(Long productId, Pageable pageable);

    long countByProductId(Long productId);
}
