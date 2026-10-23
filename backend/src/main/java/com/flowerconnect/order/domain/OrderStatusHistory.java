package com.flowerconnect.order.domain;

import com.flowerconnect.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * An append-only audit record of one order status transition (plan Phase 5,
 * task 5.4).
 *
 * <p>Every status change writes one row here — the initial {@code PLACED}
 * transition (written at checkout, {@code fromStatus = null}) and every
 * subsequent vendor/customer transition (Phase 6 state machine). Rows are
 * never updated or deleted, so there is no {@code updatedAt}.
 *
 * <p>{@code from_status} and {@code to_status} use the exact same MySQL ENUM
 * value list and ordering as {@code orders.status} (D-20). {@code actor_type}
 * records whether the transition was driven by {@code CUSTOMER},
 * {@code VENDOR}, {@code ADMIN} or {@code SYSTEM}; {@code actor_user_id} is
 * populated when the actor is a named user and left null for {@code SYSTEM}.
 */
@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private Order.OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private Order.OrderStatus toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 16)
    private ActorType actorType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
