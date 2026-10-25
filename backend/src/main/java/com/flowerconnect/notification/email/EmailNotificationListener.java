package com.flowerconnect.notification.email;

import com.flowerconnect.dispute.event.DisputeOpened;
import com.flowerconnect.dispute.event.DisputeResolved;
import com.flowerconnect.notification.event.OrderPlaced;
import com.flowerconnect.notification.event.PaymentRefundPending;
import com.flowerconnect.notification.event.PaymentRefunded;
import com.flowerconnect.notification.event.VendorApproved;
import com.flowerconnect.notification.event.VendorReinstated;
import com.flowerconnect.notification.event.VendorRejected;
import com.flowerconnect.notification.event.VendorSuspended;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.event.OrderStatusChanged;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Async email notification listeners (plan Phase 9, task 9.3).
 *
 * <p>Every listener runs {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * on the dedicated {@code notificationExecutor}, so a rolled-back business
 * transaction never produces an email and listener failures are logged and
 * swallowed without affecting the originating operation.
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
public class EmailNotificationListener {

    public static final String EXECUTOR = "notificationExecutor";

    private final EmailSender emailSender;
    private final EmailTemplateService templateService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;

    // ------------------------------------------------------------------
    // Order lifecycle
    // ------------------------------------------------------------------

    /**
     * New order placed: vendor gets new-order email, customer gets confirmation.
     */
    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced event) {
        try {
            sendOrderPlacedEmails(event);
        } catch (Exception ex) {
            log.error("Failed to send OrderPlaced emails for order {}",
                    event.getOrderId(), ex);
        }
    }

    /**
     * Order status change. Handles all transitions based on {@code toStatus}.
     * {@code OrderCancelled} and {@code OrderDelivered} are subclasses and are
     * delivered here too; they are routed by {@code toStatus} and not handled
     * separately to avoid double processing.
     */
    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChanged event) {
        try {
            Order.OrderStatus toStatus = event.getToStatus();
            Order.OrderStatus fromStatus = event.getFromStatus();

            // PLACED initial state is handled by OrderPlaced, not here.
            if (toStatus == Order.OrderStatus.PLACED) {
                return;
            }

            Long customerId = orderRepository.findCustomerIdByOrderId(event.getOrderId()).orElse(null);
            Long vendorUserId = orderRepository.findVendorUserIdByOrderId(event.getOrderId()).orElse(null);

            switch (toStatus) {
                case CONFIRMED -> {
                    if (customerId != null) {
                        sendCustomerStatusEmail(customerId, event.getOrderId(),
                                fromStatus.name(), "CONFIRMED",
                                "Your order has been confirmed by the florist.");
                    }
                }
                case PREPARING -> {
                    if (customerId != null) {
                        sendCustomerStatusEmail(customerId, event.getOrderId(),
                                fromStatus.name(), "PREPARING",
                                "Your order is being prepared.");
                    }
                }
                case OUT_FOR_DELIVERY -> {
                    if (customerId != null) {
                        sendCustomerStatusEmail(customerId, event.getOrderId(),
                                fromStatus.name(), "OUT_FOR_DELIVERY",
                                "Your order is on its way!");
                    }
                }
                case DELIVERED -> {
                    if (customerId != null) {
                        sendCustomerStatusEmail(customerId, event.getOrderId(),
                                fromStatus.name(), "DELIVERED",
                                "Your order has been delivered. Enjoy your flowers!");
                    }
                }
                case CANCELLED, REJECTED -> {
                    sendCancellationEmails(event.getOrderId(), fromStatus,
                            event.getActorType(), customerId, vendorUserId, event.getReason());
                }
                default -> {
                    // No email for other transitions.
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send OrderStatusChanged emails for order {}",
                    event.getOrderId(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Refunds
    // ------------------------------------------------------------------

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRefundPending(PaymentRefundPending event) {
        try {
            Long customerId = event.getCustomerId();
            if (customerId == null) {
                return;
            }
            sendEmail(customerId, templateService.refundCustomer(event.getOrderId(),
                    "PENDING", event.getAmount()));
        } catch (Exception ex) {
            log.error("Failed to send PaymentRefundPending email for order {}",
                    event.getOrderId(), ex);
        }
    }

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRefunded(PaymentRefunded event) {
        try {
            Long customerId = event.getCustomerId();
            if (customerId == null) {
                return;
            }
            sendEmail(customerId, templateService.refundCustomer(event.getOrderId(),
                    "COMPLETED", event.getAmount()));
        } catch (Exception ex) {
            log.error("Failed to send PaymentRefunded email for order {}",
                    event.getOrderId(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Disputes
    // ------------------------------------------------------------------

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisputeOpened(DisputeOpened event) {
        try {
            Long vendorUserId = orderRepository.findVendorUserIdByOrderId(event.getOrderId())
                    .orElse(null);
            if (vendorUserId == null) {
                return;
            }
            sendEmail(vendorUserId, templateService.disputeOpenedVendor(
                    event.getDisputeId(), event.getOrderId(), event.getReasonCode()));
        } catch (Exception ex) {
            log.error("Failed to send DisputeOpened email for dispute {}",
                    event.getDisputeId(), ex);
        }
    }

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisputeResolved(DisputeResolved event) {
        try {
            if (event.getCustomerId() != null) {
                sendEmail(event.getCustomerId(), templateService.disputeResolvedCustomer(
                        event.getDisputeId(), event.getOrderId(), event.getStatus().name()));
            }
            if (event.getVendorId() != null) {
                Long vendorUserId = vendorProfileRepository
                        .findUserIdByProfileId(event.getVendorId()).orElse(null);
                if (vendorUserId != null) {
sendEmail(vendorUserId, templateService.disputeResolvedCustomer(
                    event.getDisputeId(), event.getOrderId(), event.getStatus().name()));
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send DisputeResolved email for dispute {}",
                    event.getDisputeId(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Vendor lifecycle
    // ------------------------------------------------------------------

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVendorApproved(VendorApproved event) {
        try {
            Long vendorUserId = event.getVendorUserId();
            if (vendorUserId == null) {
                return;
            }
            Optional<String> businessName = vendorProfileRepository
                    .findById(event.getProfileId())
                    .map(p -> p.getBusinessName());
            sendEmail(vendorUserId, templateService.vendorStatusChange(
                    event.getProfileId(), businessName.orElse("Vendor"),
                    "approved", "Your vendor account has been approved. You can now receive orders."));
        } catch (Exception ex) {
            log.error("Failed to send VendorApproved email for profile {}",
                    event.getProfileId(), ex);
        }
    }

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVendorRejected(VendorRejected event) {
        try {
            Long vendorUserId = event.getVendorUserId();
            if (vendorUserId == null) {
                return;
            }
            Optional<String> businessName = vendorProfileRepository
                    .findById(event.getProfileId())
                    .map(p -> p.getBusinessName());
            String message = "Your vendor account has been rejected."
                    + (event.getReason() != null ? " Reason: " + event.getReason() : "");
            sendEmail(vendorUserId, templateService.vendorStatusChange(
                    event.getProfileId(), businessName.orElse("Vendor"),
                    "rejected", message));
        } catch (Exception ex) {
            log.error("Failed to send VendorRejected email for profile {}",
                    event.getProfileId(), ex);
        }
    }

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVendorSuspended(VendorSuspended event) {
        try {
            Long vendorUserId = event.getVendorUserId();
            if (vendorUserId == null) {
                return;
            }
            Optional<String> businessName = vendorProfileRepository
                    .findById(event.getProfileId())
                    .map(p -> p.getBusinessName());
            String message = "Your vendor account has been suspended."
                    + (event.getReason() != null ? " Reason: " + event.getReason() : "");
            sendEmail(vendorUserId, templateService.vendorStatusChange(
                    event.getProfileId(), businessName.orElse("Vendor"),
                    "suspended", message));
        } catch (Exception ex) {
            log.error("Failed to send VendorSuspended email for profile {}",
                    event.getProfileId(), ex);
        }
    }

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVendorReinstated(VendorReinstated event) {
        try {
            Long vendorUserId = event.getVendorUserId();
            if (vendorUserId == null) {
                return;
            }
            Optional<String> businessName = vendorProfileRepository
                    .findById(event.getProfileId())
                    .map(p -> p.getBusinessName());
            sendEmail(vendorUserId, templateService.vendorStatusChange(
                    event.getProfileId(), businessName.orElse("Vendor"),
                    "reinstated", "Your vendor account has been reinstated. You can now receive orders again."));
        } catch (Exception ex) {
            log.error("Failed to send VendorReinstated email for profile {}",
                    event.getProfileId(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void sendOrderPlacedEmails(OrderPlaced event) {
        // Customer email
        sendEmail(event.getCustomerId(), templateService.orderPlacedCustomer(
                event.getOrderId(), event.getTotal(), event.getOccurredAt() != null
                        ? event.getOccurredAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        : null));

        // Vendor email
        String customerName = userRepository.findById(event.getCustomerId())
                .map(u -> u.getFullName()).orElse(null);
        sendEmail(event.getVendorUserId(), templateService.orderPlacedVendor(
                event.getOrderId(), event.getTotal(),
                event.getOccurredAt() != null
                        ? event.getOccurredAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        : null, customerName));
    }

    private void sendCancellationEmails(Long orderId, Order.OrderStatus fromStatus,
                                        ActorType actorType, Long customerId, Long vendorUserId,
                                        String reason) {
        String reasonText = reason != null ? " Reason: " + reason : "";

        if (actorType == ActorType.VENDOR && customerId != null) {
            // Vendor rejected/cancelled: notify customer
            sendEmail(customerId, templateService.orderStatusChangedCustomer(
                    orderId, fromStatus.name(), "CANCELLED",
                    "Your order was cancelled by the florist." + reasonText));
        } else if (actorType == ActorType.CUSTOMER && vendorUserId != null) {
            // Customer cancelled: notify vendor
            sendEmail(vendorUserId, templateService.orderStatusChangedCustomer(
                    orderId, fromStatus.name(), "CANCELLED",
                    "Order was cancelled by the customer." + reasonText));
        } else if (actorType == ActorType.SYSTEM) {
            if (fromStatus == Order.OrderStatus.PENDING_PAYMENT) {
                // Payment timeout: notify both
                if (customerId != null) {
                    sendEmail(customerId, templateService.orderStatusChangedCustomer(
                            orderId, fromStatus.name(), "CANCELLED",
                            "Order was cancelled (payment timed out)." + reasonText));
                }
                if (vendorUserId != null) {
                    sendEmail(vendorUserId, templateService.orderStatusChangedCustomer(
                            orderId, fromStatus.name(), "CANCELLED",
                            "Order was cancelled (payment timed out)." + reasonText));
                }
            } else if (fromStatus == Order.OrderStatus.PLACED) {
                // Auto-reject: vendor only
                if (vendorUserId != null) {
                    sendEmail(vendorUserId, templateService.orderStatusChangedCustomer(
                            orderId, fromStatus.name(), "REJECTED",
                            "Order was not accepted in time." + reasonText));
                }
            }
        }
    }

    private void sendCustomerStatusEmail(Long customerId, Long orderId,
                                         String fromStatus, String toStatus, String message) {
        sendEmail(customerId, templateService.orderStatusChangedCustomer(
                orderId, fromStatus, toStatus, message));
    }

    private void sendEmail(Long userId, EmailTemplateService.EmailContent content) {
        if (userId == null) {
            return;
        }
        try {
            String email = userRepository.findById(userId)
                    .map(u -> u.getEmail()).orElse(null);
            if (email == null) {
                log.debug("No email address for user {}, skipping email", userId);
                return;
            }
            emailSender.send(email, content.subject(), content.body());
        } catch (Exception ex) {
            // Swallow and log: email must never break the business flow.
            log.error("Failed to send email to user {}: {}", userId, ex.getMessage(), ex);
        }
    }
}