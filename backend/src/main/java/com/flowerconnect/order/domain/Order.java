package com.flowerconnect.order.domain;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A customer's order for one vendor's items (plan Phase 5, tasks 5.1-5.5).
 *
 * <p>A multi-vendor cart produces one order row per vendor at checkout, so each
 * order belongs to exactly one customer ({@code user_id}) and one vendor
 * ({@code vendor_profile_id}). This keeps the per-vendor state machine
 * (Phase 6) and the vendor's order listing simple: one row IS one shop's
 * order.
 *
 * <p>Address fields are snapshotted columns, not an FK to {@code addresses}:
 * if the customer later edits or deletes the address, the delivery address is
 * preserved. {@code deliveryServiceLocation} is kept as an FK to
 * {@code service_locations} for lookups and distance re-validation.
 *
 * <p>{@code clientOrderId} is the per-user idempotency key (decision E-1): a
 * replay of the same checkout returns the existing order instead of creating a
 * duplicate. NULL is allowed for orders without a client key (POS, Phase 11).
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendor;

    @Column(name = "client_order_id", length = 36)
    private String clientOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal deliveryFee;

    @Column(name = "platform_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal platformFee;

    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    private BigDecimal total;

    @Column(name = "commission_rate", precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "delivery_address_line1", length = 255, nullable = false)
    private String deliveryAddressLine1;

    @Column(name = "delivery_address_line2", length = 255)
    private String deliveryAddressLine2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_service_location_id")
    private ServiceLocation deliveryServiceLocation;

    @Column(name = "delivery_latitude", precision = 10, scale = 8, nullable = false)
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal deliveryLongitude;

    @Column(name = "slot_start")
    private LocalDateTime slotStart;

    @Column(name = "slot_end")
    private LocalDateTime slotEnd;

    @Column(name = "note", length = 1000)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** The lifecycle state of an order. Mirrors the {@code orders.status} ENUM exactly. */
    public enum OrderStatus {
        PLACED,
        PENDING_PAYMENT,
        CONFIRMED,
        PREPARING,
        OUT_FOR_DELIVERY,
        DELIVERED,
        CANCELLED,
        REJECTED,
        REFUNDED
    }

    /** Payment method. Phase 5 supports only COD (D-1). */
    public enum PaymentMethod {
        COD, ONLINE
    }

    /** Payment lifecycle state. */
    public enum PaymentStatus {
        PENDING, PAID, COLLECTED, FAILED, REFUND_PENDING, REFUNDED
    }
}
