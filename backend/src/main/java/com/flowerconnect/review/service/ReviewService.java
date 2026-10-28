package com.flowerconnect.review.service;

import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.review.domain.Review;
import com.flowerconnect.review.dto.ReviewRequest;
import com.flowerconnect.review.dto.ReviewResponse;
import com.flowerconnect.review.dto.ReviewDetailResponse;
import com.flowerconnect.review.dto.VendorReviewSummary;
import com.flowerconnect.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Review orchestration (plan Phase 12, task 12.2).
 *
 * <p>Business rules:
 * <ul>
 *   <li>Only the order's customer may submit a review.</li>
 *   <li>The order must be in {@code DELIVERED} status.</li>
 *   <li>Rating is 1-5; comment is optional, max 1000 chars.</li>
 *   <li>At most one review per order ever (enforced by {@code uq_reviews_order}
 *       and checked here for a clean 409).</li>
 *   <li>A vendor may submit one reply per review; a second attempt returns 409.
 *       Replies are trimmed and capped at 1000 chars. A suspended vendor may
 *       still reply to reviews on their delivered orders (D-6).</li>
 *   <li>Admin hide/unhide writes an {@code audit_log} row. Hidden reviews are
 *       excluded from public storefronts and aggregates, but remain visible to
 *       admins, the owning vendor and the review's customer.</li>
 *   <li>{@code avg_rating} / {@code review_count} on {@code vendor_profiles}
 *       are recomputed over visible reviews only, in the same transaction as
 *       create/hide/unhide, under a vendor-row lock. No visible reviews means
 *       {@code avg_rating = null} and {@code review_count = 0}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    /**
     * Submits a review for the given order on behalf of the authenticated
     * customer.
     *
     * @param customerEmail the JWT subject (the customer's email)
     * @param orderId       the order to review
     * @param request       rating and optional comment
     * @return the created review detail
     * @throws BusinessException 404 if order not found, 403 if not the
     *                           customer's order, 409 if order not DELIVERED or
     *                           a review already exists, 400 for invalid input
     */
    @Transactional
    public ReviewDetailResponse submitReview(String customerEmail, Long orderId, ReviewRequest request) {
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw BusinessException.badRequest("Rating must be between 1 and 5");
        }
        if (request.getComment() != null && request.getComment().length() > 1000) {
            throw BusinessException.badRequest("Comment must be 1000 characters or fewer");
        }

        var customer = userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> BusinessException.notFound("Authenticated customer not found"));

        // Lock the order row first (agreed lock order: order row before vendor row)
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        if (!order.getUser().getId().equals(customer.getId())) {
            throw BusinessException.forbidden("Order " + orderId + " belongs to another customer");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw BusinessException.conflict(
                    "Reviews can only be submitted for DELIVERED orders; current status: " + order.getStatus());
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw BusinessException.conflict("A review already exists for order " + orderId);
        }

        Review review = Review.builder()
                .order(order)
                .customer(customer)
                .vendor(order.getVendor())
                .rating(request.getRating())
                .comment(request.getComment() == null ? null : request.getComment().trim())
                .vendorReply(null)
                .vendorRepliedAt(null)
                .hidden(false)
                .build();
        reviewRepository.saveAndFlush(review);

        recomputeAggregate(order.getVendor().getId());

        log.info("Customer {} submitted review {} for order {} (rating: {})",
                customer.getEmail(), review.getId(), orderId, request.getRating());
        return toDetailResponse(review);
    }

    /**
     * The authenticated customer's own review. Returns it even if hidden.
     */
    @Transactional(readOnly = true)
    public ReviewDetailResponse getCustomerReview(String customerEmail, Long reviewId) {
        var customer = userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> BusinessException.notFound("Authenticated customer not found"));
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> BusinessException.notFound("Review not found: " + reviewId));
        if (!review.getCustomer().getId().equals(customer.getId())) {
            throw BusinessException.forbidden("Review " + reviewId + " belongs to another customer");
        }
        return toDetailResponse(review);
    }

    /**
     * All reviews on the authenticated vendor's orders (including hidden),
     * newest first. Paginated.
     */
    @Transactional(readOnly = true)
    public Page<ReviewDetailResponse> listVendorReviews(String vendorEmail, Pageable pageable) {
        VendorProfile vendorProfile = vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound(
                        "No vendor profile exists for this account"));
        Page<Review> page = reviewRepository.findByVendorIdOrderByCreatedAtDesc(vendorProfile.getId(), pageable);
        return page.map(this::toDetailResponse);
    }

    /**
     * Aggregate rating summary for the authenticated vendor's visible reviews.
     */
    @Transactional(readOnly = true)
    public VendorReviewSummary getVendorReviewSummary(String vendorEmail) {
        VendorProfile vendorProfile = vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound(
                        "No vendor profile exists for this account"));
        Long vendorId = vendorProfile.getId();
        Long reviewCount = reviewRepository.countByVendorIdAndHiddenFalse(vendorId);
        Double average = reviewRepository.averageRatingByVendorIdAndHiddenFalse(vendorId);
        BigDecimal avgRating = average == null ? null
                : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
        return VendorReviewSummary.builder()
                .avgRating(avgRating)
                .reviewCount(reviewCount.intValue())
                .ratingDistribution(ratingDistribution(vendorId))
                .build();
    }

    /**
     * Public listing of visible (non-hidden) reviews for a vendor, newest
     * first. Paginated. Customer identity is masked.
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> listPublicVendorReviews(Long vendorId, Pageable pageable) {
        VendorProfile vendorProfile = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> BusinessException.notFound("Vendor not found: " + vendorId));
        if (vendorProfile.getStatus() != VendorProfile.Status.APPROVED) {
            throw BusinessException.notFound("Vendor not found: " + vendorId);
        }
        Page<Review> page = reviewRepository.findByVendorIdAndHiddenFalseOrderByCreatedAtDesc(vendorId, pageable);
        return page.map(this::toPublicResponse);
    }

    /**
     * Vendor replies to a review on their own order. One reply per review; a
     * second attempt returns 409. The reply is trimmed and capped at 1000
     * chars. A suspended vendor may still reply (D-6).
     */
    @Transactional
    public ReviewDetailResponse replyToReview(String vendorEmail, Long reviewId, String reply) {
        if (reply == null || reply.trim().isEmpty()) {
            throw BusinessException.badRequest("Vendor reply is required");
        }
        String trimmed = reply.trim();
        if (trimmed.length() > 1000) {
            throw BusinessException.badRequest("Vendor reply must be 1000 characters or fewer");
        }

        VendorProfile vendorProfile = vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound(
                        "No vendor profile exists for this account"));

        // Lock the review row for concurrency control
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> BusinessException.notFound("Review not found: " + reviewId));
        if (!review.getVendor().getId().equals(vendorProfile.getId())) {
            throw BusinessException.forbidden("Review " + reviewId + " belongs to another vendor");
        }
        if (review.getVendorReply() != null) {
            throw BusinessException.conflict("Review " + reviewId + " already has a vendor reply");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        review.setVendorReply(trimmed);
        review.setVendorRepliedAt(now);
        reviewRepository.saveAndFlush(review);

        log.info("Vendor {} replied to review {} for order {}", vendorEmail, reviewId, review.getOrder().getId());
        return toDetailResponse(review);
    }

    /**
     * Admin hides a review. Writes an {@code audit_log} row. Recomputes the
     * vendor aggregate in the same transaction.
     */
    @Transactional
    public ReviewDetailResponse hideReview(String adminEmail, Long reviewId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.badRequest("Hide reason is required");
        }
        if (reason.trim().length() > 500) {
            throw BusinessException.badRequest("Hide reason must be 500 characters or fewer");
        }
        var admin = userRepository.findByEmailWithRole(adminEmail)
                .orElseThrow(() -> BusinessException.notFound("Authenticated admin not found"));

        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> BusinessException.notFound("Review not found: " + reviewId));
        if (review.getHidden()) {
            throw BusinessException.conflict("Review " + reviewId + " is already hidden");
        }

        String before = "visible";
        review.setHidden(true);
        review.setHiddenReason(reason.trim());
        review.setHiddenBy(admin.getId());
        review.setHiddenAt(LocalDateTime.now(clock));
        reviewRepository.saveAndFlush(review);

        recomputeAggregate(review.getVendor().getId());

        auditLogRepository.save(com.flowerconnect.domain.AuditLog.builder()
                .actor(admin)
                .actionType("REVIEW_HIDDEN")
                .entityType("REVIEW")
                .entityId(review.getId())
                .reason("Review " + review.getId() + " for order " + review.getOrder().getId()
                        + ": " + before + " -> hidden (reason: " + reason.trim() + ")")
                .build());

        log.info("Admin {} hid review {} for order {}", adminEmail, reviewId, review.getOrder().getId());
        return toDetailResponse(review);
    }

    /**
     * Admin unhides a review. Writes an {@code audit_log} row. Recomputes the
     * vendor aggregate in the same transaction.
     */
    @Transactional
    public ReviewDetailResponse unhideReview(String adminEmail, Long reviewId) {
        var admin = userRepository.findByEmailWithRole(adminEmail)
                .orElseThrow(() -> BusinessException.notFound("Authenticated admin not found"));

        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> BusinessException.notFound("Review not found: " + reviewId));
        if (!review.getHidden()) {
            throw BusinessException.conflict("Review " + reviewId + " is not hidden");
        }

        String before = "hidden";
        review.setHidden(false);
        review.setHiddenReason(null);
        review.setHiddenBy(null);
        review.setHiddenAt(null);
        reviewRepository.saveAndFlush(review);

        recomputeAggregate(review.getVendor().getId());

        auditLogRepository.save(com.flowerconnect.domain.AuditLog.builder()
                .actor(admin)
                .actionType("REVIEW_UNHIDDEN")
                .entityType("REVIEW")
                .entityId(review.getId())
                .reason("Review " + review.getId() + " for order " + review.getOrder().getId()
                        + ": " + before + " -> visible")
                .build());

        log.info("Admin {} unhid review {} for order {}", adminEmail, reviewId, review.getOrder().getId());
        return toDetailResponse(review);
    }

    /**
     * Paginated admin review listing with optional hidden filter, newest first.
     */
    @Transactional(readOnly = true)
    public Page<ReviewDetailResponse> listAdminReviews(Boolean hidden, Pageable pageable) {
        Page<Review> page;
        if (hidden != null) {
            page = reviewRepository.findByHiddenOrderByCreatedAtDesc(hidden, pageable);
        } else {
            page = reviewRepository.findAll(pageable);
        }
        return page.map(this::toDetailResponse);
    }

    /**
     * Recomputes {@code vendor_profiles.avg_rating} and {@code review_count}
     * over visible (non-hidden) reviews for the given vendor. Must be called
     * within a transaction that already holds a lock on the vendor profile row.
     */
    private void recomputeAggregate(Long vendorId) {
        Long reviewCount = reviewRepository.countByVendorIdAndHiddenFalse(vendorId);
        Double average = reviewRepository.averageRatingByVendorIdAndHiddenFalse(vendorId);
        VendorProfile vendorProfile = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found: " + vendorId));
        vendorProfile.setReviewCount(reviewCount.intValue());
        vendorProfile.setAvgRating(average == null ? null
                : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        vendorProfileRepository.saveAndFlush(vendorProfile);
    }

    private Map<Integer, Integer> ratingDistribution(Long vendorId) {
        Map<Integer, Integer> distribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0);
        }
        List<Object[]> rows = reviewRepository.ratingDistributionByVendorId(vendorId);
        for (Object[] row : rows) {
            Integer rating = ((Number) row[0]).intValue();
            Long count = (Long) row[1];
            distribution.put(rating, count.intValue());
        }
        return distribution;
    }

    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Customer";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private ReviewDetailResponse toDetailResponse(Review review) {
        return ReviewDetailResponse.builder()
                .id(review.getId())
                .orderId(review.getOrder().getId())
                .customerId(review.getCustomer().getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .customerName(maskName(review.getCustomer().getFullName()))
                .hidden(review.getHidden())
                .hiddenReason(review.getHiddenReason())
                .vendorReply(review.getVendorReply())
                .vendorRepliedAt(review.getVendorRepliedAt())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private ReviewResponse toPublicResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .customerName(maskName(review.getCustomer().getFullName()))
                .vendorReply(review.getVendorReply())
                .vendorRepliedAt(review.getVendorRepliedAt())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
