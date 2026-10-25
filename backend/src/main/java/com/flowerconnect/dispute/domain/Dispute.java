package com.flowerconnect.dispute.domain;

import com.flowerconnect.domain.User;
import com.flowerconnect.order.domain.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Customer dispute for a delivered order (plan Phase 8, task 8.4).
 *
 * <p>One dispute per order ever, enforced by {@code uq_disputes_order} at the
 * database level. The dispute window (days after {@code DELIVERED} during which
 * a customer may raise a dispute) is read from {@code platform_config} at
 * runtime; the documented default is 7 days.
 *
 * <p>Status values mirror the plan section 8.4 exactly:
 * {@code OPEN}, {@code UNDER_REVIEW}, {@code RESOLVED_REFUND},
 * {@code RESOLVED_PARTIAL}, {@code RESOLVED_REJECTED}.
 */
@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "raised_by", nullable = false)
    private User raisedBy;

    @Column(name = "reason_code", length = 32, nullable = false)
    private String reasonCode;

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;
}