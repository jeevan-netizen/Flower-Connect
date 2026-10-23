package com.flowerconnect.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A simulated/test-mode payment intent shared by all orders in one checkout
 * group (Phase 7, task 7.4).
 *
 * <p>An online checkout creates exactly one intent per payment group, for the
 * combined grand total. Each order in the group gets its own {@link Payment}
 * row linked to the same {@code payment_group_id}. The intent is the unit the
 * frontend uses to resume/pay a pending order; it expires after a configured
 * TTL and can never be collected twice.
 *
 * <p>No external gateway is involved: the provider is the fixed simulated
 * value {@code simulated} and the reference is a server-generated
 * {@code intent_sim_&lt;uuid&gt;}. No card data is ever stored.
 */
@Entity
@Table(name = "payment_intents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_group_id", length = 36, nullable = false, unique = true)
    private String paymentGroupId;

    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IntentStatus status;

    @Column(name = "provider_reference", length = 128, nullable = false)
    private String providerReference;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Lifecycle state of a simulated payment intent. */
    public enum IntentStatus {
        CREATED, PAID, EXPIRED, FAILED
    }
}
