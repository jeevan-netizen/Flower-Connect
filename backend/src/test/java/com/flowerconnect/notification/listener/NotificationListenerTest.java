package com.flowerconnect.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowerconnect.dispute.event.DisputeOpened;
import com.flowerconnect.dispute.event.DisputeResolved;
import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.notification.event.OrderPlaced;
import com.flowerconnect.notification.event.PaymentRefundPending;
import com.flowerconnect.notification.event.PaymentRefunded;
import com.flowerconnect.notification.event.VendorApproved;
import com.flowerconnect.notification.event.VendorReinstated;
import com.flowerconnect.notification.event.VendorRejected;
import com.flowerconnect.notification.event.VendorSuspended;
import com.flowerconnect.notification.service.NotificationService;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.event.OrderCancelled;
import com.flowerconnect.order.event.OrderDelivered;
import com.flowerconnect.order.event.OrderStatusChanged;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link NotificationListener}: event-to-notification routing,
 * dedupe keys, and inheritance handling. Listener methods are invoked directly
 * (Spring's {@code @Async}/{@code @EventListener} machinery is not active in a
 * plain unit test), so each test verifies the downstream
 * {@link NotificationService#create} calls.
 */
@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @InjectMocks
    private NotificationListener listener;

    // ------------------------------------------------------------------
    // OrderPlaced: vendor order_new + customer order_placed
    // ------------------------------------------------------------------

    @Test
    void orderPlacedNotifiesVendorAndCustomer() {
        OrderPlaced event = new OrderPlaced(this, 100L, 7L, 8L,
                Order.PaymentMethod.COD, new BigDecimal("100.00"),
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderPlaced(event);

        // Vendor: order_new
        verify(notificationService).create(
                eq(8L), eq("order_new"), eq("New order #100"),
                eq("A new order has been placed"), eq("ORDER"), eq(100L),
                eq("order-placed:100:8"));
        // Customer: order_placed
        verify(notificationService).create(
                eq(7L), eq("order_placed"), eq("Order placed"),
                eq("Your order #100 is confirmed and being prepared"),
                eq("ORDER"), eq(100L), eq("order-placed:100:7"));
    }

    // ------------------------------------------------------------------
    // OrderStatusChanged: customer status notifications
    // ------------------------------------------------------------------

    @Test
    void orderStatusChangedToConfirmedNotifiesCustomer() {
        when(orderRepository.findCustomerIdByOrderId(1L)).thenReturn(java.util.Optional.of(7L));

        OrderStatusChanged event = new OrderStatusChanged(this, 1L,
                OrderStatus.PLACED, OrderStatus.CONFIRMED, ActorType.VENDOR, 8L, null,
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        verify(notificationService).create(
                eq(7L), eq("order_confirmed"), eq("Order confirmed"),
                eq("Your order #1 has been confirmed."), eq("ORDER"), eq(1L),
                eq("order-status:1:CONFIRMED:7"));
    }

    @Test
    void orderStatusChangedToPreparingNotifiesCustomer() {
        when(orderRepository.findCustomerIdByOrderId(1L)).thenReturn(java.util.Optional.of(7L));

        OrderStatusChanged event = new OrderStatusChanged(this, 1L,
                OrderStatus.CONFIRMED, OrderStatus.PREPARING, ActorType.VENDOR, 8L, null,
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        verify(notificationService).create(
                eq(7L), eq("order_preparing"), eq("Order preparing"),
                eq("Your order #1 is being prepared."), eq("ORDER"), eq(1L),
                eq("order-status:1:PREPARING:7"));
    }

    @Test
    void orderStatusChangedToOutForDeliveryNotifiesCustomer() {
        when(orderRepository.findCustomerIdByOrderId(1L)).thenReturn(java.util.Optional.of(7L));

        OrderStatusChanged event = new OrderStatusChanged(this, 1L,
                OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, 8L,
                null, Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        verify(notificationService).create(
                eq(7L), eq("order_out_for_delivery"), eq("Out for delivery"),
                eq("Your order #1 is on its way."), eq("ORDER"), eq(1L),
                eq("order-status:1:OUT_FOR_DELIVERY:7"));
    }

    @Test
    void orderStatusChangedToDeliveredNotifiesCustomer() {
        when(orderRepository.findCustomerIdByOrderId(1L)).thenReturn(java.util.Optional.of(7L));

        OrderStatusChanged event = new OrderStatusChanged(this, 1L,
                OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, ActorType.VENDOR, 8L,
                null, Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        verify(notificationService).create(
                eq(7L), eq("order_delivered"), eq("Delivered"),
                eq("Your order #1 has been delivered."), eq("ORDER"), eq(1L),
                eq("order-status:1:DELIVERED:7"));
    }

    @Test
    void orderStatusChangedToPlacedIsIgnored() {
        OrderStatusChanged event = new OrderStatusChanged(this, 1L,
                OrderStatus.PENDING_PAYMENT, OrderStatus.PLACED, ActorType.SYSTEM, null, null,
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(),
                any());
    }

    // ------------------------------------------------------------------
    // OrderCancelled: routing by actor
    // ------------------------------------------------------------------

    @Test
    void orderCancelledByVendorNotifiesCustomerRejected() {
        when(orderRepository.findCustomerIdByOrderId(1L)).thenReturn(java.util.Optional.of(7L));

        OrderCancelled event = new OrderCancelled(this, 1L, OrderStatus.PLACED,
                ActorType.VENDOR, 8L, "Out of stock",
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        verify(notificationService).create(
                eq(7L), eq("order_rejected"), eq("Order rejected"),
                eq("Order #1 was rejected by the vendor. Reason: Out of stock"),
                eq("ORDER"), eq(1L), eq("order-cancelled:1:7"));
    }

    @Test
    void orderCancelledByCustomerNotifiesVendor() {
        when(orderRepository.findVendorUserIdByOrderId(1L)).thenReturn(java.util.Optional.of(8L));

        OrderCancelled event = new OrderCancelled(this, 1L, OrderStatus.CONFIRMED,
                ActorType.CUSTOMER, 7L, "Changed my mind",
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        verify(notificationService).create(
                eq(8L), eq("order_cancelled_by_customer"), eq("Order cancelled"),
                eq("Order #1 was cancelled by the customer."), eq("ORDER"), eq(1L),
                eq("order-cancelled:1:8"));
    }

    @Test
    void orderCancelledBySystemPaymentTimeoutNotifiesBoth() {
        when(orderRepository.findCustomerIdByOrderId(1L)).thenReturn(java.util.Optional.of(7L));
        when(orderRepository.findVendorUserIdByOrderId(1L)).thenReturn(java.util.Optional.of(8L));

        OrderCancelled event = new OrderCancelled(this, 1L, OrderStatus.PENDING_PAYMENT,
                ActorType.SYSTEM, null, "Payment expired",
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        verify(notificationService).create(
                eq(7L), eq("order_payment_timeout"), eq("Payment expired"),
                eq("Order #1 was cancelled (payment timed out)."), eq("ORDER"), eq(1L),
                eq("order-cancelled:1:7"));
        verify(notificationService).create(
                eq(8L), eq("order_payment_timeout"), eq("Payment expired"),
                eq("Order #1 was cancelled (payment timed out)."), eq("ORDER"), eq(1L),
                eq("order-cancelled:1:8"));
    }

    @Test
    void orderCancelledBySystemAutoRejectNotifiesVendorOnly() {
        when(orderRepository.findVendorUserIdByOrderId(1L)).thenReturn(java.util.Optional.of(8L));

        OrderCancelled event = new OrderCancelled(this, 1L, OrderStatus.PLACED,
                ActorType.SYSTEM, null, "No response",
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        verify(notificationService).create(
                eq(8L), eq("order_auto_rejected"), eq("Order auto-rejected"),
                eq("Order #1 was not accepted in time."), eq("ORDER"), eq(1L),
                eq("order-cancelled:1:8"));
        verify(notificationService, never()).create(eq(7L), any(), any(), any(), any(), any(),
                any());
    }

    // ------------------------------------------------------------------
    // Inheritance: OrderDelivered is a subclass of OrderStatusChanged and is
    // handled by onOrderStatusChanged based on toStatus, no duplicate.
    // ------------------------------------------------------------------

    @Test
    void orderDeliveredEventDoesNotDuplicateNotification() {
        when(orderRepository.findCustomerIdByOrderId(1L)).thenReturn(java.util.Optional.of(7L));

        OrderDelivered event = new OrderDelivered(this, 1L, OrderStatus.OUT_FOR_DELIVERY,
                ActorType.VENDOR, 8L, null, Instant.parse("2026-10-20T10:00:00Z"));

        listener.onOrderStatusChanged(event);

        // Exactly one customer notification for DELIVERED.
        verify(notificationService, times(1)).create(
                eq(7L), eq("order_delivered"), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Refunds
    // ------------------------------------------------------------------

    @Test
    void paymentRefundPendingNotifiesCustomer() {
        PaymentRefundPending event = new PaymentRefundPending(this, 1L, 7L,
                new BigDecimal("100.00"), Instant.parse("2026-10-20T10:00:00Z"));

        listener.onPaymentRefundPending(event);

        verify(notificationService).create(
                eq(7L), eq("refund_pending"), eq("Refund pending"),
                eq("Refund for order #1 is being processed."), eq("ORDER"), eq(1L),
                eq("refund-pending:1:7"));
    }

    @Test
    void paymentRefundedNotifiesCustomer() {
        PaymentRefunded event = new PaymentRefunded(this, 1L, 7L,
                new BigDecimal("100.00"), 9L, Instant.parse("2026-10-20T10:00:00Z"));

        listener.onPaymentRefunded(event);

        verify(notificationService).create(
                eq(7L), eq("refunded"), eq("Refunded"),
                eq("Refund for order #1 has been completed."), eq("ORDER"), eq(1L),
                eq("refunded:1:7"));
    }

    // ------------------------------------------------------------------
    // Disputes
    // ------------------------------------------------------------------

    @Test
    void disputeOpenedNotifiesVendor() {
        when(orderRepository.findVendorUserIdByOrderId(1L)).thenReturn(java.util.Optional.of(8L));

        DisputeOpened event = new DisputeOpened(this, 10L, 1L, 7L,
                "ITEM_NOT_AS_DESCRIBED", DisputeStatus.OPEN,
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onDisputeOpened(event);

        verify(notificationService).create(
                eq(8L), eq("dispute_opened"), eq("Dispute opened"),
                eq("A customer raised a dispute on order #1."), eq("DISPUTE"), eq(10L),
                eq("dispute-opened:10:8"));
    }

    @Test
    void disputeResolvedNotifiesCustomerAndVendor() {
        when(vendorProfileRepository.findUserIdByProfileId(5L))
                .thenReturn(java.util.Optional.of(8L));

        DisputeResolved event = new DisputeResolved(this, 10L, 1L, 5L, 7L,
                DisputeStatus.RESOLVED_PARTIAL, new BigDecimal("30.00"),
                Order.PaymentMethod.COD, 9L,
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onDisputeResolved(event);

        verify(notificationService).create(
                eq(7L), eq("dispute_resolved"), eq("Dispute resolved"),
                eq("Dispute for order #1 resolved: RESOLVED_PARTIAL"), eq("DISPUTE"), eq(10L),
                eq("dispute-resolved:10:7"));
        verify(notificationService).create(
                eq(8L), eq("dispute_resolved"), eq("Dispute resolved"),
                eq("Dispute for order #1 resolved: RESOLVED_PARTIAL"), eq("DISPUTE"), eq(10L),
                eq("dispute-resolved:10:8"));
    }

    // ------------------------------------------------------------------
    // Vendor lifecycle
    // ------------------------------------------------------------------

    @Test
    void vendorApprovedNotifiesVendor() {
        VendorApproved event = new VendorApproved(this, 5L, 8L, "PENDING_APPROVAL",
                "APPROVED", 9L, null, Instant.parse("2026-10-20T10:00:00Z"));

        listener.onVendorApproved(event);

        verify(notificationService).create(
                eq(8L), eq("vendor_status_approved"), eq("Vendor account approved"),
                eq("Your vendor account is now approved."), eq("VENDOR_PROFILE"), eq(5L),
                eq("vendor-status:5:vendor_status_approved:8"));
    }

    @Test
    void vendorRejectedNotifiesVendorWithReason() {
        VendorRejected event = new VendorRejected(this, 5L, 8L, "PENDING_APPROVAL",
                "REJECTED", 9L, "Incomplete licence",
                Instant.parse("2026-10-20T10:00:00Z"));

        listener.onVendorRejected(event);

        verify(notificationService).create(
                eq(8L), eq("vendor_status_rejected"), eq("Vendor account rejected"),
                eq("Your vendor account has been rejected. Reason: Incomplete licence"),
                eq("VENDOR_PROFILE"), eq(5L),
                eq("vendor-status:5:vendor_status_rejected:8"));
    }

    @Test
    void vendorSuspendedNotifiesVendorWithReason() {
        VendorSuspended event = new VendorSuspended(this, 5L, 8L, "APPROVED", "SUSPENDED",
                9L, "Repeated late deliveries", Instant.parse("2026-10-20T10:00:00Z"));

        listener.onVendorSuspended(event);

        verify(notificationService).create(
                eq(8L), eq("vendor_status_suspended"), eq("Vendor account suspended"),
                eq("Your vendor account has been suspended. Reason: Repeated late deliveries"),
                eq("VENDOR_PROFILE"), eq(5L),
                eq("vendor-status:5:vendor_status_suspended:8"));
    }

    @Test
    void vendorReinstatedNotifiesVendor() {
        VendorReinstated event = new VendorReinstated(this, 5L, 8L, "SUSPENDED",
                "APPROVED", 9L, Instant.parse("2026-10-20T10:00:00Z"));

        listener.onVendorReinstated(event);

        verify(notificationService).create(
                eq(8L), eq("vendor_status_reinstated"), eq("Vendor account reinstated"),
                eq("Your vendor account is now approved again."), eq("VENDOR_PROFILE"), eq(5L),
                eq("vendor-status:5:vendor_status_reinstated:8"));
    }
}
