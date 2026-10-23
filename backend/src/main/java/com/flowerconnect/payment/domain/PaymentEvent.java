package com.flowerconnect.payment.domain;

import com.flowerconnect.payment.domain.Payment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One append-only record of a provider event for a payment (plan Phase 7).
 *
 * <p>{@code provider_event_id} is unique and is used for idempotency when the
 * same provider event is delivered more than once. {@code received_at} records
 * when the event arrived at this service. No card-like data or secrets are
 * stored here.
 *
 * <p>{@code payment_group_id} links events belonging to the same multi-shop
 * payment group (later online work); COD events leave it null.
 */
@Entity
@Table(name = "payment_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "payment_group_id", length = 36)
    private String paymentGroupId;

    @Column(name = "provider_event_id", length = 128, nullable = false)
    private String providerEventId;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "outcome", length = 32, nullable = false)
    private String outcome;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;
}
