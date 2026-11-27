package com.flowerconnect.b2b.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Links a {@link B2BStatement} to one of its {@link B2BOrder}s (Stage 3 of
 * the B2B module).
 *
 * <p>The database enforces {@code uq_b2b_statement_orders_order} so an order
 * can belong to at most one statement; this is the final safeguard against two
 * concurrent statement creations assigning the same order to different
 * statements.
 */
@Entity
@Table(name = "b2b_statement_orders")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class B2BStatementOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false)
    private B2BStatement statement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private B2BOrder order;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
