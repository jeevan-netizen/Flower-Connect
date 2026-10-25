package com.flowerconnect.notification.listener;

import com.flowerconnect.dispute.event.DisputeOpened;
import com.flowerconnect.dispute.event.DisputeResolved;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Async notification listeners (plan Phase 9, task 9.1).
 *
 * <p>Every listener runs {@code @TransactionalEventListener(phase =
 * AFTER_COMMIT)} on the dedicated {@code notificationExecutor}, so a rolled-back
 * business transaction never produces a notification and listener failures are
 * logged and swallowed without affecting the originating operation.
 *
 * <p>Inheritance: {@code OrderCancelled} and {@code OrderDelivered} extend
 * {@code OrderStatusChanged}. A single {@link #onOrderStatusChanged} method
 * handles all status transitions based on {@code toStatus}; there are no
 * separate {@code OrderCancelled} / {@code OrderDelivered} listeners, which
 * would otherwise double-process the same transition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    public static final String EXECUTOR = "notificationExecutor";

    private final NotificationService notificationService;
    private final OrderRepository orderRepository;
    private final VendorProfileRepository vendorProfileRepository;

    // ------------------------------------------------------------------
    // Order lifecycle
    // ------------------------------------------------------------------

    /**
     * New order placed: vendor gets {@code order_new}, customer gets
     * {@code order_placed}. One notification per order per recipient.
     */
    @Async(EXECUTOR)
    @EventListener
    public void onOrderPlaced(OrderPlaced event) {
        try {
            String title = "New order #" + event.getOrderId();
            String message = "A new order has been placed";
            notify(event.getVendorUserId(), "order_new", title, message,
                    "ORDER", event.getOrderId(),
                    dedupe("order-placed", event.getOrderId(), event.getVendorUserId()));

            title = "Order placed";
            message = "Your order #" + event.getOrderId() + " is confirmed and being prepared";
            notify(event.getCustomerId(), "order_placed", title, message,
                    "ORDER", event.getOrderId(),
                    dedupe("order-placed", event.getOrderId(), event.getCustomerId()));
        } catch (Exception ex) {
            log.error("Failed to process OrderPlaced event for order {}", event.getOrderId(), ex);
        }
    }

    /**
     * Order status change. Handles all transitions based on {@code toStatus}.
     * {@code OrderCancelled} and {@code OrderDelivered} are subclasses and are
     * delivered here too; they are routed by {@code toStatus} and not handled
     * separately to avoid double processing.
     */
    @Async(EXECUTOR)
    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        try {
            OrderStatus toStatus = event.getToStatus();
            OrderStatus fromStatus = event.getFromStatus();
            ActorType actorType = event.getActorType();
            Long orderId = event.getOrderId();

            // PLACED initial state is handled by OrderPlaced, not here.
            if (toStatus == OrderStatus.PLACED) {
                return;
            }

            Long customerId = orderRepository.findCustomerIdByOrderId(orderId).orElse(null);
            Long vendorUserId = orderRepository.findVendorUserIdByOrderId(orderId).orElse(null);

            switch (toStatus) {
                case CONFIRMED -> {
                    if (customerId != null) {
                        notify(customerId, "order_confirmed", "Order confirmed",
                                "Your order #" + orderId + " has been confirmed.",
                                "ORDER", orderId,
                                dedupe("order-status", orderId, "CONFIRMED", customerId));
                    }
                }
                case PREPARING -> {
                    if (customerId != null) {
                        notify(customerId, "order_preparing", "Order preparing",
                                "Your order #" + orderId + " is being prepared.",
                                "ORDER", orderId,
                                dedupe("order-status", orderId, "PREPARING", customerId));
                    }
                }
                case OUT_FOR_DELIVERY -> {
                    if (customerId != null) {
                        notify(customerId, "order_out_for_delivery", "Out for delivery",
                                "Your order #" + orderId + " is on its way.",
                                "ORDER", orderId,
                                dedupe("order-status", orderId, "OUT_FOR_DELIVERY", customerId));
                    }
                }
                case DELIVERED -> {
                    if (customerId != null) {
                        notify(customerId, "order_delivered", "Delivered",
                                "Your order #" + orderId + " has been delivered.",
                                "ORDER", orderId,
                                dedupe("order-status", orderId, "DELIVERED", customerId));
                    }
                }
                case CANCELLED -> handleCancellation(orderId, fromStatus, actorType,
                        customerId, vendorUserId, event.getReason());
                default -> {
                    // No notification for other transitions.
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process OrderStatusChanged event for order {}",
                    event.getOrderId(), ex);
        }
    }

    private void handleCancellation(Long orderId, OrderStatus fromStatus, ActorType actorType,
                                    Long customerId, Long vendorUserId, String reason) {
        Set<Long> notified = new HashSet<>();
        switch (actorType) {
            case VENDOR -> {
                // Vendor rejected/cancelled: notify the customer.
                if (customerId != null && notified.add(customerId)) {
                    notify(customerId, "order_rejected", "Order rejected",
                            "Order #" + orderId + " was rejected by the vendor."
                                    + (reason != null ? " Reason: " + reason : ""),
                            "ORDER", orderId,
                            dedupe("order-cancelled", orderId, customerId));
                }
            }
            case CUSTOMER -> {
                // Customer cancelled: notify the vendor.
                if (vendorUserId != null && notified.add(vendorUserId)) {
                    notify(vendorUserId, "order_cancelled_by_customer", "Order cancelled",
                            "Order #" + orderId + " was cancelled by the customer.",
                            "ORDER", orderId,
                            dedupe("order-cancelled", orderId, vendorUserId));
                }
            }
            case SYSTEM -> {
                if (fromStatus == OrderStatus.PENDING_PAYMENT) {
                    // Payment timeout: notify both customer and vendor.
                    if (customerId != null && notified.add(customerId)) {
                        notify(customerId, "order_payment_timeout", "Payment expired",
                                "Order #" + orderId + " was cancelled (payment timed out).",
                                "ORDER", orderId,
                                dedupe("order-cancelled", orderId, customerId));
                    }
                    if (vendorUserId != null && notified.add(vendorUserId)) {
                        notify(vendorUserId, "order_payment_timeout", "Payment expired",
                                "Order #" + orderId + " was cancelled (payment timed out).",
                                "ORDER", orderId,
                                dedupe("order-cancelled", orderId, vendorUserId));
                    }
                } else if (fromStatus == OrderStatus.PLACED) {
                    // Auto-reject: notify the vendor only.
                    if (vendorUserId != null && notified.add(vendorUserId)) {
                        notify(vendorUserId, "order_auto_rejected", "Order auto-rejected",
                                "Order #" + orderId + " was not accepted in time.",
                                "ORDER", orderId,
                                dedupe("order-cancelled", orderId, vendorUserId));
                    }
                }
            }
            default -> {
                // ADMIN or other actors: no other-party notification by default.
            }
        }
    }

    // ------------------------------------------------------------------
    // Refunds
    // ------------------------------------------------------------------

    @Async(EXECUTOR)
    @EventListener
    public void onPaymentRefundPending(PaymentRefundPending event) {
        try {
            Long customerId = event.getCustomerId();
            if (customerId == null) {
                return;
            }
            notify(customerId, "refund_pending", "Refund pending",
                    "Refund for order #" + event.getOrderId() + " is being processed.",
                    "ORDER", event.getOrderId(),
                    dedupe("refund-pending", event.getOrderId(), customerId));
        } catch (Exception ex) {
            log.error("Failed to process PaymentRefundPending event for order {}",
                    event.getOrderId(), ex);
        }
    }

    @Async(EXECUTOR)
    @EventListener
    public void onPaymentRefunded(PaymentRefunded event) {
        try {
            Long customerId = event.getCustomerId();
            if (customerId == null) {
                return;
            }
            notify(customerId, "refunded", "Refunded",
                    "Refund for order #" + event.getOrderId() + " has been completed.",
                    "ORDER", event.getOrderId(),
                    dedupe("refunded", event.getOrderId(), customerId));
        } catch (Exception ex) {
            log.error("Failed to process PaymentRefunded event for order {}",
                    event.getOrderId(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Disputes
    // ------------------------------------------------------------------

    @Async(EXECUTOR)
    @EventListener
    public void onDisputeOpened(DisputeOpened event) {
        try {
            // Resolve vendor user id from the order (the dispute event carries
            // orderId but not vendorUserId). Load via order repository scalar.
            Long vendorUserId = orderRepository.findVendorUserIdByOrderId(event.getOrderId())
                    .orElse(null);
            if (vendorUserId == null) {
                return;
            }
            notify(vendorUserId, "dispute_opened", "Dispute opened",
                    "A customer raised a dispute on order #" + event.getOrderId() + ".",
                    "DISPUTE", event.getDisputeId(),
                    dedupe("dispute-opened", event.getDisputeId(), vendorUserId));
        } catch (Exception ex) {
            log.error("Failed to process DisputeOpened event for dispute {}",
                    event.getDisputeId(), ex);
        }
    }

    @Async(EXECUTOR)
    @EventListener
    public void onDisputeResolved(DisputeResolved event) {
        try {
            // Customer and vendor are both notified of the resolution.
            if (event.getCustomerId() != null) {
                notify(event.getCustomerId(), "dispute_resolved", "Dispute resolved",
                        "Dispute for order #" + event.getOrderId() + " resolved: "
                                + event.getStatus(),
                        "DISPUTE", event.getDisputeId(),
                        dedupe("dispute-resolved", event.getDisputeId(), event.getCustomerId()));
            }
            // Resolve vendor user id from the vendor profile id carried in the
            // event, then notify the vendor.
            if (event.getVendorId() != null) {
                Long vendorUserId = vendorProfileRepository.findUserIdByProfileId(event.getVendorId())
                        .orElse(null);
                if (vendorUserId != null) {
                    notify(vendorUserId, "dispute_resolved", "Dispute resolved",
                            "Dispute for order #" + event.getOrderId() + " resolved: "
                                    + event.getStatus(),
                            "DISPUTE", event.getDisputeId(),
                            dedupe("dispute-resolved", event.getDisputeId(), vendorUserId));
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process DisputeResolved event for dispute {}",
                    event.getDisputeId(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Vendor lifecycle
    // ------------------------------------------------------------------

    @Async(EXECUTOR)
    @EventListener
    public void onVendorApproved(VendorApproved event) {
        try {
            notifyVendor(event, "vendor_status_approved", "Vendor account approved",
                    "Your vendor account is now approved.", "VENDOR_PROFILE",
                    event.getProfileId());
        } catch (Exception ex) {
            log.error("Failed to process VendorApproved event for profile {}",
                    event.getProfileId(), ex);
        }
    }

    @Async(EXECUTOR)
    @EventListener
    public void onVendorRejected(VendorRejected event) {
        try {
            String message = "Your vendor account has been rejected."
                    + (event.getReason() != null ? " Reason: " + event.getReason() : "");
            notifyVendor(event, "vendor_status_rejected", "Vendor account rejected",
                    message, "VENDOR_PROFILE", event.getProfileId());
        } catch (Exception ex) {
            log.error("Failed to process VendorRejected event for profile {}",
                    event.getProfileId(), ex);
        }
    }

    @Async(EXECUTOR)
    @EventListener
    public void onVendorSuspended(VendorSuspended event) {
        try {
            String message = "Your vendor account has been suspended."
                    + (event.getReason() != null ? " Reason: " + event.getReason() : "");
            notifyVendor(event, "vendor_status_suspended", "Vendor account suspended",
                    message, "VENDOR_PROFILE", event.getProfileId());
        } catch (Exception ex) {
            log.error("Failed to process VendorSuspended event for profile {}",
                    event.getProfileId(), ex);
        }
    }

    @Async(EXECUTOR)
    @EventListener
    public void onVendorReinstated(VendorReinstated event) {
        try {
            notifyVendor(event, "vendor_status_reinstated", "Vendor account reinstated",
                    "Your vendor account is now approved again.", "VENDOR_PROFILE",
                    event.getProfileId());
        } catch (Exception ex) {
            log.error("Failed to process VendorReinstated event for profile {}",
                    event.getProfileId(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void notify(Long userId, String type, String title, String message,
                        String targetType, Long targetId, String dedupeKey) {
        if (userId == null) {
            return;
        }
        try {
            notificationService.create(userId, type, title, message, targetType, targetId,
                    dedupeKey);
        } catch (Exception ex) {
            log.error("Failed to create notification for user {} type {}", userId, type, ex);
        }
    }

    private void notifyVendor(ApplicationEvent event, String type, String title, String message,
                              String targetType, Long targetId) {
        Long vendorUserId;
        if (event instanceof VendorApproved va) {
            vendorUserId = va.getVendorUserId();
        } else if (event instanceof VendorRejected vr) {
            vendorUserId = vr.getVendorUserId();
        } else if (event instanceof VendorSuspended vs) {
            vendorUserId = vs.getVendorUserId();
        } else if (event instanceof VendorReinstated vri) {
            vendorUserId = vri.getVendorUserId();
        } else {
            return;
        }
        notify(vendorUserId, type, title, message, targetType, targetId,
                dedupe("vendor-status", targetId, type, vendorUserId));
    }

    private static String dedupe(String prefix, Long orderId, Long recipientId) {
        return prefix + ":" + orderId + ":" + recipientId;
    }

    private static String dedupe(String prefix, Long orderId, String status, Long recipientId) {
        return prefix + ":" + orderId + ":" + status + ":" + recipientId;
    }
}
