package com.flowerconnect.customer.order.service;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.customer.order.dto.CustomerOrderItemResponse;
import com.flowerconnect.customer.order.dto.CustomerOrderListItem;
import com.flowerconnect.customer.order.dto.CustomerOrderPageResponse;
import com.flowerconnect.customer.order.dto.CustomerOrderResponse;
import com.flowerconnect.customer.order.dto.CustomerOrderTimelineEntry;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.OrderItem;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.repository.OrderItemRepository;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Customer-facing order reads (plan Phase 6, task 6.3).
 *
 * <p><b>Ownership.</b> Every method resolves the caller from the JWT
 * subject (the email on the authentication), never from a request
 * parameter. An order is "the caller's" when its {@code user_id}
 * matches the caller's id; a missing id is 404 and an id belonging to
 * another customer is 403. No route accepts a customer identifier, so
 * cross-customer access is not expressible in the request at all.
 *
 * <p>These endpoints are read-only: they never change order status.
 * Every status change continues to go through
 * {@link com.flowerconnect.order.service.OrderStateMachine}.
 *
 * <p><b>Read model.</b> The list returns a light per-order item
 * (id, status, totals, slot). The detail returns the full read model:
 * payment method/status, vendor name, address snapshot, delivery slot,
 * per-item snapshots, the BigDecimal fee breakdown, and the chronological
 * status timeline from {@code order_status_history}. Internal fields
 * such as vendor commission are never exposed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    /**
     * One page of the authenticated customer's own orders, newest first,
     * with an optional status filter.
     */
    @Transactional(readOnly = true)
    public CustomerOrderPageResponse listOrders(String customerEmail, OrderStatus status,
                                                Pageable pageable) {
        User user = requireUser(customerEmail);
        Page<Order> page;
        if (status != null) {
            page = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    user.getId(), status, pageable);
        } else {
            page = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        }
        return toPageResponse(page);
    }

    /**
     * Full detail of one of the authenticated customer's orders. A missing
     * id is 404; an id belonging to another customer is 403.
     */
    @Transactional(readOnly = true)
    public CustomerOrderResponse getOrder(String customerEmail, Long orderId) {
        User user = requireUser(customerEmail);
        Order order = requireOwnedOrder(orderId, user.getId());
        return toResponse(order);
    }

    /**
     * The order with the given id, after confirming it exists and belongs to
     * the caller. A missing order is 404; a foreign order is 403, so a
     * customer can never read another customer's order.
     */
    private Order requireOwnedOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        if (!order.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("Order " + orderId + " belongs to another customer");
        }
        return order;
    }

    /**
     * The authenticated user, resolved from the JWT subject only.
     */
    private User requireUser(String customerEmail) {
        return userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private CustomerOrderPageResponse toPageResponse(Page<Order> page) {
        return CustomerOrderPageResponse.builder()
                .content(page.getContent().stream().map(this::toListItem).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    private CustomerOrderListItem toListItem(Order order) {
        return CustomerOrderListItem.builder()
                .id(order.getId())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .vendorName(order.getVendor().getBusinessName())
                .total(order.getTotal())
                .createdAt(order.getCreatedAt())
                .slotStart(order.getSlotStart())
                .slotEnd(order.getSlotEnd())
                .build();
    }

    private CustomerOrderResponse toResponse(Order order) {
        List<CustomerOrderItemResponse> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(
                order.getId()).stream()
                .map(this::toItemResponse)
                .toList();
        List<CustomerOrderTimelineEntry> timeline = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(
                order.getId()).stream()
                .map(this::toTimelineEntry)
                .toList();
        LocalDateTime refundedAt = paymentRepository.findByOrderId(order.getId())
                .map(p -> p.getRefundedAt())
                .orElse(null);
        return CustomerOrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .vendorName(order.getVendor().getBusinessName())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .platformFee(order.getPlatformFee())
                .total(order.getTotal())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .deliveryAddressLine1(order.getDeliveryAddressLine1())
                .deliveryAddressLine2(order.getDeliveryAddressLine2())
                .deliveryLatitude(order.getDeliveryLatitude())
                .deliveryLongitude(order.getDeliveryLongitude())
                .slotStart(order.getSlotStart())
                .slotEnd(order.getSlotEnd())
                .note(order.getNote())
                .refundedAt(refundedAt)
                .items(items)
                .timeline(timeline)
                .build();
    }

    private CustomerOrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal subtotal = item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()));
        return CustomerOrderItemResponse.builder()
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .priceSnapshot(item.getPriceSnapshot())
                .subtotal(subtotal)
                .note(item.getNote())
                .build();
    }

    private CustomerOrderTimelineEntry toTimelineEntry(OrderStatusHistory history) {
        String actorName = null;
        if (history.getActor() != null) {
            actorName = history.getActor().getFullName();
        }
        return CustomerOrderTimelineEntry.builder()
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .actorType(history.getActorType())
                .actorName(actorName)
                .reason(history.getReason())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
