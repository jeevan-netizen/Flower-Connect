package com.flowerconnect.order.repository;

import com.flowerconnect.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Order persistence (plan Phase 5).
 *
 * <p>The {@code uq_orders_client_order_id} constraint on
 * {@code (user_id, client_order_id)} is the idempotency backstop (decision
 * E-1): a concurrent replay that slips past the service's
 * {@code SELECT ... FOR UPDATE} check still cannot create a duplicate at the
 * database. NULL keys are treated as distinct by MySQL's unique index, so
 * POS orders (Phase 11) that omit the key coexist.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.clientOrderId = :clientOrderId")
    Optional<Order> findByUserIdAndClientOrderIdForUpdate(@Param("userId") Long userId,
                                                            @Param("clientOrderId") String clientOrderId);

    long countByUserIdAndClientOrderId(Long userId, String clientOrderId);
}
