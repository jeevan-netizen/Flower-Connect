package com.flowerconnect.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

/**
 * Incoming payment webhook event payload. Only the fields the webhook
 * interprets are accepted; no card-like data or secrets are ever present.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    /** The provider's unique event id (idempotency key). */
    @NotBlank(message = "providerEventId is required")
    private String providerEventId;

    /** The payment group the event belongs to. */
    @NotBlank(message = "paymentGroupId is required")
    private String paymentGroupId;

    /** The provider event type (stored, not interpreted). */
    @NotBlank(message = "eventType is required")
    private String eventType;

    /** {@code success} or {@code failure}. */
    @NotBlank(message = "outcome is required")
    private String outcome;

    /** The event amount. */
    @NotNull(message = "amount is required")
    private BigDecimal amount;
}
