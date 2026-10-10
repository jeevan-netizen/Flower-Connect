package com.flowerconnect.discovery.service;

import com.flowerconnect.discovery.dto.DiscoveryResponse;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.dto.PageResponses;
import com.flowerconnect.geo.util.DeliveryFee;
import com.flowerconnect.geo.util.GeoCandidates;
import com.flowerconnect.geo.util.GeoDistance;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for vendor geo-discovery (plan task 4.3).
 *
 * <p>The vendor-geography half — which approved vendors are within their own
 * delivery radius of a location — is shared with task 4.4 search through
 * {@link GeoCandidates}; this service adds only the vendor-card response shape
 * and the distance-then-id ordering. Pagination and the response envelope are
 * shared through {@link PageResponses}.
 *
 * <p>The final result is sorted by distance ascending with a deterministic
 * tie-breaker (vendor id), so equal-distance vendors keep a stable order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscoveryService {

    private final VendorProfileRepository vendorProfileRepository;
    private final ServiceLocationRepository serviceLocationRepository;

    /**
     * Returns approved, accepting vendors within their delivery radius of the
     * given service location, paginated and sorted by distance.
     *
     * @param locationId the service location to search from (required)
     * @param page       zero-based page number (default 0)
     * @param size       page size, clamped to 1..100 (default 20)
     * @return paginated discovery responses with computed distance and fee
     * @throws BusinessException if locationId is unknown
     */
    public PageResponse<DiscoveryResponse> discover(Long locationId, Integer page, Integer size) {
        ServiceLocation origin = serviceLocationRepository.findById(locationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown service location"));

        List<GeoCandidates.VendorDistance> inRadius =
                GeoCandidates.approvedAcceptingWithinRadius(vendorProfileRepository, origin);
        if (inRadius.isEmpty()) {
            return PageResponses.empty(page, size);
        }

        List<DiscoveryResponse> results = new ArrayList<>(inRadius.size());
        for (GeoCandidates.VendorDistance vendorDistance : inRadius) {
            results.add(mapToResponse(vendorDistance.vendor(), vendorDistance.distanceKm()));
        }

        results.sort(Comparator.comparing(DiscoveryResponse::getDistanceKm)
                .thenComparing(DiscoveryResponse::getId));

        return PageResponses.of(results, page, size);
    }

    private DiscoveryResponse mapToResponse(VendorProfile vp, BigDecimal distanceKm) {
        return DiscoveryResponse.builder()
                .id(vp.getId())
                .businessName(vp.getBusinessName())
                .description(vp.getDescription())
                .city(vp.getServiceLocation().getCity())
                .area(vp.getServiceLocation().getArea())
                .pincode(vp.getServiceLocation().getPincode())
                .deliveryRadiusKm(vp.getDeliveryRadiusKm())
                .minOrderAmount(vp.getMinOrderAmount())
                .baseDeliveryFee(vp.getBaseDeliveryFee())
                .perKmFee(vp.getPerKmFee())
                .freeDeliveryAbove(vp.getFreeDeliveryAbove())
                .prepTimeMinutes(vp.getPrepTimeMinutes())
                .avgRating(vp.getAvgRating())
                .reviewCount(vp.getReviewCount())
                .acceptingOrders(vp.isAcceptingOrders())
                .distanceKm(GeoDistance.displayKm(distanceKm))
                .estimatedDeliveryFee(DeliveryFee.estimate(
                        vp.getBaseDeliveryFee(), vp.getPerKmFee(), distanceKm))
                .build();
    }
}
