package com.flowerconnect.geo.util;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.VendorProfileRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared resolution of "which vendors serve this location" for the two public
 * geo endpoints (plan task 4.3 discovery and task 4.4 search).
 *
 * <p>The whole candidate-resolution algorithm lives here once: a bounding-box
 * prefilter derived from the maximum approved delivery radius (served by
 * {@code idx_vendor_profiles_status_geo}), followed by an exact Haversine
 * refinement against each vendor's own {@code delivery_radius_km}. Discovery
 * consumes the vendors directly; search consumes their ids. Keeping one
 * implementation means the two endpoints cannot disagree about which vendors
 * are "near" a location.
 *
 * <p>The returned distances are the exact (4-decimal) Haversine values, so a
 * caller never has to recompute them per row — discovery and search report the
 * same distance for the same vendor and origin.
 */
public final class GeoCandidates {

    private GeoCandidates() {
    }

    /**
     * A vendor that passes the exact radius check, paired with its distance
     * from the search origin.
     */
    public record VendorDistance(VendorProfile vendor, BigDecimal distanceKm) {
    }

    /**
     * Returns every approved, order-accepting vendor within its own delivery
     * radius of the origin location, each with its exact distance.
     *
     * <p>Bounding-box prefilter first (so the candidate set is bounded by the
     * indexed geo columns), then the exact Haversine refinement — the radius
     * rule is the vendor's own, not a global one. Longitude wraparound and
     * polar edge cases are handled by the Haversine refinement; the bounding
     * box is only a candidate prefilter.
     *
     * @param vendorProfileRepository repository over {@code vendor_profiles}
     * @param origin                  the service location to search from
     * @return matching vendors with distances; empty when no approved vendor
     *         exists or none is in range
     */
    public static List<VendorDistance> approvedAcceptingWithinRadius(
            VendorProfileRepository vendorProfileRepository, ServiceLocation origin) {

        BigDecimal maxRadius = vendorProfileRepository.findMaxDeliveryRadiusForApproved();
        if (maxRadius == null) {
            return List.of();
        }

        double latDelta = GeoDistance.latDeltaForRadius(maxRadius);
        double lngDelta = GeoDistance.lngDeltaForRadius(maxRadius, origin.getLatitude());

        BigDecimal minLat = origin.getLatitude().subtract(BigDecimal.valueOf(latDelta));
        BigDecimal maxLat = origin.getLatitude().add(BigDecimal.valueOf(latDelta));
        BigDecimal minLng = origin.getLongitude().subtract(BigDecimal.valueOf(lngDelta));
        BigDecimal maxLng = origin.getLongitude().add(BigDecimal.valueOf(lngDelta));

        List<VendorProfile> candidates = vendorProfileRepository.findApprovedAcceptingInBoundingBox(
                minLat, maxLat, minLng, maxLng);

        BigDecimal originLat = origin.getLatitude();
        BigDecimal originLng = origin.getLongitude();

        List<VendorDistance> inRadius = new ArrayList<>(candidates.size());
        for (VendorProfile vendor : candidates) {
            BigDecimal distance = GeoDistance.haversineKm(originLat, originLng,
                    vendor.getLatitude(), vendor.getLongitude());
            if (distance.compareTo(vendor.getDeliveryRadiusKm()) <= 0) {
                inRadius.add(new VendorDistance(vendor, distance));
            }
        }
        return inRadius;
    }
}
