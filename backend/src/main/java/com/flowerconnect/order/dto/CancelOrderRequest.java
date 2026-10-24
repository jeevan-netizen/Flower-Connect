package com.flowerconnect.order.dto;

import lombok.*;

/**
 * Request body for {@code POST /api/v1/orders/{id}/cancel} (plan Phase 8,
 * task 8.2). The reason is optional for a customer cancelling their own
 * {@code PLACED} order, but mandatory for vendor and admin cancellations;
 * the requirement is enforced by {@link OrderStateMachine}'s declared
 * transition table rather than by a blanket DTO constraint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelOrderRequest {

    private String reason;
}
