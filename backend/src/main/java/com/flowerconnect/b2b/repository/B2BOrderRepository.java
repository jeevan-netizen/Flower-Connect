package com.flowerconnect.b2b.repository;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
