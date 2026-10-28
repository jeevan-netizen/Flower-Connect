package com.flowerconnect.review.controller;

import com.flowerconnect.review.dto.ReviewRequest;
import com.flowerconnect.review.dto.ReviewResponse;
import com.flowerconnect.review.dto.ReviewDetailResponse;
import com.flowerconnect.review.dto.VendorReplyRequest;
import com.flowerconnect.review.dto.VendorReviewSummary;
import com.flowerconnect.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Customer review submission and retrieval, and vendor reply (plan Phase 12,
 * task 12.2).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/orders/{orderId}/reviews} — submit a review (CUSTOMER)</li>
 *   <li>{@code GET /api/v1/reviews/{reviewId}} — the caller's own review (CUSTOMER)</li>
 *   <li>{@code PATCH /api/v1/vendors/reviews/{reviewId}/reply} — vendor reply (FLORIST)</li>
 *   <li>{@code GET /api/v1/vendors/reviews} — the vendor's own reviews (FLORIST)</li>
 *   <li>{@code GET /api/v1/vendors/reviews/summary} — vendor review aggregate (FLORIST)</li>
 * </ul>
 * All endpoints require the indicated role and operate on the caller's own data;
 * ownership is enforced in {@link ReviewService}.
 */
@Slf4j
@Validated
@Tag(name = "Customer reviews", description = "Submit and retrieve reviews for delivered orders")
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Submits a review for a delivered order. Only the order's customer may
     * submit, and only once per order.
     */
    @Operation(summary = "Submit a review",
            description = "Creates a review for a DELIVERED order. Rating 1-5, "
                    + "optional comment up to 1000 characters. One review per order.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Review created"),
            @ApiResponse(responseCode = "400", description = "Invalid rating or comment"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not the order's customer"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order not DELIVERED or review already exists")
    })
    @PostMapping("/api/v1/orders/{orderId}/reviews")
    public ResponseEntity<ReviewDetailResponse> submitReview(
            Authentication authentication,
            @Parameter(description = "Order ID (must be DELIVERED and owned by the caller)")
            @PathVariable Long orderId,
            @Valid @RequestBody ReviewRequest request) {
        ReviewDetailResponse response = reviewService.submitReview(
                authentication.getName(), orderId, request);
        log.info("Customer {} submitted review for order {}", authentication.getName(), orderId);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Returns the authenticated customer's own review, even if it is hidden.
     */
    @Operation(summary = "Get the customer's own review",
            description = "Returns the caller's review for the given review ID. "
                    + "The caller must be the review's customer; hidden reviews "
                    + "are still returned to their owner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review detail"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not the review's customer"),
            @ApiResponse(responseCode = "404", description = "Review not found")
    })
    @GetMapping("/api/v1/reviews/{reviewId}")
    public ResponseEntity<ReviewDetailResponse> getCustomerReview(
            Authentication authentication,
            @Parameter(description = "Review ID")
            @PathVariable Long reviewId) {
        ReviewDetailResponse response = reviewService.getCustomerReview(
                authentication.getName(), reviewId);
        return ResponseEntity.ok(response);
    }

    /**
     * Replies to a review on the vendor's own order. One reply per review; a
     * second attempt returns 409.
     */
    @Operation(summary = "Reply to a review",
            description = "Adds a vendor reply to a review on the vendor's own "
                    + "order. One reply per review; a second attempt returns 409. "
                    + "Reply is trimmed and capped at 1000 characters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reply added"),
            @ApiResponse(responseCode = "400", description = "Invalid reply"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not the review's vendor"),
            @ApiResponse(responseCode = "404", description = "Review not found"),
            @ApiResponse(responseCode = "409", description = "Review already has a reply")
    })
    @PatchMapping("/api/v1/vendors/reviews/{reviewId}/reply")
    public ResponseEntity<ReviewDetailResponse> replyToReview(
            Authentication authentication,
            @Parameter(description = "Review ID")
            @PathVariable Long reviewId,
            @Valid @RequestBody VendorReplyRequest request) {
        ReviewDetailResponse response = reviewService.replyToReview(
                authentication.getName(), reviewId, request.getReply());
        log.info("Vendor {} replied to review {}", authentication.getName(), reviewId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the authenticated vendor's own reviews (including hidden ones),
     * newest first. Paginated.
     */
    @Operation(summary = "List the vendor's own reviews",
            description = "Returns one page of the vendor's reviews, newest first. "
                    + "Hidden reviews are included because the caller owns them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vendor review page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account")
    })
    @GetMapping("/api/v1/vendors/reviews")
    public ResponseEntity<Page<ReviewDetailResponse>> listVendorReviews(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ReviewDetailResponse> page = reviewService.listVendorReviews(
                authentication.getName(), pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * Returns the vendor's review aggregate (average rating, count, and 1-5
     * distribution over visible reviews).
     */
    @Operation(summary = "Get vendor review summary",
            description = "Returns the average rating, review count, and "
                    + "rating distribution over the vendor's visible reviews.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review summary"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account")
    })
    @GetMapping("/api/v1/vendors/reviews/summary")
    public ResponseEntity<VendorReviewSummary> getVendorReviewSummary(
            Authentication authentication) {
        VendorReviewSummary summary = reviewService.getVendorReviewSummary(
                authentication.getName());
        return ResponseEntity.ok(summary);
    }

    /**
     * Public listing of visible (non-hidden) reviews for a vendor, newest first.
     * Paginated. Customer identity is masked. Returns 404 for a vendor that is
     * unknown or not approved (consistent with the storefront rule).
     */
    @Operation(summary = "List public reviews for a vendor",
            description = "Returns one page of visible reviews for an approved "
                    + "vendor, newest first. No authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Public review page"),
            @ApiResponse(responseCode = "404", description = "Vendor not found or not approved")
    })
    @GetMapping("/api/v1/vendors/{vendorId}/reviews")
    public ResponseEntity<Page<ReviewResponse>> listPublicVendorReviews(
            @Parameter(description = "Vendor profile ID")
            @PathVariable Long vendorId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ReviewResponse> page = reviewService.listPublicVendorReviews(vendorId, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * Admin hides a review. Requires a non-blank reason (<= 500 chars). Writes
     * an audit-log row and recomputes the vendor aggregate in the same
     * transaction.
     */
    @Operation(summary = "Hide a review",
            description = "Hides a review from public storefronts and aggregates. "
                    + "Remains visible to admins, the owning vendor, and the "
                    + "review's customer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review hidden"),
            @ApiResponse(responseCode = "400", description = "Reason missing or too long"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Review not found"),
            @ApiResponse(responseCode = "409", description = "Review already hidden")
    })
    @PatchMapping("/api/v1/admin/reviews/{reviewId}/hide")
    public ResponseEntity<ReviewDetailResponse> hideReview(
            Authentication authentication,
            @Parameter(description = "Review ID")
            @PathVariable Long reviewId,
            @Parameter(description = "Moderation reason, required, max 500 chars")
            @RequestParam String reason) {
        ReviewDetailResponse response = reviewService.hideReview(
                authentication.getName(), reviewId, reason);
        log.info("Admin {} hid review {}", authentication.getName(), reviewId);
        return ResponseEntity.ok(response);
    }

    /**
     * Admin unhides a previously hidden review. Writes an audit-log row and
     * recomputes the vendor aggregate in the same transaction.
     */
    @Operation(summary = "Unhide a review",
            description = "Restores a hidden review to public storefronts and "
                    + "aggregates.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review unhidden"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Review not found"),
            @ApiResponse(responseCode = "409", description = "Review not hidden")
    })
    @PatchMapping("/api/v1/admin/reviews/{reviewId}/unhide")
    public ResponseEntity<ReviewDetailResponse> unhideReview(
            Authentication authentication,
            @Parameter(description = "Review ID")
            @PathVariable Long reviewId) {
        ReviewDetailResponse response = reviewService.unhideReview(
                authentication.getName(), reviewId);
        log.info("Admin {} unhid review {}", authentication.getName(), reviewId);
        return ResponseEntity.ok(response);
    }

    /**
     * Paginated admin review listing with optional hidden-state filter,
     * newest first.
     */
    @Operation(summary = "List reviews for moderation",
            description = "Returns one page of reviews, newest first. Optional "
                    + "hidden filter shows only hidden or only visible reviews.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Admin review page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    @GetMapping("/api/v1/admin/reviews")
    public ResponseEntity<Page<ReviewDetailResponse>> listAdminReviews(
            Authentication authentication,
            @Parameter(description = "Optional hidden-state filter")
            @RequestParam(required = false) Boolean hidden,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ReviewDetailResponse> page = reviewService.listAdminReviews(hidden, pageable);
        return ResponseEntity.ok(page);
    }
}
