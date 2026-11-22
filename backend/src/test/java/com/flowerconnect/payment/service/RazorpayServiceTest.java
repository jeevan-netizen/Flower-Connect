package com.flowerconnect.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.domain.AuditActorType;
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
import com.flowerconnect.payment.client.RazorpayOrder;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.Payment.PaymentMethod;
import com.flowerconnect.payment.domain.PaymentEvent;
import com.flowerconnect.payment.repository.PaymentEventRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RazorpayServiceTest {

    @Mock
    private RazorpayClient razorpayClient;
    @Mock
    private AppProperties appProperties;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentEventRepository paymentEventRepository;
    @Mock
    private ObjectProvider<OrderStateMachine> orderStateMachineProvider;
    @Mock
    private OrderStateMachine orderStateMachine;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private Clock clock;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RazorpayService razorpayService;

    private User customer;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(100L)
                .email("customer@test.com")
                .role(null)
                .build();

        order = Order.builder()
                .id(500L)
                .user(customer)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentMethod(Order.PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        payment = Payment.builder()
                .id(700L)
                .order(order)
                .method(PaymentMethod.ONLINE)
                .provider("razorpay")
                .providerReference("order_test_123")
                .amount(new BigDecimal("329.00"))
                .status(Payment.PaymentStatus.PENDING)
                .paymentGroupId("group_abc")
                .build();

        when(orderStateMachineProvider.getObject()).thenReturn(orderStateMachine);
        when(paymentEventRepository.existsByProviderEventId(anyString())).thenReturn(false);
        when(paymentRepository.findByOrderId(500L)).thenReturn(Optional.of(payment));
        when(userRepository.findByEmailWithRole("customer@test.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findCustomerIdByOrderId(500L)).thenReturn(Optional.of(100L));
    }

    // ------------------------------------------------------------------
    // createOrder
    // ------------------------------------------------------------------

    @Test
    void createOrderCallsRazorpayClientWithAmountInPaise() {
        when(appProperties.getRazorpayKeyId()).thenReturn("rzp_test_123");
        when(appProperties.getRazorpayKeySecret()).thenReturn("secret_key_at_least_32_characters_long");
        when(razorpayClient.createOrder(
                eq(new BigDecimal("32900")), eq("INR"), anyString()))
                .thenReturn(new RazorpayOrder("order_test_123",
                        new BigDecimal("32900"), "INR", "created", "receipt_1"));

        RazorpayOrder result = razorpayService.createOrder(new BigDecimal("329.00"), "receipt_1");

        assertThat(result.id()).isEqualTo("order_test_123");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("32900"));
        assertThat(result.currency()).isEqualTo("INR");
    }

    @Test
    void createOrderThrowsWhenCredentialsMissing() {
        when(appProperties.getRazorpayKeyId()).thenReturn(null);
        when(appProperties.getRazorpayKeySecret()).thenReturn("secret");

        assertThatThrownBy(() -> razorpayService.createOrder(new BigDecimal("100.00"), "receipt_1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .hasMessageContaining("Razorpay Key ID and Key Secret must be configured");
    }

    // ------------------------------------------------------------------
    // verifyCheckout - signature verification
    // ------------------------------------------------------------------

    @Test
    void verifyCheckoutRejectsInvalidSignature() {
        when(appProperties.getRazorpayKeySecret()).thenReturn("secret_key_at_least_32_characters_long");

        assertThatThrownBy(() -> razorpayService.verifyCheckout(
                500L, "order_test_123", "pay_test_456", "bad_signature", "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .hasMessageContaining("Invalid Razorpay signature");
    }

    @Test
    void verifyCheckoutAcceptsValidSignature() throws Exception {
        when(appProperties.getRazorpayKeySecret()).thenReturn("secret_key_at_least_32_characters_long");
        when(paymentEventRepository.existsByProviderEventId("pay_test_456")).thenReturn(false);
        when(paymentRepository.findByProviderReference("order_test_123")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(orderRepository.findCustomerIdByOrderId(500L)).thenReturn(Optional.of(100L));
        when(userRepository.findByEmailWithRole("customer@test.com")).thenReturn(Optional.of(customer));

        String validSig = RazorpayServiceTest.hmacSha256("secret_key_at_least_32_characters_long",
                "order_test_123|pay_test_456");

        razorpayService.verifyCheckout(500L, "order_test_123", "pay_test_456", validSig, "customer@test.com");

        // Payment marked PAID
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        verify(paymentRepository).saveAndFlush(payment);

        // Order transitioned to PLACED via state machine
        verify(orderStateMachine).transition(
                eq(500L), eq(OrderStatus.PLACED), eq(ActorType.SYSTEM), eq(null),
                eq("Razorpay payment captured"));

        // Payment event persisted for idempotency
        verify(paymentEventRepository).saveAndFlush(any(PaymentEvent.class));
    }

    @Test
    void verifyCheckoutIdempotentOnDuplicatePaymentId() throws Exception {
        when(appProperties.getRazorpayKeySecret()).thenReturn("secret_key_at_least_32_characters_long");
        when(paymentEventRepository.existsByProviderEventId("pay_test_456")).thenReturn(true);
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(orderRepository.findCustomerIdByOrderId(500L)).thenReturn(Optional.of(100L));
        when(userRepository.findByEmailWithRole("customer@test.com")).thenReturn(Optional.of(customer));

        String validSig = RazorpayServiceTest.hmacSha256("secret_key_at_least_32_characters_long",
                "order_test_123|pay_test_456");

        razorpayService.verifyCheckout(500L, "order_test_123", "pay_test_456", validSig, "customer@test.com");

        // No state change for duplicate
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(orderStateMachine, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    void verifyCheckoutRejectsForeignOrder() throws Exception {
        when(appProperties.getRazorpayKeySecret()).thenReturn("secret_key_at_least_32_characters_long");
        when(paymentEventRepository.existsByProviderEventId("pay_test_456")).thenReturn(false);
        when(paymentRepository.findByProviderReference("order_test_123")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(orderRepository.findCustomerIdByOrderId(500L)).thenReturn(Optional.of(999L)); // different user

        String validSig = RazorpayServiceTest.hmacSha256("secret_key_at_least_32_characters_long",
                "order_test_123|pay_test_456");

        assertThatThrownBy(() -> razorpayService.verifyCheckout(
                500L, "order_test_123", "pay_test_456", validSig, "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    void verifyCheckoutRejectsNonPendingOrder() throws Exception {
        when(appProperties.getRazorpayKeySecret()).thenReturn("secret_key_at_least_32_characters_long");
        when(paymentEventRepository.existsByProviderEventId("pay_test_456")).thenReturn(false);
        when(paymentRepository.findByProviderReference("order_test_123")).thenReturn(Optional.of(payment));

        Order nonPending = Order.builder()
                .id(500L)
                .user(customer)
                .status(OrderStatus.PLACED) // already placed
                .paymentMethod(Order.PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        when(orderRepository.findById(500L)).thenReturn(Optional.of(nonPending));
        when(orderRepository.findCustomerIdByOrderId(500L)).thenReturn(Optional.of(100L));
        when(userRepository.findByEmailWithRole("customer@test.com")).thenReturn(Optional.of(customer));

        String validSig = RazorpayServiceTest.hmacSha256("secret_key_at_least_32_characters_long",
                "order_test_123|pay_test_456");

        assertThatThrownBy(() -> razorpayService.verifyCheckout(
                500L, "order_test_123", "pay_test_456", validSig, "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
    }

    // ------------------------------------------------------------------
    // processWebhook
    // ------------------------------------------------------------------

    @Test
    void processWebhookCapturedMarksPaidAndPlacesOrder() throws Exception {
        when(appProperties.getRazorpayWebhookSecret()).thenReturn("wh_secret_at_least_32_characters_long_here");
        when(paymentEventRepository.existsByProviderEventId("evt_1")).thenReturn(false);
        when(paymentRepository.findByProviderReference("order_test_123")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));

        byte[] body = "{\"id\":\"evt_1\",\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_test_456\",\"order_id\":\"order_test_123\",\"amount\":32900}}}}"
                .getBytes();
        String sig = RazorpayServiceTest.hmacSha256Hex("wh_secret_at_least_32_characters_long_here", body);

        RazorpayService.RazorpayWebhookResult result = razorpayService.processWebhook(sig, body);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.isDuplicate()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        verify(orderStateMachine).transition(
                eq(500L), eq(OrderStatus.PLACED), eq(ActorType.SYSTEM), eq(null),
                eq("Razorpay webhook payment captured"));
    }

    @Test
    void processWebhookFailedCancelsOrder() throws Exception {
        when(appProperties.getRazorpayWebhookSecret()).thenReturn("wh_secret_at_least_32_characters_long_here");
        when(paymentEventRepository.existsByProviderEventId("evt_2")).thenReturn(false);
        when(paymentRepository.findByProviderReference("order_test_123")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));

        byte[] body = "{\"id\":\"evt_2\",\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_test_456\",\"order_id\":\"order_test_123\",\"amount\":32900}}}}"
                .getBytes();
        String sig = RazorpayServiceTest.hmacSha256Hex("wh_secret_at_least_32_characters_long_here", body);

        RazorpayService.RazorpayWebhookResult result = razorpayService.processWebhook(sig, body);

        assertThat(result.isAccepted()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        verify(orderStateMachine).transition(
                eq(500L), eq(OrderStatus.CANCELLED), eq(ActorType.SYSTEM), eq(null),
                eq("Razorpay payment failed"));
    }

    @Test
    void processWebhookRejectsInvalidSignature() {
        when(appProperties.getRazorpayWebhookSecret()).thenReturn("wh_secret_at_least_32_characters_long_here");

        assertThatThrownBy(() -> razorpayService.processWebhook("bad_sig", new byte[0]))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void processWebhookIdempotentOnDuplicateEventId() throws Exception {
        when(appProperties.getRazorpayWebhookSecret()).thenReturn("wh_secret_at_least_32_characters_long_here");
        when(paymentEventRepository.existsByProviderEventId("evt_dup")).thenReturn(true);

        byte[] body = "{\"id\":\"evt_dup\",\"event\":\"payment.captured\"}".getBytes();
        String sig = RazorpayServiceTest.hmacSha256Hex("wh_secret_at_least_32_characters_long_here", body);

        RazorpayService.RazorpayWebhookResult result = razorpayService.processWebhook(sig, body);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.isAccepted()).isFalse();
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(orderStateMachine, never()).transition(any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Signature verification helpers
    // ------------------------------------------------------------------

    static String hmacSha256(String key, String payload) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return bytesToHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    static String hmacSha256Hex(String key, byte[] payload) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return bytesToHex(mac.doFinal(payload));
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
