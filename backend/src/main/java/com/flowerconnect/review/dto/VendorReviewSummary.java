package com.flowerconnect.review.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vendor review summary: aggregate rating and 1-5 distribution over visible
 * (non-hidden) reviews (plan Phase 12, task 12.2).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorReviewSummary {

    /**
     * Average rating over visible reviews, HALF_UP to 2 decimals. Null when
     * there are no visible reviews.
     */
    private BigDecimal avgRating;

    /** Number of visible reviews. */
    private Integer reviewCount;

    /** Count of visible reviews per star value, keys 1-5. */
    private Map<Integer, Integer> ratingDistribution = new java.util.LinkedHashMap<>(
            java.util.Map.of(1, 0, 2, 0, 3, 0, 4, 0, 5, 0));
}
