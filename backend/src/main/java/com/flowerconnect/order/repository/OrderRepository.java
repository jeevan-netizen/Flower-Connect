package com.flowerconnect.order.repository;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
     * One page of the customer's orders in a single status, newest first.
     */
    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, Order.OrderStatus status,
                                                          Pageable pageable);

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

    // ------------------------------------------------------------------
    // Phase 6: vendor-scoped order lookup (task 6.4)
    // ------------------------------------------------------------------

    /**
     * One page of the authenticated vendor's own orders, newest first.
     */
    Page<Order> findByVendorIdOrderByCreatedAtDesc(Long vendorId, Pageable pageable);

    /**
     * One page of the vendor's orders in a single status, newest first.
     */
    Page<Order> findByVendorIdAndStatusOrderByCreatedAtDesc(Long vendorId, OrderStatus status,
                                                            Pageable pageable);

    /**
     * One page of the vendor's orders whose {@code createdAt} falls within the
     * half-open interval {@code [from, to)}, newest first. Bounds are expressed
     * in UTC wall time so they compare directly against the stored
     * {@code DATETIME(6)} timestamps.
     */
    Page<Order> findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long vendorId, LocalDateTime from,
                                                                      LocalDateTime to, Pageable pageable);

    /**
     * One page of the vendor's orders in a single status within a date range,
     * newest first.
     */
    Page<Order> findByVendorIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(Long vendorId, OrderStatus status,
                                                                               LocalDateTime from, LocalDateTime to,
                                                                               Pageable pageable);

    /**
     * All of the vendor's orders within a date range, unpaged: used to aggregate
     * a today's summary (counts by status, totals, expected COD cash).
     */
    List<Order> findByVendorIdAndCreatedAtBetween(Long vendorId, LocalDateTime from, LocalDateTime to);

    // ------------------------------------------------------------------
    // Phase 6: auto-reject job (task 6.5)
    // ------------------------------------------------------------------

    /**
     * PLACED orders created strictly before {@code before}, oldest first.
     * Used by the auto-reject job to select candidates whose age exceeds the
     * configured threshold. Bounded by the caller's {@code Pageable} so one
     * run never holds more than {@code autoRejectBatchSize} order locks.
     */
    Page<Order> findByStatusAndCreatedAtBefore(Order.OrderStatus status, LocalDateTime before,
                                               Pageable pageable);
}