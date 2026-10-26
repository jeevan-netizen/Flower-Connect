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

import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.billing.service.BillingLedgerService;
import com.flowerconnect.dispute.domain.Dispute;
import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.dispute.dto.ResolveDisputeRequest;
import com.flowerconnect.dispute.repository.DisputeRepository;
import com.flowerconnect.dispute.service.DisputeService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.payment.service.PaymentService;
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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
class SettlementLedgerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private OrderStateMachine orderStateMachine;
    @Autowired
    private BillingLedgerService billingLedgerService;
    @Autowired
    private SettlementEntryRepository settlementEntryRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private DisputeRepository disputeRepository;
    @Autowired
    private DisputeService disputeService;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
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
                .email("florist-ledger-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Florist Ledger User")
                .phone("+91" + randomDigits(10))
                .role(floristRole)
                .status(User.Status.ACTIVE)
                .build());

        Role customerRole = roleRepository.findByName("CUSTOMER").orElseThrow();
        customer = userRepository.saveAndFlush(User.builder()
                .email("customer-ledger-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Customer Ledger User")
                .phone("+91" + randomDigits(10))
                .role(customerRole)
                .status(User.Status.ACTIVE)
                .build());

        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        adminUser = userRepository.saveAndFlush(User.builder()
                .email("admin-ledger-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Admin Ledger User")
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
                .businessName("Ledger Florist " + UUID.randomUUID())
                .addressLine1("100 Garden Road")
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
    @DisplayName("Online and COD delivered orders produce correct signed ledger entries and net into outstanding balance")
    void onlineAndCodDeliveriesCreateLedgerEntries() {
        // 1. Online order: S = 100.00, D = 20.00, P = 0.00, C = 10.00 -> Credit = +(100 - 10 + 20) = +110.00
        Order onlineOrder = createOrder(new BigDecimal("100.00"), new BigDecimal("20.00"), BigDecimal.ZERO, PaymentMethod.ONLINE);
        orderStateMachine.transition(onlineOrder.getId(), OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(onlineOrder.getId(), OrderStatus.PREPARING, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(onlineOrder.getId(), OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(onlineOrder.getId(), OrderStatus.DELIVERED, ActorType.VENDOR, vendorUser.getId(), null);

        SettlementEntry onlineEntry = settlementEntryRepository.findByOrderId(onlineOrder.getId()).orElseThrow();
        assertThat(onlineEntry.getType()).isEqualTo(SettlementEntryType.ONLINE_ORDER_CREDIT);
        assertThat(onlineEntry.getAmount()).isEqualByComparingTo(new BigDecimal("110.00"));

        BigDecimal balance1 = billingLedgerService.getOutstandingBalance(vendor.getId());
        assertThat(balance1).isEqualByComparingTo(new BigDecimal("110.00"));

        // 2. COD order: S = 100.00, D = 20.00, P = 0.00, C = 10.00 -> Dues = -(10 + 0) = -10.00
        Order codOrder = createOrder(new BigDecimal("100.00"), new BigDecimal("20.00"), BigDecimal.ZERO, PaymentMethod.COD);
        orderStateMachine.transition(codOrder.getId(), OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(codOrder.getId(), OrderStatus.PREPARING, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(codOrder.getId(), OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(codOrder.getId(), OrderStatus.DELIVERED, ActorType.VENDOR, vendorUser.getId(), null);

        SettlementEntry codEntry = settlementEntryRepository.findByOrderId(codOrder.getId()).orElseThrow();
        assertThat(codEntry.getType()).isEqualTo(SettlementEntryType.COD_DUES);
        assertThat(codEntry.getAmount()).isEqualByComparingTo(new BigDecimal("-10.00"));

        // 3. Outstanding balance nets: 110.00 - 10.00 = +100.00
        BigDecimal balance2 = billingLedgerService.getOutstandingBalance(vendor.getId());
        assertThat(balance2).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Dispute resolution creates atomic DISPUTE_ADJUSTMENT ledger entry and updates balance")
    void disputeResolutionCreatesLedgerEntry() {
        // Online order delivered -> +110.00
        Order order = createOrder(new BigDecimal("100.00"), new BigDecimal("20.00"), BigDecimal.ZERO, PaymentMethod.ONLINE);
        orderStateMachine.transition(order.getId(), OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.PREPARING, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.DELIVERED, ActorType.VENDOR, vendorUser.getId(), null);

        // Raise dispute
        Dispute dispute = disputeRepository.saveAndFlush(Dispute.builder()
                .order(order)
                .raisedBy(customer)
                .reasonCode("QUALITY_COMPLAINT")
                .description("Damaged flowers")
                .status(DisputeStatus.OPEN)
                .build());

        // Admin resolves partial refund 30.00
        ResolveDisputeRequest request = new ResolveDisputeRequest();
        request.setStatus(DisputeStatus.RESOLVED_PARTIAL);
        request.setRefundAmount(new BigDecimal("30.00"));
        request.setResolutionNote("Partial refund granted for damaged flowers");

        disputeService.resolveDispute(adminUser.getEmail(), dispute.getId(), request);

        // Verify dispute adjustment entry: -30.00
        SettlementEntry disputeEntry = settlementEntryRepository.findByDisputeId(dispute.getId()).orElseThrow();
        assertThat(disputeEntry.getType()).isEqualTo(SettlementEntryType.DISPUTE_ADJUSTMENT);
        assertThat(disputeEntry.getAmount()).isEqualByComparingTo(new BigDecimal("-30.00"));

        // Outstanding balance: +110.00 - 30.00 = +80.00
        BigDecimal balance = billingLedgerService.getOutstandingBalance(vendor.getId());
        assertThat(balance).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("Cancelled and rejected orders never create ledger entries")
    void cancelledAndRejectedOrdersProduceNoLedgerRows() {
        Order cancelled = createOrder(new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO, PaymentMethod.COD);
        orderStateMachine.transition(cancelled.getId(), OrderStatus.CANCELLED, ActorType.CUSTOMER, customer.getId(), "Cancel");
        assertThat(settlementEntryRepository.existsByOrderId(cancelled.getId())).isFalse();

        Order rejected = createOrder(new BigDecimal("180.00"), BigDecimal.ZERO, BigDecimal.ZERO, PaymentMethod.COD);
        orderStateMachine.transition(rejected.getId(), OrderStatus.REJECTED, ActorType.VENDOR, vendorUser.getId(), "Reject");
        assertThat(settlementEntryRepository.existsByOrderId(rejected.getId())).isFalse();
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
