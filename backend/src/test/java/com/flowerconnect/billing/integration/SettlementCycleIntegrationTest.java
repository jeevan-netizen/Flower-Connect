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

package com.flowerconnect.billing.integration;

import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementDirection;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.billing.repository.SettlementRepository;
import com.flowerconnect.billing.service.BillingLedgerService;
import com.flowerconnect.billing.service.SettlementService;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
class SettlementCycleIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private OrderStateMachine orderStateMachine;
    @Autowired
    private BillingLedgerService billingLedgerService;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private SettlementEntryRepository settlementEntryRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PaymentService paymentService;

    private User vendorUser;
    private VendorProfile vendor;
    private User customer;
    private User adminUser;
    private ServiceLocation location;

    @BeforeEach
    void setUp() {
        Role floristRole = roleRepository.findByName("FLORIST").orElseThrow();
        vendorUser = userRepository.saveAndFlush(User.builder()
                .email("florist-cycle-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Florist Cycle User")
                .phone("+91" + randomDigits(10))
                .role(floristRole)
                .status(User.Status.ACTIVE)
                .build());

        Role customerRole = roleRepository.findByName("CUSTOMER").orElseThrow();
        customer = userRepository.saveAndFlush(User.builder()
                .email("customer-cycle-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Customer Cycle User")
                .phone("+91" + randomDigits(10))
                .role(customerRole)
                .status(User.Status.ACTIVE)
                .build());

        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        adminUser = userRepository.saveAndFlush(User.builder()
                .email("admin-cycle-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Admin Cycle User")
                .phone("+91" + randomDigits(10))
                .role(adminRole)
                .status(User.Status.ACTIVE)
                .build());

        location = serviceLocationRepository.findAll().stream().findFirst()
                .orElseGet(() -> serviceLocationRepository.saveAndFlush(ServiceLocation.builder()
                        .city("Bengaluru")
                        .area("Indiranagar")
                        .pincode("560038")
                        .timezone("Asia/Kolkata")
                        .latitude(BigDecimal.valueOf(12.97))
                        .longitude(BigDecimal.valueOf(77.64))
                        .build()));

        vendor = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(vendorUser)
                .businessName("Settlement Cycle Florist " + UUID.randomUUID())
                .addressLine1("500 Blossom Ave")
                .serviceLocation(location)
                .latitude(BigDecimal.valueOf(12.97))
                .longitude(BigDecimal.valueOf(77.64))
                .deliveryRadiusKm(new BigDecimal("10.00"))
                .status(VendorProfile.Status.APPROVED)
                .commissionRate(new BigDecimal("10.00")) // 10% rate
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());
    }

    @Test
    @DisplayName("End-to-end weekly settlement cycle: delivery entries -> settlement run -> entries attached -> finalize -> balance netting")
    void endToEndSettlementCycle() {
        // 1. Deliver Online order: S = 100.00, D = 20.00, P = 0.00, C = 10.00 -> Credit = +110.00
        Order onlineOrder = createOrder(new BigDecimal("100.00"), new BigDecimal("20.00"), BigDecimal.ZERO, PaymentMethod.ONLINE);
        orderStateMachine.transition(onlineOrder.getId(), OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(onlineOrder.getId(), OrderStatus.PREPARING, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(onlineOrder.getId(), OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(onlineOrder.getId(), OrderStatus.DELIVERED, ActorType.VENDOR, vendorUser.getId(), null);

        // 2. Deliver COD order: S = 200.00, D = 30.00, P = 0.00, C = 20.00 -> COD_DUES = -20.00
        Order codOrder = createOrder(new BigDecimal("200.00"), new BigDecimal("30.00"), BigDecimal.ZERO, PaymentMethod.COD);
        orderStateMachine.transition(codOrder.getId(), OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(codOrder.getId(), OrderStatus.PREPARING, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(codOrder.getId(), OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(codOrder.getId(), OrderStatus.DELIVERED, ActorType.VENDOR, vendorUser.getId(), null);

        // 3. Outstanding balance before settlement run: +110.00 - 20.00 = +90.00
        BigDecimal initialBalance = billingLedgerService.getOutstandingBalance(vendor.getId());
        assertThat(initialBalance).isEqualByComparingTo(new BigDecimal("90.00"));

        // 4. Run weekly settlement
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(7);
        LocalDate end = today;

        Settlement settlement = settlementService.runSettlement(vendor.getId(), start, end);
        assertThat(settlement).isNotNull();
        assertThat(settlement.getId()).isNotNull();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(settlement.getTotalCredits()).isEqualByComparingTo(new BigDecimal("110.00"));
        assertThat(settlement.getTotalDues()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(settlement.getNetAmount()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(settlement.getDirection()).isEqualTo(SettlementDirection.PLATFORM_PAYS_VENDOR);

        // 5. Verify entries attached to this settlement
        SettlementEntry reloadedOnline = settlementEntryRepository.findByOrderId(onlineOrder.getId()).orElseThrow();
        SettlementEntry reloadedCod = settlementEntryRepository.findByOrderId(codOrder.getId()).orElseThrow();
        assertThat(reloadedOnline.getSettlement()).isNotNull();
        assertThat(reloadedOnline.getSettlement().getId()).isEqualTo(settlement.getId());
        assertThat(reloadedCod.getSettlement()).isNotNull();
        assertThat(reloadedCod.getSettlement().getId()).isEqualTo(settlement.getId());

        // 6. Outstanding balance while settlement is PENDING is still +90.00 (not yet discharged)
        BigDecimal pendingBalance = billingLedgerService.getOutstandingBalance(vendor.getId());
        assertThat(pendingBalance).isEqualByComparingTo(new BigDecimal("90.00"));

        // 7. Idempotent re-run on PENDING returns existing settlement without duplicate rows
        Settlement rerunSettlement = settlementService.runSettlement(vendor.getId(), start, end);
        assertThat(rerunSettlement.getId()).isEqualTo(settlement.getId());

        // 8. Finalize settlement with admin reference note
        String refNote = "NEFT-REF-" + UUID.randomUUID();
        Settlement finalized = settlementService.finalizeSettlement(settlement.getId(), adminUser.getEmail(), refNote);
        assertThat(finalized.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(finalized.getSettledBy().getId()).isEqualTo(adminUser.getId());
        assertThat(finalized.getAdminReferenceNote()).isEqualTo(refNote);
        assertThat(finalized.getSettledAt()).isNotNull();

        // 9. Outstanding balance after SETTLED is 0.00 (settlement is finalized and accounted for)
        BigDecimal settledBalance = billingLedgerService.getOutstandingBalance(vendor.getId());
        assertThat(settledBalance).isEqualByComparingTo(BigDecimal.ZERO);

        // 10. Audit log verified
        List<AuditLog> audits = auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc("SETTLEMENT", settlement.getId());
        assertThat(audits).hasSize(1);
        AuditLog audit = audits.get(0);
        assertThat(audit.getActionType()).isEqualTo("SETTLEMENT_FINALIZED");
        assertThat(audit.getActor().getId()).isEqualTo(adminUser.getId());
        assertThat(audit.getReason()).isEqualTo(refNote);
        assertThat(audit.getPreviousValue()).isEqualTo("PENDING");
        assertThat(audit.getNewValue()).isEqualTo("SETTLED");

        // 11. Re-running settlement on SETTLED period throws 409 CONFLICT
        assertThatThrownBy(() -> settlementService.runSettlement(vendor.getId(), start, end))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already settled");

        // 12. Finalizing already SETTLED settlement throws 409 CONFLICT
        assertThatThrownBy(() -> settlementService.finalizeSettlement(settlement.getId(), adminUser.getEmail(), "Another note"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already settled");
    }

    private Order createOrder(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal platformFee, PaymentMethod method) {
        BigDecimal total = subtotal.add(deliveryFee).add(platformFee);
        LocalDateTime now = LocalDateTime.now();
        Order saved = orderRepository.saveAndFlush(Order.builder()
                .clientOrderId(UUID.randomUUID().toString())
                .user(customer)
                .vendor(vendor)
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .platformFee(platformFee)
                .total(total)
                .commissionRate(vendor.getCommissionRate())
                .deliveryAddressLine1("123 Street")
                .deliveryServiceLocation(location)
                .deliveryLatitude(new BigDecimal("12.97000000"))
                .deliveryLongitude(new BigDecimal("77.64000000"))
                .slotStart(now.plusHours(2))
                .slotEnd(now.plusHours(3))
                .paymentMethod(method)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.PLACED)
                .build());

        if (method == PaymentMethod.COD) {
            paymentService.createCodPayment(saved, saved.getTotal());
        }
        return saved;
    }

    private static String randomDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return digits.toString();
    }
}
