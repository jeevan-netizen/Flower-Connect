package com.flowerconnect.search.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.service.GeoService;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.search.dto.PublicProductImageResponse;
import com.flowerconnect.search.dto.PublicProductResponse;
import com.flowerconnect.search.specification.PublicProductSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

/**
 * Public product search (plan task 4.4).
 *
 * <p>Applies the discovery/search eligibility policy: {@code ACTIVE} products,
 * available stock ({@code quantity - reservedQuantity > 0}), {@code APPROVED} and
 * order-accepting vendors, and delivery-radius filtering against the selected
 * service location. Results are filtered and sorted in Java before pagination so
 * radius eligibility is never distorted by a database-level page cut.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ServiceLocationRepository serviceLocationRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;
    private final GeoService geoService;

    /**
     * Searches products eligible for customer discovery at the given location.
     *
     * @throws BusinessException when {@code locationId} is missing or unknown
     */
    public PageResponse<PublicProductResponse> search(
            Long locationId,
            String q,
            Long category,
            BigDecimal priceMin,
            BigDecimal priceMax,
            String sort,
            Long vendorId,
            Integer page,
            Integer size) {

        if (locationId == null) {
            throw BusinessException.badRequest("locationId is required");
        }

        ServiceLocation origin = serviceLocationRepository.findById(locationId)
                .orElseThrow(() -> BusinessException.badRequest("Unknown service location"));

        SortKey sortKey = SortKey.from(sort);

        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            throw BusinessException.badRequest("priceMin must not exceed priceMax");
        }

        int safePage = Math.max(0, page != null ? page : 0);
        int safeSize = Math.min(Math.max(1, size != null ? size : DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE);

        BigDecimal maxRadius = vendorProfileRepository.findMaxDeliveryRadiusForApproved();
        if (maxRadius == null) {
            return emptyPage(safePage, safeSize);
        }

        GeoService.BoundingBox box = geoService.computeBoundingBox(
                origin.getLatitude(), origin.getLongitude(), maxRadius);

        List<VendorProfile> candidates = vendorProfileRepository.findApprovedAcceptingInBoundingBox(
                box.minLat(), box.maxLat(), box.minLng(), box.maxLng());

        // Haversine refinement: keep vendors within their own delivery radius.
        Set<Long> qualifyingVendorIds = new HashSet<>();
        Map<Long, BigDecimal> vendorDistance = new HashMap<>();
        for (VendorProfile vp : candidates) {
            BigDecimal distance = geoService.haversineKm(
                    origin.getLatitude(), origin.getLongitude(),
                    vp.getLatitude(), vp.getLongitude());
            if (distance.compareTo(vp.getDeliveryRadiusKm()) <= 0) {
                qualifyingVendorIds.add(vp.getId());
                vendorDistance.put(vp.getId(), distance);
            }
        }

        if (qualifyingVendorIds.isEmpty()) {
            return emptyPage(safePage, safeSize);
        }

        // Product eligibility: vendor-qualified + ACTIVE + optional filters.
        Specification<Product> spec = PublicProductSpecifications.forVendorIds(
                new java.util.ArrayList<>(qualifyingVendorIds))
                .and(PublicProductSpecifications.activeProduct());
        if (category != null) {
            spec = spec.and(PublicProductSpecifications.inCategory(category));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(PublicProductSpecifications.nameContains(q));
        }
        if (priceMin != null || priceMax != null) {
            spec = spec.and(PublicProductSpecifications.priceBetween(priceMin, priceMax));
        }
        if (vendorId != null) {
            spec = spec.and(PublicProductSpecifications.vendorId(vendorId));
        }

        List<Product> products = productRepository.findAll(spec);

        // Batch inventory fetch, then filter to available stock.
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, Inventory> inventoryMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            inventoryRepository.findAllByProductIdIn(productIds).forEach(
                    inv -> inventoryMap.put(inv.getProduct().getId(), inv));
        }

        // Deterministic sort, then stable id tie-break. The list is collected
        // (not `Stream.toList()`) because the result is sorted in place below.
        //
        // The vendor and status predicates are also re-checked here rather than
        // left to the query alone: the specification is the database's job, and
        // re-checking in the service means the eligibility rule holds in one
        // readable place and cannot be lost if the query is ever widened.
        Comparator<Product> comparator = sortKey.comparator(vendorDistance);
        List<Product> available = products.stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .filter(p -> vendorDistance.containsKey(p.getVendor().getId()))
                .filter(p -> {
                    Inventory inv = inventoryMap.get(p.getId());
                    return inv != null && inv.getAvailable() > 0;
                })
                .sorted(comparator.thenComparing(Product::getId))
                .collect(Collectors.toList());

        // Paginate after sorting.
        int start = Math.min(safePage * safeSize, available.size());
        int end = Math.min(start + safeSize, available.size());
        List<Product> pageContent = available.subList(start, end);

        List<PublicProductResponse> content = pageContent.stream()
                .map(p -> toResponse(p, inventoryMap.get(p.getId()), vendorDistance.get(p.getVendor().getId())))
                .toList();

        int totalPages = (int) Math.ceil((double) available.size() / safeSize);

        return PageResponse.<PublicProductResponse>builder()
                .content(content)
                .page(safePage)
                .size(safeSize)
                .totalElements(available.size())
                .totalPages(totalPages)
                .first(safePage == 0)
                .last(end >= available.size())
                .empty(available.isEmpty())
                .build();
    }

    private PublicProductResponse toResponse(Product product, Inventory inventory, BigDecimal distanceKm) {
        List<PublicProductImageResponse> images = productImageRepository
                .findByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                .map(image -> PublicProductImageResponse.builder()
                        .id(image.getId())
                        .originalFilename(image.getOriginalFilename())
                        .mimeType(image.getMimeType())
                        .fileSize(image.getFileSize())
                        .sortOrder(image.getSortOrder())
                        .primary(image.isPrimary())
                        .build())
                .toList();

        return PublicProductResponse.builder()
                .id(product.getId())
                .vendorId(product.getVendor().getId())
                .vendorName(product.getVendor().getBusinessName())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .available(inventory == null ? null : inventory.getAvailable())
                .images(images)
                .build();
    }

    private PageResponse<PublicProductResponse> emptyPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return PageResponse.<PublicProductResponse>builder()
                .content(List.of())
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();
    }

    /** Supported sort keys with deterministic id tie-breaking. */
    enum SortKey {
        DISTANCE("distance"),
        PRICE_ASC("price_asc"),
        PRICE_DESC("price_desc"),
        NAME("name");

        private final String value;

        SortKey(String value) {
            this.value = value;
        }

        static SortKey from(String sort) {
            if (sort == null) {
                return DISTANCE;
            }
            for (SortKey key : values()) {
                if (key.value.equalsIgnoreCase(sort.trim())) {
                    return key;
                }
            }
            throw BusinessException.badRequest("Unknown sort value: " + sort);
        }

        Comparator<Product> comparator(Map<Long, BigDecimal> vendorDistance) {
            return switch (this) {
                case DISTANCE -> Comparator.comparing(
                        (Product p) -> vendorDistance.get(p.getVendor().getId()),
                        Comparator.nullsLast(BigDecimal::compareTo));
                case PRICE_ASC -> Comparator.comparing(Product::getBasePrice);
                case PRICE_DESC -> Comparator.comparing(Product::getBasePrice).reversed();
                case NAME -> Comparator.comparing(Product::getName);
            };
        }
    }
}
