package com.flowerconnect.geo.service;

import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Shared geographic helpers for public discovery and search (plan tasks 4.3 and 4.4).
 *
 * <p>Extracted from {@code DiscoveryService} so the haversine formula and bounding-box
 * prefilter are implemented once and reused by every location-aware customer endpoint.
 */
@Service
public class GeoService {

    private static final int EARTH_RADIUS_KM = 6371;
    private static final BigDecimal KM_PER_DEGREE_LAT = new BigDecimal("111.0");
    private static final BigDecimal KM_PER_DEGREE_LNG_AT_EQUATOR = new BigDecimal("111.32");
    private static final BigDecimal COS_EPSILON = new BigDecimal("1e-9");

    /** Result of a bounding-box prefilter computation. */
    public record BoundingBox(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {
    }

    /**
     * Haversine distance in kilometres between two lat/lng points.
     *
     * <p>Formula: 2 * R * asin(sqrt(sin^2((lat2-lat1)/2) + cos(lat1)*cos(lat2)*sin^2((lng2-lng1)/2)))
     * where R = 6371 km (Earth's mean radius).
     */
    public BigDecimal haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
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
     * Computes a latitude/longitude bounding box that is guaranteed not to exclude any
     * vendor whose {@code deliveryRadiusKm} could reach the origin.
     *
     * <p>The box radius is the maximum delivery radius among candidates, so every vendor
     * within its own radius lies within this box. Longitude bounds are computed relative
     * to the cosine of the origin latitude; near the poles the cosine tends to zero and
     * the longitude span is widened to the full range rather than producing an excessively
     * large or degenerate delta. Latitude and longitude are clamped to their valid ranges.
     */
    public BoundingBox computeBoundingBox(BigDecimal originLat, BigDecimal originLng, BigDecimal maxRadiusKm) {
        double latDelta = maxRadiusKm.doubleValue() / KM_PER_DEGREE_LAT.doubleValue();

        double cosLat = Math.cos(Math.toRadians(originLat.doubleValue()));
        double lngDelta;
        if (Math.abs(cosLat) < COS_EPSILON.doubleValue()) {
            // Near a pole: degrees of longitude collapse, so widen to the full range.
            lngDelta = 180.0;
        } else {
            lngDelta = maxRadiusKm.doubleValue() /
                    (KM_PER_DEGREE_LNG_AT_EQUATOR.doubleValue() * cosLat);
        }

        BigDecimal minLat = originLat.subtract(BigDecimal.valueOf(latDelta))
                .max(BigDecimal.valueOf(-90));
        BigDecimal maxLat = originLat.add(BigDecimal.valueOf(latDelta))
                .min(BigDecimal.valueOf(90));
        BigDecimal minLng = originLng.subtract(BigDecimal.valueOf(lngDelta))
                .max(BigDecimal.valueOf(-180));
        BigDecimal maxLng = originLng.add(BigDecimal.valueOf(lngDelta))
                .min(BigDecimal.valueOf(180));

        return new BoundingBox(minLat, maxLat, minLng, maxLng);
    }
}
