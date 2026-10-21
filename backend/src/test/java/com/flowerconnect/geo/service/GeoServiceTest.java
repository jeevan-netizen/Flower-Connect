package com.flowerconnect.geo.service;

import com.flowerconnect.geo.service.GeoService.BoundingBox;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GeoService}: haversine distance and safe bounding-box
 * prefilter, including longitude handling near the poles.
 */
class GeoServiceTest {

    private static final BigDecimal KORAMANGALA_LAT = new BigDecimal("12.9352");
    private static final BigDecimal KORAMANGALA_LNG = new BigDecimal("77.6145");

    private final GeoService geoService = new GeoService();

    @Test
    void haversineSamePointReturnsZero() {
        BigDecimal distance = geoService.haversineKm(
                KORAMANGALA_LAT, KORAMANGALA_LNG, KORAMANGALA_LAT, KORAMANGALA_LNG);

        assertThat(distance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void haversineOneDegreeLatitudeIsApproximately111Km() {
        BigDecimal oneDegreeNorth = KORAMANGALA_LAT.add(BigDecimal.ONE);
        BigDecimal distance = geoService.haversineKm(
                KORAMANGALA_LAT, KORAMANGALA_LNG, oneDegreeNorth, KORAMANGALA_LNG);

        assertThat(distance).isGreaterThan(BigDecimal.valueOf(100));
        assertThat(distance).isLessThan(BigDecimal.valueOf(112));
    }

    @Test
    void haversineSymmetric() {
        BigDecimal a = geoService.haversineKm(
                KORAMANGALA_LAT, KORAMANGALA_LNG,
                new BigDecimal("13.0000"), new BigDecimal("78.0000"));
        BigDecimal b = geoService.haversineKm(
                new BigDecimal("13.0000"), new BigDecimal("78.0000"),
                KORAMANGALA_LAT, KORAMANGALA_LNG);

        assertThat(a).isEqualByComparingTo(b);
    }

    @Test
    void boundingBoxNormalCaseContainsOrigin() {
        BoundingBox box = geoService.computeBoundingBox(
                KORAMANGALA_LAT, KORAMANGALA_LNG, new BigDecimal("10"));

        assertThat(box.minLat().compareTo(KORAMANGALA_LAT)).isLessThanOrEqualTo(0);
        assertThat(box.maxLat().compareTo(KORAMANGALA_LAT)).isGreaterThanOrEqualTo(0);
        assertThat(box.minLng().compareTo(KORAMANGALA_LNG)).isLessThanOrEqualTo(0);
        assertThat(box.maxLng().compareTo(KORAMANGALA_LNG)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void boundingBoxHighLatitudeDoesNotDegenerateLongitude() {
        BigDecimal nearPoleLat = new BigDecimal("89.9999");
        BigDecimal nearPoleLng = new BigDecimal("0");

        BoundingBox box = geoService.computeBoundingBox(
                nearPoleLat, nearPoleLng, new BigDecimal("10"));

        // Near the pole the longitude span is widened to the full range.
        assertThat(box.minLng().compareTo(BigDecimal.valueOf(-180))).isGreaterThanOrEqualTo(0);
        assertThat(box.maxLng().compareTo(BigDecimal.valueOf(180))).isLessThanOrEqualTo(0);
        assertThat(box.minLat().compareTo(BigDecimal.valueOf(-90))).isGreaterThanOrEqualTo(0);
        assertThat(box.maxLat().compareTo(BigDecimal.valueOf(90))).isLessThanOrEqualTo(0);
    }

    @Test
    void boundingBoxDoesNotExcludeVendorAtMaxRadius() {
        BigDecimal originLat = new BigDecimal("12.9352");
        BigDecimal originLng = new BigDecimal("77.6145");
        BigDecimal maxRadius = new BigDecimal("10");

        BoundingBox box = geoService.computeBoundingBox(originLat, originLng, maxRadius);

        // A vendor exactly maxRadius km due north is roughly 10/111 degree away.
        BigDecimal vendorLat = originLat.add(maxRadius.divide(BigDecimal.valueOf(111), 6, java.math.RoundingMode.HALF_UP));
        BigDecimal vendorLng = originLng;

        assertThat(vendorLat.compareTo(box.minLat())).isGreaterThanOrEqualTo(0);
        assertThat(vendorLat.compareTo(box.maxLat())).isLessThanOrEqualTo(0);
        assertThat(vendorLng.compareTo(box.minLng())).isGreaterThanOrEqualTo(0);
        assertThat(vendorLng.compareTo(box.maxLng())).isLessThanOrEqualTo(0);
    }
}
