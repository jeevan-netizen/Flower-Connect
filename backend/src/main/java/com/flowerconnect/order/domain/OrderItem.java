package com.flowerconnect.order.domain;

import com.flowerconnect.catalog.domain.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One product line within an order (plan Phase 5, task 5.3).
 *
 * <p>{@code priceSnapshot} is copied from {@code cart_items.price_snapshot}
 * at checkout time (decision C-1) and is never updated: the order is an
 * immutable record of what the customer agreed to pay, independent of later
 * price changes on {@code products.base_price}.
 *
 * <p>{@code vendorId} is denormalized from {@code cart_items.vendor_id}, so
 * Phase 6 can list a vendor's orders without joining through products.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Denormalized vendor id (copied from {@code cart_items.vendor_id}).
     * No foreign key — the product row is the authority.
     */
    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "price_snapshot", precision = 10, scale = 2, nullable = false)
    private BigDecimal priceSnapshot;

    @Column(name = "note", length = 1000)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
