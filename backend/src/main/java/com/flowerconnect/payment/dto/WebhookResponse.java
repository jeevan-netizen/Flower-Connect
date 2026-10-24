package com.flowerconnect.payment.dto;

import lombok.*;
import com.flowerconnect.payment.service.PaymentService;

/**
 * Response returned by {@code POST /api/v1/payments/webhook}.
 *
 * <p>Response policy: invalid/missing/incorrect signatures, stale timestamps
 * and oversized bodies are rejected (4xx) without any state change. Duplicate
 * event IDs and already-terminal group outcomes are acknowledged with 200 and
 * no repeated side effects. Accepted events are applied atomically and
 * acknowledged with 200. No secret or sensitive value is ever returned.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookResponse {

    /** {@code accepted}, {@code duplicate} or {@code ignored}. */
    private String outcome;

    /** The provider event id the response refers to. */
    private String providerEventId;

    /** The payment group the event belongs to. */
    private String paymentGroupId;

    /** The intent status after processing. */
    private String intentStatus;

    public static WebhookResponse from(PaymentService.WebhookResult result) {
        return WebhookResponse.builder()
                .outcome(result.getOutcome().name().toLowerCase())
                .providerEventId(result.getProviderEventId())
                .paymentGroupId(result.getPaymentGroupId())
                .intentStatus(result.getIntentStatus())
                .build();
    }
}
