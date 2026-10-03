package com.flowerconnect.inventory.domain;

import com.flowerconnect.catalog.domain.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The stock level of a single product (plan task 3.3).
 *
 * <p>Exactly one row exists per product, created automatically at
 * {@code quantity = 0} in the same transaction as the product
 * (see {@code ProductService.create}). The unique constraint on
 * {@code product_id} is the database's guarantee of the
 * one-to-one.
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@code quantity} — physical units on hand (non-negative)</li>
 *   <li>{@code reservedQuantity} — units reserved for pending
 *       orders (non-negative, never exceeds {@code quantity})</li>
 *   <li>{@code available} — {@code quantity − reservedQuantity}:
 *       what a customer can still buy. Computed, not stored.</li>
 *   <li>{@code lowStockThreshold} — alert threshold (non-negative)</li>
 *   <li>{@code expiryDate} — optional expiry for perishables; the
 *       scheduler (plan task 3.7) writes expired stock off as
 *       {@code WASTE} and delists the product</li>
 * </ul>
 *
 * <p>All four invariants are backed by MySQL CHECK constraints in
 * {@code V10__inventory.sql}, so they hold for every writer —
 * HTTP, scheduler, or direct data fix — not just the service.
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Units a customer can still buy: {@code quantity − reservedQuantity}. */
    public int getAvailable() {
        return quantity - reservedQuantity;
    }
}
