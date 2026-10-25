package com.flowerconnect.order.service;

import com.flowerconnect.domain.User;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancellation orchestration (plan Phase 8, tasks 8.1 and 8.2).
 *
 * <p>Validates the authenticated caller's role and ownership of the target
 * order, resolves the cancellation target status from the declared section
 * 6.1 transition table, then delegates the status change (and its stock
 * side effect) exclusively to {@link OrderStateMachine}. This service never
 * sets order status, payment status, or stock directly; payment status is
 * updated through {@link PaymentService}.
 *
 * <p>Actor rules (section 6.1):
 * <ul>
 *   <li>{@code PLACED &#8594; CANCELLED} — customer or admin (reason optional
 *       for customer, required for admin); releases the reservation.</li>
 *   <li>{@code PLACED &#8594; REJECTED} — vendor (reason required); releases
 *       the reservation. A vendor "cancelling" a placed order is a reject.</li>
 *   <li>{@code CONFIRMED &#8594; CANCELLED} — customer, vendor or admin
 *       (reason required for vendor and admin); restocks.</li>
 *   <li>{@code PREPARING &#8594; CANCELLED} — vendor or admin (reason
 *       required); no restock.</li>
 *   <li>{@code OUT_FOR_DELIVERY &#8594; CANCELLED} — admin only (reason
 *       required); no restock.</li>
 * </ul>
 *
 * <p>Ownership: a customer may cancel only their own order, a vendor only
 * their own order (a suspended vendor may still cancel in-flight orders per
 * D-6), and an admin may cancel any order. Terminal states admit no
 * outgoing transition (409).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationService {

    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final PaymentService paymentService;

    /**
     * Cancels (or rejects) the order on behalf of the authenticated caller.
     *
     * @param authenticatedEmail the JWT subject (the caller's email)
     * @param actorType          the caller's role, derived from the
     *                           authentication authorities
     * @param orderId            the order to cancel
     * @param reason             optional cancellation reason (mandatory for
     *                           vendor and admin cancellations)
     * @return the post-transition order summary
     * @throws BusinessException 404 if the order (or the caller's profile)
     *                           does not exist, 403 for a foreign order,
     *                           400 for a missing mandatory reason, 409 for
     *                           an illegal transition or a terminal order
     */
    @Transactional
    public CancelOrderResponse cancel(String authenticatedEmail, ActorType actorType,
                                      Long orderId, String reason) {
        User actor = userRepository.findByEmailWithRole(authenticatedEmail)
                .orElseThrow(() -> BusinessException.notFound("Authenticated user not found"));

        // Acquire the order lock before reading its status or ownership. The
        // transition service uses the same order-before-inventory lock order;
        // loading an unlocked entity here could leave this transaction with a
        // stale status while it waits for OrderStateMachine's lock.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));

        enforceOwnership(order, actor, actorType);

        OrderStatus fromStatus = order.getStatus();
        OrderStatus target = targetStatus(fromStatus, actorType);

        Order updated = orderStateMachine.transition(orderId, target, actorType, actor.getId(), reason);

        boolean refundPending = false;
        if (updated.getPaymentMethod() == PaymentMethod.ONLINE
                && updated.getPaymentStatus() == PaymentStatus.PAID) {
            paymentService.markRefundPending(updated.getId());
            refundPending = true;
        }

        return CancelOrderResponse.builder()
                .id(updated.getId())
                .status(updated.getStatus())
                .paymentStatus(refundPending ? PaymentStatus.REFUND_PENDING : updated.getPaymentStatus())
                .total(updated.getTotal())
                .refundPending(refundPending)
                .build();
    }

    private void enforceOwnership(Order order, User actor, ActorType actorType) {
        switch (actorType) {
            case CUSTOMER -> {
                if (!order.getUser().getId().equals(actor.getId())) {
                    throw BusinessException.forbidden(
                            "Order " + order.getId() + " belongs to another customer");
                }
            }
            case VENDOR -> {
                VendorProfile profile = vendorProfileRepository.findByUserEmail(actor.getEmail())
                        .orElseThrow(() -> BusinessException.notFound(
                                "No vendor profile exists for this account"));
                if (!order.getVendor().getId().equals(profile.getId())) {
                    throw BusinessException.forbidden(
                            "Order " + order.getId() + " belongs to another vendor");
                }
            }
            case ADMIN -> {
                // an admin may cancel any order
            }
            case SYSTEM -> throw BusinessException.badRequest(
                    "System-initiated cancellation is not available through this endpoint");
        }
    }

    /**
     * Resolves the cancellation target status from the section 6.1 table.
     * A vendor acting on a {@code PLACED} order rejects it; every other
     * cancellation targets {@code CANCELLED}. The state machine re-validates
     * the {@code (from, to, actor)} triple under the order row lock, so an
     * illegal combination surfaces as 409.
     */
    private static OrderStatus targetStatus(OrderStatus fromStatus, ActorType actorType) {
        if (actorType == ActorType.VENDOR && fromStatus == OrderStatus.PLACED) {
            return OrderStatus.REJECTED;
        }
        return OrderStatus.CANCELLED;
    }
}
