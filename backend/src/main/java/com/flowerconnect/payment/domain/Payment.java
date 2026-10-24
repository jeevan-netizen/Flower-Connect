package com.flowerconnect.payment.domain;

import com.flowerconnect.order.domain.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment record for an order (plan Phase 7, tasks 7.1-7.2).
 *
 * <p>There is exactly one {@code payments} row per order (1:1). It is the
 * authoritative source of the payment lifecycle, kept in sync with
 * {@code orders.payment_status} by {@link com.flowerconnect.payment.service.PaymentService},
 * which is the only application service allowed to change payment status.
 *
 * <p>{@code payment_group_id} is nullable and reserved for the later shared
 * online payment intent (Phase 7 online work); COD orders leave it null.
 *
 * <p>{@code collected_at} is populated when a COD order is delivered and the
 * payment moves to {@code COLLECTED}. It is null until then.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "payment_group_id", length = 36)
    private String paymentGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PaymentMethod method;

    @Column(name = "provider", length = 64, nullable = false)
    private String provider;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    /**
     * Timestamp when this payment was marked refunded ({@code payments.status}
     * moved {@code REFUND_PENDING -> REFUNDED}). Null until then. Stamped from
     * the injected {@link java.time.Clock} in {@link PaymentService#markRefunded}.
     */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Payment method. Mirrors {@code Order.PaymentMethod}. COD is the only
     *  method supported in Stage 1; ONLINE is reserved for Phase 7 online work. */
    public enum PaymentMethod {
        COD, ONLINE
    }

    /** Payment lifecycle state. Mirrors {@code Order.PaymentStatus}, including
     *  COLLECTED, so backfill and synchronization preserve existing state. */
    public enum PaymentStatus {
        PENDING, PAID, COLLECTED, FAILED, REFUND_PENDING, REFUNDED
    }
}
