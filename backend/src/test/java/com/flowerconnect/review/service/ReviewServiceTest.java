package com.flowerconnect.review.service;

import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
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
import com.flowerconnect.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for {@link ReviewService} (plan Phase 12, task 12.2):
 * review submission validation, ownership, one-review-per-order, and
 * aggregate recomputation.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-15T10:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private ReviewService reviewService;

    private User customer;
    private Order order;
    private VendorProfile vendor;

    private void setUp() {
        customer = User.builder().id(100L).email("customer@example.com").fullName("Customer One").build();
        vendor = VendorProfile.builder().id(200L).businessName("Florist Shop").build();
        order = Order.builder().id(300L).user(customer).vendor(vendor).status(OrderStatus.DELIVERED).build();
        reviewService = new ReviewService(reviewRepository, orderRepository, userRepository,
                vendorProfileRepository, auditLogRepository, CLOCK);
    }

    private ReviewRequest request(Integer rating, String comment) {
        ReviewRequest r = new ReviewRequest();
        r.setRating(rating);
        r.setComment(comment);
        return r;
    }

    @Test
    void submitReviewHappyPathCreatesReviewAndRecomputesAggregate() {
        setUp();
        when(userRepository.findByEmailWithRole("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(order));
        when(reviewRepository.existsByOrderId(300L)).thenReturn(false);
        when(vendorProfileRepository.findById(200L)).thenReturn(Optional.of(vendor));
        when(reviewRepository.countByVendorIdAndHiddenFalse(200L)).thenReturn(1L);
        when(reviewRepository.averageRatingByVendorIdAndHiddenFalse(200L)).thenReturn(4.5);

        ReviewDetailResponse response = reviewService.submitReview("customer@example.com", 300L,
                request(5, "Lovely bouquet"));

        assertNotNull(response);
        assertEquals(5, response.getRating());
        assertEquals("Lovely bouquet", response.getComment());
        assertFalse(response.getHidden());

        verify(reviewRepository).saveAndFlush(any(Review.class));
        verify(vendorProfileRepository).saveAndFlush(any(VendorProfile.class));
        ArgumentCaptor<VendorProfile> captor = ArgumentCaptor.forClass(VendorProfile.class);
        verify(vendorProfileRepository).saveAndFlush(captor.capture());
        VendorProfile updated = captor.getValue();
        assertEquals(1, updated.getReviewCount());
        assertEquals(new BigDecimal("4.50"), updated.getAvgRating());
    }

    @Test
    void submitReviewRejectsRatingOutOfBounds() {
        setUp();
        assertThrows(BusinessException.class, () -> reviewService.submitReview("customer@example.com", 300L,
                request(0, "x")));
        assertThrows(BusinessException.class, () -> reviewService.submitReview("customer@example.com", 300L,
                request(6, "x")));
        verifyNoInteractions(orderRepository);
    }

    @Test
    void submitReviewRejectsCommentTooLong() {
        setUp();
        String tooLong = "a".repeat(1001);
        assertThrows(BusinessException.class, () -> reviewService.submitReview("customer@example.com", 300L,
                request(5, tooLong)));
        verifyNoInteractions(orderRepository);
    }

    @Test
    void submitReviewOrderNotFound() {
        setUp();
        when(userRepository.findByEmailWithRole("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(300L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> reviewService.submitReview(
                "customer@example.com", 300L, request(5, "x")));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void submitReviewForeignOrderIsForbidden() {
        setUp();
        User other = User.builder().id(101L).email("other@example.com").build();
        Order foreign = Order.builder().id(300L).user(other).vendor(vendor).status(OrderStatus.DELIVERED).build();
        when(userRepository.findByEmailWithRole("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(foreign));
        BusinessException ex = assertThrows(BusinessException.class, () -> reviewService.submitReview(
                "customer@example.com", 300L, request(5, "x")));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void submitReviewNonDeliveredOrderIsConflict() {
        setUp();
        Order placed = Order.builder().id(300L).user(customer).vendor(vendor).status(OrderStatus.PLACED).build();
        when(userRepository.findByEmailWithRole("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(placed));
        BusinessException ex = assertThrows(BusinessException.class, () -> reviewService.submitReview(
                "customer@example.com", 300L, request(5, "x")));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void submitReviewDuplicateIsConflict() {
        setUp();
        when(userRepository.findByEmailWithRole("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(order));
        when(reviewRepository.existsByOrderId(300L)).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> reviewService.submitReview(
                "customer@example.com", 300L, request(5, "x")));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void submitReviewNoVisibleReviewsAggregateIsNull() {
        setUp();
        when(userRepository.findByEmailWithRole("customer@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(order));
        when(reviewRepository.existsByOrderId(300L)).thenReturn(false);
        when(vendorProfileRepository.findById(200L)).thenReturn(Optional.of(vendor));
        when(reviewRepository.countByVendorIdAndHiddenFalse(200L)).thenReturn(0L);
        when(reviewRepository.averageRatingByVendorIdAndHiddenFalse(200L)).thenReturn(null);

        reviewService.submitReview("customer@example.com", 300L, request(5, "x"));

        ArgumentCaptor<VendorProfile> captor = ArgumentCaptor.forClass(VendorProfile.class);
        verify(vendorProfileRepository).saveAndFlush(captor.capture());
        VendorProfile updated = captor.getValue();
        assertEquals(0, updated.getReviewCount());
        assertNull(updated.getAvgRating());
    }

    @Test
    void getCustomerReviewReturnsOwnReviewEvenIfHidden() {
        setUp();
        Review hidden = Review.builder().id(400L).order(order).customer(customer).vendor(vendor)
                .rating(4).hidden(true).build();
        when(userRepository.findByEmailWithRole("customer@example.com")).thenReturn(Optional.of(customer));
        when(reviewRepository.findById(400L)).thenReturn(Optional.of(hidden));

        ReviewDetailResponse response = reviewService.getCustomerReview("customer@example.com", 400L);
        assertEquals(400L, response.getId());
        assertTrue(response.getHidden());
    }

    @Test
    void getCustomerReviewForeignReviewIsForbidden() {
        setUp();
        User other = User.builder().id(101L).build();
        Review foreign = Review.builder().id(400L).order(order).customer(other).vendor(vendor).rating(4).build();
        when(userRepository.findByEmailWithRole("customer@example.com")).thenReturn(Optional.of(customer));
        when(reviewRepository.findById(400L)).thenReturn(Optional.of(foreign));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                reviewService.getCustomerReview("customer@example.com", 400L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void getCustomerReviewNotFound() {
        setUp();
        when(userRepository.findByEmailWithRole("customer@example.com")).thenReturn(Optional.of(customer));
        when(reviewRepository.findById(400L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () ->
                reviewService.getCustomerReview("customer@example.com", 400L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void replyToReviewHappyPathSetsReplyAndTimestamp() {
        setUp();
        User vendorUser = User.builder().id(102L).email("vendor@example.com").build();
        VendorProfile vendorProfile = VendorProfile.builder().id(200L).businessName("Florist Shop").build();
        Review review = Review.builder().id(400L).order(order).customer(customer).vendor(vendorProfile)
                .rating(4).vendorReply(null).build();

        when(vendorProfileRepository.findByUserEmail("vendor@example.com")).thenReturn(Optional.of(vendorProfile));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.of(review));

        ReviewDetailResponse response = reviewService.replyToReview("vendor@example.com", 400L, "Thank you!");

        assertEquals("Thank you!", response.getVendorReply());
        assertNotNull(response.getVendorRepliedAt());
        verify(reviewRepository).saveAndFlush(any(Review.class));
        assertEquals("Thank you!", review.getVendorReply());
    }

    @Test
    void replyToReviewRejectsEmptyReply() {
        setUp();
        assertThrows(BusinessException.class,
                () -> reviewService.replyToReview("vendor@example.com", 400L, "   "));
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void replyToReviewRejectsTooLongReply() {
        setUp();
        String tooLong = "a".repeat(1001);
        assertThrows(BusinessException.class,
                () -> reviewService.replyToReview("vendor@example.com", 400L, tooLong));
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void replyToReviewReviewNotFound() {
        setUp();
        User vendorUser = User.builder().id(102L).email("vendor@example.com").build();
        VendorProfile vendorProfile = VendorProfile.builder().id(200L).build();

        when(vendorProfileRepository.findByUserEmail("vendor@example.com")).thenReturn(Optional.of(vendorProfile));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.replyToReview("vendor@example.com", 400L, "x"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void replyToReviewForeignReviewIsForbidden() {
        setUp();
        User vendorUser = User.builder().id(102L).email("vendor@example.com").build();
        VendorProfile vendorProfile = VendorProfile.builder().id(200L).build();
        VendorProfile otherVendor = VendorProfile.builder().id(201L).build();
        Review foreign = Review.builder().id(400L).order(order).customer(customer).vendor(otherVendor)
                .rating(4).build();

        when(vendorProfileRepository.findByUserEmail("vendor@example.com")).thenReturn(Optional.of(vendorProfile));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.of(foreign));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.replyToReview("vendor@example.com", 400L, "x"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void replyToReviewSecondReplyIsConflict() {
        setUp();
        User vendorUser = User.builder().id(102L).email("vendor@example.com").build();
        VendorProfile vendorProfile = VendorProfile.builder().id(200L).build();
        Review alreadyReplied = Review.builder().id(400L).order(order).customer(customer).vendor(vendorProfile)
                .rating(4).vendorReply("Existing reply").build();

        when(vendorProfileRepository.findByUserEmail("vendor@example.com")).thenReturn(Optional.of(vendorProfile));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.of(alreadyReplied));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.replyToReview("vendor@example.com", 400L, "x"));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void replyToReviewSuspendedVendorCanReply() {
        setUp();
        User vendorUser = User.builder().id(102L).email("vendor@example.com").build();
        VendorProfile suspended = VendorProfile.builder().id(200L)
                .status(VendorProfile.Status.SUSPENDED).build();
        Review review = Review.builder().id(400L).order(order).customer(customer).vendor(suspended)
                .rating(4).vendorReply(null).build();

        when(vendorProfileRepository.findByUserEmail("vendor@example.com")).thenReturn(Optional.of(suspended));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.of(review));

        // Suspended vendor can still reply (D-6); reply does not touch aggregates.
        ReviewDetailResponse response = reviewService.replyToReview("vendor@example.com", 400L, "Thanks");
        assertEquals("Thanks", response.getVendorReply());
        verify(reviewRepository).saveAndFlush(any(Review.class));
    }

    @Test
    void hideReviewHappyPathWritesAuditAndRecomputesAggregate() {
        setUp();
        User admin = User.builder().id(103L).email("admin@example.com").fullName("Admin One").build();
        Review hidden = Review.builder().id(400L).order(order).customer(customer).vendor(vendor)
                .rating(4).hidden(false).build();
        when(userRepository.findByEmailWithRole("admin@example.com")).thenReturn(Optional.of(admin));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.of(hidden));
        when(vendorProfileRepository.findById(200L)).thenReturn(Optional.of(vendor));
        when(reviewRepository.countByVendorIdAndHiddenFalse(200L)).thenReturn(0L);
        when(reviewRepository.averageRatingByVendorIdAndHiddenFalse(200L)).thenReturn(null);

        ReviewDetailResponse response = reviewService.hideReview("admin@example.com", 400L, "Offensive content");

        assertTrue(response.getHidden());
        assertEquals("Offensive content", response.getHiddenReason());
        assertEquals(100L, response.getCustomerId());
        verify(auditLogRepository).save(any(com.flowerconnect.domain.AuditLog.class));
        ArgumentCaptor<VendorProfile> captor = ArgumentCaptor.forClass(VendorProfile.class);
        verify(vendorProfileRepository).saveAndFlush(captor.capture());
        assertEquals(0, captor.getValue().getReviewCount());
        assertNull(captor.getValue().getAvgRating());
    }

    @Test
    void hideReviewRequiresReason() {
        setUp();
        assertThrows(BusinessException.class,
                () -> reviewService.hideReview("admin@example.com", 400L, "   "));
        assertThrows(BusinessException.class,
                () -> reviewService.hideReview("admin@example.com", 400L, "a".repeat(501)));
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void hideReviewNotFound() {
        setUp();
        when(userRepository.findByEmailWithRole("admin@example.com"))
                .thenReturn(Optional.of(User.builder().id(103L).build()));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.hideReview("admin@example.com", 400L, "x"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void hideReviewAlreadyHiddenIsConflict() {
        setUp();
        User admin = User.builder().id(103L).build();
        Review alreadyHidden = Review.builder().id(400L).order(order).customer(customer).vendor(vendor)
                .rating(4).hidden(true).build();
        when(userRepository.findByEmailWithRole("admin@example.com")).thenReturn(Optional.of(admin));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.of(alreadyHidden));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.hideReview("admin@example.com", 400L, "x"));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void unhideReviewHappyPath() {
        setUp();
        User admin = User.builder().id(103L).build();
        Review hidden = Review.builder().id(400L).order(order).customer(customer).vendor(vendor)
                .rating(4).hidden(true).build();
        when(userRepository.findByEmailWithRole("admin@example.com")).thenReturn(Optional.of(admin));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.of(hidden));
        when(vendorProfileRepository.findById(200L)).thenReturn(Optional.of(vendor));
        when(reviewRepository.countByVendorIdAndHiddenFalse(200L)).thenReturn(1L);
        when(reviewRepository.averageRatingByVendorIdAndHiddenFalse(200L)).thenReturn(4.0);

        ReviewDetailResponse response = reviewService.unhideReview("admin@example.com", 400L);
        assertFalse(response.getHidden());
        verify(auditLogRepository).save(any(com.flowerconnect.domain.AuditLog.class));
    }

    @Test
    void unhideReviewNotHiddenIsConflict() {
        setUp();
        User admin = User.builder().id(103L).build();
        Review visible = Review.builder().id(400L).order(order).customer(customer).vendor(vendor)
                .rating(4).hidden(false).build();
        when(userRepository.findByEmailWithRole("admin@example.com")).thenReturn(Optional.of(admin));
        when(reviewRepository.findByIdForUpdate(400L)).thenReturn(Optional.of(visible));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.unhideReview("admin@example.com", 400L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void listPublicVendorReviewsNotApprovedIsNotFound() {
        setUp();
        VendorProfile notApproved = VendorProfile.builder().id(200L)
                .status(VendorProfile.Status.PENDING_APPROVAL).build();
        when(vendorProfileRepository.findById(200L)).thenReturn(Optional.of(notApproved));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.listPublicVendorReviews(200L, Pageable.unpaged()));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void listPublicVendorReviewsExcludesHidden() {
        setUp();
        VendorProfile approved = VendorProfile.builder().id(200L)
                .status(VendorProfile.Status.APPROVED).build();
        Review visible = Review.builder().id(400L).order(order).customer(customer).vendor(approved)
                .rating(4).hidden(false).build();
        Review hidden = Review.builder().id(401L).order(order).customer(customer).vendor(approved)
                .rating(1).hidden(true).build();
        when(vendorProfileRepository.findById(200L)).thenReturn(Optional.of(approved));
        when(reviewRepository.findByVendorIdAndHiddenFalseOrderByCreatedAtDesc(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(visible), Pageable.unpaged(), 1));

        Page<ReviewResponse> page = reviewService.listPublicVendorReviews(200L, Pageable.unpaged());
        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals(4, page.getContent().get(0).getRating());
        // Public response carries masked customer name, no order id / customer id.
        assertNotNull(page.getContent().get(0).getCustomerName());
        assertNull(page.getContent().get(0).getVendorReply());
    }
}
