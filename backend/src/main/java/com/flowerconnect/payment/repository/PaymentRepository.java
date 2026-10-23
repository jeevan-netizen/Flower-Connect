package com.flowerconnect.payment.repository;

import com.flowerconnect.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
