package com.flowerconnect.order.repository;

import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.domain.Order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Order status history persistence (plan Phase 5, task 5.4).
 *
 * <p>Append-only: rows are never updated or deleted. The {@code idx_osh_order}
 * index supports the per-order timeline view (Phase 6.3).
 */
@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    /**
     * The most recent history entry for an order entering a given status.
     * Used by the auto-reject job to measure age from entry into {@code PLACED}
     * and by the dispute service to measure the window from entry into
     * {@code DELIVERED}.
     */
    Optional<OrderStatusHistory> findFirstByOrderIdAndToStatusOrderByCreatedAtDesc(
            Long orderId, OrderStatus toStatus);
}
