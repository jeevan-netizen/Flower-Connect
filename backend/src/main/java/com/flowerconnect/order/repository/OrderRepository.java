package com.flowerconnect.order.repository;

import com.flowerconnect.order.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    /**
     * Locks the order row with {@code SELECT ... FOR UPDATE} so the state
     * machine can re-check the current status under the lock before
     * transitioning. Taken before any inventory row lock to maintain the
     * agreed order-row-then-inventory lock order (plan section 6.2,
     * decision D-24).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.vendor.id = :vendorId AND o.slotStart = :slotStart AND o.status IN ('PLACED', 'CONFIRMED')")
    long countBookedOrdersForSlot(@Param("vendorId") Long vendorId,
                                  @Param("slotStart") java.time.LocalDateTime slotStart);
}