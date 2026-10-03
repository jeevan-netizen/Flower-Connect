package com.flowerconnect.inventory.domain;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * An append-only record of one stock change (plan task 3.4).
 *
 * <p>Every change to a product's stock writes one row here — the
 * {@code inventory} table holds the current level, this table
 * holds the history of how it got there. Rows are never updated
 * or deleted, which is why there is no {@code updatedAt}.
 *
 * <p>{@code quantityDelta} is the signed change: positive when
 * stock is added ({@code STOCK_IN}, {@code RESTOCK}), negative
 * when removed ({@code STOCK_OUT}, {@code SALE_*}, {@code WASTE}),
 * and either sign for {@code ADJUSTMENT}. {@code RESERVE} and
 * {@code RELEASE} move units between {@code quantity} and
 * {@code reservedQuantity} and are recorded so reservations are
 * auditable.
 *
 * <p>{@code actor} is nullable because system-initiated movements
 * (the expiry scheduler's {@code WASTE} write-off) have no human
 * actor. {@code referenceId}/{@code referenceType} identify the
 * document that caused the movement (order, purchase order,
 * adjustment, ...).
 */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_type", length = 64)
    private String referenceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** The stock change vocabulary. Mirrors plan section 6.2 exactly. */
    public enum MovementType {
        STOCK_IN, STOCK_OUT, ADJUSTMENT, RESERVE, RELEASE,
        SALE_ONLINE, SALE_POS, RESTOCK, WASTE
    }
}
