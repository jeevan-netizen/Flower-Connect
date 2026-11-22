package com.flowerconnect.payment.client;

import java.math.BigDecimal;

/**
 * A Razorpay Order returned by the Orders API.
 */
public record RazorpayOrder(
        String id,
        BigDecimal amount,
        String currency,
        String status,
        String receipt
) {
}
