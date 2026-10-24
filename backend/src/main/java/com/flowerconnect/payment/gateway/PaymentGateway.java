package com.flowerconnect.payment.gateway;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.payment.domain.Payment;

import java.math.BigDecimal;

/**
 * Abstraction over a payment provider (plan Phase 7, task 7.2).
 *
 * <p>Kept intentionally narrow for Stage 1: only the operation needed to
 * create a pending payment record for an order. A later online phase adds
 * intent creation, confirmation, webhook handling, refund and capture
 * through additional implementations of this interface.
 *
 * <p>Implementations are free to make external provider calls; the COD
 * implementation ({@link CodPaymentProvider}) makes none --- it only
 * persists a pending payment record.
 */
public interface PaymentGateway {

    /**
     * Creates a pending payment record for {@code order} at {@code amount}.
     *
     * @param order  the order being paid for (never null)
     * @param amount the order total to record
     * @return the created payment record in {@code PENDING} status
     */
    Payment createPayment(Order order, BigDecimal amount);
}
