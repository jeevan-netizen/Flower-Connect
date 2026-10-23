package com.flowerconnect.payment.service;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.gateway.PaymentGateway;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/**
 * The sole application service that writes payment status (plan Phase 7,
 * task 7.2).
 *
 * <p>{@link #createCodPayment} creates the initial {@code PENDING} COD
 * payment record during checkout (delegating to the {@link PaymentGateway}
 * provider). {@link #collectPayment} is the only status transition supported
 * in Stage 1: {@code PENDING -> COLLECTED} at delivery, with
 * {@code payments.status} and {@code orders.payment_status} updated together
 * in one transaction and {@code collected_at} stamped from the injected
 * {@link java.time.Clock}.
 *
 * <p>Illegal payment-status changes (collecting a non-COD order, or collecting
 * a payment that is not {@code PENDING}) are rejected with 409
 * {@code CONFLICT}. Under the confirmed Option B, a COD order that is
 * rejected or cancelled before delivery keeps both payment statuses at
 * {@code PENDING} because no collection was attempted --- this service makes
 * no change for those transitions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final java.time.Clock clock;

    /**
     * Creates the initial {@code PENDING} COD payment record for an order
     * during checkout. Delegates to the COD payment gateway, which makes no
     * external call. Runs inside the checkout transaction so a rollback
     * removes the payment row along with the order and stock reservations.
     */
    @Transactional
    public Payment createCodPayment(Order order, BigDecimal amount) {
        return paymentGateway.createPayment(order, amount);
    }

    /**
     * Collects payment for a delivered COD order: transitions
     * {@code payments.status} and {@code orders.payment_status} from
     * {@code PENDING} to {@code COLLECTED} in a single transaction and stamps
     * {@code collected_at} from the injected clock.
     *
     * @return the updated payment record
     * @throws BusinessException 404 if the order or payment is missing,
     *         409 {@code CONFLICT} if the order is not COD or the payment is
     *         not in {@code PENDING} state (illegal transition)
     */
    @Transactional
    public Payment collectPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));

        if (order.getPaymentMethod() != PaymentMethod.COD) {
            throw BusinessException.conflict(
                    "Order " + orderId + " is not a COD order; payment collection does not apply");
        }

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> BusinessException.notFound("Payment not found for order: " + orderId));

        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            throw BusinessException.conflict(
                    "Payment for order " + orderId + " is already " + payment.getStatus()
                            + "; cannot collect a non-pending payment");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        payment.setStatus(Payment.PaymentStatus.COLLECTED);
        payment.setCollectedAt(now);
        order.setPaymentStatus(PaymentStatus.COLLECTED);

        paymentRepository.saveAndFlush(payment);
        orderRepository.saveAndFlush(order);

        log.info("Collected COD payment {} for order {} at {}", payment.getId(), orderId, now);
        return payment;
    }
}
