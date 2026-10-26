/*
 * Copyright 2026 FlowerConnect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flowerconnect.billing.service;

import com.flowerconnect.billing.domain.Commission;
import com.flowerconnect.billing.repository.CommissionRepository;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock
    private CommissionRepository commissionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CommissionRateService commissionRateService;
    @Mock
    private BillingLedgerService billingLedgerService;

    private CommissionService commissionService;

    private VendorProfile vendor;
    private User customer;
    private Order deliveredOrder;

    @BeforeEach
    void setUp() {
        commissionService = new CommissionService(commissionRepository, orderRepository, commissionRateService, billingLedgerService);

        vendor = VendorProfile.builder()
                .id(1L)
                .commissionRate(new BigDecimal("10.00"))
                .build();

        customer = User.builder()
                .id(2L)
                .email("customer@test.com")
                .build();

        deliveredOrder = Order.builder()
                .id(100L)
                .vendor(vendor)
                .user(customer)
                .status(Order.OrderStatus.DELIVERED)
                .subtotal(new BigDecimal("500.00"))
                .deliveryFee(new BigDecimal("50.00"))
                .platformFee(new BigDecimal("10.00"))
                .total(new BigDecimal("560.00"))
                .build();
    }

    @Test
    @DisplayName("Creates commission row for DELIVERED order with subtotal-only calculation")
    void createCommissionForDeliveredOrder() {
        when(commissionRepository.existsByOrderId(100L)).thenReturn(false);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(deliveredOrder));
        when(commissionRateService.resolveRate(vendor)).thenReturn(new BigDecimal("0.1000"));
        when(commissionRateService.toPercentage(new BigDecimal("0.1000"))).thenReturn(new BigDecimal("10.00"));
        when(commissionRepository.saveAndFlush(any(Commission.class))).thenAnswer(inv -> inv.getArgument(0));

        Commission commission = commissionService.createOrGet(deliveredOrder);

        assertThat(commission).isNotNull();
        // 500.00 * 0.1000 = 50.00
        assertThat(commission.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(commission.getSubtotal()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(commission.getDeliveryFee()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(commission.getPlatformFee()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(commission.getCommissionRate()).isEqualByComparingTo(new BigDecimal("10.00"));

        ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
        verify(commissionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOrder()).isEqualTo(deliveredOrder);
    }

    @Test
    @DisplayName("Subtotal rounding test: subtotal 333.33 at 7.5% gives exactly 25.00 HALF_UP")
    void rounding333At7Point5Percent() {
        Order specialOrder = Order.builder()
                .id(101L)
                .vendor(vendor)
                .user(customer)
                .status(Order.OrderStatus.DELIVERED)
                .subtotal(new BigDecimal("333.33"))
                .deliveryFee(new BigDecimal("40.00"))
                .platformFee(new BigDecimal("5.00"))
                .total(new BigDecimal("378.33"))
                .build();

        when(commissionRepository.existsByOrderId(101L)).thenReturn(false);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(specialOrder));
        // 7.5% -> 0.0750 multiplier
        when(commissionRateService.resolveRate(vendor)).thenReturn(new BigDecimal("0.0750"));
        when(commissionRateService.toPercentage(new BigDecimal("0.0750"))).thenReturn(new BigDecimal("7.50"));
        when(commissionRepository.saveAndFlush(any(Commission.class))).thenAnswer(inv -> inv.getArgument(0));

        Commission commission = commissionService.createOrGet(specialOrder);

        // 333.33 * 0.0750 = 24.99975 -> HALF_UP = 25.00
        assertThat(commission.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("Non-delivered orders (e.g. CANCELLED, PLACED) are not commissioned")
    void nonDeliveredOrdersExcluded() {
        Order cancelledOrder = Order.builder()
                .id(102L)
                .vendor(vendor)
                .user(customer)
                .status(Order.OrderStatus.CANCELLED)
                .subtotal(new BigDecimal("200.00"))
                .build();

        when(commissionRepository.existsByOrderId(102L)).thenReturn(false);
        when(orderRepository.findById(102L)).thenReturn(Optional.of(cancelledOrder));

        Commission result = commissionService.createOrGet(cancelledOrder);

        assertThat(result).isNull();
        verify(commissionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Idempotent replay: existing commission row is returned without duplicate save")
    void idempotentReplay() {
        Commission existing = Commission.builder()
                .id(50L)
                .order(deliveredOrder)
                .commissionAmount(new BigDecimal("50.00"))
                .build();

        when(commissionRepository.existsByOrderId(100L)).thenReturn(true);
        when(commissionRepository.findByOrderId(100L)).thenReturn(Optional.of(existing));

        Commission result = commissionService.createOrGet(deliveredOrder);

        assertThat(result).isSameAs(existing);
        verify(commissionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Backfill processes delivered orders without commission")
    void backfillDeliveredOrders() {
        Order order1 = Order.builder().id(201L).vendor(vendor).user(customer).status(Order.OrderStatus.DELIVERED)
                .subtotal(new BigDecimal("100.00")).deliveryFee(BigDecimal.ZERO).platformFee(BigDecimal.ZERO).build();
        Order order2 = Order.builder().id(202L).vendor(vendor).user(customer).status(Order.OrderStatus.DELIVERED)
                .subtotal(new BigDecimal("200.00")).deliveryFee(BigDecimal.ZERO).platformFee(BigDecimal.ZERO).build();

        when(orderRepository.findDeliveredOrdersWithoutCommission()).thenReturn(List.of(order1, order2));
        when(commissionRepository.existsByOrderId(201L)).thenReturn(false);
        when(commissionRepository.existsByOrderId(202L)).thenReturn(false);
        when(orderRepository.findById(201L)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(202L)).thenReturn(Optional.of(order2));
        when(commissionRateService.resolveRate(vendor)).thenReturn(new BigDecimal("0.1000"));
        when(commissionRepository.saveAndFlush(any(Commission.class))).thenAnswer(inv -> inv.getArgument(0));

        int backfilled = commissionService.backfillDeliveredOrders();

        assertThat(backfilled).isEqualTo(2);
    }
}
