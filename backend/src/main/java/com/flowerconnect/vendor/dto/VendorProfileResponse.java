package com.flowerconnect.vendor.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full read model of a vendor profile, as returned to the owning vendor and to
 * admins.
 *
 * <p>{@code reviewCount} and {@code avgRating} are platform-owned: they are not
 * accepted by any Phase 2c request DTO. They are exposed read-only so the
 * future vendor dashboard can show them.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorProfileResponse {

    private Long id;
    private String ownerEmail;

    private String businessName;
    private String description;
    private String addressLine1;
    private String addressLine2;

    private Long serviceLocationId;
    private String city;
    private String area;
    private String pincode;
    private BigDecimal latitude;
    private BigDecimal longitude;

    private BigDecimal deliveryRadiusKm;
    private String logoUrl;
    private String status;

    private BigDecimal minOrderAmount;
    private BigDecimal baseDeliveryFee;
    private BigDecimal perKmFee;
    private BigDecimal freeDeliveryAbove;
    private Integer prepTimeMinutes;
    private Integer slotDurationMinutes;
    private Integer maxOrdersPerSlot;
    private Boolean acceptingOrders;

    private BigDecimal avgRating;
    private Integer reviewCount;

    private List<VendorHoursResponse> hours;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
