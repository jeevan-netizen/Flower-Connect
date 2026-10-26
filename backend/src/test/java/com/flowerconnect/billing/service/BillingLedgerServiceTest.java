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
import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.dispute.domain.Dispute;
import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingLedgerServiceTest {

    @Mock
    private SettlementEntryRepository settlementEntryRepository;

    private BillingLedgerService billingLedgerService;

    private VendorProfile vendor;
    private Order deliveredOnlineOrder;
    private Order deliveredCodOrder;
    private Commission onlineCommission;
    private Commission codCommission;

    @BeforeEach
    void setUp() {
        billingLedgerService = new BillingLedgerService(settlementEntryRepository);

        vendor = VendorProfile.builder()
                .id(1L)
                .commissionRate(new BigDecimal("10.00"))
                .build();

        // Worked example 1: S = 100.00, D = 20.00, P = 0.00, C = 10.00
        deliveredOnlineOrder = Order.builder()
                .id(100L)
                .vendor(vendor)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.ONLINE)
                .subtotal(new BigDecimal("100.00"))
                .deliveryFee(new BigDecimal("20.00"))
                .platformFee(BigDecimal.ZERO)
                .total(new BigDecimal("120.00"))
                .build();

        onlineCommission = Commission.builder()
                .id(10L)
                .order(deliveredOnlineOrder)
                .subtotal(new BigDecimal("100.00"))
                .commissionAmount(new BigDecimal("10.00"))
                .build();

        deliveredCodOrder = Order.builder()
                .id(101L)
                .vendor(vendor)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.COD)
                .subtotal(new BigDecimal("100.00"))
                .deliveryFee(new BigDecimal("20.00"))
                .platformFee(BigDecimal.ZERO)
                .total(new BigDecimal("120.00"))
                .build();

        codCommission = Commission.builder()
                .id(11L)
                .order(deliveredCodOrder)
                .subtotal(new BigDecimal("100.00"))
                .commissionAmount(new BigDecimal("10.00"))
                .build();
    }

    @Test
    @DisplayName("Online delivery formula: ONLINE_ORDER_CREDIT = +(S - C + D) = +(100 - 10 + 20) = +110.00")
    void onlineDeliveryFormulaWorkedExample() {
        when(settlementEntryRepository.existsByOrderId(100L)).thenReturn(false);
        when(settlementEntryRepository.saveAndFlush(any(SettlementEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementEntry entry = billingLedgerService.recordOrderDelivery(deliveredOnlineOrder, onlineCommission);

        assertThat(entry).isNotNull();
        assertThat(entry.getType()).isEqualTo(SettlementEntryType.ONLINE_ORDER_CREDIT);
        assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("110.00"));
        assertThat(entry.getOrder()).isEqualTo(deliveredOnlineOrder);
        assertThat(entry.getVendor()).isEqualTo(vendor);
    }

    @Test
    @DisplayName("COD delivery formula: COD_DUES = -(C + P) = -(10 + 0) = -10.00")
    void codDeliveryFormulaWorkedExample() {
        when(settlementEntryRepository.existsByOrderId(101L)).thenReturn(false);
        when(settlementEntryRepository.saveAndFlush(any(SettlementEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementEntry entry = billingLedgerService.recordOrderDelivery(deliveredCodOrder, codCommission);

        assertThat(entry).isNotNull();
        assertThat(entry.getType()).isEqualTo(SettlementEntryType.COD_DUES);
        assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("-10.00"));
        assertThat(entry.getOrder()).isEqualTo(deliveredCodOrder);
        assertThat(entry.getVendor()).isEqualTo(vendor);
    }

    @Test
    @DisplayName("Formula with non-zero platform fee and 7.5% rounding: Online = +348.33, COD = -40.00")
    void roundingWithPlatformFeeFormula() {
        // S = 333.33, C = 25.00, D = 40.00, P = 15.00
        Order onlineOrder2 = Order.builder()
                .id(102L)
                .vendor(vendor)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.ONLINE)
                .subtotal(new BigDecimal("333.33"))
                .deliveryFee(new BigDecimal("40.00"))
                .platformFee(new BigDecimal("15.00"))
                .total(new BigDecimal("388.33"))
                .build();
        Commission comm2 = Commission.builder()
                .order(onlineOrder2)
                .commissionAmount(new BigDecimal("25.00"))
                .build();

        when(settlementEntryRepository.existsByOrderId(102L)).thenReturn(false);
        when(settlementEntryRepository.saveAndFlush(any(SettlementEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementEntry onlineEntry = billingLedgerService.recordOrderDelivery(onlineOrder2, comm2);
        // +(333.33 - 25.00 + 40.00) = +348.33
        assertThat(onlineEntry.getAmount()).isEqualByComparingTo(new BigDecimal("348.33"));

        Order codOrder2 = Order.builder()
                .id(103L)
                .vendor(vendor)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.COD)
                .subtotal(new BigDecimal("333.33"))
                .deliveryFee(new BigDecimal("40.00"))
                .platformFee(new BigDecimal("15.00"))
                .total(new BigDecimal("388.33"))
                .build();

        when(settlementEntryRepository.existsByOrderId(103L)).thenReturn(false);
        SettlementEntry codEntry = billingLedgerService.recordOrderDelivery(codOrder2, comm2);
        // -(25.00 + 15.00) = -40.00
        assertThat(codEntry.getAmount()).isEqualByComparingTo(new BigDecimal("-40.00"));
    }

    @Test
    @DisplayName("Non-delivered orders (e.g. CANCELLED, REJECTED) never create ledger entries")
    void nonDeliveredOrdersExcluded() {
        Order cancelled = Order.builder().id(104L).status(OrderStatus.CANCELLED).build();
        Order rejected = Order.builder().id(105L).status(OrderStatus.REJECTED).build();

        assertThat(billingLedgerService.recordOrderDelivery(cancelled, onlineCommission)).isNull();
        assertThat(billingLedgerService.recordOrderDelivery(rejected, codCommission)).isNull();
        verify(settlementEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Idempotent delivery ledger creation returns existing row without duplicate insert")
    void deliveryLedgerIdempotent() {
        SettlementEntry existing = SettlementEntry.builder().id(55L).order(deliveredOnlineOrder).amount(new BigDecimal("110.00")).build();
        when(settlementEntryRepository.existsByOrderId(100L)).thenReturn(true);
        when(settlementEntryRepository.findByOrderId(100L)).thenReturn(Optional.of(existing));

        SettlementEntry result = billingLedgerService.recordOrderDelivery(deliveredOnlineOrder, onlineCommission);

        assertThat(result).isSameAs(existing);
        verify(settlementEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Dispute resolution: RESOLVED_PARTIAL creates DISPUTE_ADJUSTMENT with -refundAmount")
    void disputePartialRefundAdjustment() {
        Dispute dispute = Dispute.builder().id(501L).order(deliveredOnlineOrder).build();
        when(settlementEntryRepository.existsByDisputeId(501L)).thenReturn(false);
        when(settlementEntryRepository.saveAndFlush(any(SettlementEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementEntry entry = billingLedgerService.recordDisputeAdjustment(
                dispute, DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("30.00"));

        assertThat(entry).isNotNull();
        assertThat(entry.getType()).isEqualTo(SettlementEntryType.DISPUTE_ADJUSTMENT);
        assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("-30.00"));
        assertThat(entry.getDispute()).isEqualTo(dispute);
    }

    @Test
    @DisplayName("Dispute resolution: RESOLVED_REFUND full reversal negates original ONLINE_ORDER_CREDIT")
    void disputeFullRefundReversal() {
        Dispute dispute = Dispute.builder().id(502L).order(deliveredOnlineOrder).build();
        SettlementEntry originalCredit = SettlementEntry.builder()
                .id(20L)
                .order(deliveredOnlineOrder)
                .type(SettlementEntryType.ONLINE_ORDER_CREDIT)
                .amount(new BigDecimal("110.00"))
                .build();

        when(settlementEntryRepository.existsByDisputeId(502L)).thenReturn(false);
        when(settlementEntryRepository.findByOrderId(100L)).thenReturn(Optional.of(originalCredit));
        when(settlementEntryRepository.saveAndFlush(any(SettlementEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementEntry entry = billingLedgerService.recordDisputeAdjustment(
                dispute, DisputeStatus.RESOLVED_REFUND, new BigDecimal("120.00"));

        assertThat(entry).isNotNull();
        assertThat(entry.getType()).isEqualTo(SettlementEntryType.DISPUTE_ADJUSTMENT);
        // Negates original credit of 110.00 -> -110.00
        assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("-110.00"));
    }

    @Test
    @DisplayName("Dispute resolution: RESOLVED_REJECTED creates no ledger entry")
    void disputeRejectedNoLedgerEntry() {
        Dispute dispute = Dispute.builder().id(503L).order(deliveredOnlineOrder).build();
        SettlementEntry entry = billingLedgerService.recordDisputeAdjustment(
                dispute, DisputeStatus.RESOLVED_REJECTED, null);

        assertThat(entry).isNull();
        verify(settlementEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Outstanding balance returns signed sum of unattached/pending entries")
    void outstandingBalanceCalculation() {
        when(settlementEntryRepository.calculateOutstandingBalance(1L)).thenReturn(new BigDecimal("70.00"));

        BigDecimal balance = billingLedgerService.getOutstandingBalance(1L);

        assertThat(balance).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    @DisplayName("SettlementEntry immutability: has no public setters for core fields")
    void settlementEntryIsImmutable() {
        for (Method method : SettlementEntry.class.getMethods()) {
            String name = method.getName();
            if (name.startsWith("set") && !name.equals("setSettlement")) {
                org.junit.jupiter.api.Assertions.fail("Found unexpected setter on SettlementEntry: " + name);
            }
        }

        // Test attachToSettlement only works once
        Settlement settlement1 = Settlement.builder().id(1L).build();
        Settlement settlement2 = Settlement.builder().id(2L).build();

        SettlementEntry entry = SettlementEntry.builder().id(10L).build();
        entry.attachToSettlement(settlement1);
        assertThat(entry.getSettlement()).isEqualTo(settlement1);

        assertThatThrownBy(() -> entry.attachToSettlement(settlement2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already attached");
    }
}
