package com.flowerconnect.payment.service;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.gateway.PaymentGateway;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private PaymentService paymentService;

    private Order order;
    private Payment payment;
    private Instant fixedInstant;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-10-20T10:00:00Z");
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        order = Order.builder()
                .id(100L)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        payment = Payment.builder()
                .id(1L)
                .order(order)
                .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.COD)
                .provider("cod")
                .amount(new BigDecimal("643.00"))
                .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING)
                .collectedAt(null)
                .build();
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
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));

        Payment result = paymentService.collectPayment(100L);

        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COLLECTED);
        assertThat(result.getCollectedAt()).isEqualTo(LocalDateTime.parse("2026-10-20T10:00:00"));
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COLLECTED);
        verify(paymentRepository).saveAndFlush(payment);
        verify(orderRepository).saveAndFlush(order);
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
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
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
        Payment alreadyCollected = Payment.builder()
                .id(2L)
                .order(order)
                .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.COD)
                .provider("cod")
                .amount(new BigDecimal("643.00"))
                .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.COLLECTED)
                .collectedAt(LocalDateTime.parse("2026-10-20T09:00:00"))
                .build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(alreadyCollected));

        assertThatThrownBy(() -> paymentService.collectPayment(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already COLLECTED");
        verify(orderRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }
}
