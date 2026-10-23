package com.flowerconnect.order.event;

import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Published on delivery ({@code OUT_FOR_DELIVERY} to {@code DELIVERED}).
 * Reserved as the Phase 10 hook for commission rows, ledger entries and
 * review unlocking; Phase 6 publishes the event only.
 */
public class OrderDelivered extends OrderStatusChanged {

    public OrderDelivered(Object source, Long orderId, Order.OrderStatus fromStatus,
                          ActorType actorType, Long actorUserId, String reason, Instant occurredAt) {
        super(source, orderId, fromStatus, Order.OrderStatus.DELIVERED, actorType, actorUserId, reason, occurredAt);
    }
}
