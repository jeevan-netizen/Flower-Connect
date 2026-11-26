package com.flowerconnect.review.repository;

import com.flowerconnect.review.domain.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Persistence for {@link Review} (plan Phase 12, task 12.1).
 *
 * <p>The unique constraint {@code uq_reviews_order} guarantees at most one
 * review per order. {@link #existsByOrderId} fails fast before an insert that
 * would hit the constraint, so concurrent duplicate submissions resolve to one
 * review and one clean 409.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Checks whether a review already exists for the given order. Used to fail
     * fast with a clean 409 rather than a raw unique-constraint violation.
     */
    boolean existsByOrderId(Long orderId);

    /**
     * Page of visible (non-hidden) reviews for a vendor, newest first. Used by
     * the public storefront listing; hidden reviews are excluded.
     */
    @Query("SELECT r FROM Review r WHERE r.vendor.id = :vendorId AND r.hidden = false ORDER BY r.createdAt DESC")
    Page<Review> findByVendorIdAndHiddenFalseOrderByCreatedAtDesc(
            @Param("vendorId") Long vendorId, Pageable pageable);

    /**
     * Page of all reviews for a vendor (including hidden), newest first. Used by
     * the vendor's own review list; a vendor sees reviews on their own orders
     * regardless of moderation state.
     */
    @Query("SELECT r FROM Review r WHERE r.vendor.id = :vendorId ORDER BY r.createdAt DESC")
    Page<Review> findByVendorIdOrderByCreatedAtDesc(
            @Param("vendorId") Long vendorId, Pageable pageable);

    /**
     * Page of reviews for a vendor filtered by hidden state, newest first.
     * Used by the admin moderation listing.
     */
    Page<Review> findByHiddenOrderByCreatedAtDesc(Boolean hidden, Pageable pageable);

    /**
     * Page of the given customer's own reviews (including hidden), newest first.
     */
    @Query("SELECT r FROM Review r WHERE r.customer.id = :customerId ORDER BY r.createdAt DESC")
    Page<Review> findByCustomerIdOrderByCreatedAtDesc(
            @Param("customerId") Long customerId, Pageable pageable);

    /**
     * Count of visible (non-hidden) reviews for a vendor. Used to recompute
     * {@code vendor_profiles.review_count}.
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.vendor.id = :vendorId AND r.hidden = false")
    long countByVendorIdAndHiddenFalse(@Param("vendorId") Long vendorId);

    /**
     * Average rating over visible (non-hidden) reviews for a vendor. Used to
     * recompute {@code vendor_profiles.avg_rating}. Returns null when there are
     * no visible reviews.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.vendor.id = :vendorId AND r.hidden = false")
    Double averageRatingByVendorIdAndHiddenFalse(@Param("vendorId") Long vendorId);

    /**
     * Count of visible reviews per star value for a vendor. Returns rows of
     * {@code [rating, count]} for ratings 1-5 that have at least one visible
     * review. Used to build the vendor's rating distribution.
     */
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.vendor.id = :vendorId AND r.hidden = false GROUP BY r.rating")
    java.util.List<java.lang.Object[]> ratingDistributionByVendorId(@Param("vendorId") Long vendorId);

    /**
     * Finds a review by ID with a pessimistic write lock for concurrency control
     * during vendor reply and admin hide/unhide. The lock is held until the
     * transaction commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") Long id);
}
