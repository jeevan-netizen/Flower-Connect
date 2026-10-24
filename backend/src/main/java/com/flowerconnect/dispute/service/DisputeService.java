package com.flowerconnect.dispute.service;

import com.flowerconnect.config.PlatformConfigRepository;
import com.flowerconnect.dispute.domain.Dispute;
import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.dispute.dto.DisputeDetailResponse;
import com.flowerconnect.dispute.dto.DisputeListItem;
import com.flowerconnect.dispute.dto.DisputePageResponse;
import com.flowerconnect.dispute.dto.RaiseDisputeRequest;
import com.flowerconnect.dispute.dto.VendorDisputeResponseRequest;
import com.flowerconnect.dispute.event.DisputeOpened;
import com.flowerconnect.dispute.repository.DisputeRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.domain.VendorProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dispute orchestration (plan Phase 8, task 8.4).
 *
 * <p>Business rules:
 * <ul>
 *   <li>Only the order's customer may raise a dispute.</li>
 *   <li>The order must be in {@code DELIVERED} status.</li>
 *   <li>The dispute must be raised within the configured window (days after the
 *       order enters {@code DELIVERED}, read from {@code platform_config};
 *       documented default 7 days).</li>
 *   <li>At most one dispute per order ever (enforced by {@code uq_disputes_order}
 *       and checked here for a clean 409).</li>
 *   <li>The vendor may respond once, moving {@code OPEN} to {@code UNDER_REVIEW}.
 *       A suspended vendor may still respond (D-6).</li>
 *   <li>Admin resolution (Phase 8 Stage 5) is out of scope for this stage.</li>
 * </ul>
 *
 * <p>The dispute window is measured from the order's entry into {@code DELIVERED}
 * --- the {@code order_status_history} row where {@code to_status = DELIVERED} ---
 * using the injected {@link Clock}. This ensures a delayed delivery scan does
 * not shrink the customer's window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeService {

    /** Config key for the dispute window in {@code platform_config}. */
    private static final String DISPUTE_WINDOW_DAYS_KEY = "dispute_window_days";

    /** Documented default if {@code platform_config} has no dispute_window_days row. */
    private static final int DEFAULT_DISPUTE_WINDOW_DAYS = 7;

    /** Allowed reason codes for customer-raised disputes. */
    private static final java.util.Set<String> ALLOWED_REASON_CODES = java.util.Set.of(
            "ITEM_NOT_AS_DESCRIBED",
            "ITEM_DAMAGED",
            "ITEM_MISSING",
            "WRONG_ITEM_DELIVERED",
            "QUALITY_COMPLAINT",
            "LATE_DELIVERY",
            "OTHER"
    );

    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final PlatformConfigRepository platformConfigRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Raises a dispute for the given order on behalf of the authenticated customer.
     *
     * @param customerEmail the JWT subject (the customer's email)
     * @param orderId       the order to dispute
     * @param request       reason code and description
     * @return the created dispute detail
     * @throws BusinessException 404 if order/customer not found, 403 if not the
     *                           customer's order, 400 for invalid reason code or
     *                           description, 409 if order not DELIVERED, window
     *                           expired, or dispute already exists
     */
    @Transactional
    public DisputeDetailResponse raiseDispute(String customerEmail, Long orderId, RaiseDisputeRequest request) {
        // Validate reason code
        if (request.getReasonCode() == null || !ALLOWED_REASON_CODES.contains(request.getReasonCode())) {
            throw BusinessException.badRequest(
                    "Invalid reason_code: must be one of " + ALLOWED_REASON_CODES);
        }
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw BusinessException.badRequest("Description is required");
        }
        if (request.getDescription().length() > 2000) {
            throw BusinessException.badRequest("Description must not exceed 2000 characters");
        }

        // Resolve the customer
        var customer = userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> BusinessException.notFound("Authenticated customer not found"));

        // Load the order with ownership check
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        if (!order.getUser().getId().equals(customer.getId())) {
            throw BusinessException.forbidden("Order " + orderId + " belongs to another customer");
        }

        // Must be DELIVERED
        if (order.getStatus() != Order.OrderStatus.DELIVERED) {
            throw BusinessException.conflict(
                    "Disputes can only be raised for DELIVERED orders; current status: " + order.getStatus());
        }

        // Check dispute window from DELIVERED timestamp in order_status_history
        LocalDateTime deliveredAt = orderStatusHistoryRepository
                .findFirstByOrderIdAndToStatusOrderByCreatedAtDesc(orderId, Order.OrderStatus.DELIVERED)
                .map(OrderStatusHistory::getCreatedAt)
                .orElseThrow(() -> BusinessException.conflict(
                        "Order " + orderId + " has no DELIVERED history entry"));
        int windowDays = getDisputeWindowDays();
        LocalDateTime cutoff = deliveredAt.plusDays(windowDays);
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isAfter(cutoff)) {
            throw BusinessException.conflict(
                    "Dispute window expired: order was delivered on " + deliveredAt
                            + " and the " + windowDays + "-day window has passed");
        }

        // Check for existing dispute (clean 409 before unique constraint violation)
        if (disputeRepository.existsByOrderId(orderId)) {
            throw BusinessException.conflict("A dispute already exists for order " + orderId);
        }

        // Create the dispute
        Dispute dispute = Dispute.builder()
                .order(order)
                .raisedBy(customer)
                .reasonCode(request.getReasonCode())
                .description(request.getDescription().trim())
                .status(DisputeStatus.OPEN)
                .build();
        disputeRepository.saveAndFlush(dispute);

        // Publish DisputeOpened event after commit (scalar-only payload)
        Instant nowInstant = now.atZone(clock.getZone()).toInstant();
        DisputeOpened event = new DisputeOpened(this, dispute.getId(), orderId, customer.getId(),
                request.getReasonCode(), DisputeStatus.OPEN, nowInstant);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCommit() {
                            eventPublisher.publishEvent(event);
                        }
                    });
        } else {
            eventPublisher.publishEvent(event);
        }

        log.info("Customer {} raised dispute {} for order {} (reason: {})",
                customer.getEmail(), dispute.getId(), orderId, request.getReasonCode());
        return toDetailResponse(dispute);
    }

    /**
     * Vendor responds to a dispute on their order. Moves {@code OPEN} to
     * {@code UNDER_REVIEW}. One response per dispute.
     *
     * @param vendorEmail   the JWT subject (the vendor's email)
     * @param disputeId     the dispute to respond to
     * @param request       vendor's response text
     * @return the updated dispute detail
     * @throws BusinessException 404 if dispute not found, 403 if not the vendor's
     *                           order, 400 if missing response, 409 if dispute not OPEN
     */
    @Transactional
    public DisputeDetailResponse respondToDispute(String vendorEmail, Long disputeId, VendorDisputeResponseRequest request) {
        if (request.getResponse() == null || request.getResponse().trim().isEmpty()) {
            throw BusinessException.badRequest("Vendor response is required");
        }
        if (request.getResponse().length() > 500) {
            throw BusinessException.badRequest("Vendor response must not exceed 500 characters");
        }

        // Resolve the vendor profile
        VendorProfile vendorProfile = vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound(
                        "No vendor profile exists for this account"));

        // Load the dispute with ownership check
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> BusinessException.notFound("Dispute not found: " + disputeId));
        if (!dispute.getOrder().getVendor().getId().equals(vendorProfile.getId())) {
            throw BusinessException.forbidden("Dispute " + disputeId + " belongs to another vendor");
        }

        // Must be OPEN
        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw BusinessException.conflict(
                    "Vendor may only respond to OPEN disputes; current status: " + dispute.getStatus());
        }

        // Apply response
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        // Store vendor response in resolution_note for now; resolution_note is
        // nullable and will be overwritten by admin resolution in Stage 5.
        dispute.setResolutionNote("Vendor response: " + request.getResponse().trim());
        disputeRepository.saveAndFlush(dispute);

        log.info("Vendor {} responded to dispute {} for order {}",
                vendorEmail, disputeId, dispute.getOrder().getId());
        return toDetailResponse(dispute);
    }

    /**
     * Customer's own dispute list, newest first.
     */
    @Transactional(readOnly = true)
    public DisputePageResponse listCustomerDisputes(String customerEmail, Pageable pageable) {
        var customer = userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> BusinessException.notFound("Authenticated customer not found"));
        Page<Dispute> page = disputeRepository.findByRaisedByOrderByCreatedAtDesc(customer.getId(), pageable);
        return toPageResponse(page);
    }

    /**
     * Customer's single dispute detail. 404 if not found, 403 if not theirs.
     */
    @Transactional(readOnly = true)
    public DisputeDetailResponse getCustomerDispute(String customerEmail, Long disputeId) {
        var customer = userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> BusinessException.notFound("Authenticated customer not found"));
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> BusinessException.notFound("Dispute not found: " + disputeId));
        if (!dispute.getRaisedBy().getId().equals(customer.getId())) {
            throw BusinessException.forbidden("Dispute " + disputeId + " belongs to another customer");
        }
        return toDetailResponse(dispute);
    }

    /**
     * Vendor's dispute list (disputes on their orders), newest first.
     */
    @Transactional(readOnly = true)
    public DisputePageResponse listVendorDisputes(String vendorEmail, Pageable pageable) {
        VendorProfile vendorProfile = vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound(
                        "No vendor profile exists for this account"));
        Page<Dispute> page = disputeRepository.findByOrderVendorIdOrderByCreatedAtDesc(vendorProfile.getId(), pageable);
        return toPageResponse(page);
    }

    /**
     * Vendor's single dispute detail. 404 if not found, 403 if not their order.
     */
    @Transactional(readOnly = true)
    public DisputeDetailResponse getVendorDispute(String vendorEmail, Long disputeId) {
        VendorProfile vendorProfile = vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound(
                        "No vendor profile exists for this account"));
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> BusinessException.notFound("Dispute not found: " + disputeId));
        if (!dispute.getOrder().getVendor().getId().equals(vendorProfile.getId())) {
            throw BusinessException.forbidden("Dispute " + disputeId + " belongs to another vendor");
        }
        return toDetailResponse(dispute);
    }

    /**
     * Admin dispute queue with optional status and date filters.
     */
    @Transactional(readOnly = true)
    public DisputePageResponse listAdminDisputes(DisputeStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Page<Dispute> page;
        if (status != null && from != null && to != null) {
            page = disputeRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, from, to, pageable);
        } else if (status != null) {
            page = disputeRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            page = disputeRepository.findAll(pageable);
        }
        return toPageResponse(page);
    }

    /**
     * Admin dispute detail. 404 if not found.
     */
    @Transactional(readOnly = true)
    public DisputeDetailResponse getAdminDispute(Long disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> BusinessException.notFound("Dispute not found: " + disputeId));
        return toDetailResponse(dispute);
    }

    /**
     * Returns the dispute window in days. Read from {@code platform_config};
     * falls back to the documented default if the row is absent.
     */
    public int getDisputeWindowDays() {
        return platformConfigRepository.findByConfigKey(DISPUTE_WINDOW_DAYS_KEY)
                .map(cfg -> parseDays(cfg.getConfigValue()))
                .orElseGet(() -> {
                    log.warn("platform_config row '{}' missing; using documented default {} days",
                            DISPUTE_WINDOW_DAYS_KEY, DEFAULT_DISPUTE_WINDOW_DAYS);
                    return DEFAULT_DISPUTE_WINDOW_DAYS;
                });
    }

    private static int parseDays(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "platform_config.dispute_window_days value '" + value + "' is not a valid integer", e);
        }
    }

    private DisputePageResponse toPageResponse(Page<Dispute> page) {
        return DisputePageResponse.builder()
                .content(page.getContent().stream().map(this::toListItem).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    private DisputeListItem toListItem(Dispute d) {
        return DisputeListItem.builder()
                .id(d.getId())
                .orderId(d.getOrder().getId())
                .orderNumber("Order #" + d.getOrder().getId())
                .status(d.getStatus())
                .reasonCode(d.getReasonCode())
                .customerName(d.getRaisedBy().getFullName())
                .vendorName(d.getOrder().getVendor().getBusinessName())
                .orderTotal(d.getOrder().getTotal())
                .createdAt(d.getCreatedAt())
                .resolvedAt(d.getResolvedAt())
                .build();
    }

    private DisputeDetailResponse toDetailResponse(Dispute d) {
        String vendorResponse = null;
        if (d.getResolutionNote() != null && d.getResolutionNote().startsWith("Vendor response: ")) {
            vendorResponse = d.getResolutionNote().substring("Vendor response: ".length());
        }
        String resolutionNote = (d.getStatus() == DisputeStatus.OPEN || d.getStatus() == DisputeStatus.UNDER_REVIEW)
                ? null : d.getResolutionNote();
        return DisputeDetailResponse.builder()
                .id(d.getId())
                .orderId(d.getOrder().getId())
                .orderNumber("Order #" + d.getOrder().getId())
                .status(d.getStatus())
                .reasonCode(d.getReasonCode())
                .description(d.getDescription())
                .customerName(d.getRaisedBy().getFullName())
                .customerEmail(d.getRaisedBy().getEmail())
                .vendorName(d.getOrder().getVendor().getBusinessName())
                .orderTotal(d.getOrder().getTotal())
                .vendorResponse(vendorResponse)
                .resolutionNote(resolutionNote)
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .resolvedAt(d.getResolvedAt())
                .build();
    }
}