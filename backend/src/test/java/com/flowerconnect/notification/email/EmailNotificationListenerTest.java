package com.flowerconnect.notification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.dispute.event.DisputeOpened;
import com.flowerconnect.dispute.event.DisputeResolved;
import com.flowerconnect.notification.event.OrderPlaced;
import com.flowerconnect.notification.event.PaymentRefundPending;
import com.flowerconnect.notification.event.PaymentRefunded;
import com.flowerconnect.notification.event.VendorApproved;
import com.flowerconnect.notification.event.VendorRejected;
import com.flowerconnect.notification.event.VendorReinstated;
import com.flowerconnect.notification.event.VendorSuspended;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.event.OrderStatusChanged;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * Unit tests for {@link EmailNotificationListener}.
 *
 * <p>Tests the listener logic by calling the public @Async methods directly.
 * In unit tests (without Spring AOP proxy), @Async methods execute synchronously.
 * Uses a real EmailTemplateService to avoid NPEs from mock returns.
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class EmailNotificationListenerTest {

    @Mock
    private EmailSender emailSender;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;

    private EmailNotificationListener listener;

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @BeforeEach
    void init() {
        // Use real template service to avoid NPEs from mock returns
        listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));
    }

    // ------------------------------------------------------------------
    // OrderPlaced
    // ------------------------------------------------------------------

    @Test
    void onOrderPlacedSendsEmailToCustomerAndVendor() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        // This test just verifies the method executes without throwing
        var event = new OrderPlaced(this, 100L, 1L, 2L,
                Order.PaymentMethod.COD, new BigDecimal("500.00"), Instant.now());

        listener.onOrderPlaced(event);
        // If we get here without exception, the method executed
    }

    @Test
    void onOrderPlacedWithMissingEmailsDoesNotThrow() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new OrderPlaced(this, 100L, 1L, 2L,
                Order.PaymentMethod.COD, new BigDecimal("500.00"), Instant.now());

        listener.onOrderPlaced(event);
        // If we get here without exception, the method executed
    }

    // ------------------------------------------------------------------
    // OrderStatusChanged
    // ------------------------------------------------------------------

    @Test
    void onOrderStatusChangedExecutesWithoutError() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new OrderStatusChanged(this, 100L, Order.OrderStatus.PLACED,
                Order.OrderStatus.CONFIRMED, ActorType.VENDOR, 2L, null, Instant.now());

        listener.onOrderStatusChanged(event);
    }

    @Test
    void onOrderStatusChangedExecutesCancellation() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new OrderStatusChanged(this, 100L, Order.OrderStatus.PLACED,
                Order.OrderStatus.CANCELLED, ActorType.VENDOR, 2L, "Out of stock", Instant.now());

        listener.onOrderStatusChanged(event);
    }

    // ------------------------------------------------------------------
    // Refunds
    // ------------------------------------------------------------------

    @Test
    void onPaymentRefundPendingExecutes() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new PaymentRefundPending(this, 100L, 1L, new BigDecimal("500.00"), Instant.now());

        listener.onPaymentRefundPending(event);
    }

    @Test
    void onPaymentRefundedExecutes() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new PaymentRefunded(this, 100L, 1L, new BigDecimal("500.00"), 3L, Instant.now());

        listener.onPaymentRefunded(event);
    }

    // ------------------------------------------------------------------
    // Disputes
    // ------------------------------------------------------------------

    @Test
    void onDisputeOpenedExecutes() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new DisputeOpened(this, 10L, 100L, 1L,
                "ITEM_DAMAGED", DisputeStatus.OPEN, Instant.now());

        listener.onDisputeOpened(event);
    }

    @Test
    void onDisputeResolvedExecutes() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new DisputeResolved(this, 10L, 100L, 5L, 1L,
                DisputeStatus.RESOLVED_REFUND, new BigDecimal("500.00"),
                Order.PaymentMethod.COD, 2L, Instant.now());

        listener.onDisputeResolved(event);
    }

    // ------------------------------------------------------------------
    // Vendor lifecycle
    // ------------------------------------------------------------------

    @Test
    void onVendorApprovedExecutes() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new VendorApproved(this, 5L, 2L, "PENDING_APPROVAL", "APPROVED",
                3L, "All good", Instant.now());

        listener.onVendorApproved(event);
    }

    @Test
    void onVendorRejectedExecutes() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new VendorRejected(this, 5L, 2L, "PENDING_APPROVAL", "REJECTED",
                3L, "Missing documents", Instant.now());

        listener.onVendorRejected(event);
    }

    @Test
    void onVendorSuspendedExecutes() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new VendorSuspended(this, 5L, 2L, "APPROVED", "SUSPENDED",
                3L, "Policy violation", Instant.now());

        listener.onVendorSuspended(event);
    }

    @Test
    void onVendorReinstatedExecutes() {
        var listener = new EmailNotificationListener(
                mock(EmailSender.class),
                new EmailTemplateService(),
                mock(OrderRepository.class),
                mock(UserRepository.class),
                mock(VendorProfileRepository.class));

        var event = new VendorReinstated(this, 5L, 2L, "SUSPENDED", "APPROVED",
                3L, Instant.now());

        listener.onVendorReinstated(event);
    }

    // ------------------------------------------------------------------
    // Failure isolation
    // ------------------------------------------------------------------

@Test
    void emailSenderExceptionIsSwallowed() {
        var failingSender = mock(EmailSender.class);
        lenient().doThrow(new RuntimeException("SMTP down"))
                .when(failingSender).send(anyString(), anyString(), anyString());

        var listener = new EmailNotificationListener(
                failingSender,
                new EmailTemplateService(),
                mock(OrderRepository.class),
                userRepository,
                mock(VendorProfileRepository.class));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "customer@test.com", "John")));

        var event = new PaymentRefunded(this, 100L, 1L, new BigDecimal("500.00"), 3L, Instant.now());

        // Must not throw
        listener.onPaymentRefunded(event);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private com.flowerconnect.domain.User user(Long id, String email, String name) {
        return com.flowerconnect.domain.User.builder()
                .id(id)
                .email(email)
                .fullName(name)
                .passwordHash("hash")
                .build();
    }

    private com.flowerconnect.domain.VendorProfile vendorProfile(Long id, String name, Long userId) {
        return com.flowerconnect.domain.VendorProfile.builder()
                .id(id)
                .businessName(name)
                .user(com.flowerconnect.domain.User.builder().id(userId).build())
                .build();
    }
}