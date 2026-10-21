package com.flowerconnect.discovery.service;

import com.flowerconnect.discovery.dto.DiscoveryResponse;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.service.GeoService;
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

    private final VendorProfileRepository vendorProfileRepository;
    private final ServiceLocationRepository serviceLocationRepository;
    private final GeoService geoService;

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

        GeoService.BoundingBox box = geoService.computeBoundingBox(originLat, originLng, maxRadius);

        List<VendorProfile> candidates = vendorProfileRepository.findApprovedAcceptingInBoundingBox(
                box.minLat(), box.maxLat(), box.minLng(), box.maxLng());

        List<DiscoveryResponse> results = new ArrayList<>();
        for (VendorProfile vp : candidates) {
            BigDecimal distance = geoService.haversineKm(originLat, originLng, vp.getLatitude(), vp.getLongitude());
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
}