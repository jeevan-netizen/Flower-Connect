package com.flowerconnect.vendor.service;

import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.vendor.dto.VendorOrderPageResponse;
import com.flowerconnect.vendor.dto.VendorOrderResponse;
import com.flowerconnect.vendor.dto.VendorOrderSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vendor-facing order orchestration (plan Phase 6, tasks 6.2 and 6.4).
 *
 * <p>Every status change is delegated to {@link OrderStateMachine}; this
 * service only resolves the caller's vendor profile, enforces order
 * ownership, and maps entities to response DTOs. It never sets
 * {@code Order#status} directly.
 *
 * <p>Authorization layering (D-13 / D-6): the route is guarded by the
 * {@code hasRole("FLORIST")} namespace rule, so any vendor account —
 * including a {@code SUSPENDED} vendor — can reach it. {@link
 * com.flowerconnect.vendor.security.RequiresApprovedVendor} is deliberately
 * *not* applied here, because a suspended vendor must still be able to
 * process their own existing in-flight orders to completion (accept,
 * reject, prepare, out for delivery, delivered). What a suspended vendor
 * must not do is take on *new* work, which is guarded elsewhere
 * (catalog, inventory, order creation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorOrderService {

    private final VendorProfileRepository vendorProfileRepository;
    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final Clock clock;

    /**
     * The caller's vendor profile, resolved from the JWT subject. Used for
     * both ownership checks and (for the summary) the vendor's timezone.
     */
    private VendorProfile requireOwnProfile(String authenticatedEmail) {
        return vendorProfileRepository.findByUserEmail(authenticatedEmail)
                .orElseThrow(() -> BusinessException.notFound("No vendor profile exists for this account"));
    }

    /**
     * The order with the given id, after confirming it exists and belongs to
     * the caller's vendor profile. A missing order is 404; a foreign order is
     * 403, so a vendor can never touch another vendor's order.
     */
    @Transactional(readOnly = true)
    private Order requireOwnedOrder(Long orderId, Long vendorId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        if (!order.getVendor().getId().equals(vendorId)) {
            throw BusinessException.forbidden("Order " + orderId + " belongs to another vendor");
        }
        return order;
    }

    // ------------------------------------------------------------------
    // Vendor actions (task 6.2)
    // ------------------------------------------------------------------

    @Transactional
    public VendorOrderResponse accept(String authenticatedEmail, Long orderId) {
        VendorProfile profile = requireOwnProfile(authenticatedEmail);
        requireOwnedOrder(orderId, profile.getId());
        Order order = orderStateMachine.transition(orderId, OrderStatus.CONFIRMED,
                ActorType.VENDOR, profile.getUser().getId(), null);
        return toResponse(order);
    }

    @Transactional
    public VendorOrderResponse reject(String authenticatedEmail, Long orderId, String reason) {
        VendorProfile profile = requireOwnProfile(authenticatedEmail);
        requireOwnedOrder(orderId, profile.getId());
        Order order = orderStateMachine.transition(orderId, OrderStatus.REJECTED,
                ActorType.VENDOR, profile.getUser().getId(), reason);
        return toResponse(order);
    }

    @Transactional
    public VendorOrderResponse prepare(String authenticatedEmail, Long orderId) {
        VendorProfile profile = requireOwnProfile(authenticatedEmail);
        requireOwnedOrder(orderId, profile.getId());
        Order order = orderStateMachine.transition(orderId, OrderStatus.PREPARING,
                ActorType.VENDOR, profile.getUser().getId(), null);
        return toResponse(order);
    }

    @Transactional
    public VendorOrderResponse outForDelivery(String authenticatedEmail, Long orderId) {
        VendorProfile profile = requireOwnProfile(authenticatedEmail);
        requireOwnedOrder(orderId, profile.getId());
        Order order = orderStateMachine.transition(orderId, OrderStatus.OUT_FOR_DELIVERY,
                ActorType.VENDOR, profile.getUser().getId(), null);
        return toResponse(order);
    }

    @Transactional
    public VendorOrderResponse delivered(String authenticatedEmail, Long orderId) {
        VendorProfile profile = requireOwnProfile(authenticatedEmail);
        requireOwnedOrder(orderId, profile.getId());
        Order order = orderStateMachine.transition(orderId, OrderStatus.DELIVERED,
                ActorType.VENDOR, profile.getUser().getId(), null);
        return toResponse(order);
    }

    // ------------------------------------------------------------------
    // Vendor order list (task 6.4)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public VendorOrderPageResponse listOrders(String authenticatedEmail, OrderStatus status,
                                              LocalDateTime from, LocalDateTime to,
                                              Pageable pageable) {
        VendorProfile profile = requireOwnProfile(authenticatedEmail);
        Long vendorId = profile.getId();
        Page<Order> page;
        // PENDING_PAYMENT orders are not yet actionable by the vendor: they
        // await payment and are hidden from vendor lists/summaries until the
        // customer pays and the order transitions to PLACED (task 7.4).
        if (status == OrderStatus.PENDING_PAYMENT) {
            page = Page.empty(pageable);
        } else if (status != null && from != null && to != null) {
            page = orderRepository.findByVendorIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
                    vendorId, status, from, to, pageable);
        } else if (status != null) {
            page = orderRepository.findByVendorIdAndStatusOrderByCreatedAtDesc(vendorId, status, pageable);
        } else if (from != null && to != null) {
            page = orderRepository.findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    vendorId, from, to, pageable);
        } else {
            page = orderRepository.findByVendorIdAndStatusNotOrderByCreatedAtDesc(
                    vendorId, OrderStatus.PENDING_PAYMENT, pageable);
        }
        return toPageResponse(page);
    }

    // ------------------------------------------------------------------
    // Today's summary (task 6.4)
    // ------------------------------------------------------------------

    /**
     * Today's summary for the caller's vendor, where "today" is the local
     * date in the vendor's service-location timezone as observed on the
     * injected {@link Clock}.
     */
    @Transactional(readOnly = true)
    public VendorOrderSummaryResponse todaySummary(String authenticatedEmail, LocalDate date) {
        VendorProfile profile = requireOwnProfile(authenticatedEmail);
        Long vendorId = profile.getId();
        ZoneId zone = zoneFor(profile);
        LocalDate today = (date != null) ? date : LocalDate.now(clock.withZone(zone));
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        LocalDateTime fromUtc = start.atZone(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime toUtc = end.atZone(ZoneOffset.UTC).toLocalDateTime();
        List<Order> orders = orderRepository.findByVendorIdAndCreatedAtBetween(vendorId, fromUtc, toUtc);
        return aggregate(today, orders);
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private VendorOrderResponse toResponse(Order order) {
        return VendorOrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .total(order.getTotal())
                .createdAt(order.getCreatedAt())
                .slotStart(order.getSlotStart())
                .slotEnd(order.getSlotEnd())
                .deliveryAddressLine1(order.getDeliveryAddressLine1())
                .deliveryAddressLine2(order.getDeliveryAddressLine2())
                .note(order.getNote())
                .build();
    }

    private VendorOrderPageResponse toPageResponse(Page<Order> page) {
        return VendorOrderPageResponse.builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    private VendorOrderSummaryResponse aggregate(LocalDate today, List<Order> orders) {
        VendorOrderSummaryResponse summary = VendorOrderSummaryResponse.empty(today);
        Map<OrderStatus, Long> counts = new LinkedHashMap<>();
        BigDecimal todayTotalValue = BigDecimal.ZERO;
        BigDecimal expectedCodCash = BigDecimal.ZERO;
        for (Order order : orders) {
            // PENDING_PAYMENT orders await payment and are hidden from the
            // vendor summary until paid (task 7.4).
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                continue;
            }
            counts.merge(order.getStatus(), 1L, Long::sum);
            if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.REJECTED) {
                todayTotalValue = todayTotalValue.add(order.getTotal());
            }
            if (order.getStatus() == OrderStatus.DELIVERED
                    && order.getPaymentMethod() == PaymentMethod.COD) {
                expectedCodCash = expectedCodCash.add(order.getTotal());
            }
        }
        summary.setOrdersByStatus(counts);
        summary.setTodayOrderCount(orders.size());
        summary.setTodayTotalValue(todayTotalValue);
        summary.setExpectedCodCash(expectedCodCash);
        return summary;
    }

    private ZoneId zoneFor(VendorProfile profile) {
        String timezone = profile.getServiceLocation() == null
                ? null
                : profile.getServiceLocation().getTimezone();
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        return ZoneId.of(timezone);
    }
}
