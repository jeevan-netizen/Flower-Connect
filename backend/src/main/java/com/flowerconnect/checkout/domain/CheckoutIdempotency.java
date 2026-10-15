package com.flowerconnect.checkout.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Checkout idempotency record.
 *
 * <p>One row per (user_id, client_order_id) pair. Created at the start of a
 * checkout attempt with {@code completed_at = NULL}. The row is locked with
 * {@code SELECT ... FOR UPDATE} to serialize concurrent attempts. On success,
 * {@code completed_at} is set and the checkout fingerprint is stored for
 * replay verification. On failure (rollback), the incomplete row remains but
 * is distinguishable by its null {@code completed_at}.
 */
@Entity
@Table(name = "checkout_idempotency")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CheckoutIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_order_id", length = 36, nullable = false)
    private String clientOrderId;

    @Column(name = "checkout_fingerprint", length = 128, nullable = false)
    private String checkoutFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}