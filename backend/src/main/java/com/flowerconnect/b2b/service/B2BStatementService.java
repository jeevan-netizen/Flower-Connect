package com.flowerconnect.b2b.service;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BStatement;
import com.flowerconnect.b2b.domain.B2BStatementOrder;
import com.flowerconnect.b2b.dto.B2BStatementOrderSummary;
import com.flowerconnect.b2b.dto.B2BStatementPageResponse;
import com.flowerconnect.b2b.dto.B2BStatementPreviewResponse;
import com.flowerconnect.b2b.dto.B2BStatementResponse;
import com.flowerconnect.b2b.dto.CreateB2BStatementRequest;
import com.flowerconnect.b2b.dto.RecordB2BStatementPaymentRequest;
import com.flowerconnect.b2b.repository.B2BOrderRepository;
import com.flowerconnect.b2b.repository.B2BStatementOrderRepository;
import com.flowerconnect.b2b.repository.B2BStatementRepository;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * B2B statement service (Stage 3 of the B2B module).
 *
 * <p>Owns statement creation, listing, detail and payment recording. A
 * statement groups delivered, unpaid orders for one seller and one buyer
 * within an inclusive period. Statement creation, order linking and payment
 * recording are atomic: the eligible orders are pessimistically locked, the
 * unique constraint on {@code b2b_statement_orders(order_id)} is the final
 * safeguard against two statements sharing an order, and any uniqueness
 * conflict rolls the whole transaction back and is reported as a clear API
 * error.
 *
 * <p>All timestamps come from the injected {@link Clock}; {@code
 * Instant.now()} is never called directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class B2BStatementService {

    private static final int DEFAULT_LOOKBACK_DAYS = 15;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final B2BOrderRepository orderRepository;
    private final B2BStatementRepository statementRepository;
    private final B2BStatementOrderRepository statementOrderRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final Clock clock;

    // ------------------------------------------------------------------
    // Preview eligible orders
    // ------------------------------------------------------------------

    /**
     * Returns the orders currently eligible for a statement from the caller
     * (as seller) to the selected buyer within the optional date range, plus
     * their combined total. Advisory only: no lock is taken, so the result may
     * change before a statement is created.
     *
     * @param sellerEmail the authenticated caller (resolved as the seller)
     * @param buyerId     the buyer vendor profile id
     * @param from        inclusive period start date (optional; defaults to
     *                    {@code to - 15 days})
     * @param to          inclusive period end date (optional; defaults to today)
     */
    @Transactional(readOnly = true)
    public B2BStatementPreviewResponse preview(String sellerEmail, Long buyerId,
                                               LocalDate from, LocalDate to) {
        VendorProfile seller = requireApprovedVendor(sellerEmail);
        VendorProfile buyer = requireVendor(buyerId);
        if (buyer.getId().equals(seller.getId())) {
            throw BusinessException.badRequest("A vendor cannot create a statement for itself");
        }
        LocalDate[] range = resolveRange(from, to);
        List<B2BOrder> eligible = orderRepository.findDeliveredUnpaidBySellerAndBuyer(
                seller.getId(), buyer.getId(),
                Status.DELIVERED, PaymentStatus.UNPAID,
                range[0].atStartOfDay(), range[1].plusDays(1).atStartOfDay());
        BigDecimal total = eligible.stream()
                .map(B2BOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return B2BStatementPreviewResponse.builder()
                .orders(eligible.stream().map(this::toOrderSummary).collect(Collectors.toList()))
                .totalAmount(total)
                .build();
    }

    // ------------------------------------------------------------------
    // Create statement
    // ------------------------------------------------------------------

    /**
     * Creates an {@code OPEN} statement from the currently eligible orders.
     * The eligible orders are pessimistically locked so concurrent creations
     * serialise; the database unique constraint on
     * {@code b2b_statement_orders(order_id)} is the final safeguard. A
     * uniqueness conflict rolls the whole transaction back and is reported as
     * a clear {@code CONFLICT} error.
     */
    @Transactional
    public B2BStatementResponse createStatement(String sellerEmail,
                                                CreateB2BStatementRequest request) {
        VendorProfile seller = requireApprovedVendor(sellerEmail);
        VendorProfile buyer = requireVendor(request.getBuyerId());
        if (buyer.getId().equals(seller.getId())) {
            throw BusinessException.badRequest("A vendor cannot create a statement for itself");
        }
        LocalDate[] range = resolveRange(request.getFrom(), request.getTo());
        LocalDateTime fromBoundary = range[0].atStartOfDay();
        LocalDateTime toExclusive = range[1].plusDays(1).atStartOfDay();

        // Lock the eligible orders so concurrent statement creations serialise
        // on the same orders. Only orders not already in a statement qualify.
        List<B2BOrder> eligible = orderRepository.findDeliveredUnpaidBySellerAndBuyerForUpdate(
                seller.getId(), buyer.getId(),
                Status.DELIVERED, PaymentStatus.UNPAID,
                fromBoundary, toExclusive);
        if (eligible.isEmpty()) {
            throw BusinessException.badRequest(
                    "No eligible orders to create a statement for buyer " + buyer.getId() +
                            " from " + range[0] + " to " + range[1]);
        }

        BigDecimal total = eligible.stream()
                .map(B2BOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime now = LocalDateTime.now(clock);
        B2BStatement statement = B2BStatement.builder()
                .statementNo(generateStatementNo())
                .seller(seller)
                .buyer(buyer)
                .periodStart(fromBoundary)
                .periodEnd(toExclusive.minusNanos(1))
                .totalAmount(total)
                .status(B2BStatement.Status.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();
        statementRepository.saveAndFlush(statement);

        try {
            for (B2BOrder order : eligible) {
                B2BStatementOrder link = B2BStatementOrder.builder()
                        .statement(statement)
                        .order(order)
                        .build();
                statementOrderRepository.save(link);
            }
            statementRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // An order is already part of another statement. The whole
            // transaction rolls back, so no partial statement row remains.
            throw BusinessException.conflict(
                    "One or more orders are already part of another statement");
        }

        log.info("Created statement {}: seller={} buyer={} orders={} total={}",
                statement.getStatementNo(), seller.getId(), buyer.getId(),
                eligible.size(), total);
        return toResponse(statement);
    }

    // ------------------------------------------------------------------
    // List and detail
    // ------------------------------------------------------------------

    /**
     * Lists the caller's statements as {@code SELLER} or {@code BUYER}. The
     * requested role selects which side of the statement the caller must be
     * on; role filtering can never surface a statement the caller is not a
     * party to.
     */
    @Transactional(readOnly = true)
    public B2BStatementPageResponse listStatements(String vendorEmail, String role,
                                                   int page, int size) {
        VendorProfile vendor = requireApprovedVendor(vendorEmail);
        String resolvedRole = (role == null || role.isBlank()) ? "SELLER" : role;
        if (!"SELLER".equalsIgnoreCase(resolvedRole) && !"BUYER".equalsIgnoreCase(resolvedRole)) {
            throw BusinessException.badRequest("Role must be SELLER or BUYER");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        List<B2BStatement> all = "SELLER".equalsIgnoreCase(resolvedRole)
                ? statementRepository.findBySellerId(vendor.getId())
                : statementRepository.findByBuyerId(vendor.getId());

        long total = all.size();
        List<B2BStatement> content = all.stream()
                .sorted((a, b) -> {
                    int c = b.getCreatedAt().compareTo(a.getCreatedAt());
                    if (c != 0) {
                        return c;
                    }
                    return Long.compare(b.getId(), a.getId());
                })
                .skip((long) safePage * safeSize)
                .limit(safeSize)
                .collect(Collectors.toList());

        List<B2BStatementResponse> contentResponse = content.stream()
                .map(this::toResponse).collect(Collectors.toList());

        return B2BStatementPageResponse.builder()
                .content(contentResponse)
                .page(safePage)
                .size(safeSize)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / safeSize))
                .first(safePage == 0)
                .last((safePage + 1) * safeSize >= total)
                .empty(contentResponse.isEmpty())
                .build();
    }

    /**
     * Returns a single statement. Only the seller or the buyer on the
     * statement may read it; any other vendor is refused.
     */
    @Transactional(readOnly = true)
    public B2BStatementResponse getStatement(String vendorEmail, Long statementId) {
        VendorProfile vendor = requireApprovedVendor(vendorEmail);
        B2BStatement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> BusinessException.notFound("B2B statement not found"));
        if (!statement.getSeller().getId().equals(vendor.getId())
                && !statement.getBuyer().getId().equals(vendor.getId())) {
            throw BusinessException.forbidden("You are not a party to this statement");
        }
        return toResponse(statement);
    }

    // ------------------------------------------------------------------
    // Record payment
    // ------------------------------------------------------------------

    /**
     * Records full payment for an {@code OPEN} statement and marks every
     * linked order's {@code payment_status} {@code PAID} in one transaction.
     * Only the statement's seller may record payment. A statement that is
     * already {@code PAID} is rejected.
     */
    @Transactional
    public B2BStatementResponse recordPayment(String sellerEmail, Long statementId,
                                              RecordB2BStatementPaymentRequest request) {
        VendorProfile seller = requireApprovedVendor(sellerEmail);
        B2BStatement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> BusinessException.notFound("B2B statement not found"));
        if (!statement.getSeller().getId().equals(seller.getId())) {
            throw BusinessException.forbidden("Only the seller can record payment for this statement");
        }
        if (statement.getStatus() == B2BStatement.Status.PAID) {
            throw BusinessException.conflict("Statement " + statementId + " is already PAID");
        }

        LocalDateTime paidAt = (request.getPaidAt() != null)
                ? request.getPaidAt()
                : LocalDateTime.now(clock);
        statement.setStatus(B2BStatement.Status.PAID);
        statement.setPaidAt(paidAt);
        statement.setPaymentMode(request.getPaymentMode());
        statement.setPaymentNote(request.getPaymentNote());
        statement.setUpdatedAt(paidAt);
        statementRepository.saveAndFlush(statement);

        List<B2BStatementOrder> links = statementOrderRepository.findByStatementId(statement.getId());
        for (B2BStatementOrder link : links) {
            B2BOrder order = link.getOrder();
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setUpdatedAt(paidAt);
            orderRepository.saveAndFlush(order);
        }

        log.info("Recorded payment for statement {}: mode={} paidAt={} orders={}",
                statement.getStatementNo(), request.getPaymentMode(), paidAt, links.size());
        return toResponse(statement);
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    private VendorProfile requireApprovedVendor(String email) {
        VendorProfile vendor = vendorProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));
        if (vendor.getStatus() != VendorProfile.Status.APPROVED) {
            throw BusinessException.forbidden("Vendor profile is not APPROVED");
        }
        return vendor;
    }

    private VendorProfile requireVendor(Long vendorId) {
        return vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));
    }

    // ------------------------------------------------------------------
    // Date range helpers
    // ------------------------------------------------------------------

    /**
     * Resolves the inclusive from/to range. A missing {@code to} defaults to
     * today; a missing {@code from} defaults to 15 days before {@code to}.
     * The range must be valid (from not after to).
     */
    private LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = (to != null) ? to : LocalDate.now(clock);
        LocalDate resolvedFrom = (from != null) ? from : resolvedTo.minusDays(DEFAULT_LOOKBACK_DAYS);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw BusinessException.badRequest(
                    "From date " + resolvedFrom + " must not be after to date " + resolvedTo);
        }
        return new LocalDate[]{resolvedFrom, resolvedTo};
    }

    // ------------------------------------------------------------------
    // Mappers
    // ------------------------------------------------------------------

    private B2BStatementResponse toResponse(B2BStatement statement) {
        List<B2BStatementOrder> links = statementOrderRepository.findByStatementId(statement.getId());
        return B2BStatementResponse.builder()
                .id(statement.getId())
                .statementNo(statement.getStatementNo())
                .sellerId(statement.getSeller().getId())
                .sellerName(statement.getSeller().getBusinessName())
                .buyerId(statement.getBuyer().getId())
                .buyerName(statement.getBuyer().getBusinessName())
                .periodStart(statement.getPeriodStart())
                .periodEnd(statement.getPeriodEnd())
                .totalAmount(statement.getTotalAmount())
                .status(statement.getStatus())
                .paidAt(statement.getPaidAt())
                .paymentMode(statement.getPaymentMode())
                .paymentNote(statement.getPaymentNote())
                .createdAt(statement.getCreatedAt())
                .updatedAt(statement.getUpdatedAt())
                .orders(links.stream()
                        .map(link -> toOrderSummary(link.getOrder()))
                        .collect(Collectors.toList()))
                .build();
    }

    private B2BStatementOrderSummary toOrderSummary(B2BOrder order) {
        return B2BStatementOrderSummary.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .total(order.getTotal())
                .deliveredAt(order.getDeliveredAt())
                .buyerId(order.getBuyer().getId())
                .sellerId(order.getSeller().getId())
                .build();
    }

    private String generateStatementNo() {
        String stamp = String.valueOf(LocalDateTime.now(clock).toEpochSecond(java.time.ZoneOffset.UTC));
        return "STM-" + stamp + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
