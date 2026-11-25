package com.flowerconnect.b2b.repository;

import com.flowerconnect.b2b.domain.B2BOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence for B2B order items (Stage 1 of the B2B module).
 */
@Repository
public interface B2BOrderItemRepository extends JpaRepository<B2BOrderItem, Long> {

    List<B2BOrderItem> findByOrderId(Long orderId);
}
