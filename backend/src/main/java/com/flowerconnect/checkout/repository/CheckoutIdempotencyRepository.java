package com.flowerconnect.checkout.repository;

import com.flowerconnect.checkout.domain.CheckoutIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Checkout idempotency record persistence.
 *
 * <p>The {@code uq_checkout_idempotency_user_client} constraint on
 * {@code (user_id, client_order_id)} is the primary idempotency backstop.
 * The record is created with {@code completed_at = NULL} at the start of
 * a checkout attempt, providing a lockable row for concurrent first requests.
 * On successful completion, {@code completed_at} is set and the fingerprint
 * is stored for replay verification.
 */
@Repository
public interface CheckoutIdempotencyRepository extends JpaRepository<CheckoutIdempotency, Long> {

    /**
     * Finds an idempotency record by user and client order id with a pessimistic write lock.
     * Used to serialize concurrent first-time checkout attempts with the same key.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CheckoutIdempotency c WHERE c.userId = :userId AND c.clientOrderId = :clientOrderId")
    Optional<CheckoutIdempotency> findByUserIdAndClientOrderIdForUpdate(@Param("userId") Long userId,
                                                                         @Param("clientOrderId") String clientOrderId);

    /**
     * Finds an idempotency record without a lock. Used for replay reads.
     */
    @Query("SELECT c FROM CheckoutIdempotency c WHERE c.userId = :userId AND c.clientOrderId = :clientOrderId")
    Optional<CheckoutIdempotency> findByUserIdAndClientOrderId(@Param("userId") Long userId,
                                                                @Param("clientOrderId") String clientOrderId);
}