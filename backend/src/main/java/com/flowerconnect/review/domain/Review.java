package com.flowerconnect.review.domain;

import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A customer review for a delivered order (plan Phase 12, task 12.1).
 *
 * <p>One review per order ever, enforced by {@code uq_reviews_order}. A review
 * can only be created for an order in {@code OrderStatus#DELIVERED} status;
 * that rule is enforced by {@link com.flowerconnect.review.service.ReviewService}.
 *
 * <p>{@code hidden} supports admin moderation. Hidden reviews are excluded from
 * public storefronts and from the {@code avg_rating} / {@code review_count}
 * aggregates on {@code vendor_profiles}, but remain visible to admins, the
 * owning vendor and the review's customer.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorProfile vendor;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "vendor_reply", length = 1000)
    private String vendorReply;

    @Column(name = "vendor_replied_at")
    private LocalDateTime vendorRepliedAt;

    @Column(name = "hidden", nullable = false)
    private Boolean hidden;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
