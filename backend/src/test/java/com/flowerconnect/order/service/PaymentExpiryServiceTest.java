package com.flowerconnect.order.service;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentExpiryService} configuration reading — the parts
 * not exercised by {@link com.flowerconnect.order.PaymentExpiryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentExpiryServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStateMachine orderStateMachine;
    @Mock
    private AppProperties appProperties;
    @Mock
    private Clock clock;

    @InjectMocks
    private PaymentExpiryService paymentExpiryService;

    @Test
    void getPaymentExpiryMinutesReturnsConfiguredValue() {
        when(appProperties.getPaymentExpiryMinutes()).thenReturn(45);
        assertEquals(45, paymentExpiryService.getPaymentExpiryMinutes());
    }

    @Test
    void getPaymentExpiryMinutesFallsBackToDocumentedDefault() {
        when(appProperties.getPaymentExpiryMinutes()).thenReturn(30);
        assertEquals(30, paymentExpiryService.getPaymentExpiryMinutes());
    }
}
