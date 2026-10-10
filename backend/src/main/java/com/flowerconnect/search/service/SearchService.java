package com.flowerconnect.search.service;

import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
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
import com.flowerconnect.search.dto.SearchResponse;
import com.flowerconnect.search.specification.SearchSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service for location-aware product search (plan task 4.4).
 *
 * <p>The search pipeline:
 * <ol>
 *   <li>Validate required {@code locationId} and optional filters.</li>
 *   <li>Resolve the category subtree (all active descendants of the given
 *       category, pruning at inactive nodes).</li>
 *   <li>Find approved, accepting vendors within their delivery radius of the
 *       location — shared with task 4.3 discovery through
 *       {@link GeoCandidates}, so the two endpoints cannot disagree about
 *       which vendors are "near" or how far.</li>
 *   <li>Build a JPA Specification combining product eligibility (ACTIVE,
 *       available stock &gt; 0) with optional filters (q, category, price,
 *       vendorId).</li>
 *   <li>Execute the query (unpaged to get all candidates for distance sort).</li>
 *   <li>Map each candidate using the vendor distance the radius check already
 *       computed — no per-product Haversine recomputation.</li>
 *   <li>Sort by the requested criterion (distance / price_asc / price_desc /
 *       name), every mode tie-broken by product id so the order is total and
 *       pagination is stable across requests.</li>
 *   <li>Paginate in memory through {@link PageResponses} (distance is not a
 *       column, and the page window arithmetic is overflow-safe and shared
 *       with discovery).</li>
 * </ol>
 *
 * <p>Zero results returns an empty page (not 404).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final ServiceLocationRepository serviceLocationRepository;

    /**
     * Searches products with location-aware filtering and distance-based sorting.
     *
     * @param locationId  required service location id (drives distance sort + delivery filter)
     * @param q           optional search query (case-insensitive name contains)
     * @param categoryId  optional category id (includes active descendants)
     * @param priceMin    optional minimum base price (inclusive)
     * @param priceMax    optional maximum base price (inclusive)
     * @param sort        sort criterion: distance | price_asc | price_desc | name (default distance)
     * @param vendorId    optional vendor id filter
     * @param page        zero-based page number (default 0)
     * @param size        page size, clamped to 1..100 (default 20)
     * @return paginated search responses with distance and estimated fee
     * @throws BusinessException if locationId is unknown, categoryId is unknown/inactive,
     *                           priceMin &gt; priceMax, or sort value is invalid
     */
    public PageResponse<SearchResponse> search(
            Long locationId,
            String q,
            Long categoryId,
            BigDecimal priceMin,
            BigDecimal priceMax,
            String sort,
            Long vendorId,
            Integer page,
            Integer size) {

        // 1. Validate required locationId
        if (locationId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "locationId is required");
        }
        ServiceLocation origin = serviceLocationRepository.findById(locationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown service location"));

        // 2. Validate price bounds
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "priceMin must not exceed priceMax");
        }

        // 3. Validate and normalize sort parameter
        String normalizedSort = normalizeSort(sort);

        // 4. Resolve category subtree (active descendants only)
        List<Long> categoryIds = resolveCategorySubtree(categoryId);

        // 5. Find vendors within delivery radius (shared with discovery)
        List<GeoCandidates.VendorDistance> inRadius =
                GeoCandidates.approvedAcceptingWithinRadius(vendorProfileRepository, origin);

        // If no vendors in radius, return empty page early
        if (inRadius.isEmpty()) {
            return PageResponses.empty(page, size);
        }

        // The radius check already computed each in-range vendor's exact
        // distance, so index it rather than recomputing Haversine per product.
        Map<Long, BigDecimal> distanceByVendorId = new HashMap<>(inRadius.size() * 2);
        List<Long> vendorIdsInRadius = new ArrayList<>(inRadius.size());
        for (GeoCandidates.VendorDistance vendorDistance : inRadius) {
            distanceByVendorId.put(vendorDistance.vendor().getId(), vendorDistance.distanceKm());
            vendorIdsInRadius.add(vendorDistance.vendor().getId());
        }

        // 6. Build specification and fetch all candidates (unpaged)
        Specification<Product> spec = buildSpecification(
                vendorIdsInRadius, categoryIds, q, priceMin, priceMax, vendorId);

        List<Product> candidates = productRepository.findAll(spec);

        // 7. Map to responses using the already-computed distances
        List<SearchResponse> results = new ArrayList<>(candidates.size());
        for (Product product : candidates) {
            VendorProfile vendor = product.getVendor();
            BigDecimal distance = distanceByVendorId.get(vendor.getId());
            if (distance == null) {
                // Unreachable from the public request path: the specification
                // restricts candidates to the in-radius vendor set above. Skip
                // rather than emit a product whose distance is unknown.
                continue;
            }
            results.add(mapToResponse(product, vendor, distance));
        }

        // 8. Sort results
        sortResults(results, normalizedSort);

        // 9. Paginate in memory (shared envelope, overflow-safe window)
        return PageResponses.of(results, page, size);
    }

    /**
     * Normalizes and validates the sort parameter.
     * Whitelist: distance, price_asc, price_desc, name.
     * Unknown value throws VALIDATION_FAILED.
     * Default is "distance".
     */
    private String normalizeSort(String sort) {
        String trimmed = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "distance";
        }
        if (!Set.of("distance", "price_asc", "price_desc", "name").contains(trimmed)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid sort value: " + sort);
        }
        return trimmed;
    }

    /**
     * Resolves the category subtree: all active descendants of the given
     * category id (including the category itself). Prunes at inactive nodes
     * — children of a deactivated category are not included, even if active.
     *
     * @param categoryId the root category id, or null for no filter
     * @return list of category ids to include, or empty list if no filter
     * @throws BusinessException if categoryId is unknown or inactive
     */
    private List<Long> resolveCategorySubtree(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }

        Category root = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown category"));

        if (!root.isActive()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Category is inactive");
        }

        Set<Long> result = new HashSet<>();
        collectActiveDescendants(root, result);
        return new ArrayList<>(result);
    }

    private void collectActiveDescendants(Category category, Set<Long> accumulator) {
        accumulator.add(category.getId());
        for (Category child : categoryRepository.findByParentId(category.getId())) {
            if (child.isActive()) {
                collectActiveDescendants(child, accumulator);
            }
        }
    }

    /**
     * Builds the combined specification for product search.
     */
    private Specification<Product> buildSpecification(
            List<Long> vendorIdsInRadius,
            List<Long> categoryIds,
            String q,
            BigDecimal priceMin,
            BigDecimal priceMax,
            Long vendorId) {

        Specification<Product> spec = SearchSpecifications.eligibleForStorefront(vendorIdsInRadius, categoryIds);

        if (q != null && !q.trim().isEmpty()) {
            spec = spec.and(SearchSpecifications.nameContains(q));
        }
        if (priceMin != null) {
            spec = spec.and(SearchSpecifications.priceAtLeast(priceMin));
        }
        if (priceMax != null) {
            spec = spec.and(SearchSpecifications.priceAtMost(priceMax));
        }
        if (vendorId != null) {
            spec = spec.and(SearchSpecifications.forVendor(vendorId));
        }

        return spec;
    }

    /**
     * Sorts the results according to the requested criterion. Every mode is a
     * total order — product id breaks ties — because pagination is applied
     * after the sort and an unstable order would let an item shift between
     * pages from one request to the next.
     */
    private void sortResults(List<SearchResponse> results, String sort) {
        Comparator<SearchResponse> comparator = switch (sort) {
            case "price_asc" -> Comparator.comparing(SearchResponse::getBasePrice,
                    Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(SearchResponse::getId);
            case "price_desc" -> Comparator.comparing(SearchResponse::getBasePrice,
                    Comparator.nullsLast(BigDecimal::compareTo).reversed())
                    .thenComparing(SearchResponse::getId);
            case "name" -> Comparator.comparing(SearchResponse::getName,
                    Comparator.nullsLast(String::compareToIgnoreCase))
                    .thenComparing(SearchResponse::getId);
            default -> Comparator.comparing(SearchResponse::getDistanceKm,
                    Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(SearchResponse::getId);
        };
        results.sort(comparator);
    }

    private SearchResponse mapToResponse(Product product, VendorProfile vendor, BigDecimal distanceKm) {
        return SearchResponse.builder()
                .id(product.getId())
                .vendorId(vendor.getId())
                .vendorName(vendor.getBusinessName())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .distanceKm(GeoDistance.displayKm(distanceKm))
                .estimatedDeliveryFee(DeliveryFee.estimate(
                        vendor.getBaseDeliveryFee(), vendor.getPerKmFee(), distanceKm))
                .build();
    }
}
