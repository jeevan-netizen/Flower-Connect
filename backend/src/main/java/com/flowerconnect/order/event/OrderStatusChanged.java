package com.flowerconnect.order.event;

import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Scalar-only domain event published after an order status transition commits.
 *
 * <p>Holds only primitives, wrappers, enums and an {@code Instant} — no JPA
 * entities — so it is safe to publish after transaction commit and to handle
 * in later phases (notifications, settlement, analytics) without pulling the
 * ordering session into event listeners.
 *
 * @see OrderCancelled
 * @see OrderDelivered
 */
public class OrderStatusChanged extends ApplicationEvent {

    private final Long orderId;
    private final Order.OrderStatus fromStatus;
    private final Order.OrderStatus toStatus;
    private final ActorType actorType;
    private final Long actorUserId;
    private final String reason;
    private final Instant occurredAt;

    public OrderStatusChanged(Object source, Long orderId, Order.OrderStatus fromStatus,
                              Order.OrderStatus toStatus, ActorType actorType, Long actorUserId,
                              String reason, Instant occurredAt) {
        super(source);
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actorType;
        this.actorUserId = actorUserId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Order.OrderStatus getFromStatus() {
        return fromStatus;
    }

    public Order.OrderStatus getToStatus() {
        return toStatus;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
