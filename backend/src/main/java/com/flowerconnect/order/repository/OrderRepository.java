package com.flowerconnect.order.repository;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.OrderStatusHistory;
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

    @Query("SELECT COUNT(o) FROM Order o WHERE o.vendor.id = :vendorId AND o.slotStart = :slotStart AND o.status IN ('PLACED', 'CONFIRMED', 'PENDING_PAYMENT')")
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
     * One page of the vendor's own orders excluding {@link OrderStatus#PENDING_PAYMENT},
     * newest first. Orders that are still awaiting payment are not yet
     * actionable by the vendor and are hidden from vendor lists, board queries
     * and summaries until payment succeeds and the order transitions to
     * {@code PLACED} (Phase 7, task 7.4).
     */
    Page<Order> findByVendorIdAndStatusNotOrderByCreatedAtDesc(Long vendorId, OrderStatus status,
                                                               Pageable pageable);

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
    // Phase 9: notification recipient resolution (task 9.1)
    // ------------------------------------------------------------------

    /**
     * Resolves the customer (user) id for an order as a scalar, avoiding lazy
     * loading of the order entity in async notification listeners.
     */
    @Query("SELECT o.user.id FROM Order o WHERE o.id = :id")
    Optional<Long> findCustomerIdByOrderId(@Param("id") Long id);

    /**
     * Resolves the vendor user id for an order as a scalar, avoiding lazy
     * loading of the order and vendor profile entities in async notification
     * listeners. Navigates order -> vendor_profile -> user.
     */
    @Query("SELECT o.vendor.user.id FROM Order o WHERE o.id = :id")
    Optional<Long> findVendorUserIdByOrderId(@Param("id") Long id);

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

    /**
     * PLACED orders whose entry into {@code PLACED} (the {@code order_status_history}
     * row where {@code to_status = PLACED}) is strictly before {@code before},
     * oldest first. Used by the auto-reject job so age is measured from the
     * {@code PLACED} transition timestamp rather than {@code orders.created_at}.
     * Bounded by the caller's {@code Pageable}.
     */
    @Query("SELECT DISTINCT o FROM Order o WHERE o.status = 'PLACED' AND o.id IN " +
            "(SELECT osh.order.id FROM OrderStatusHistory osh " +
            " WHERE osh.toStatus = 'PLACED' AND osh.createdAt < :before)")
    Page<Order> findPlacedStaleBefore(@Param("before") LocalDateTime before, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = 'DELIVERED' AND NOT EXISTS (SELECT c FROM Commission c WHERE c.order.id = o.id)")
    List<Order> findDeliveredOrdersWithoutCommission();
}