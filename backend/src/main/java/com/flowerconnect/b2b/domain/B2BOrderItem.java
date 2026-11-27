package com.flowerconnect.b2b.domain;

import com.flowerconnect.catalog.domain.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One product line within a B2B order (Stage 1 of the B2B module).
 *
 * <p>{@code productName}, {@code unit} and {@code unitPrice} are snapshots taken
 * at placement time, so later catalog changes (price edits, deactivation) never
 * alter an existing order. {@code lineTotal} is {@code quantity * unitPrice}
 * computed by the server and stored for easy read-model use.
 */
@Entity
@Table(name = "b2b_order_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class B2BOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private B2BOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", length = 160, nullable = false)
    private String productName;

    @Column(name = "unit", length = 50, nullable = false)
    private String unit;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal lineTotal;
}
