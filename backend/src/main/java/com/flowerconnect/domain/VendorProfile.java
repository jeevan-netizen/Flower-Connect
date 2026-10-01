package com.flowerconnect.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vendor_profiles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VendorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "business_name", length = 160, nullable = false)
    private String businessName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "address_line1", length = 255, nullable = false)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_location_id", nullable = false)
    private ServiceLocation serviceLocation;

    @Column(name = "latitude", precision = 10, scale = 8, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal longitude;

    @Column(name = "delivery_radius_km", precision = 5, scale = 2, nullable = false)
    private BigDecimal deliveryRadiusKm;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "commission_rate", precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;

    @Column(name = "min_order_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal minOrderAmount;

    @Column(name = "base_delivery_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseDeliveryFee;

    @Column(name = "per_km_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal perKmFee;

    @Column(name = "free_delivery_above", precision = 10, scale = 2)
    private BigDecimal freeDeliveryAbove;

    @Column(name = "prep_time_minutes", nullable = false)
    private Integer prepTimeMinutes;

    @Column(name = "slot_duration_minutes", nullable = false)
    private Integer slotDurationMinutes;

    @Column(name = "max_orders_per_slot", nullable = false)
    private Integer maxOrdersPerSlot;

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING_APPROVAL, APPROVED, REJECTED, SUSPENDED
    }
}
