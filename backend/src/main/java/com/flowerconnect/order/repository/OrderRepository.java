package com.flowerconnect.order.repository;

import com.flowerconnect.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Order persistence (plan Phase 5).
 *
 * <p>Idempotency is now handled by the {@code checkout_idempotency} table
 * (task 5.4 remediation). The {@code client_order_id} column on orders is
 * retained for lookups on the replay path.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Finds all orders for a user with the given clientOrderId.
     * Used to build the idempotent replay response after the idempotency
     * table confirms the checkout was already completed.
     */
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.clientOrderId = :clientOrderId")
    List<Order> findByUserIdAndClientOrderId(@Param("userId") Long userId,
                                             @Param("clientOrderId") String clientOrderId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.vendor.id = :vendorId AND o.slotStart = :slotStart AND o.status IN ('PLACED', 'CONFIRMED')")
    long countBookedOrdersForSlot(@Param("vendorId") Long vendorId,
                                  @Param("slotStart") java.time.LocalDateTime slotStart);
}