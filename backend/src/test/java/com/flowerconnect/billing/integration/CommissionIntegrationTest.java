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

import com.flowerconnect.billing.domain.Commission;
import com.flowerconnect.billing.repository.CommissionRepository;
import com.flowerconnect.billing.service.CommissionRateService;
import com.flowerconnect.billing.service.CommissionService;
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
class CommissionIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private OrderStateMachine orderStateMachine;
    @Autowired
    private CommissionService commissionService;
    @Autowired
    private CommissionRepository commissionRepository;
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
    private PasswordEncoder passwordEncoder;
    @Autowired
    private com.flowerconnect.payment.service.PaymentService paymentService;

    private User vendorUser;
    private VendorProfile vendor;
    private User customer;
    private ServiceLocation location;

    @BeforeEach
    void setUp() {
        Role floristRole = roleRepository.findByName("FLORIST").orElseThrow();
        vendorUser = userRepository.saveAndFlush(User.builder()
                .email("florist-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Florist User")
                .phone("+91" + randomDigits(10))
                .role(floristRole)
                .status(User.Status.ACTIVE)
                .build());

        Role customerRole = roleRepository.findByName("CUSTOMER").orElseThrow();
        customer = userRepository.saveAndFlush(User.builder()
                .email("customer-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Customer User")
                .phone("+91" + randomDigits(10))
                .role(customerRole)
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
                .businessName("Commission Florist " + UUID.randomUUID())
                .addressLine1("100 Garden Road")
                .serviceLocation(location)
                .latitude(BigDecimal.valueOf(12.97))
                .longitude(BigDecimal.valueOf(77.64))
                .deliveryRadiusKm(new BigDecimal("10.00"))
                .status(VendorProfile.Status.APPROVED)
                .commissionRate(new BigDecimal("10.00")) // Initial rate 10%
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
    @DisplayName("Delivery transitions create commission row with delivery-time rate snapshot; subsequent rate changes do not affect it")
    void deliveryCreatesCommissionWithDeliveryTimeRate() {
        // 1. Create order at status PLACED with checkout-time commissionRate = 10.00
        Order order = createOrder(new BigDecimal("400.00"), new BigDecimal("40.00"), new BigDecimal("10.00"));
        assertThat(order.getCommissionRate()).isEqualByComparingTo(new BigDecimal("10.00"));

        // 2. Change vendor commission rate to 15.00 before delivery
        vendor.setCommissionRate(new BigDecimal("15.00"));
        vendorProfileRepository.saveAndFlush(vendor);

        // 3. Move order: PLACED -> CONFIRMED -> PREPARING -> OUT_FOR_DELIVERY -> DELIVERED
        orderStateMachine.transition(order.getId(), OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.PREPARING, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.DELIVERED, ActorType.VENDOR, vendorUser.getId(), null);

        // 4. Verify commission row was created
        Commission commission = commissionRepository.findByOrderId(order.getId()).orElseThrow();
        // Rate snapshot at delivery time: 15.00%
        assertThat(commission.getCommissionRate()).isEqualByComparingTo(new BigDecimal("15.00"));
        // Commission on subtotal ONLY: 400.00 * 0.15 = 60.00
        assertThat(commission.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(commission.getSubtotal()).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(commission.getDeliveryFee()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(commission.getPlatformFee()).isEqualByComparingTo(new BigDecimal("10.00"));

        // 5. Change rate to 20.00 AFTER delivery
        vendor.setCommissionRate(new BigDecimal("20.00"));
        vendorProfileRepository.saveAndFlush(vendor);

        // Reload commission and verify it was NOT mutated
        Commission reloaded = commissionRepository.findById(commission.getId()).orElseThrow();
        assertThat(reloaded.getCommissionRate()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(reloaded.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("Subtotal 333.33 at 7.5% commission rounds HALF_UP to 25.00; delivery/platform fees excluded")
    void subtotalRoundingAndFeeExclusion() {
        vendor.setCommissionRate(new BigDecimal("7.50"));
        vendorProfileRepository.saveAndFlush(vendor);

        Order order = createOrder(new BigDecimal("333.33"), new BigDecimal("50.00"), new BigDecimal("15.00"));

        orderStateMachine.transition(order.getId(), OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.PREPARING, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.DELIVERED, ActorType.VENDOR, vendorUser.getId(), null);

        Commission commission = commissionRepository.findByOrderId(order.getId()).orElseThrow();
        // 333.33 * 0.0750 = 24.99975 -> 25.00
        assertThat(commission.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(commission.getCommissionRate()).isEqualByComparingTo(new BigDecimal("7.50"));
    }

    @Test
    @DisplayName("Duplicate delivery commission calls are idempotent no-ops")
    void idempotentCommissionCreation() {
        Order order = createOrder(new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        orderStateMachine.transition(order.getId(), OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.PREPARING, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendorUser.getId(), null);
        orderStateMachine.transition(order.getId(), OrderStatus.DELIVERED, ActorType.VENDOR, vendorUser.getId(), null);

        Commission first = commissionRepository.findByOrderId(order.getId()).orElseThrow();

        // Call createOrGet again directly
        Order delivered = orderRepository.findById(order.getId()).orElseThrow();
        Commission second = commissionService.createOrGet(delivered);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(commissionRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Cancelled and rejected orders do not produce commission rows")
    void cancelledAndRejectedOrdersNotCommissioned() {
        // Cancelled order
        Order order1 = createOrder(new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        orderStateMachine.transition(order1.getId(), OrderStatus.CANCELLED, ActorType.CUSTOMER, customer.getId(), "Changed mind");
        assertThat(commissionRepository.existsByOrderId(order1.getId())).isFalse();

        // Rejected order
        Order order2 = createOrder(new BigDecimal("180.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        orderStateMachine.transition(order2.getId(), OrderStatus.REJECTED, ActorType.VENDOR, vendorUser.getId(), "Out of flowers");
        assertThat(commissionRepository.existsByOrderId(order2.getId())).isFalse();
    }

    @Test
    @DisplayName("Backfill idempotently creates commissions for delivered orders lacking one")
    void backfillDeliveredOrdersIdempotently() {
        // Create an order and force its status to DELIVERED without going through state machine (simulating pre-Phase 10 order)
        Order legacyOrder = createOrder(new BigDecimal("250.00"), new BigDecimal("20.00"), BigDecimal.ZERO);
        legacyOrder.setStatus(OrderStatus.DELIVERED);
        orderRepository.saveAndFlush(legacyOrder);

        assertThat(commissionRepository.existsByOrderId(legacyOrder.getId())).isFalse();

        // Run backfill
        int created = commissionService.backfillDeliveredOrders();
        assertThat(created).isGreaterThanOrEqualTo(1);

        Commission commission = commissionRepository.findByOrderId(legacyOrder.getId()).orElseThrow();
        // 250.00 * 0.1000 = 25.00
        assertThat(commission.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("25.00"));

        // Rerun backfill: no new commissions should be created
        int rerun = commissionService.backfillDeliveredOrders();
        assertThat(rerun).isEqualTo(0);
    }

    private Order createOrder(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal platformFee) {
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
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.PLACED)
                .build());
        paymentService.createCodPayment(saved, saved.getTotal());
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
