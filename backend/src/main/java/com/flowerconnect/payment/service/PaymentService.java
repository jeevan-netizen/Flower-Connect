package com.flowerconnect.payment.service;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.PaymentIntent;
import com.flowerconnect.payment.domain.PaymentIntent.IntentStatus;
import com.flowerconnect.payment.dto.PaymentGroupStateResponse;
import com.flowerconnect.payment.dto.PaymentResumeResponse;
import com.flowerconnect.payment.dto.PaymentStateResponse;
import com.flowerconnect.payment.gateway.PaymentGateway;
import com.flowerconnect.payment.repository.PaymentIntentRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole application service that writes payment status (plan Phase 7,
 * task 7.2).
 *
 * <p>{@link #createCodPayment} creates the initial {@code PENDING} COD
 * payment record during checkout (delegating to the {@link PaymentGateway}
 * provider). {@link #collectPayment} is the Stage 1 status transition:
 * {@code PENDING -> COLLECTED} at delivery, with
 * {@code payments.status} and {@code orders.payment_status} updated together
 * in one transaction and {@code collected_at} stamped from the injected
 * {@link java.time.Clock}.
 *
 * <p>Stage 2 (online checkout) adds:
 * <ul>
 *   <li>{@link #fetchPaymentState} — read-only view of one order's payment
 *       state, scoped to the owning customer.</li>
 *   <li>{@link #fetchGroupState} — read-only view of a payment group
 *       (simulated intent + linked orders), scoped to the owning customer.</li>
 *   <li>{@link #resumePayment} — customer-initiated payment confirmation for
 *       an unexpired {@code PENDING_PAYMENT} order. Transitions the order
 *       {@code PENDING_PAYMENT -> PLACED} exclusively through {@link
 *       OrderStateMachine}, then synchronizes {@code payments.status} and
 *       {@code orders.payment_status} to {@code PAID} and marks the intent
 *       {@code PAID}, all in one transaction.</li>
 * </ul>
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
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final OrderRepository orderRepository;
    private final ObjectProvider<OrderStateMachine> orderStateMachineProvider;
    private final java.time.Clock clock;

    @Autowired
    public PaymentService(
            PaymentGateway paymentGateway,
            PaymentRepository paymentRepository,
            PaymentIntentRepository paymentIntentRepository,
            OrderRepository orderRepository,
            ObjectProvider<OrderStateMachine> orderStateMachineProvider,
            java.time.Clock clock) {
        this.paymentGateway = paymentGateway;
        this.paymentRepository = paymentRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderRepository = orderRepository;
        this.orderStateMachineProvider = orderStateMachineProvider;
        this.clock = clock;
    }

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

    // ------------------------------------------------------------------
    // Stage 2: online payment state and resume
    // ------------------------------------------------------------------

    /**
     * Returns the current payment state for one order, scoped to the owning
     * customer.
     *
     * @param orderId the order to inspect
     * @param customerEmail the authenticated customer's email (ownership scope)
     * @return the payment state
     * @throws BusinessException 404 if the order or payment is missing,
     *         403 if the order belongs to another customer
     */
    @Transactional(readOnly = true)
    public PaymentStateResponse fetchPaymentState(Long orderId, String customerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        requireOrderOwner(order, customerEmail);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> BusinessException.notFound("Payment not found for order: " + orderId));
        PaymentIntent intent = payment.getPaymentGroupId() != null
                ? paymentIntentRepository.findByPaymentGroupId(payment.getPaymentGroupId()).orElse(null)
                : null;

        return toPaymentStateResponse(order, payment, intent);
    }

    /**
     * Returns the current state of a payment group (simulated intent plus all
     * linked orders), scoped to the owning customer.
     *
     * @param paymentGroupId the payment group to inspect
     * @param customerEmail the authenticated customer's email (ownership scope)
     * @return the group state
     * @throws BusinessException 404 if the group is missing,
     *         403 if the group belongs to another customer
     */
    @Transactional(readOnly = true)
    public PaymentGroupStateResponse fetchGroupState(String paymentGroupId, String customerEmail) {
        PaymentIntent intent = paymentIntentRepository.findByPaymentGroupId(paymentGroupId)
                .orElseThrow(() -> BusinessException.notFound("Payment intent not found: " + paymentGroupId));

        List<Payment> payments = paymentRepository.findByPaymentGroupId(paymentGroupId);
        if (payments.isEmpty()) {
            throw BusinessException.notFound("No payments found for group: " + paymentGroupId);
        }
        requireOrderOwner(payments.get(0).getOrder(), customerEmail);

        LocalDateTime now = LocalDateTime.now(clock);
        List<PaymentGroupStateResponse.OrderEntry> entries = payments.stream()
                .map(p -> {
                    Order o = p.getOrder();
                    return PaymentGroupStateResponse.OrderEntry.builder()
                            .orderId(o.getId())
                            .paymentStatus(p.getStatus().name())
                            .orderStatus(o.getStatus().name())
                            .method(p.getMethod().name())
                            .amount(p.getAmount())
                            .build();
                })
                .collect(Collectors.toList());

        return PaymentGroupStateResponse.builder()
                .paymentGroupId(intent.getPaymentGroupId())
                .intentReference(intent.getProviderReference())
                .status(intent.getStatus().name())
                .total(intent.getTotal())
                .expiresAt(intent.getExpiresAt() != null ? intent.getExpiresAt().toString() : null)
                .orders(entries)
                .build();
    }

    /**
     * Confirms payment for an unexpired {@code PENDING_PAYMENT} order
     * (simulated/test-mode resume). Transitions the order
     * {@code PENDING_PAYMENT -> PLACED} exclusively through {@link
     * OrderStateMachine}, then synchronizes {@code payments.status} and
     * {@code orders.payment_status} to {@code PAID} and marks the intent
     * {@code PAID}, all in one transaction.
     *
     * @param orderId the order to pay
     * @param customerEmail the authenticated customer's email (ownership scope)
     * @return the resume result
     * @throws BusinessException 404 if missing, 403 if foreign,
     *         409 if not pending, already paid, or the intent has expired
     */
    @Transactional
    public PaymentResumeResponse resumePayment(Long orderId, String customerEmail) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        requireOrderOwner(order, customerEmail);

        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT) {
            throw BusinessException.conflict(
                    "Order " + orderId + " is not in PENDING_PAYMENT state (current: " + order.getStatus() + ")");
        }
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw BusinessException.conflict(
                    "Order " + orderId + " is not an ONLINE payment order (method: " + order.getPaymentMethod() + ")");
        }

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> BusinessException.notFound("Payment not found for order: " + orderId));
        if (payment.getStatus() != com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING) {
            throw BusinessException.conflict(
                    "Payment for order " + orderId + " is not PENDING (current: " + payment.getStatus() + ")");
        }

        PaymentIntent intent = paymentIntentRepository.findByPaymentGroupId(payment.getPaymentGroupId())
                .orElseThrow(() -> BusinessException.notFound("Payment intent not found for group: "
                        + payment.getPaymentGroupId()));
        if (intent.getStatus() != IntentStatus.CREATED) {
            throw BusinessException.conflict(
                    "Payment intent is not in CREATED state (current: " + intent.getStatus() + ")");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (intent.getExpiresAt() != null && intent.getExpiresAt().isBefore(now)) {
            intent.setStatus(IntentStatus.EXPIRED);
            paymentIntentRepository.saveAndFlush(intent);
            throw BusinessException.conflict("Payment intent for order " + orderId + " has expired");
        }

        // Transition the order PENDING_PAYMENT -> PLACED through the state
        // machine only (SideEffect.NONE: stock was already reserved at checkout).
        orderStateMachineProvider.getObject().transition(orderId, Order.OrderStatus.PLACED,
                ActorType.SYSTEM, null, "Payment confirmed (online)");

        // Synchronize payment status with order payment status in the same
        // transaction, and mark the intent PAID.
        order.setPaymentStatus(PaymentStatus.PAID);
        payment.setStatus(com.flowerconnect.payment.domain.Payment.PaymentStatus.PAID);
        orderRepository.saveAndFlush(order);
        paymentRepository.saveAndFlush(payment);

        intent.setStatus(IntentStatus.PAID);
        paymentIntentRepository.saveAndFlush(intent);

        log.info("Resumed online payment for order {} (group {}, intent {}) at {}",
                orderId, intent.getPaymentGroupId(), intent.getProviderReference(), now);

        return toPaymentResumeResponse(order, payment, intent);
    }

    private void requireOrderOwner(Order order, String customerEmail) {
        if (!order.getUser().getEmail().equals(customerEmail)) {
            throw BusinessException.forbidden(
                    "Order " + order.getId() + " belongs to another customer");
        }
    }

    private PaymentStateResponse toPaymentStateResponse(Order order, Payment payment, PaymentIntent intent) {
        return PaymentStateResponse.builder()
                .orderId(order.getId())
                .paymentGroupId(payment.getPaymentGroupId())
                .intentReference(intent != null ? intent.getProviderReference() : null)
                .method(payment.getMethod().name())
                .paymentStatus(payment.getStatus().name())
                .orderStatus(order.getStatus().name())
                .amount(payment.getAmount())
                .expiresAt(intent != null && intent.getExpiresAt() != null
                        ? intent.getExpiresAt().toString() : null)
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null)
                .build();
    }

    private PaymentResumeResponse toPaymentResumeResponse(Order order, Payment payment, PaymentIntent intent) {
        return PaymentResumeResponse.builder()
                .orderId(order.getId())
                .orderStatus(order.getStatus().name())
                .paymentStatus(payment.getStatus().name())
                .paymentGroupId(payment.getPaymentGroupId())
                .intentReference(intent.getProviderReference())
                .paidAt(LocalDateTime.now(clock).toString())
                .build();
    }
}

