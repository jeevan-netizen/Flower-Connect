package com.flowerconnect.storefront.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Public vendor storefront response.
 *
 * <p>Excludes internal fields (owner email, commission rate, status, audit
 * fields). Products are the vendor's active, in-stock catalogue; delivery-radius
 * filtering is not applied because the customer has already chosen this vendor.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontResponse {

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
    private List<StorefrontHour> hours;
    private StorefrontProductPage products;
}
