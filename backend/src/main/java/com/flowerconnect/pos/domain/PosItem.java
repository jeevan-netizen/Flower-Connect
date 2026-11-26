package com.flowerconnect.pos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One line of a POS sale (plan Phase 11, task 11.1).
 *
 * <p>The name and unit price are server-side snapshots taken at sale time;
 * the client supplies product IDs and quantities only and is never trusted
 * with a price or total.
 */
@Entity
@Table(name = "pos_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PosItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private PosTransaction transaction;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", length = 160, nullable = false)
    private String productNameSnapshot;

    @Column(name = "unit_price_snapshot", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "line_total", precision = 10, scale = 2, nullable = false)
    private BigDecimal lineTotal;
}
