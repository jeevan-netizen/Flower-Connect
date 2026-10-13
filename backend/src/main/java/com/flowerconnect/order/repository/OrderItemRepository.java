package com.flowerconnect.order.repository;

import com.flowerconnect.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Order item persistence (plan Phase 5).
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    long countByOrderId(Long orderId);
}
