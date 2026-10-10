package com.flowerconnect.search.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Public product search response (plan task 4.4).
 *
 * <p>Contains the product fields needed for the storefront product grid,
 * plus the vendor name and the computed distance + estimated delivery fee
 * from the search origin. Only products that are {@code ACTIVE}, have
 * available stock, and belong to an {@code APPROVED} vendor accepting
 * orders within their delivery radius are ever returned.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResponse {

    private Long id;
    private Long vendorId;
    private String vendorName;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;
    private BigDecimal distanceKm;
    private BigDecimal estimatedDeliveryFee;
}