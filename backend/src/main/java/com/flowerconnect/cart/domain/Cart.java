package com.flowerconnect.cart.domain;

import com.flowerconnect.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A customer's shopping cart (plan Phase 5, tasks 5.1-5.5).
 *
 * <p>Exactly one cart exists per customer, enforced by the
 * {@code uq_carts_user} unique constraint on {@code user_id}. The cart is
 * server-side (Redis was removed per plan v2.2 stage 6), so it survives
 * browser reloads and device switches.
 *
 * <p>The cart is created lazily on the first add-to-cart and retained
 * (empty) after a successful checkout, so the next add-to-cart is a simple
 * insert rather than a create-or-update.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
