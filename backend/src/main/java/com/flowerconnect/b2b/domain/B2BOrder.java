package com.flowerconnect.b2b.domain;

import com.flowerconnect.domain.VendorProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A B2B order between two approved vendors (Stage 1 of the B2B module).
 *
 * <p>One order belongs to exactly one buyer vendor and one seller vendor.
 * The buyer places the order; the seller accepts, rejects, ships or delivers
 * it; either party may cancel before shipment under the transition rules below.
 * Stock is owned by the seller and is deducted through
 * {@link com.flowerconnect.inventory.service.InventoryService} when the seller
 * accepts. The buyer's inventory is never increased.
 *
 * <p>Payment stays {@code UNPAID} in Stage 1; settlement and e-bills are
 * deferred to later stages and deliberately do not touch the settlement ledger.
 *
 * <p>Transitions (enforced by {@link com.flowerconnect.b2b.service.B2BOrderService}):
 * <ul>
 *   <li>PLACED &#8594; ACCEPTED or REJECTED: seller only</li>
 *   <li>PLACED &#8594; CANCELLED: buyer only</li>
 *   <li>ACCEPTED &#8594; CANCELLED: buyer or seller; seller's deducted stock is restored</li>
 *   <li>ACCEPTED &#8594; SHIPPED: seller only; requires a shipment reference</li>
 *   <li>SHIPPED &#8594; DELIVERED: seller only</li>
 * </ul>
 * Every other transition is rejected with a project {@code ErrorCode}.
 */
@Entity
@Table(name = "b2b_orders")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class B2BOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", length = 64, nullable = false, unique = true)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private VendorProfile buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private VendorProfile seller;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "total", precision = 12, scale = 2, nullable = false)
    private BigDecimal total;

    @Column(name = "order_note", length = 1000)
    private String orderNote;

    @Column(name = "shipment_reference", length = 255)
    private String shipmentReference;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "bill_number", length = 100)
    private String billNumber;

    @CreationTimestamp
    @Column(name = "placed_at", nullable = false, updatable = false)
    private LocalDateTime placedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<B2BOrderItem> items = new ArrayList<>();

    /** Lifecycle state of a B2B order. Mirrors the {@code b2b_orders.status} ENUM exactly. */
    public enum Status {
        PLACED, ACCEPTED, REJECTED, CANCELLED, SHIPPED, DELIVERED
    }

    /** Payment lifecycle state. Stage 1 supports only UNPAID. */
    public enum PaymentStatus {
        UNPAID
    }

    /** Convenience: add an item and back-reference this order. */
    public void addItem(B2BOrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
