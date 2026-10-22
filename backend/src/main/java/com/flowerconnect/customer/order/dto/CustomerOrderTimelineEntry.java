package com.flowerconnect.customer.order.dto;

import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order.OrderStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row of an order's status timeline (plan Phase 6, task 6.3).
 *
 * <p>Rows are returned in chronological order (oldest first), mirroring
 * the append-only {@code order_status_history} table. The initial
 * {@code PLACED} transition written at checkout has {@code fromStatus =
 * null}.
 *
 * <p>Only actor information that is safe to show a customer is exposed:
 * the actor type and, for a named user, their display name. Actor email,
 * id and any internal notes are deliberately omitted.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrderTimelineEntry {

    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private ActorType actorType;
    private String actorName;
    private String reason;
    private LocalDateTime createdAt;
}
