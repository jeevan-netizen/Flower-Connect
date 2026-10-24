package com.flowerconnect.dispute.repository;

import com.flowerconnect.dispute.domain.Dispute;
import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.order.domain.Order;
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
 * Persistence for {@link Dispute} (plan Phase 8, task 8.4).
 */
@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    /**
     * The one dispute for the given order, if any. The unique constraint
     * {@code uq_disputes_order} guarantees at most one row.
     */
    Optional<Dispute> findByOrderId(Long orderId);

    /**
     * Page of disputes raised by the given customer, newest first.
     */
    Page<Dispute> findByRaisedByOrderByCreatedAtDesc(Long raisedById, Pageable pageable);

    /**
     * Page of disputes on orders belonging to the given vendor, newest first.
     */
    Page<Dispute> findByOrderVendorIdOrderByCreatedAtDesc(Long vendorId, Pageable pageable);

    /**
     * Page of disputes in a single status, newest first. Used by the admin
     * queue with optional status filter.
     */
    Page<Dispute> findByStatusOrderByCreatedAtDesc(DisputeStatus status, Pageable pageable);

    /**
     * Page of disputes in a single status within a date range, newest first.
     * Used by the admin queue with status and date filters.
     */
    Page<Dispute> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            DisputeStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * All disputes within a date range, unpaged. Used for admin analytics
     * or exports if needed later.
     */
    List<Dispute> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * Checks whether a dispute already exists for the given order. Used to
     * fail fast before attempting an insert that would hit the unique
     * constraint, so the error message is clean rather than a constraint
     * violation.
     */
    @Query("SELECT COUNT(d) > 0 FROM Dispute d WHERE d.order.id = :orderId")
    boolean existsByOrderId(@Param("orderId") Long orderId);

    /**
     * Finds a dispute by ID with a pessimistic write lock for concurrency
     * control during resolution. The lock is held until the transaction commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Dispute d WHERE d.id = :id")
    Optional<Dispute> findByIdForUpdate(@Param("id") Long id);
}