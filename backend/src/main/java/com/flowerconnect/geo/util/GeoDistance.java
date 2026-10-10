package com.flowerconnect.geo.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared geospatial utilities for distance calculations.
 *
 * <p>Extracted from {@code DiscoveryService} (plan task 4.3) so that both
 * discovery and search (plan task 4.4) use the exact same Haversine
 * implementation. This ensures consistent distance semantics across
 * all geo-aware endpoints.
 */
public final class GeoDistance {

    private GeoDistance() {
    }

    /** Earth's mean radius in kilometres (WGS84 authalic sphere). */
    public static final int EARTH_RADIUS_KM = 6371;

    /**
     * Haversine formula: distance in kilometres between two lat/lng points.
     *
     * <p>Formula: {@code 2 * R * asin(sqrt(sin^2((lat2-lat1)/2) + cos(lat1)*cos(lat2)*sin^2((lng2-lng1)/2)))}
     * where {@code R = 6371 km}.
     *
     * @param lat1 latitude of the first point (decimal degrees)
     * @param lng1 longitude of the first point (decimal degrees)
     * @param lat2 latitude of the second point (decimal degrees)
     * @param lng2 longitude of the second point (decimal degrees)
     * @return distance in kilometres, scaled to 4 decimal places with HALF_UP rounding
     */
    public static BigDecimal haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double lat1Rad = Math.toRadians(lat1.doubleValue());
        double lng1Rad = Math.toRadians(lng1.doubleValue());
        double lat2Rad = Math.toRadians(lat2.doubleValue());
        double lng2Rad = Math.toRadians(lng2.doubleValue());

        double dLat = lat2Rad - lat1Rad;
        double dLng = lng2Rad - lng1Rad;

        double sinDLat = Math.sin(dLat / 2);
        double sinDLng = Math.sin(dLng / 2);

        double a = sinDLat * sinDLat +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinDLng * sinDLng;

        double c = 2 * Math.asin(Math.min(1.0, Math.sqrt(a)));
        double distance = EARTH_RADIUS_KM * c;

        return BigDecimal.valueOf(distance).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Computes the latitude delta (in degrees) corresponding to a given
     * radius in kilometres. Uses the approximation that 1 degree of
     * latitude ≈ 111 km.
     *
     * @param radiusKm radius in kilometres
     * @return latitude delta in decimal degrees
     */
    public static double latDeltaForRadius(BigDecimal radiusKm) {
        return radiusKm.doubleValue() / 111.0;
    }

    /**
     * Computes the longitude delta (in degrees) corresponding to a given
     * radius in kilometres at a specific latitude. Uses the approximation
     * that 1 degree of longitude at the equator ≈ 111.32 km, scaled by
     * the cosine of the latitude.
     *
     * @param radiusKm radius in kilometres
     * @param latitude latitude in decimal degrees
     * @return longitude delta in decimal degrees
     */
    public static double lngDeltaForRadius(BigDecimal radiusKm, BigDecimal latitude) {
        return radiusKm.doubleValue() / (111.32 * Math.cos(Math.toRadians(latitude.doubleValue())));
    }

    /**
     * Scales a computed distance for display, at 2 decimal places with
     * HALF_UP rounding. Both public geo endpoints report kilometres at this
     * precision, so they cannot disagree about how they render a distance.
     *
     * @param distanceKm distance from {@link #haversineKm}
     * @return the same distance scaled to 2 decimal places
     */
    public static BigDecimal displayKm(BigDecimal distanceKm) {
        return distanceKm.setScale(2, RoundingMode.HALF_UP);
    }
}