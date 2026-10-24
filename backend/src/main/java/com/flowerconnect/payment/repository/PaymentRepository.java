package com.flowerconnect.payment.repository;

import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.Payment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link Payment} rows (plan Phase 7, task 7.2).
 *
 * <p>There is one row per order, so {@code findByOrderId} is the primary
 * access path. The order row is locked separately by the state machine; the
 * payment row is locked explicitly here when a status change must be
 * serialized against concurrent collection attempts.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findByPaymentGroupId(String paymentGroupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    /**
     * One page of payments matching the given status and optional filters,
     * ordered by {@code created_at} desc (newest first). Used by the admin
     * refund queue to list {@code REFUND_PENDING} payments.
     *
     * @param status required payment status filter
     * @param vendorId optional vendor profile id (joined via order)
     * @param fromDate optional lower bound on {@code payments.created_at} (inclusive)
     * @param toDate optional upper bound on {@code payments.created_at} (exclusive)
     * @param pageable pagination
     */
    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = :status
          AND (:vendorId IS NULL OR p.order.vendor.id = :vendorId)
          AND (:fromDate IS NULL OR p.createdAt >= :fromDate)
          AND (:toDate IS NULL OR p.createdAt < :toDate)
        ORDER BY p.createdAt DESC
        """)
    Page<Payment> findByStatus(@Param("status") PaymentStatus status,
                               @Param("vendorId") Long vendorId,
                               @Param("fromDate") LocalDateTime fromDate,
                               @Param("toDate") LocalDateTime toDate,
                               Pageable pageable);
}
