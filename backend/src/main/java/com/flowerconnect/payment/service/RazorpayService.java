package com.flowerconnect.payment.service;

import com.flowerconnect.domain.AuditActorType;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.payment.client.RazorpayClient;
import com.flowerconnect.payment.client.RazorpayClientException;
import com.flowerconnect.payment.client.RazorpayOrder;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.Payment.PaymentMethod;
import com.flowerconnect.payment.domain.PaymentEvent;
import com.flowerconnect.payment.repository.PaymentEventRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.config.AppProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.valueOf;

/**
 * Razorpay test-mode payment service.
 *
 * <p>Creates Razorpay Orders server-side for ONLINE checkouts using the
 * server-calculated total (INR, converted to paise). Verifies Razorpay checkout
 * signatures (HMAC-SHA256 over {@code order_id + "|" + payment_id}) before
 * marking any payment paid. Handles Razorpay webhook events
 * ({@code payment.captured}, {@code payment.failed}) idempotently.
 *
 * <p>The Razorpay Key Secret is never logged or returned in responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayService {

    private static final String RAZORPAY_CHECKOUT_SUCCESS_EVENT = "razorpay.checkout.success";
    private static final String RAZORPAY_WEBHOOK_CAPTURED = "payment.captured";
    private static final String RAZORPAY_WEBHOOK_FAILED = "payment.failed";

    private final RazorpayClient razorpayClient;
    private final AppProperties appProperties;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final ObjectProvider<OrderStateMachine> orderStateMachineProvider;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates a Razorpay Order for the given INR amount.
     *
     * @param amountInr the server-calculated checkout total in INR (never
     *        supplied by the browser)
     * @param receipt   a server-generated receipt identifier
     * @return the created Razorpay order (id, amount, currency, status, receipt)
     * @throws BusinessException 400 if Razorpay credentials are not configured
     *         or the Razorpay API call fails
     */
    @Transactional
    public RazorpayOrder createOrder(BigDecimal amountInr, String receipt) {
        String keyId = appProperties.getRazorpayKeyId();
        String keySecret = appProperties.getRazorpayKeySecret();
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw BusinessException.badRequest(
                    "Razorpay Key ID and Key Secret must be configured for test-mode checkout");
        }
        BigDecimal amountPaise = amountInr.multiply(valueOf(100)).setScale(0, BigDecimal.ROUND_HALF_UP);
        try {
            return razorpayClient.createOrder(amountPaise, "INR", receipt);
        } catch (RazorpayClientException e) {
            throw BusinessException.badRequest("Razorpay order creation failed: " + e.getMessage());
        }
    }

    /**
     * Verifies a Razorpay checkout signature and, if valid, marks the payment
     * {@code PAID} and transitions the order {@code PENDING_PAYMENT -> PLACED}.
     *
     * <p>The signature is {@code HMAC-SHA256(razorpay_order_id + "|" +
     * razorpay_payment_id, key_secret)}. An unverified signature never changes
     * payment or order state.
     *
     * @param orderId             the order id (ownership is enforced)
     * @param razorpayOrderId     the Razorpay order id (matches {@code
     *                            payments.provider_reference})
     * @param razorpayPaymentId   the Razorpay payment id
     * @param razorpaySignature   the Razorpay checkout signature
     * @param customerEmail       the authenticated customer's email (ownership scope)
     * @return the updated payment and order state
     * @throws BusinessException 400 for an invalid signature, 404 if missing,
     *         403 if foreign, 409 if the order is not awaiting payment
     */
    @Transactional
    public RazorpayVerifyResult verifyCheckout(Long orderId, String razorpayOrderId,
                                               String razorpayPaymentId, String razorpaySignature,
                                               String customerEmail) {
        if (!verifyCheckoutSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            throw BusinessException.badRequest("Invalid Razorpay signature");
        }

        // Idempotency: a payment_id already processed is acknowledged without side effects.
        if (paymentEventRepository.existsByProviderEventId(razorpayPaymentId)) {
            return toVerifyResult(orderId, customerEmail);
        }

        Payment payment = paymentRepository.findByProviderReference(razorpayOrderId)
                .orElseThrow(() -> BusinessException.notFound("Razorpay order not found: " + razorpayOrderId));

        Order order = orderRepository.findById(payment.getOrder().getId())
                .orElseThrow(() -> BusinessException.notFound("Order not found"));
        requireOrderOwner(order, customerEmail);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw BusinessException.conflict(
                    "Order " + orderId + " is not awaiting payment (current: " + order.getStatus() + ")");
        }

        // Mark the payment PAID (sole writer of payment status).
        payment.setStatus(Payment.PaymentStatus.PAID);
        paymentRepository.saveAndFlush(payment);

        // Transition the order to PLACED via the state machine (actor SYSTEM,
        // no stock side effect -- stock was already reserved at checkout).
        orderStateMachineProvider.getObject()
                .transition(order.getId(), OrderStatus.PLACED, ActorType.SYSTEM, null,
                        "Razorpay payment captured");

        // Persist the event for idempotency.
        PaymentEvent event = PaymentEvent.builder()
                .payment(payment)
                .paymentGroupId(payment.getPaymentGroupId())
                .providerEventId(razorpayPaymentId)
                .eventType(RAZORPAY_CHECKOUT_SUCCESS_EVENT)
                .outcome("success")
                .amount(payment.getAmount())
                .build();
        paymentEventRepository.saveAndFlush(event);

        return toVerifyResult(orderId, customerEmail);
    }

    /**
     * Processes a Razorpay webhook event. Verifies the signature against the
     * raw body before any processing. Handles {@code payment.captured} (mark
     * PAID, transition to PLACED) and {@code payment.failed} (mark FAILED,
     * cancel order and release reservation). Duplicate event IDs are
     * acknowledged without side effects.
     *
     * @param signature the {@code X-Razorpay-Signature} header value
     * @param body      the raw request body
     * @return the processing outcome
     * @throws BusinessException 401 for an invalid signature, 404 if the intent
     *         is unknown, 409 if the group cannot transition atomically
     */
    @Transactional
    public RazorpayWebhookResult processWebhook(String signature, byte[] body) {
        String webhookSecret = appProperties.getRazorpayWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()
                || !verifyWebhookSignature(webhookSecret, signature, body)) {
            log.info("Razorpay webhook rejected: invalid signature");
            throw BusinessException.unauthorized("Invalid Razorpay webhook signature");
        }

        com.fasterxml.jackson.databind.JsonNode event;
        try {
            event = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        } catch (Exception e) {
            throw BusinessException.badRequest("Malformed Razorpay webhook payload");
        }

        String providerEventId = event.path("id").asText(null);
        if (providerEventId == null || providerEventId.isBlank()) {
            throw BusinessException.badRequest("Razorpay webhook event id is missing");
        }

        // Idempotency: duplicate event ID is acknowledged without side effects.
        if (paymentEventRepository.existsByProviderEventId(providerEventId)) {
            return RazorpayWebhookResult.duplicate(providerEventId);
        }

        String eventType = event.path("event").asText(null);
        com.fasterxml.jackson.databind.JsonNode payload = event.path("payload");
        com.fasterxml.jackson.databind.JsonNode paymentNode = payload.path("payment").path("entity");
        String razorpayOrderId = paymentNode.path("order_id").asText(null);
        String razorpayPaymentId = paymentNode.path("id").asText(null);

        Payment payment = razorpayOrderId != null
                ? paymentRepository.findByProviderReference(razorpayOrderId).orElse(null)
                : null;
        if (payment == null) {
            throw BusinessException.notFound("Razorpay order not found: " + razorpayOrderId);
        }

        Order order = orderRepository.findById(payment.getOrder().getId())
                .orElseThrow(() -> BusinessException.notFound("Order not found"));

        BigDecimal amount = new BigDecimal(paymentNode.path("amount").asText("0"));

        switch (eventType) {
            case RAZORPAY_WEBHOOK_CAPTURED -> {
                if (payment.getStatus() != Payment.PaymentStatus.PAID) {
                    payment.setStatus(Payment.PaymentStatus.PAID);
                    paymentRepository.saveAndFlush(payment);
                }
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    orderStateMachineProvider.getObject()
                            .transition(order.getId(), OrderStatus.PLACED, ActorType.SYSTEM, null,
                                    "Razorpay webhook payment captured");
                }
            }
            case RAZORPAY_WEBHOOK_FAILED -> {
                if (payment.getStatus() != Payment.PaymentStatus.FAILED) {
                    payment.setStatus(Payment.PaymentStatus.FAILED);
                    paymentRepository.saveAndFlush(payment);
                }
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    orderStateMachineProvider.getObject()
                            .transition(order.getId(), OrderStatus.CANCELLED, ActorType.SYSTEM, null,
                                    "Razorpay payment failed");
                }
            }
            default -> {
                // Unknown event type: acknowledged but no state change.
            }
        }

        PaymentEvent eventRow = PaymentEvent.builder()
                .payment(payment)
                .paymentGroupId(payment.getPaymentGroupId())
                .providerEventId(providerEventId)
                .eventType(eventType)
                .outcome("success")
                .amount(amount)
                .build();
        paymentEventRepository.saveAndFlush(eventRow);

        return RazorpayWebhookResult.accepted(providerEventId);
    }

    /**
     * Verifies a Razorpay checkout signature:
     * {@code HMAC-SHA256(order_id + "|" + payment_id, key_secret)}.
     */
    private boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        String keySecret = appProperties.getRazorpayKeySecret();
        if (keySecret == null || keySecret.isBlank()
                || orderId == null || paymentId == null || signature == null) {
            return false;
        }
        String payload = orderId + "|" + paymentId;
        return hmacSha256Hex(keySecret, payload).equalsIgnoreCase(signature.trim());
    }

    /**
     * Verifies a Razorpay webhook signature:
     * {@code HMAC-SHA256(raw_body, webhook_secret)}.
     */
    private boolean verifyWebhookSignature(String secret, String signature, byte[] body) {
        if (secret == null || secret.isBlank() || signature == null || body == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            String expectedHex = bytesToHex(expected);
            return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private void requireOrderOwner(Order order, String customerEmail) {
        Long customerId = orderRepository.findCustomerIdByOrderId(order.getId())
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + order.getId()));
        User actor = userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> BusinessException.notFound("Authenticated user not found"));
        if (!actor.getId().equals(customerId)) {
            throw BusinessException.forbidden(
                    "Order " + order.getId() + " belongs to another customer");
        }
    }

    private RazorpayVerifyResult toVerifyResult(Long orderId, String customerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        requireOrderOwner(order, customerEmail);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> BusinessException.notFound("Payment not found for order: " + orderId));
        return new RazorpayVerifyResult(
                order.getId(),
                order.getStatus().name(),
                payment.getStatus().name(),
                payment.getPaymentGroupId());
    }

    private static String hmacSha256Hex(String key, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return bytesToHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Result of verifying a Razorpay checkout.
     */
    public record RazorpayVerifyResult(
            Long orderId,
            String orderStatus,
            String paymentStatus,
            String paymentGroupId
    ) {
    }

    /**
     * Result of processing a Razorpay webhook.
     */
    public static class RazorpayWebhookResult {
        private final String providerEventId;
        private final boolean duplicate;
        private final boolean accepted;

        private RazorpayWebhookResult(String providerEventId, boolean duplicate, boolean accepted) {
            this.providerEventId = providerEventId;
            this.duplicate = duplicate;
            this.accepted = accepted;
        }

        public static RazorpayWebhookResult duplicate(String providerEventId) {
            return new RazorpayWebhookResult(providerEventId, true, false);
        }

        public static RazorpayWebhookResult accepted(String providerEventId) {
            return new RazorpayWebhookResult(providerEventId, false, true);
        }

        public String getProviderEventId() { return providerEventId; }
        public boolean isDuplicate() { return duplicate; }
        public boolean isAccepted() { return accepted; }
    }
}
