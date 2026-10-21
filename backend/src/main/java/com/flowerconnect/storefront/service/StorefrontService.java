package com.flowerconnect.storefront.service;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.search.dto.PublicProductImageResponse;
import com.flowerconnect.search.dto.PublicProductResponse;
import com.flowerconnect.storefront.dto.StorefrontHour;
import com.flowerconnect.storefront.dto.StorefrontProductPage;
import com.flowerconnect.storefront.dto.StorefrontResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Public vendor storefront (plan task 4.5).
 *
 * <p>Returns the vendor's public profile, operating hours, and active in-stock
 * products. Delivery-radius filtering is intentionally not applied: the customer
 * has already chosen this vendor. A non-existent, non-approved, or
 * non-accepting vendor returns {@code 404} without leaking its existence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StorefrontService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final VendorProfileRepository vendorProfileRepository;
    private final VendorHoursRepository vendorHoursRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;

    /**
     * Returns the storefront for a vendor.
     *
     * @throws BusinessException with {@code NOT_FOUND} when the vendor is unknown,
     *         not approved, or not accepting orders
     */
    public StorefrontResponse storefront(Long vendorId, Integer page, Integer size) {
        VendorProfile vendor = vendorProfileRepository.findByIdWithDetails(vendorId)
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));

        if (vendor.getStatus() != VendorProfile.Status.APPROVED || !vendor.isAcceptingOrders()) {
            throw BusinessException.notFound("Vendor not found");
        }

        int safePage = Math.max(0, page != null ? page : 0);
        int safeSize = Math.min(Math.max(1, size != null ? size : DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE);

        List<VendorHours> hours = vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(vendorId);

        // Product eligibility: ACTIVE + available stock > 0 (no radius filter).
        Specification<Product> spec = Specification
                .<Product>where((root, query, builder) -> builder.equal(root.get("vendor").get("id"), vendorId))
                .and((root, query, builder) -> builder.equal(root.get("status"), ProductStatus.ACTIVE));

        List<Product> products = productRepository.findAll(spec);

        Map<Long, Inventory> inventoryMap = new HashMap<>();
        List<Long> productIds = products.stream().map(Product::getId).toList();
        if (!productIds.isEmpty()) {
            inventoryRepository.findAllByProductIdIn(productIds).forEach(
                    inv -> inventoryMap.put(inv.getProduct().getId(), inv));
        }

        // Newest first, with the id as a deterministic tie-break. The list is
        // collected (not `Stream.toList()`) because the result is sorted in place.
        //
        // The ACTIVE predicate is re-checked here as well as in the query, so the
        // storefront eligibility rule holds in one readable place.
        List<Product> available = products.stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .filter(p -> {
                    Inventory inv = inventoryMap.get(p.getId());
                    return inv != null && inv.getAvailable() > 0;
                })
                .sorted(Comparator
                        .comparing(Product::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Product::getId, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        int start = Math.min(safePage * safeSize, available.size());
        int end = Math.min(start + safeSize, available.size());
        List<Product> pageContent = available.subList(start, end);

        List<PublicProductResponse> content = pageContent.stream()
                .map(p -> toResponse(p, inventoryMap.get(p.getId())))
                .toList();

        int totalPages = (int) Math.ceil((double) available.size() / safeSize);

        com.flowerconnect.geo.dto.PageResponse<PublicProductResponse> pageResponse =
                com.flowerconnect.geo.dto.PageResponse.<PublicProductResponse>builder()
                        .content(content)
                        .page(safePage)
                        .size(safeSize)
                        .totalElements(available.size())
                        .totalPages(totalPages)
                        .first(safePage == 0)
                        .last(end >= available.size())
                        .empty(available.isEmpty())
                        .build();

        List<StorefrontHour> hourDtos = hours.stream()
                .map(h -> StorefrontHour.builder()
                        .weekday(h.getWeekday().name())
                        .openTime(h.getOpenTime())
                        .closeTime(h.getCloseTime())
                        .closed(h.isClosed())
                        .build())
                .toList();

        return StorefrontResponse.builder()
                .id(vendor.getId())
                .businessName(vendor.getBusinessName())
                .description(vendor.getDescription())
                .city(vendor.getServiceLocation().getCity())
                .area(vendor.getServiceLocation().getArea())
                .pincode(vendor.getServiceLocation().getPincode())
                .deliveryRadiusKm(vendor.getDeliveryRadiusKm())
                .minOrderAmount(vendor.getMinOrderAmount())
                .baseDeliveryFee(vendor.getBaseDeliveryFee())
                .perKmFee(vendor.getPerKmFee())
                .freeDeliveryAbove(vendor.getFreeDeliveryAbove())
                .prepTimeMinutes(vendor.getPrepTimeMinutes())
                .avgRating(vendor.getAvgRating())
                .reviewCount(vendor.getReviewCount())
                .acceptingOrders(vendor.isAcceptingOrders())
                .hours(hourDtos)
                .products(StorefrontProductPage.from(pageResponse))
                .build();
    }

    private PublicProductResponse toResponse(Product product, Inventory inventory) {
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
}
