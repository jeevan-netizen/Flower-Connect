package com.flowerconnect.payment.service;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.OrderStatusHistory;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentIntentRepository paymentIntentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStateMachine orderStateMachine;
    @Mock
    private ObjectProvider<OrderStateMachine> orderStateMachineProvider;
    @Mock
    private Clock clock;

    @InjectMocks
    private PaymentService paymentService;

    private Order order;
    private Payment payment;
    private PaymentIntent intent;
    private Instant fixedInstant;
    private com.flowerconnect.domain.User customerUser;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-10-20T10:00:00Z");
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        customerUser = com.flowerconnect.domain.User.builder()
                .id(10L)
                .email("customer@test.com")
                .build();

        order = Order.builder()
                .id(100L)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PENDING)
                .user(customerUser)
                .build();

        payment = Payment.builder()
                .id(1L)
                .order(order)
                .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE)
                .provider("simulated")
                .providerReference("intent_sim_" + UUID.randomUUID())
                .amount(new BigDecimal("643.00"))
                .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING)
                .collectedAt(null)
                .paymentGroupId("group-" + UUID.randomUUID())
                .build();

        intent = PaymentIntent.builder()
                .id(1L)
                .paymentGroupId(payment.getPaymentGroupId())
                .total(new BigDecimal("643.00"))
                .status(IntentStatus.CREATED)
                .providerReference("intent_sim_" + UUID.randomUUID())
                .expiresAt(LocalDateTime.parse("2026-10-20T10:30:00"))
                .build();

        when(orderStateMachineProvider.getObject()).thenReturn(orderStateMachine);
    }

    @Test
    void createCodPaymentDelegatesToCodProviderAndReturnsRecord() {
        when(paymentGateway.createPayment(order, new BigDecimal("643.00")))
                .thenReturn(payment);

        Payment result = paymentService.createCodPayment(order, new BigDecimal("643.00"));

        assertThat(result).isEqualTo(payment);
        verify(paymentGateway).createPayment(order, new BigDecimal("643.00"));
        verifyNoMoreInteractions(paymentRepository);
    }

    @Test
    void collectPaymentTransitionsPendingToCollectedAndStampsCollectedAt() {
        Order codOrder = Order.builder()
                .id(100L)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .user(customerUser)
                .build();
        Payment codPayment = Payment.builder()
                .id(1L)
                .order(codOrder)
                .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.COD)
                .provider("cod")
                .amount(new BigDecimal("643.00"))
                .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING)
                .collectedAt(null)
                .build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(codOrder));
        when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(codPayment));

        Payment result = paymentService.collectPayment(100L);

        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COLLECTED);
        assertThat(result.getCollectedAt()).isEqualTo(LocalDateTime.parse("2026-10-20T10:00:00"));
        assertThat(codOrder.getPaymentStatus()).isEqualTo(PaymentStatus.COLLECTED);
        verify(paymentRepository).saveAndFlush(codPayment);
        verify(orderRepository).saveAndFlush(codOrder);
    }

    @Test
    void collectPaymentRejectsUnknownOrder() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.collectPayment(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void collectPaymentRejectsUnknownPayment() {
        Order codOrder = Order.builder()
                .id(100L)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .user(customerUser)
                .build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(codOrder));
        when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.collectPayment(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Payment not found for order");
    }

    @Test
    void collectPaymentRejectsNonCodOrder() {
        Order nonCod = Order.builder()
                .id(101L)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        when(orderRepository.findById(101L)).thenReturn(Optional.of(nonCod));

        assertThatThrownBy(() -> paymentService.collectPayment(101L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not a COD order");
    }

    @Test
    void collectPaymentRejectsNonPendingPayment() {
        Order codOrder = Order.builder()
                .id(100L)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .user(customerUser)
                .build();
        Payment alreadyCollected = Payment.builder()
                .id(2L)
                .order(codOrder)
                .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.COD)
                .provider("cod")
                .amount(new BigDecimal("643.00"))
                .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.COLLECTED)
                .collectedAt(LocalDateTime.parse("2026-10-20T09:00:00"))
                .build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(codOrder));
        when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(alreadyCollected));

        assertThatThrownBy(() -> paymentService.collectPayment(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already COLLECTED");
        verify(orderRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------
    // Stage 2: fetchPaymentState
    // ------------------------------------------------------------------

    @Test
    void fetchPaymentStateReturnsOnlinePaymentState() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));
        when(paymentIntentRepository.findByPaymentGroupId(payment.getPaymentGroupId()))
                .thenReturn(Optional.of(intent));

        PaymentStateResponse result = paymentService.fetchPaymentState(100L, "customer@test.com");

        assertThat(result.getOrderId()).isEqualTo(100L);
        assertThat(result.getMethod()).isEqualTo("ONLINE");
        assertThat(result.getPaymentStatus()).isEqualTo("PENDING");
        assertThat(result.getOrderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.getPaymentGroupId()).isEqualTo(payment.getPaymentGroupId());
        assertThat(result.getIntentReference()).isEqualTo(intent.getProviderReference());
    }

    @Test
    void fetchPaymentStateRejectsUnknownOrder() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.fetchPaymentState(999L, "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void fetchPaymentStateRejectsForeignOrder() {
        Order foreignOrder = Order.builder()
                .id(101L)
                .user(com.flowerconnect.domain.User.builder().email("other@test.com").build())
                .build();
        when(orderRepository.findById(101L)).thenReturn(Optional.of(foreignOrder));

        assertThatThrownBy(() -> paymentService.fetchPaymentState(101L, "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("belongs to another customer");
    }

    // ------------------------------------------------------------------
    // Stage 2: fetchGroupState
    // ------------------------------------------------------------------

    @Test
    void fetchGroupStateReturnsGroupWithOrders() {
        when(paymentIntentRepository.findByPaymentGroupId(payment.getPaymentGroupId()))
                .thenReturn(Optional.of(intent));
        when(paymentRepository.findByPaymentGroupId(payment.getPaymentGroupId()))
                .thenReturn(List.of(payment));

        PaymentGroupStateResponse result = paymentService.fetchGroupState(
                payment.getPaymentGroupId(), "customer@test.com");

        assertThat(result.getPaymentGroupId()).isEqualTo(payment.getPaymentGroupId());
        assertThat(result.getStatus()).isEqualTo(IntentStatus.CREATED.name());
        assertThat(result.getOrders()).hasSize(1);
        assertThat(result.getOrders().get(0).getOrderId()).isEqualTo(100L);
    }

    @Test
    void fetchGroupStateRejectsUnknownGroup() {
        when(paymentIntentRepository.findByPaymentGroupId("unknown-group"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.fetchGroupState("unknown-group", "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void fetchGroupStateRejectsForeignGroup() {
        Order foreignOrder = Order.builder()
                .id(101L)
                .user(com.flowerconnect.domain.User.builder().email("other@test.com").build())
                .build();
        Payment foreignPayment = Payment.builder()
                .id(2L)
                .order(foreignOrder)
                .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE)
                .provider("simulated")
                .amount(new BigDecimal("100.00"))
                .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING)
                .paymentGroupId(payment.getPaymentGroupId())
                .build();
        when(paymentIntentRepository.findByPaymentGroupId(payment.getPaymentGroupId()))
                .thenReturn(Optional.of(intent));
        when(paymentRepository.findByPaymentGroupId(payment.getPaymentGroupId()))
                .thenReturn(List.of(foreignPayment));

        assertThatThrownBy(() -> paymentService.fetchGroupState(
                payment.getPaymentGroupId(), "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("belongs to another customer");
    }

    // ------------------------------------------------------------------
    // Stage 2: resumePayment
    // ------------------------------------------------------------------

    @Test
    void resumePaymentTransitionsThroughStateMachineAndSetsPaid() {
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
        when(paymentIntentRepository.findByPaymentGroupId(payment.getPaymentGroupId()))
                .thenReturn(Optional.of(intent));
        Order placed = Order.builder()
                .id(100L)
                .status(OrderStatus.PLACED)
                .build();
        when(orderStateMachine.transition(
                eq(100L), eq(OrderStatus.PLACED), eq(ActorType.SYSTEM),
                eq(null), anyString()))
            .thenAnswer(inv -> { order.setStatus(OrderStatus.PLACED); return placed; });
        when(clock.instant()).thenReturn(fixedInstant);

        PaymentResumeResponse result = paymentService.resumePayment(100L, "customer@test.com");

        assertThat(result.getOrderId()).isEqualTo(100L);
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.PLACED.name());
        assertThat(result.getPaymentStatus()).isEqualTo("PAID");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(com.flowerconnect.payment.domain.Payment.PaymentStatus.PAID);
        assertThat(intent.getStatus()).isEqualTo(IntentStatus.PAID);
        verify(orderStateMachine).transition(
                eq(100L), eq(OrderStatus.PLACED), eq(ActorType.SYSTEM), eq(null), anyString());
    }

    @Test
    void resumePaymentRejectsNonPendingOrder() {
        Order notPending = Order.builder()
                .id(100L)
                .status(OrderStatus.PLACED)
                .paymentMethod(PaymentMethod.ONLINE)
                .user(customerUser)
                .build();
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(notPending));

        assertThatThrownBy(() -> paymentService.resumePayment(100L, "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not in PENDING_PAYMENT state");
    }

    @Test
    void resumePaymentRejectsNonOnlineOrder() {
        Order codOrder = Order.builder()
                .id(100L)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentMethod(PaymentMethod.COD)
                .user(customerUser)
                .build();
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(codOrder));

        assertThatThrownBy(() -> paymentService.resumePayment(100L, "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not an ONLINE payment order");
    }

    @Test
    void resumePaymentRejectsExpiredIntent() {
        PaymentIntent expiredIntent = PaymentIntent.builder()
                .id(1L)
                .paymentGroupId(payment.getPaymentGroupId())
                .total(new BigDecimal("643.00"))
                .status(IntentStatus.CREATED)
                .providerReference("intent_sim_expired")
                .expiresAt(LocalDateTime.parse("2026-10-20T09:00:00"))
                .build();
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
        when(paymentIntentRepository.findByPaymentGroupId(payment.getPaymentGroupId()))
                .thenReturn(Optional.of(expiredIntent));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-20T10:00:00Z"));

        assertThatThrownBy(() -> paymentService.resumePayment(100L, "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has expired");
    }

    @Test
    void resumePaymentRejectsForeignOrder() {
        Order foreignOrder = Order.builder()
                .id(100L)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentMethod(PaymentMethod.ONLINE)
                .user(com.flowerconnect.domain.User.builder().email("other@test.com").build())
                .build();
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(foreignOrder));

        assertThatThrownBy(() -> paymentService.resumePayment(100L, "customer@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("belongs to another customer");
    }
}
