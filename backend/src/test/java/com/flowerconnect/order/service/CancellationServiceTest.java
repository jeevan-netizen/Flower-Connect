package com.flowerconnect.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.User.Status;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.dto.CancelOrderResponse;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancellationServiceTest {

    private static final String PASSWORD_HASH = "bcrypt-hash";

    private final User customer = user("customer@t.com", Role.builder().name("CUSTOMER").build());
    private final User florist = user("vendor@t.com", Role.builder().name("FLORIST").build());
    private final User admin = user("admin@t.com", Role.builder().name("ADMIN").build());
    private final VendorProfile vendorProfile = VendorProfile.builder()
        .id(9L)
        .user(florist)
        .businessName("Test Florist")
        .status(VendorProfile.Status.APPROVED)
        .build();

    @Mock private UserRepository users;
    @Mock private VendorProfileRepository vendorProfiles;
    @Mock private OrderRepository orders;
    @Mock private OrderStateMachine stateMachine;
    @Mock private PaymentService payments;
    @InjectMocks private CancellationService service;

    @Test
    void customerCancelsOwnPlacedOrderWithoutReason() {
        Order order = order(1L, customer, vendorProfile, OrderStatus.PLACED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        Order cancelled = order(1L, customer, vendorProfile, OrderStatus.CANCELLED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        when(users.findByEmailWithRole("customer@t.com")).thenReturn(Optional.of(customer));
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(stateMachine.transition(eq(1L), eq(OrderStatus.CANCELLED), eq(ActorType.CUSTOMER),
            eq(customer.getId()), eq(null))).thenReturn(cancelled);

        CancelOrderResponse res = service.cancel("customer@t.com", ActorType.CUSTOMER, 1L, null);

        assertThat(res.getId()).isEqualTo(1L);
        assertThat(res.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(res.isRefundPending()).isFalse();
        verify(stateMachine).transition(eq(1L), eq(OrderStatus.CANCELLED), eq(ActorType.CUSTOMER),
            eq(customer.getId()), eq(null));
        verify(payments, never()).markRefundPending(anyLong());
    }

    @Test
    void vendorRejectsOwnPlacedOrderWithReason() {
        Order order = order(2L, customer, vendorProfile, OrderStatus.PLACED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        Order rejected = order(2L, customer, vendorProfile, OrderStatus.REJECTED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        when(users.findByEmailWithRole("vendor@t.com")).thenReturn(Optional.of(florist));
        when(vendorProfiles.findByUserEmail("vendor@t.com")).thenReturn(Optional.of(vendorProfile));
        when(orders.findByIdForUpdate(2L)).thenReturn(Optional.of(order));
        when(stateMachine.transition(eq(2L), eq(OrderStatus.REJECTED), eq(ActorType.VENDOR),
            eq(florist.getId()), eq("out of stock"))).thenReturn(rejected);

        CancelOrderResponse res = service.cancel("vendor@t.com", ActorType.VENDOR, 2L, "out of stock");

        assertThat(res.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verify(stateMachine).transition(eq(2L), eq(OrderStatus.REJECTED), eq(ActorType.VENDOR),
            eq(florist.getId()), eq("out of stock"));
    }

    @Test
    void adminCancelsAnyOrder() {
        Order order = order(3L, customer, vendorProfile, OrderStatus.CONFIRMED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        Order cancelled = order(3L, customer, vendorProfile, OrderStatus.CANCELLED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        when(users.findByEmailWithRole("admin@t.com")).thenReturn(Optional.of(admin));
        when(orders.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
        when(stateMachine.transition(eq(3L), eq(OrderStatus.CANCELLED), eq(ActorType.ADMIN),
            eq(admin.getId()), eq("fraud"))).thenReturn(cancelled);

        CancelOrderResponse res = service.cancel("admin@t.com", ActorType.ADMIN, 3L, "fraud");

        assertThat(res.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(stateMachine).transition(eq(3L), eq(OrderStatus.CANCELLED), eq(ActorType.ADMIN),
            eq(admin.getId()), eq("fraud"));
    }

    @Test
    void customerCancellingForeignOrderIsForbidden() {
        Order order = order(4L, customer, vendorProfile, OrderStatus.PLACED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        User other = user("other@t.com", Role.builder().name("CUSTOMER").build());
        when(users.findByEmailWithRole("other@t.com")).thenReturn(Optional.of(other));
        when(orders.findByIdForUpdate(4L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel("other@t.com", ActorType.CUSTOMER, 4L, null))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(ex.getMessage()).contains("belongs to another customer"));
    }

    @Test
    void vendorCancellingForeignOrderIsForbidden() {
        Order order = order(5L, customer, vendorProfile, OrderStatus.PLACED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        User otherFlorist = user("other-vendor@t.com", Role.builder().name("FLORIST").build());
        VendorProfile otherProfile = VendorProfile.builder()
            .id(8L).user(otherFlorist).businessName("Other").status(VendorProfile.Status.APPROVED).build();
        when(users.findByEmailWithRole("other-vendor@t.com")).thenReturn(Optional.of(otherFlorist));
        when(vendorProfiles.findByUserEmail("other-vendor@t.com")).thenReturn(Optional.of(otherProfile));
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel("other-vendor@t.com", ActorType.VENDOR, 5L, "x"))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(ex.getMessage()).contains("belongs to another vendor"));
    }

    @Test
    void paidOnlineCancellationMarksRefundPending() {
        Order order = order(6L, customer, vendorProfile, OrderStatus.PLACED, PaymentMethod.ONLINE,
            PaymentStatus.PAID, new BigDecimal("200.00"));
        Order cancelled = order(6L, customer, vendorProfile, OrderStatus.CANCELLED, PaymentMethod.ONLINE,
            PaymentStatus.PAID, new BigDecimal("200.00"));
        when(users.findByEmailWithRole("customer@t.com")).thenReturn(Optional.of(customer));
        when(orders.findByIdForUpdate(6L)).thenReturn(Optional.of(order));
        when(stateMachine.transition(eq(6L), eq(OrderStatus.CANCELLED), eq(ActorType.CUSTOMER),
            eq(customer.getId()), eq(null))).thenReturn(cancelled);

        CancelOrderResponse res = service.cancel("customer@t.com", ActorType.CUSTOMER, 6L, null);

        assertThat(res.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(res.isRefundPending()).isTrue();
        assertThat(res.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        verify(payments).markRefundPending(6L);
    }

    @Test
    void codCancellationLeavesPaymentPending() {
        Order order = order(7L, customer, vendorProfile, OrderStatus.PLACED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        Order cancelled = order(7L, customer, vendorProfile, OrderStatus.CANCELLED, PaymentMethod.COD,
            PaymentStatus.PENDING, new BigDecimal("100.00"));
        when(users.findByEmailWithRole("customer@t.com")).thenReturn(Optional.of(customer));
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(stateMachine.transition(eq(7L), eq(OrderStatus.CANCELLED), eq(ActorType.CUSTOMER),
            eq(customer.getId()), eq(null))).thenReturn(cancelled);

        CancelOrderResponse res = service.cancel("customer@t.com", ActorType.CUSTOMER, 7L, null);

        assertThat(res.isRefundPending()).isFalse();
        assertThat(res.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(payments, never()).markRefundPending(anyLong());
    }

    @Test
    void missingOrderIsNotFound() {
        when(users.findByEmailWithRole("customer@t.com")).thenReturn(Optional.of(customer));
        when(orders.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel("customer@t.com", ActorType.CUSTOMER, 99L, null))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(ex.getMessage()).contains("not found"));
    }

    private User user(String email, Role role) {
        return User.builder()
            .id(100L + email.hashCode()) // deterministic non-null ID
            .email(email)
            .passwordHash(PASSWORD_HASH)
            .fullName("Test " + email)
            .phone("+919999999999")
            .role(role)
            .status(Status.ACTIVE)
            .build();
    }

    private Order order(Long id, User user, VendorProfile vendor, OrderStatus status,
                        PaymentMethod method, PaymentStatus paymentStatus, BigDecimal total) {
        return Order.builder()
            .id(id)
            .user(user)
            .vendor(vendor)
            .status(status)
            .paymentMethod(method)
            .paymentStatus(paymentStatus)
            .total(total)
            .build();
    }
}
