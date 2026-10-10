package com.flowerconnect.discovery.service;

import com.flowerconnect.discovery.dto.DiscoveryResponse;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for vendor geo-discovery (plan task 4.3).
 *
 * <p>Uses a bounding-box prefilter on the indexed {@code latitude}/{@code longitude}
 * columns followed by an exact Haversine distance check against each vendor's
 * {@code delivery_radius_km}. The final result is sorted by distance ascending
 * with a deterministic tie-breaker (vendor id).
 *
 * <p>The bounding box is derived from the maximum delivery radius among all
 * approved vendors, so it never excludes a vendor that could be within range.
 * Longitude wraparound and polar edge cases are handled by the Haversine
 * refinement; the bounding box is only a candidate prefilter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscoveryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int EARTH_RADIUS_KM = 6371;
    private static final BigDecimal KM_PER_DEGREE_LAT = new BigDecimal("111.0");
    private static final BigDecimal KM_PER_DEGREE_LNG_AT_EQUATOR = new BigDecimal("111.32");

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

        int safePage = Math.max(0, page != null ? page : 0);
        int safeSize = Math.min(Math.max(1, size != null ? size : DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id"));

        BigDecimal originLat = origin.getLatitude();
        BigDecimal originLng = origin.getLongitude();

        BigDecimal maxRadius = vendorProfileRepository.findMaxDeliveryRadiusForApproved();
        if (maxRadius == null) {
            return emptyPage(safePage, safeSize);
        }

        double latDelta = maxRadius.doubleValue() / 111.0;
        double lngDelta = maxRadius.doubleValue() / (111.32 * Math.cos(Math.toRadians(originLat.doubleValue())));

        BigDecimal minLat = originLat.subtract(BigDecimal.valueOf(latDelta));
        BigDecimal maxLat = originLat.add(BigDecimal.valueOf(latDelta));
        BigDecimal minLng = originLng.subtract(BigDecimal.valueOf(lngDelta));
        BigDecimal maxLng = originLng.add(BigDecimal.valueOf(lngDelta));

        List<VendorProfile> candidates = vendorProfileRepository.findApprovedAcceptingInBoundingBox(
                minLat, maxLat, minLng, maxLng);

        List<DiscoveryResponse> results = new ArrayList<>();
        for (VendorProfile vp : candidates) {
            BigDecimal distance = haversineKm(originLat, originLng, vp.getLatitude(), vp.getLongitude());
            BigDecimal radius = vp.getDeliveryRadiusKm();
            if (distance.compareTo(radius) <= 0) {
                results.add(mapToResponse(vp, distance, originLat, originLng));
            }
        }

        results.sort((a, b) -> {
            int cmp = a.getDistanceKm().compareTo(b.getDistanceKm());
            if (cmp != 0) {
                return cmp;
            }
            return a.getId().compareTo(b.getId());
        });

        int start = Math.min(safePage * safeSize, results.size());
        int end = Math.min(start + safeSize, results.size());
        List<DiscoveryResponse> pageContent = results.subList(start, end);

        Page<DiscoveryResponse> resultPage = new PageImpl<>(pageContent, pageable, results.size());

        return PageResponse.<DiscoveryResponse>builder()
                .content(resultPage.getContent())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .first(resultPage.isFirst())
                .last(resultPage.isLast())
                .empty(resultPage.isEmpty())
                .build();
    }

    private PageResponse<DiscoveryResponse> emptyPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DiscoveryResponse> empty = new PageImpl<>(List.of(), pageable, 0);
        return PageResponse.<DiscoveryResponse>builder()
                .content(List.of())
                .page(empty.getNumber())
                .size(empty.getSize())
                .totalElements(empty.getTotalElements())
                .totalPages(empty.getTotalPages())
                .first(empty.isFirst())
                .last(empty.isLast())
                .empty(empty.isEmpty())
                .build();
    }

    private DiscoveryResponse mapToResponse(VendorProfile vp, BigDecimal distanceKm,
                                            BigDecimal originLat, BigDecimal originLng) {
        BigDecimal baseFee = vp.getBaseDeliveryFee();
        BigDecimal perKm = vp.getPerKmFee();
        BigDecimal estimatedFee = baseFee.add(perKm.multiply(distanceKm))
                .setScale(2, RoundingMode.HALF_UP);

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
                .distanceKm(distanceKm.setScale(2, RoundingMode.HALF_UP))
                .estimatedDeliveryFee(estimatedFee)
                .build();
    }

    /**
     * Haversine formula: distance in kilometres between two lat/lng points.
     *
     * <p>Formula: 2 * R * asin(sqrt(sin^2((lat2-lat1)/2) + cos(lat1)*cos(lat2)*sin^2((lng2-lng1)/2)))
     * where R = 6371 km (Earth's mean radius).
     */
    private BigDecimal haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
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
}