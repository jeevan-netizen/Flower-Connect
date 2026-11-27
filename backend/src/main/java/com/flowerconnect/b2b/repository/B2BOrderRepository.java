package com.flowerconnect.b2b.repository;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for B2B orders (Stage 1 of the B2B module).
 *
 * <p>Queries are scoped to the calling vendor as buyer or seller; the service
 * layer enforces that only a participant may read or act on an order.
 */
@Repository
public interface B2BOrderRepository
        extends JpaRepository<B2BOrder, Long>, JpaSpecificationExecutor<B2BOrder> {

    Optional<B2BOrder> findByOrderNo(String orderNo);

    List<B2BOrder> findByBuyerId(Long buyerId);

    List<B2BOrder> findBySellerId(Long sellerId);

    List<B2BOrder> findByBuyerIdAndStatus(Long buyerId, Status status);

    List<B2BOrder> findBySellerIdAndStatus(Long sellerId, Status status);

    @Query("""
            SELECT o FROM B2BOrder o
            WHERE o.seller.id = :sellerId
              AND o.buyer.id = :buyerId
              AND o.deliveredAt IS NOT NULL
            ORDER BY o.deliveredAt DESC
            """)
    List<B2BOrder> findBySellerAndBuyerWithDelivery(
            @Param("sellerId") Long sellerId,
            @Param("buyerId") Long buyerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM B2BOrder o WHERE o.id = :id")
    Optional<B2BOrder> findByIdForUpdate(@Param("id") Long id);

    /**
     * Returns the caller's delivered, unpaid orders for a buyer within an
     * inclusive delivered-at range that have not yet been added to a statement.
     * Used to preview the orders a statement would cover (no lock: the result is
     * advisory and may change before a statement is created).
     */
    @Query("""
            SELECT o FROM B2BOrder o
            JOIN FETCH o.buyer
            JOIN FETCH o.seller
            WHERE o.seller.id = :sellerId
              AND o.buyer.id = :buyerId
              AND o.status = :status
              AND o.paymentStatus = :paymentStatus
              AND o.deliveredAt >= :from
              AND o.deliveredAt < :toExclusive
              AND NOT EXISTS (
                  SELECT 1 FROM B2BStatementOrder so WHERE so.order.id = o.id
              )
            ORDER BY o.id ASC
            """)
    List<B2BOrder> findDeliveredUnpaidBySellerAndBuyer(
            @Param("sellerId") Long sellerId,
            @Param("buyerId") Long buyerId,
            @Param("status") Status status,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * Same as {@link #findDeliveredUnpaidBySellerAndBuyer} but takes a
     * pessimistic write lock on each matching order row so concurrent statement
     * creations serialise on the same orders. Used by statement creation to
     * prevent two statements from assigning the same order; the database unique
     * constraint on {@code b2b_statement_orders(order_id)} is the final
     * safeguard.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM B2BOrder o
            JOIN FETCH o.buyer
            JOIN FETCH o.seller
            WHERE o.seller.id = :sellerId
              AND o.buyer.id = :buyerId
              AND o.status = :status
              AND o.paymentStatus = :paymentStatus
              AND o.deliveredAt >= :from
              AND o.deliveredAt < :toExclusive
              AND NOT EXISTS (
                  SELECT 1 FROM B2BStatementOrder so WHERE so.order.id = o.id
              )
            ORDER BY o.id ASC
            """)
    List<B2BOrder> findDeliveredUnpaidBySellerAndBuyerForUpdate(
            @Param("sellerId") Long sellerId,
            @Param("buyerId") Long buyerId,
            @Param("status") Status status,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive);
}
