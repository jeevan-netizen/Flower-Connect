package com.flowerconnect.discovery.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Public vendor discovery response.
 *
 * <p>Contains only storefront-relevant fields. Private vendor/user fields
 * (owner email, commission rate, status, audit fields) are intentionally
 * omitted. Distance and estimated fee are computed server-side from the
 * search origin.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveryResponse {

    private Long id;
    private String businessName;
    private String description;

    private String city;
    private String area;
    private String pincode;

    private BigDecimal deliveryRadiusKm;
    private BigDecimal minOrderAmount;
    private BigDecimal baseDeliveryFee;
    private BigDecimal perKmFee;
    private BigDecimal freeDeliveryAbove;
    private Integer prepTimeMinutes;

    private BigDecimal avgRating;
    private Integer reviewCount;
    private Boolean acceptingOrders;

    private BigDecimal distanceKm;
    private BigDecimal estimatedDeliveryFee;
}