package com.flowerconnect.payment.provider;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.Payment.PaymentMethod;
import com.flowerconnect.payment.domain.Payment.PaymentStatus;
import com.flowerconnect.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodPaymentProviderTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private CodPaymentProvider provider;

    @Test
    void createPaymentPersistsPendingCodRecordWithNoExternalCall() {
        Order order = Order.builder().id(42L).build();
        BigDecimal amount = new BigDecimal("123.45");
        Payment saved = Payment.builder()
                .id(7L).order(order).method(PaymentMethod.COD)
                .provider("cod").amount(amount).status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(saved);

        Payment result = provider.createPayment(order, amount);

        assertThat(result).isEqualTo(saved);
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(result.getProvider()).isEqualTo("cod");
        assertThat(result.getProviderReference()).isNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getCollectedAt()).isNull();
        assertThat(result.getAmount()).isEqualByComparingTo(amount);
        verify(paymentRepository).saveAndFlush(argThat(p ->
                p.getOrder().getId().equals(42L)
                        && p.getMethod() == PaymentMethod.COD
                        && p.getStatus() == PaymentStatus.PENDING));
    }
}
