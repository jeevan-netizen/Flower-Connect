package com.flowerconnect.cart.domain;

import com.flowerconnect.catalog.domain.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line item in a customer's cart (plan Phase 5).
 *
 * <p>A cart holds at most one row per product per cart, enforced by the
 * {@code uq_cart_item_cart_product} unique constraint on
 * {@code (cart_id, product_id)}: adding the same product again increments
 * {@code quantity} rather than creating a duplicate row.
 *
 * <p>{@code vendorId} is a denormalized copy of {@code products.vendor_id},
 * taken at insert time. It lets the cart view group items by vendor and
 * resolve each vendor's delivery settings without joining through
 * {@code products} on every read.
 *
 * <p>{@code priceSnapshot} is the product's {@code base_price} at the moment
 * the item was added — the authority for the cart total until checkout copies
 * it to {@code order_items} (decision C-1).
 */
@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Denormalized vendor id (copied from {@code products.vendor_id}).
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

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
