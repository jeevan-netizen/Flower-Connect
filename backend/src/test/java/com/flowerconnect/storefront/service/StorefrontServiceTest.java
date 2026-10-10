package com.flowerconnect.storefront.service;

import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.storefront.dto.StorefrontProductResponse;
import com.flowerconnect.storefront.dto.StorefrontResponse;
import com.flowerconnect.storefront.dto.StorefrontVendorResponse;
import com.flowerconnect.vendor.mapper.VendorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StorefrontService} (plan task 4.5).
 *
 * <p>Approval status is re-read from the repository on every call rather than
 * memoised in the service, so the non-approved cases stub a fresh lookup, and
 * {@link #storefrontReadsApprovalStatusAgainOnEveryCall()} flips the status
 * mid-test to prove a state change is picked up with nothing to invalidate.
 */
@ExtendWith(MockitoExtension.class)
class StorefrontServiceTest {

    private static final Long VENDOR_ID = 7L;

    /** The vendor catalog listing's order, reused by the storefront (plan task 4.5). */
    private static final Sort STOREFRONT_SORT =
            Sort.by("createdAt").descending().and(Sort.by("id").descending());

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @Mock
    private VendorHoursRepository vendorHoursRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private VendorMapper vendorMapper;

    private StorefrontService storefrontService;

    private ServiceLocation location;
    private Category category;

    @BeforeEach
    void setUp() {
        storefrontService = new StorefrontService(vendorProfileRepository, vendorHoursRepository,
                productRepository, inventoryRepository, vendorMapper);

        location = ServiceLocation.builder()
                .id(3L)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .build();

        category = Category.builder()
                .id(5L)
                .name("Roses")
                .slug("roses")
                .displayOrder(0)
                .active(true)
                .build();
    }

    // ---------------------------------------------------------------- fixtures

    private VendorProfile vendor(VendorProfile.Status status) {
        return vendor(status, true);
    }

    private VendorProfile vendor(VendorProfile.Status status, boolean acceptingOrders) {
        return VendorProfile.builder()
                .id(VENDOR_ID)
                .businessName("Koramangala Florist")
                .description("Neighbourhood flower shop")
                .logoUrl("https://example.test/logo.png")
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(status)
                .commissionRate(new BigDecimal("0.1000"))
                .avgRating(null)
                .reviewCount(0)
                .minOrderAmount(new BigDecimal("199.00"))
                .baseDeliveryFee(new BigDecimal("39.00"))
                .perKmFee(new BigDecimal("12.00"))
                .freeDeliveryAbove(new BigDecimal("799.00"))
                .prepTimeMinutes(45)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(acceptingOrders)
                .build();
    }

    private Product product(Long id, ProductStatus status) {
        return Product.builder()
                .id(id)
                .vendor(vendor(VendorProfile.Status.APPROVED))
                .category(category)
                .name("Product " + id)
                .slug("product-" + id)
                .description("A test product")
                .basePrice(new BigDecimal("249.00"))
                .status(status)
                .build();
    }

    private void stubApprovedVendor(VendorProfile profile) {
        when(vendorProfileRepository.findByIdWithDetails(VENDOR_ID)).thenReturn(Optional.of(profile));
        when(vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(VENDOR_ID)).thenReturn(List.of());
        when(vendorMapper.toHoursResponses(any())).thenReturn(List.of());
    }

    private void stubProducts(Product... products) {
        when(productRepository.findByVendorIdAndStatus(eq(VENDOR_ID), eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(products), PageRequest.of(0, 20, STOREFRONT_SORT), products.length));
    }

    private void stubInventories(Inventory... inventories) {
        when(inventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(inventories));
    }

    private Inventory inventory(Long productId, int quantity, int reservedQuantity) {
        return Inventory.builder()
                .id(productId)
                .product(product(productId, ProductStatus.ACTIVE))
                .quantity(quantity)
                .reservedQuantity(reservedQuantity)
                .lowStockThreshold(5)
                .build();
    }

    /** Builds the Pageable the service is expected to ask the repository for. */
    private static Pageable expectedPageable(int page, int size) {
        return PageRequest.of(page, size, STOREFRONT_SORT);
    }

    // ------------------------------------------------------- approval / 404s

    @Test
    void storefrontThrows404WhenVendorIsUnknown() {
        when(vendorProfileRepository.findByIdWithDetails(VENDOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storefrontService.storefront(VENDOR_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Storefront not found")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.NOT_FOUND);
    }

    @Test
    void storefrontThrows404ForAPendingVendor() {
        stubNonApproved(VendorProfile.Status.PENDING_APPROVAL);

        assertThatThrownBy(() -> storefrontService.storefront(VENDOR_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Storefront not found")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.NOT_FOUND);
    }

    @Test
    void storefrontThrows404ForARejectedVendor() {
        stubNonApproved(VendorProfile.Status.REJECTED);

        assertThatThrownBy(() -> storefrontService.storefront(VENDOR_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Storefront not found")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.NOT_FOUND);
    }

    @Test
    void storefrontThrows404ForASuspendedVendor() {
        stubNonApproved(VendorProfile.Status.SUSPENDED);

        assertThatThrownBy(() -> storefrontService.storefront(VENDOR_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Storefront not found")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.NOT_FOUND);
    }

    /**
     * The unknown and the non-approved case are indistinguishable on the wire:
     * same code, same message. A caller who could tell them apart could
     * enumerate which vendor ids exist and what state each is in.
     */
    @Test
    void storefrontHidesTheDifferenceBetweenUnknownAndNonApproved() {
        when(vendorProfileRepository.findByIdWithDetails(VENDOR_ID)).thenReturn(Optional.empty());
        BusinessException unknown = catchNotFound(() -> storefrontService.storefront(VENDOR_ID, null, null));

        stubNonApproved(VendorProfile.Status.PENDING_APPROVAL);
        BusinessException nonApproved = catchNotFound(() -> storefrontService.storefront(VENDOR_ID, null, null));

        assertThat(unknown.getErrorCode()).isEqualTo(nonApproved.getErrorCode());
        assertThat(unknown.getMessage()).isEqualTo(nonApproved.getMessage());
    }

    private void stubNonApproved(VendorProfile.Status status) {
        when(vendorProfileRepository.findByIdWithDetails(VENDOR_ID)).thenReturn(Optional.of(vendor(status)));
    }

    private BusinessException catchNotFound(Runnable call) {
        try {
            call.run();
        } catch (BusinessException ex) {
            return ex;
        }
        throw new AssertionError("Expected a BusinessException");
    }

    // ------------------------------------------------------------- visibility

    @Test
    void storefrontIsReadableWhenTheVendorHasPausedOrdering() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED, false));
        stubProducts();

        StorefrontResponse response = storefrontService.storefront(VENDOR_ID, null, null);

        assertThat(response.getVendor().getAcceptingOrders()).isFalse();
        assertThat(response.getVendor().getBusinessName()).isEqualTo("Koramangala Florist");
    }

    @Test
    void storefrontReadsApprovalStatusAgainOnEveryCall() {
        VendorProfile approved = vendor(VendorProfile.Status.APPROVED);
        stubApprovedVendor(approved);
        stubProducts();

        StorefrontResponse first = storefrontService.storefront(VENDOR_ID, null, null);
        assertThat(first.getVendor()).isNotNull();

        // An admin suspends the vendor: the next request must not see the
        // storefront, with no token re-issue and nothing to invalidate.
        approved.setStatus(VendorProfile.Status.SUSPENDED);

        assertThatThrownBy(() -> storefrontService.storefront(VENDOR_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Storefront not found");
        verify(vendorProfileRepository, times(2)).findByIdWithDetails(VENDOR_ID);
    }

    // ------------------------------------------------------ product filtering

    @Test
    void storefrontReturnsOnlyActiveProducts() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts(product(101L, ProductStatus.ACTIVE), product(102L, ProductStatus.ACTIVE));

        StorefrontResponse response = storefrontService.storefront(VENDOR_ID, null, null);

        assertThat(response.getProducts().getContent())
                .extracting(StorefrontProductResponse::getId)
                .containsExactly(101L, 102L);
        // The DRAFT / INACTIVE / ARCHIVED exclusion is the status argument,
        // asserted here rather than assumed.
        verify(productRepository)
                .findByVendorIdAndStatus(VENDOR_ID, ProductStatus.ACTIVE, expectedPageable(0, 20));
    }

    @Test
    void storefrontKeepsAnActiveButOutOfStockProductAndMarksIt() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts(product(201L, ProductStatus.ACTIVE));
        stubInventories(inventory(201L, 10, 10));   // every unit reserved

        StorefrontResponse response = storefrontService.storefront(VENDOR_ID, null, null);

        assertThat(response.getProducts().getContent()).hasSize(1);
        assertThat(response.getProducts().getContent().get(0).isInStock()).isFalse();
    }

    @Test
    void storefrontMarksAProductWithAvailableStock() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts(product(202L, ProductStatus.ACTIVE));
        stubInventories(inventory(202L, 12, 4));    // 8 available

        StorefrontResponse response = storefrontService.storefront(VENDOR_ID, null, null);

        assertThat(response.getProducts().getContent().get(0).isInStock()).isTrue();
    }

    @Test
    void storefrontMarksAProductWithNoAvailableStockAsOutOfStock() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts(product(203L, ProductStatus.ACTIVE));
        // No inventory row at all: unreachable through the write paths, and
        // the safe answer for an unknown level is "cannot be bought".
        stubInventories();

        StorefrontResponse response = storefrontService.storefront(VENDOR_ID, null, null);

        assertThat(response.getProducts().getContent().get(0).isInStock()).isFalse();
    }

    @Test
    void storefrontReadsAvailabilityForTheWholePageInOneQuery() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts(product(301L, ProductStatus.ACTIVE),
                product(302L, ProductStatus.ACTIVE),
                product(303L, ProductStatus.ACTIVE));
        stubInventories();

        storefrontService.storefront(VENDOR_ID, null, null);

        verify(inventoryRepository, times(1)).findByProductIdIn(List.of(301L, 302L, 303L));
    }

    // ------------------------------------------------------------------- hours

    @Test
    void storefrontMapsTheWeeklyHoursInWeekdayOrder() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts();

        VendorHours monday = VendorHours.builder()
                .id(1L).weekday(DayOfWeek.MONDAY)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0)).closed(false)
                .build();
        VendorHours sunday = VendorHours.builder()
                .id(2L).weekday(DayOfWeek.SUNDAY).closed(true)
                .build();
        when(vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(VENDOR_ID)).thenReturn(List.of(monday, sunday));

        StorefrontResponse response = storefrontService.storefront(VENDOR_ID, null, null);

        verify(vendorMapper).toHoursResponses(List.of(monday, sunday));
        assertThat(response.getVendor().getHours()).isEmpty();
    }

    // --------------------------------------------------------------- envelopes

    @Test
    void storefrontReportsTheStandardPageEnvelope() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts(product(401L, ProductStatus.ACTIVE));
        stubInventories();

        PageResponse<StorefrontProductResponse> products =
                storefrontService.storefront(VENDOR_ID, null, null).getProducts();

        assertThat(products.getPage()).isZero();
        assertThat(products.getSize()).isEqualTo(20);
        assertThat(products.getTotalElements()).isEqualTo(1);
        assertThat(products.getTotalPages()).isEqualTo(1);
        assertThat(products.isFirst()).isTrue();
        assertThat(products.isLast()).isTrue();
        assertThat(products.isEmpty()).isFalse();
    }

    @Test
    void storefrontReturnsAnEmptyProductPageWhenNoActiveProducts() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts();

        PageResponse<StorefrontProductResponse> products =
                storefrontService.storefront(VENDOR_ID, null, null).getProducts();

        assertThat(products.getContent()).isEmpty();
        assertThat(products.getTotalElements()).isZero();
        assertThat(products.getTotalPages()).isZero();
        assertThat(products.isEmpty()).isTrue();
    }

    @Test
    void storefrontClampsPageAndSizeLikeDiscoveryAndSearch() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts();

        storefrontService.storefront(VENDOR_ID, -5, 1000);

        verify(productRepository).findByVendorIdAndStatus(VENDOR_ID, ProductStatus.ACTIVE, expectedPageable(0, 100));
    }

    @Test
    void storefrontSortsByCreatedAtThenIdDescending() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts();

        storefrontService.storefront(VENDOR_ID, null, null);

        // The vendor catalog listing's order, so both screens agree; the id
        // tie-break keeps a row from moving between pages on a shared
        // created_at timestamp.
        verify(productRepository).findByVendorIdAndStatus(VENDOR_ID, ProductStatus.ACTIVE, expectedPageable(0, 20));
    }

    // ------------------------------------------------------------- profile map

    @Test
    void storefrontMapsThePublicProfile() {
        stubApprovedVendor(vendor(VendorProfile.Status.APPROVED));
        stubProducts();

        StorefrontVendorResponse vendor = storefrontService.storefront(VENDOR_ID, null, null).getVendor();

        assertThat(vendor.getId()).isEqualTo(VENDOR_ID);
        assertThat(vendor.getCity()).isEqualTo("Bengaluru");
        assertThat(vendor.getArea()).isEqualTo("Koramangala");
        assertThat(vendor.getPincode()).isEqualTo("560034");
        assertThat(vendor.getDeliveryRadiusKm()).isEqualByComparingTo("5.00");
        assertThat(vendor.getMinOrderAmount()).isEqualByComparingTo("199.00");
        assertThat(vendor.getBaseDeliveryFee()).isEqualByComparingTo("39.00");
        assertThat(vendor.getPerKmFee()).isEqualByComparingTo("12.00");
        assertThat(vendor.getFreeDeliveryAbove()).isEqualByComparingTo("799.00");
        assertThat(vendor.getPrepTimeMinutes()).isEqualTo(45);
        assertThat(vendor.getSlotDurationMinutes()).isEqualTo(60);
        assertThat(vendor.getMaxOrdersPerSlot()).isEqualTo(10);
        assertThat(vendor.getAcceptingOrders()).isTrue();
        assertThat(vendor.getAvgRating()).isNull();
        assertThat(vendor.getReviewCount()).isZero();
    }
}
