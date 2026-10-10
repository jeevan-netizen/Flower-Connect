package com.flowerconnect.storefront.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.dto.PageResponses;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.storefront.dto.StorefrontProductResponse;
import com.flowerconnect.storefront.dto.StorefrontResponse;
import com.flowerconnect.storefront.dto.StorefrontVendorResponse;
import com.flowerconnect.vendor.dto.VendorHoursResponse;
import com.flowerconnect.vendor.mapper.VendorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for the public vendor storefront (plan task 4.5).
 *
 * <p>Answers "who is this shop and what can I buy from it?" for an anonymous
 * caller, so every rule here is a visibility rule:
 *
 * <ul>
 *   <li><b>Only an APPROVED vendor has a storefront.</b> An unknown id and a
 *       pending, rejected or suspended one return the same 404 body, so the
 *       endpoint does not leak which vendors exist. This is the discovery
 *       rule of {@code VendorProfileSpecifications.approved()} applied to a
 *       single shop, and it honours D-6: a suspended vendor is hidden
 *       immediately.</li>
 *   <li><b>Approval is read from the database on every call</b> and is never
 *       cached in a token, so an admin's approval or suspension takes effect
 *       on the vendor's next request (D-13).</li>
 *   <li><b>Only ACTIVE products are listed</b> — {@code ProductStatus}'s own
 *       javadoc makes ACTIVE the only storefront-visible state, so DRAFT,
 *       INACTIVE and ARCHIVED are excluded.</li>
 *   <li><b>An ACTIVE product stays listed when its stock runs out</b>, with
 *       {@code inStock = false}. The plan's wording is "active products",
 *       which is a lifecycle statement; dropping the row would make a
 *       listing vanish from the storefront while the vendor's own catalog
 *       screen still shows it.</li>
 *   <li><b>A paused shop is still browsable.</b> {@code accepting_orders} is
 *       reported rather than enforced — a vendor who has paused ordering is
 *       hidden from discovery and cannot transact, but the page describing
 *       them remains readable, and the flag is what a client renders as a
 *       closed shop.</li>
 * </ul>
 *
 * <p>The whole path is read-only and takes no locks: it reports state, it
 * does not change any. Product availability is read for one page of products
 * in a single query, so a large catalogue does not cost one lookup per row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StorefrontService {

    private final VendorProfileRepository vendorProfileRepository;
    private final VendorHoursRepository vendorHoursRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final VendorMapper vendorMapper;

    /**
     * Returns the public storefront of one vendor.
     *
     * @param vendorId the vendor profile id (required, positive)
     * @param page     zero-based page number (default 0)
     * @param size     page size, clamped to 1..100 (default 20)
     * @return the public profile and one page of ACTIVE products
     * @throws BusinessException 404 if no APPROVED vendor has this id
     */
    public StorefrontResponse storefront(Long vendorId, Integer page, Integer size) {
        VendorProfile vendor = requireApprovedVendor(vendorId);

        List<VendorHours> hours = vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(vendorId);
        PageResponse<StorefrontProductResponse> products = activeProducts(vendorId, page, size);

        return StorefrontResponse.builder()
                .vendor(toVendorResponse(vendor, vendorMapper.toHoursResponses(hours)))
                .products(products)
                .build();
    }

    /**
     * Resolves the vendor whose storefront is being read, and refuses any
     * status other than {@code APPROVED}.
     *
     * <p>The unknown-id and non-approved cases throw the same exception from
     * the same message, on purpose: a caller that could tell them apart could
     * enumerate which vendor ids exist and what state each is in.
     */
    private VendorProfile requireApprovedVendor(Long vendorId) {
        // JOIN FETCHes the service location, so the area row does not cost a
        // second query when the city/area/pincode is read below.
        return vendorProfileRepository.findByIdWithDetails(vendorId)
                .filter(vendor -> vendor.getStatus() == VendorProfile.Status.APPROVED)
                .orElseThrow(() -> BusinessException.notFound("Storefront not found"));
    }

    /**
     * One page of the vendor's ACTIVE products, tagged with availability.
     *
     * <p>Sorting and paging happen in the database. The order is the vendor
     * catalog listing's own — {@code createdAt} then {@code id}, both
     * descending — reused rather than invented so the two screens agree, and
     * the id tie-break keeps a row from moving between pages when two
     * products share a creation timestamp.
     *
     * <p>The page window is clamped through {@link PageResponses} exactly as
     * discovery and search clamp theirs, so an out-of-range {@code page} is
     * an empty page rather than an error and the same envelope is returned.
     */
    private PageResponse<StorefrontProductResponse> activeProducts(Long vendorId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(
                PageResponses.safePage(page),
                PageResponses.safeSize(size),
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));

        Page<Product> productPage =
                productRepository.findByVendorIdAndStatus(vendorId, ProductStatus.ACTIVE, pageable);

        Map<Long, Boolean> inStockByProductId = availabilityByProductId(productPage.getContent());
        return PageResponses.of(productPage.map(product -> toProductResponse(product, inStockByProductId)));
    }

    /**
     * Reads availability for the products on this page in one query.
     *
     * <p>The rule is {@link Inventory#getAvailable()} — the entity's own
     * computation of {@code quantity − reservedQuantity} — rather than a
     * second copy of that arithmetic here, so the storefront and every other
     * caller cannot disagree about what "in stock" means. A product with no
     * inventory row is reported as out of stock: one row per product is a
     * database invariant, so this is unreachable through the write paths, and
     * the safe answer for an unknown level is "cannot be bought".
     */
    private Map<Long, Boolean> availabilityByProductId(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, Boolean> inStockByProductId = new HashMap<>(productIds.size() * 2);
        for (Inventory inventory : inventoryRepository.findByProductIdIn(productIds)) {
            // The products are already loaded in this transaction, and
            // reading the identifier off the association is what the
            // existing mappers do, so this does not trigger a select per row.
            inStockByProductId.put(inventory.getProduct().getId(), inventory.getAvailable() > 0);
        }
        return inStockByProductId;
    }

    private StorefrontProductResponse toProductResponse(Product product, Map<Long, Boolean> inStockByProductId) {
        return StorefrontProductResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .inStock(inStockByProductId.getOrDefault(product.getId(), false))
                .build();
    }

    /**
     * Maps the profile to its public shape. Hours are mapped through the
     * shared {@code VendorMapper} rather than re-declared, so the storefront
     * and the vendor's own profile screen report a week identically.
     */
    private StorefrontVendorResponse toVendorResponse(VendorProfile vendor, List<VendorHoursResponse> hours) {
        return StorefrontVendorResponse.builder()
                .id(vendor.getId())
                .businessName(vendor.getBusinessName())
                .description(vendor.getDescription())
                .logoUrl(vendor.getLogoUrl())
                .city(vendor.getServiceLocation().getCity())
                .area(vendor.getServiceLocation().getArea())
                .pincode(vendor.getServiceLocation().getPincode())
                .deliveryRadiusKm(vendor.getDeliveryRadiusKm())
                .minOrderAmount(vendor.getMinOrderAmount())
                .baseDeliveryFee(vendor.getBaseDeliveryFee())
                .perKmFee(vendor.getPerKmFee())
                .freeDeliveryAbove(vendor.getFreeDeliveryAbove())
                .prepTimeMinutes(vendor.getPrepTimeMinutes())
                .slotDurationMinutes(vendor.getSlotDurationMinutes())
                .maxOrdersPerSlot(vendor.getMaxOrdersPerSlot())
                .acceptingOrders(vendor.isAcceptingOrders())
                .avgRating(vendor.getAvgRating())
                .reviewCount(vendor.getReviewCount())
                .hours(hours)
                .build();
    }
}
