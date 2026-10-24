package com.flowerconnect.payment.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * The result of resuming/paying a pending order
 * ({@code POST /api/v1/payments/{orderId}/resume}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResumeResponse {

    private Long orderId;
    private String orderStatus;
    private String paymentStatus;
    private String paymentGroupId;
    private String intentReference;
    private String paidAt;
}
