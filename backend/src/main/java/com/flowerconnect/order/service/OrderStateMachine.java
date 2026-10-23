package com.flowerconnect.order.service;

import com.flowerconnect.domain.User;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.event.OrderCancelled;
import com.flowerconnect.order.event.OrderDelivered;
import com.flowerconnect.order.event.OrderStatusChanged;
import com.flowerconnect.order.repository.OrderItemRepository;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.StockDelta;
import com.flowerconnect.inventory.service.InventoryService;
import com.flowerconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sole service for changing an order's status (plan section 6.1, task 6.1).
 *
 * <p>Every transition is validated against the declared section 6.1 table,
 * writes exactly one {@code order_status_history} row (from/to status, actor
 * type, actor user id, reason, timestamp from the injected {@link Clock}),
 * and publishes scalar-only domain events after the transaction commits.
 *
 * <p>No other class may set {@code Order#status}. Stock side effects go
 * through {@link InventoryService}, with inventory rows locked in ascending
 * product-id order after the order row is locked.
 *
 * @see #transition(Long, OrderStatus, ActorType, Long, String)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateMachine {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // ------------------------------------------------------------------
    // Declared transition table (plan section 6.1)
    //
    // PENDING_PAYMENT flows are unreachable until Phase 7 online payment but
    // are encoded here per section 6.1. Customer/admin cancellation transitions
    // are reserved for Phase 8 endpoints; the state machine validates them
    // without exposing them.
    // ------------------------------------------------------------------

    static final Map<OrderStateMachine.TransitionKey, OrderStateMachine.Transition> TRANSITIONS =
            Map.ofEntries(
                    // PENDING_PAYMENT flows (Phase 7)
                    entry(OrderStatus.PENDING_PAYMENT, OrderStatus.PLACED, ActorType.SYSTEM,
                            false, SideEffect.NONE),
                    entry(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, ActorType.SYSTEM,
                            false, SideEffect.RELEASE),
                    entry(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, ActorType.CUSTOMER,
                            false, SideEffect.RELEASE),
                    // PLACED flows
                    entry(OrderStatus.PLACED, OrderStatus.CONFIRMED, ActorType.VENDOR,
                            false, SideEffect.COMMIT),
                    entry(OrderStatus.PLACED, OrderStatus.REJECTED, ActorType.VENDOR,
                            true, SideEffect.RELEASE),
                    entry(OrderStatus.PLACED, OrderStatus.CANCELLED, ActorType.CUSTOMER,
                            false, SideEffect.RELEASE),
                    entry(OrderStatus.PLACED, OrderStatus.CANCELLED, ActorType.ADMIN,
                            true, SideEffect.RELEASE),
                    entry(OrderStatus.PLACED, OrderStatus.CANCELLED, ActorType.SYSTEM,
                            false, SideEffect.RELEASE),
                    // CONFIRMED flows
                    entry(OrderStatus.CONFIRMED, OrderStatus.PREPARING, ActorType.VENDOR,
                            false, SideEffect.NONE),
                    entry(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, ActorType.CUSTOMER,
                            false, SideEffect.RESTOCK),
                    entry(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, ActorType.VENDOR,
                            true, SideEffect.RESTOCK),
                    entry(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, ActorType.ADMIN,
                            true, SideEffect.RESTOCK),
                    // PREPARING flows
                    entry(OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR,
                            false, SideEffect.NONE),
                    entry(OrderStatus.PREPARING, OrderStatus.CANCELLED, ActorType.VENDOR,
                            true, SideEffect.NONE),
                    entry(OrderStatus.PREPARING, OrderStatus.CANCELLED, ActorType.ADMIN,
                            true, SideEffect.NONE),
                    // OUT_FOR_DELIVERY flows
                    entry(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, ActorType.VENDOR,
                            false, SideEffect.DELIVER),
                    entry(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED, ActorType.ADMIN,
                            true, SideEffect.NONE)
            );

    private static OrderStateMachine.TransitionKey key(OrderStatus from, OrderStatus to, ActorType actor) {
        return new OrderStateMachine.TransitionKey(from, to, actor);
    }

    private static Map.Entry<OrderStateMachine.TransitionKey, OrderStateMachine.Transition> entry(
            OrderStatus from, OrderStatus to, ActorType actor, boolean reasonRequired, SideEffect sideEffect) {
        return Map.entry(key(from, to, actor), new OrderStateMachine.Transition(reasonRequired, sideEffect));
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Transitions {@code orderId} from its current status to {@code toStatus}
     * by {@code actorType} (actor user {@code actorUserId} when applicable,
     * null for {@code SYSTEM}) with {@code reason}.
     *
     * @return the updated order
     * @throws BusinessException 404 if the order does not exist, 409 for an
     *         illegal transition or insufficient stock on accept, 400 when a
     *         required reason is missing
     */
    @Transactional
    public Order transition(Long orderId, OrderStatus toStatus,
                            ActorType actorType, Long actorUserId, String reason) {
        // 1. Lock the order row first (agreed lock order: order before inventory).
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        OrderStatus fromStatus = order.getStatus();

        // 2. Terminal states admit no outgoing transition.
        if (isTerminal(fromStatus)) {
            throw BusinessException.conflict(
                    "Order " + orderId + " is already in terminal state " + fromStatus);
        }

        // 3. Validate the (from, to, actor) triple against the declared table.
        Transition transition = TRANSITIONS.get(key(fromStatus, toStatus, actorType));
        if (transition == null) {
            throw BusinessException.conflict(
                    "Illegal transition " + fromStatus + " -> " + toStatus + " by " + actorType);
        }

        // 4. Reason validation (vendor reject, vendor cancel, admin cancel).
        if (transition.reasonRequired() && isBlank(reason)) {
            throw BusinessException.badRequest(
                    "Reason is required for " + actorType + " " + toStatus);
        }

        // 5. Resolve the actor user when a named user is involved.
        User actor = null;
        if (actorUserId != null) {
            actor = userRepository.findById(actorUserId)
                    .orElseThrow(() -> BusinessException.notFound("Actor user not found: " + actorUserId));
        }

        // 6. Stock side effects under inventory locks (ascending product id).
        applyStockSideEffects(order, transition.sideEffect(), actor);

        // 7. Delivery: record COD collection.
        if (transition.sideEffect() == SideEffect.DELIVER &&
                order.getPaymentMethod() == Order.PaymentMethod.COD) {
            order.setPaymentStatus(Order.PaymentStatus.COLLECTED);
        }

        // 8. Update the order status.
        order.setStatus(toStatus);
        orderRepository.saveAndFlush(order);

        // 9. Write exactly one history row, timestamped from the injected Clock.
        LocalDateTime now = LocalDateTime.now(clock);
        String normalizedReason = normalize(reason);
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .reason(normalizedReason)
                .actor(actor)
                .actorType(actorType)
                .createdAt(now)
                .build();
        orderStatusHistoryRepository.saveAndFlush(history);

        // 10. Publish scalar-only events after the transaction commits.
        Instant nowInstant = now.atZone(clock.getZone()).toInstant();
        publishAfterCommit(new OrderStatusChanged(this, orderId, fromStatus, toStatus,
                actorType, actorUserId, normalizedReason, nowInstant));
        if (toStatus == OrderStatus.CANCELLED) {
            publishAfterCommit(new OrderCancelled(this, orderId, fromStatus, actorType,
                    actorUserId, normalizedReason, nowInstant));
        }
        if (toStatus == OrderStatus.DELIVERED) {
            publishAfterCommit(new OrderDelivered(this, orderId, fromStatus, actorType,
                    actorUserId, normalizedReason, nowInstant));
        }

        log.info("Order {} transitioned {} -> {} by {} {}", orderId, fromStatus, toStatus,
                actorType, actorUserId != null ? "user=" + actorUserId : "system");
        return order;
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void applyStockSideEffects(Order order, SideEffect sideEffect, User actor) {
        if (sideEffect == SideEffect.NONE) {
            return;
        }
        List<StockDelta> deltas = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(item -> new StockDelta(item.getProduct().getId(), item.getQuantity()))
                .toList();
        switch (sideEffect) {
            case COMMIT -> inventoryService.commitReservedStock(deltas, actor);
            case RELEASE -> inventoryService.releaseReservedStock(deltas, actor);
            case RESTOCK -> inventoryService.restockCancelledOrder(deltas, actor);
            case DELIVER, NONE -> {
                // no stock change
            }
        }
    }

    private static boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED
                || status == OrderStatus.REJECTED;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String reason) {
        return isBlank(reason) ? null : reason.trim();
    }

    private void publishAfterCommit(ApplicationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCommit() {
                            eventPublisher.publishEvent(event);
                        }
                    });
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    // ------------------------------------------------------------------
    // Declared table model
    // ------------------------------------------------------------------

    static record TransitionKey(OrderStatus from, OrderStatus to, ActorType actor) {
    }

    static record Transition(boolean reasonRequired, SideEffect sideEffect) {
    }

    enum SideEffect {
        NONE, COMMIT, RELEASE, RESTOCK, DELIVER
    }
}
