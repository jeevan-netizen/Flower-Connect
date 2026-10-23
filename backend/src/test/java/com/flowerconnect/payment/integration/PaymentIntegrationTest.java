package com.flowerconnect.payment;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.PaymentEvent;
import com.flowerconnect.payment.repository.PaymentEventRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.test.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration coverage for Phase 7 task 7.1-7.2 (payments model + COD checkout)
 * against a real MySQL instance.
 *
 * <p>Validates the {@code payments} and {@code payment_events} schema (V19),
 * that {@link PaymentService} is the sole writer of payment status, that COD
 * delivery collection syncs {@code payments.status} and {@code orders.payment_status}
 * together, and that reject/cancel leaves a COD payment at {@code PENDING}
 * (Option B).
 */
@SpringBootTest
class PaymentIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderStateMachine orderStateMachine;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentEventRepository paymentEventRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private MutableClock clock;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;

    private Long customerUserId;
    private Long vendorUserId;
    private Long vendorProfileId;
    private Long locationId;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        locationId = serviceLocationRepository.findByPincode("560034").orElseThrow().getId();

        User customer = createUser("payment-customer-" + UUID.randomUUID() + "@test.com", "CUSTOMER");
        customerUserId = customer.getId();

        User vendorUser = createUser("payment-vendor-" + UUID.randomUUID() + "@test.com", "FLORIST");
        vendorUserId = vendorUser.getId();
        VendorProfile profile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(vendorUser)
                .businessName("Payment Florist")
                .addressLine1("12 Test Street")
                .serviceLocation(serviceLocationRepository.findById(locationId).orElseThrow())
                .latitude(new BigDecimal("12.93"))
                .longitude(new BigDecimal("77.62"))
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(VendorProfile.Status.APPROVED)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());
        vendorProfileId = profile.getId();
    }

    @AfterEach
    void restoreClock() {
        clock.setInstant(NOW);
    }

    // ------------------------------------------------------------------
    // COD payment creation
    // ------------------------------------------------------------------

    @Test
    void createCodPaymentPersistsOnePendingRowPerOrder() {
        Order order = savePlacedCodOrder(new BigDecimal("643.00"));

        Payment payment = paymentService.createCodPayment(order, order.getTotal());

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getMethod()).isEqualTo(Payment.PaymentMethod.COD);
        assertThat(payment.getProvider()).isEqualTo("cod");
        assertThat(payment.getProviderReference()).isNull();
        assertThat(payment.getAmount()).isEqualByComparingTo(order.getTotal());
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(payment.getCollectedAt()).isNull();

        Optional<Payment> persisted = paymentRepository.findByOrderId(order.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
    }

    // ------------------------------------------------------------------
    // Delivery collection syncs payment and order together
    // ------------------------------------------------------------------

    @Test
    void collectPaymentSyncsPaymentAndOrderStatusesAndStampsCollectedAt() {
        Order order = savePlacedCodOrder(new BigDecimal("643.00"));
        paymentService.createCodPayment(order, order.getTotal());

        Payment collected = paymentService.collectPayment(order.getId());

        assertThat(collected.getStatus()).isEqualTo(Payment.PaymentStatus.COLLECTED);
        assertThat(collected.getCollectedAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW, clock.getZone()));

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getPaymentStatus()).isEqualTo(PaymentStatus.COLLECTED);
    }

    @Test
    void collectPaymentRejectsNonCodOrder() {
        Order order = savePlacedOrder(new BigDecimal("643.00"), PaymentMethod.ONLINE);

        assertThatThrownBy(() -> paymentService.collectPayment(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT)
                .hasMessageContaining("is not a COD order");
    }

    @Test
    void collectPaymentRejectsNonPendingPayment() {
        Order order = savePlacedCodOrder(new BigDecimal("643.00"));
        Payment payment = paymentService.createCodPayment(order, order.getTotal());
        payment.setStatus(Payment.PaymentStatus.COLLECTED);
        payment.setCollectedAt(LocalDateTime.parse("2026-10-20T09:00:00"));
        paymentRepository.saveAndFlush(payment);

        assertThatThrownBy(() -> paymentService.collectPayment(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT)
                .hasMessageContaining("already COLLECTED");
    }

    // ------------------------------------------------------------------
    // Option B: reject / cancel leave a COD payment at PENDING
    // ------------------------------------------------------------------

    @Test
    void codRejectKeepsPaymentPending() {
        Order order = savePlacedCodOrder(new BigDecimal("643.00"));
        paymentService.createCodPayment(order, order.getTotal());

        orderStateMachine.transition(order.getId(), OrderStatus.REJECTED,
                ActorType.VENDOR, vendorUserId, "Customer requested cancellation");

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(payment.getCollectedAt()).isNull();
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void codCancelKeepsPaymentPending() {
        Order order = savePlacedCodOrder(new BigDecimal("643.00"));
        paymentService.createCodPayment(order, order.getTotal());

        orderStateMachine.transition(order.getId(), OrderStatus.CANCELLED,
                ActorType.CUSTOMER, customerUserId, "Customer cancelled");

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(payment.getCollectedAt()).isNull();
    }

    // ------------------------------------------------------------------
    // payment_events schema
    // ------------------------------------------------------------------

    @Test
    void paymentEventCanBeInsertedAndReadBack() {
        Order order = savePlacedCodOrder(new BigDecimal("643.00"));
        Payment payment = paymentService.createCodPayment(order, order.getTotal());

        PaymentEvent event = PaymentEvent.builder()
                .payment(payment)
                .providerEventId("evt_" + UUID.randomUUID())
                .eventType("payment.collected")
                .outcome("success")
                .amount(order.getTotal())
                .build();
        PaymentEvent saved = paymentEventRepository.saveAndFlush(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(paymentEventRepository.findById(saved.getId())).isPresent()
                .get().satisfies(e -> {
                    assertThat(e.getProviderEventId()).isEqualTo(event.getProviderEventId());
                    assertThat(e.getAmount()).isEqualByComparingTo(order.getTotal());
                });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Order savePlacedCodOrder(BigDecimal total) {
        return savePlacedOrder(total, PaymentMethod.COD);
    }

    private Order savePlacedOrder(BigDecimal total, PaymentMethod paymentMethod) {
        return orderRepository.saveAndFlush(Order.builder()
                .user(userRepository.findById(customerUserId).orElseThrow())
                .vendor(vendorProfileRepository.findById(vendorProfileId).orElseThrow())
                .status(OrderStatus.PLACED)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(total)
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(total)
                .deliveryAddressLine1("123 Test Street")
                .deliveryLatitude(new BigDecimal("12.9716"))
                .deliveryLongitude(new BigDecimal("77.5946"))
                .build());
    }

    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash("$2a$10$dummyhash")
                .fullName("Test " + roleName)
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
    }
}
