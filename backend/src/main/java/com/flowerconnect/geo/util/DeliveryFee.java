package com.flowerconnect.geo.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Delivery-fee arithmetic shared by the two public geo endpoints (plan tasks
 * 4.3 discovery and 4.4 search).
 *
 * <p>Both endpoints show the same vendor's fee for the same origin, so the
 * formula lives here once rather than in each service's response mapper —
 * otherwise a change to the rule would make discovery and search quote
 * different numbers for one vendor at one distance.
 */
public final class DeliveryFee {

    private DeliveryFee() {
    }

    /**
     * Estimates the delivery fee from a vendor's pricing and the distance in
     * kilometres from the search origin.
     *
     * @param baseFee    the vendor's flat base delivery fee
     * @param perKmFee   the vendor's per-kilometre fee
     * @param distanceKm the exact Haversine distance from the origin
     * @return {@code baseFee + perKmFee × distanceKm}, HALF_UP at 2 decimals
     */
    public static BigDecimal estimate(BigDecimal baseFee, BigDecimal perKmFee, BigDecimal distanceKm) {
        return baseFee.add(perKmFee.multiply(distanceKm))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
