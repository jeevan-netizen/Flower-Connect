package com.flowerconnect.payment.service;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.PaymentEvent;
import com.flowerconnect.payment.domain.PaymentIntent;
import com.flowerconnect.payment.domain.PaymentIntent.IntentStatus;
import com.flowerconnect.payment.dto.PaymentGroupStateResponse;
import com.flowerconnect.payment.dto.PaymentResumeResponse;
import com.flowerconnect.payment.dto.PaymentStateResponse;
import com.flowerconnect.payment.gateway.PaymentGateway;
import com.flowerconnect.payment.repository.PaymentEventRepository;
import com.flowerconnect.payment.repository.PaymentIntentRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final ObjectProvider<OrderStateMachine> orderStateMachineProvider;
    private final java.time.Clock clock;

    @Autowired
    public PaymentService(
            PaymentGateway paymentGateway,
            PaymentRepository paymentRepository,
            PaymentIntentRepository paymentIntentRepository,
            PaymentEventRepository paymentEventRepository,
            OrderRepository orderRepository,
            ObjectProvider<OrderStateMachine> orderStateMachineProvider,
            java.time.Clock clock) {
        this.paymentGateway = paymentGateway;
        this.paymentRepository = paymentRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.paymentEventRepository = paymentEventRepository;
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
     * Refreshes the payment state of an unexpired {@code PENDING_PAYMENT} order
     * without changing it. A resume request is deliberately <b>not</b> treated
     * as payment success: only a valid signed success event delivered through
     * {@link #processProviderEvent} may complete a payment and transition the
     * order. This method only validates that the order is still awaiting
     * payment and returns the current (still-pending) state; if the intent has
     * expired it is marked {@code EXPIRED} (timeout bookkeeping only --- the
     * order and payment statuses are left untouched) and a 409 is returned.
     *
     * @param orderId the order to inspect
     * @param customerEmail the authenticated customer's email (ownership scope)
     * @return the current resume/refresh result (payment and order unchanged)
     * @throws BusinessException 404 if missing, 403 if foreign,
     *         409 if not pending, not online, not CREATED, or the intent has expired
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
            // Timeout bookkeeping only: mark the intent expired so a late webhook
            // event lands on the REFUND_PENDING path instead of completing the
            // order. The order and payment statuses are left untouched here.
            intent.setStatus(IntentStatus.EXPIRED);
            paymentIntentRepository.saveAndFlush(intent);
            throw BusinessException.conflict("Payment intent for order " + orderId + " has expired");
        }

        // Deliberately no state change: payment and order stay PENDING /
        // PENDING_PAYMENT until a signed webhook event completes them.
        return toPaymentResumeResponse(order, payment, intent);
    }

    // ------------------------------------------------------------------
    // Stage 3: signed webhook event handling
    // ------------------------------------------------------------------

    /**
     * Outcome of processing a provider webhook event.
     */
    public enum WebhookOutcome {
        /** The event was accepted and its state effects applied. */
        ACCEPTED,
        /** The event ID was already processed; no side effects repeated. */
        DUPLICATE,
        /** The group was already terminal; the outcome was recorded without regression. */
        IGNORED
    }

    /**
     * The result of processing a provider event.
     */
    public static class WebhookResult {
        private final WebhookOutcome outcome;
        private final String providerEventId;
        private final String paymentGroupId;
        private final String intentStatus;

        private WebhookResult(WebhookOutcome outcome, String providerEventId,
                              String paymentGroupId, String intentStatus) {
            this.outcome = outcome;
            this.providerEventId = providerEventId;
            this.paymentGroupId = paymentGroupId;
            this.intentStatus = intentStatus;
        }

        public static WebhookResult duplicate(String providerEventId, String paymentGroupId, String intentStatus) {
            return new WebhookResult(WebhookOutcome.DUPLICATE, providerEventId, paymentGroupId, intentStatus);
        }

        public static WebhookResult ignored(String providerEventId, String paymentGroupId, String intentStatus) {
            return new WebhookResult(WebhookOutcome.IGNORED, providerEventId, paymentGroupId, intentStatus);
        }

        public static WebhookResult accepted(String providerEventId, String paymentGroupId, String intentStatus) {
            return new WebhookResult(WebhookOutcome.ACCEPTED, providerEventId, paymentGroupId, intentStatus);
        }

        public WebhookOutcome getOutcome() { return outcome; }
        public String getProviderEventId() { return providerEventId; }
        public String getPaymentGroupId() { return paymentGroupId; }
        public String getIntentStatus() { return intentStatus; }
    }

    /**
     * Processes a provider webhook event for a payment group.
     *
     * <p>The signature must already have been verified by the caller (the
     * webhook controller verifies it before parsing or looking up an intent).
     * The event is persisted in {@code payment_events} together with its status
     * effects in one transaction, so concurrent duplicate deliveries produce
     * exactly one state change.
     *
     * <p>Group transitions are atomic and go exclusively through {@link
     * OrderStateMachine}; stock side effects use the existing order-then-
     * inventory lock policy. A success event moves every group order
     * {@code PENDING_PAYMENT -> PLACED} (actor {@code SYSTEM}); a failure event
     * moves them {@code PENDING_PAYMENT -> CANCELLED} with reason
     * {@code PAYMENT_FAILED}, releasing reservations through the state machine.
     * A late success after cancellation/timeout sets both
     * {@code payments.status} and {@code orders.payment_status} to
     * {@code REFUND_PENDING} without resurrecting the order. A failure after
     * success never regresses {@code PAID}/{@code REFUND_PENDING}.
     *
     * @param paymentGroupId the payment group the event belongs to
     * @param providerEventId the provider's unique event id (idempotency key)
     * @param eventType the provider event type (stored, not interpreted)
     * @param outcome {@code success} or {@code failure}
     * @param amount the event amount (falls back to the order total)
     * @return the processing result
     * @throws BusinessException 404 if the intent is unknown (no information
     *         leakage), 409 if the group cannot transition atomically
     */
    @Transactional
    public WebhookResult processProviderEvent(String paymentGroupId, String providerEventId,
                                              String eventType, String outcome, BigDecimal amount) {
        boolean success = "success".equalsIgnoreCase(outcome);
        boolean failure = "failure".equalsIgnoreCase(outcome);
        if (!success && !failure) {
            throw BusinessException.badRequest("Unknown payment outcome: " + outcome);
        }

        // 1. Idempotency: a duplicate event ID is acknowledged without side effects.
        if (paymentEventRepository.existsByProviderEventId(providerEventId)) {
            PaymentIntent known = paymentIntentRepository.findByPaymentGroupId(paymentGroupId).orElse(null);
            return WebhookResult.duplicate(providerEventId, paymentGroupId,
                    known != null ? known.getStatus().name() : null);
        }

        // 2. Look up the intent. Unknown intent -> 404 with no information leakage.
        PaymentIntent intent = paymentIntentRepository.findByPaymentGroupId(paymentGroupId)
                .orElseThrow(() -> BusinessException.notFound("Payment intent not found"));

        // 3. Lock every order in the group in ascending id order before reading
        //    the group state, so concurrent deliveries serialize and the status
        //    observed below is stable under the locks.
        List<Payment> payments = paymentRepository.findByPaymentGroupId(paymentGroupId);
        List<Long> orderIds = payments.stream()
                .map(p -> p.getOrder().getId())
                .distinct()
                .sorted()
                .toList();
        List<Order> lockedOrders = new ArrayList<>();
        for (Long id : orderIds) {
            lockedOrders.add(orderRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> BusinessException.notFound("Order not found: " + id)));
        }

        // Re-read the intent under the locks to observe the latest committed
        // group state (a prior concurrent delivery may have completed it).
        intent = paymentIntentRepository.findByPaymentGroupId(paymentGroupId)
                .orElseThrow(() -> BusinessException.notFound("Payment intent not found"));
        payments = paymentRepository.findByPaymentGroupId(paymentGroupId);

        IntentStatus status = intent.getStatus();
        boolean stateChanged = false;

        switch (status) {
            case CREATED:
                // Initial outcome: every order must still be awaiting payment.
                for (Order order : lockedOrders) {
                    if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT) {
                        throw BusinessException.conflict(
                                "Order " + order.getId() + " is not awaiting payment (current: " + order.getStatus() + ")");
                    }
                }
                List<Long> ids = lockedOrders.stream().map(Order::getId).toList();
                if (success) {
                    orderStateMachineProvider.getObject().transitionGroup(
                            ids, Order.OrderStatus.PLACED, ActorType.SYSTEM, null, "Payment succeeded (webhook)");
                    intent.setStatus(IntentStatus.PAID);
                    setPaymentAndOrderStatuses(payments, lockedOrders,
                            com.flowerconnect.payment.domain.Payment.PaymentStatus.PAID,
                            Order.PaymentStatus.PAID);
                    stateChanged = true;
                } else {
                    orderStateMachineProvider.getObject().transitionGroup(
                            ids, Order.OrderStatus.CANCELLED, ActorType.SYSTEM, null, "PAYMENT_FAILED");
                    intent.setStatus(IntentStatus.FAILED);
                    setPaymentAndOrderStatuses(payments, lockedOrders,
                            com.flowerconnect.payment.domain.Payment.PaymentStatus.FAILED,
                            Order.PaymentStatus.FAILED);
                    stateChanged = true;
                }
                break;
            case PAID:
                // Already succeeded: any further event is idempotent, no regression.
                break;
            case FAILED:
            case EXPIRED:
                if (success) {
                    // Late success after cancellation/timeout: REFUND_PENDING,
                    // do not resurrect the order.
                    setPaymentAndOrderStatuses(payments, lockedOrders,
                            com.flowerconnect.payment.domain.Payment.PaymentStatus.REFUND_PENDING,
                            Order.PaymentStatus.REFUND_PENDING);
                    stateChanged = true;
                }
                // Failure after already failed/expired: idempotent, no regression.
                break;
        }

        // 4. Persist the event and all status effects atomically.
        Payment firstPayment = payments.stream()
                .min(Comparator.comparing(Payment::getId))
                .orElseThrow(() -> new IllegalStateException("Group has no payments"));
        PaymentEvent event = PaymentEvent.builder()
                .payment(firstPayment)
                .paymentGroupId(paymentGroupId)
                .providerEventId(providerEventId)
                .eventType(eventType)
                .outcome(outcome)
                .amount(amount != null ? amount : firstPayment.getAmount())
                .build();
        paymentEventRepository.saveAndFlush(event);
        paymentIntentRepository.saveAndFlush(intent);
        payments.forEach(paymentRepository::saveAndFlush);
        lockedOrders.forEach(orderRepository::saveAndFlush);

        WebhookOutcome resultOutcome = stateChanged
                ? WebhookOutcome.ACCEPTED
                : WebhookOutcome.IGNORED;
        return new WebhookResult(resultOutcome, providerEventId, paymentGroupId, intent.getStatus().name());
    }

    private void setPaymentAndOrderStatuses(List<Payment> payments, List<Order> orders,
                                            com.flowerconnect.payment.domain.Payment.PaymentStatus paymentStatus,
                                            Order.PaymentStatus orderPaymentStatus) {
        for (Payment payment : payments) {
            payment.setStatus(paymentStatus);
        }
        for (Order order : orders) {
            order.setPaymentStatus(orderPaymentStatus);
        }
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
                .paidAt(null)
                .build();
    }
}

