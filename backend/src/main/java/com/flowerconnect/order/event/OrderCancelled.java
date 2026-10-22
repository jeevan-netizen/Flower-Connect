package com.flowerconnect.order.event;

import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Published for cancellation transitions ({@code PLACED}, {@code CONFIRMED},
 * {@code PREPARING} or {@code OUT_FOR_DELIVERY} to {@code CANCELLED}).
 * Extends {@link OrderStatusChanged} so listeners can react to all
 * transitions generically or to cancellations specifically.
 *
 * <p>Phase 6 publishes the event; cancellation endpoints and the
 * {@code CancellationService} (plan task 8) that consume it arrive in Phase 8.
 */
public class OrderCancelled extends OrderStatusChanged {

    public OrderCancelled(Object source, Long orderId, Order.OrderStatus fromStatus,
                          ActorType actorType, Long actorUserId, String reason, Instant occurredAt) {
        super(source, orderId, fromStatus, Order.OrderStatus.CANCELLED, actorType, actorUserId, reason, occurredAt);
    }
}
