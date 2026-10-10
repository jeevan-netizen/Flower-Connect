package com.flowerconnect.search.service;

import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.search.dto.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @Mock
    private ServiceLocationRepository serviceLocationRepository;

    private SearchService searchService;

    private ServiceLocation originLocation;
    private Category activeCategory;
    private Category inactiveCategory;
    private Category childCategory;
    private VendorProfile approvedVendor;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(productRepository, categoryRepository,
                vendorProfileRepository, serviceLocationRepository);

        originLocation = ServiceLocation.builder()
                .id(1L)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.9352"))
                .longitude(new BigDecimal("77.6145"))
                .build();

        activeCategory = Category.builder()
                .id(10L)
                .name("Roses")
                .slug("roses")
                .active(true)
                .build();

        inactiveCategory = Category.builder()
                .id(20L)
                .name("Bouquets")
                .slug("bouquets")
                .active(false)
                .build();

        childCategory = Category.builder()
                .id(30L)
                .name("Hybrid Tea")
                .slug("hybrid-tea")
                .active(true)
                .parent(activeCategory)
                .build();

        approvedVendor = createVendor(1L, "Test Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"),
                new BigDecimal("5.00"));
    }

    @Test
    void searchThrowsWhenLocationIdNull() {
        assertThatThrownBy(() -> searchService.search(null, null, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("locationId is required");
    }

    @Test
    void searchThrowsWhenLocationUnknown() {
        when(serviceLocationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.search(999L, null, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unknown service location")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void searchThrowsWhenPriceMinExceedsPriceMax() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));

        assertThatThrownBy(() -> searchService.search(1L, null, null,
                new BigDecimal("100.00"), new BigDecimal("50.00"), null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("priceMin must not exceed priceMax")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void searchThrowsWhenSortInvalid() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));

        assertThatThrownBy(() -> searchService.search(1L, null, null, null, null, "invalid_sort", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid sort value")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void searchAcceptsValidSortValues() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String[] validSorts = {"distance", "price_asc", "price_desc", "name", "DISTANCE", "Price_Asc", " price_asc "};
        for (String sort : validSorts) {
            PageResponse<SearchResponse> response = searchService.search(1L, null, null, null, null, sort, null, null, null);
            assertThat(response.getContent()).isEmpty();
        }
    }

    @Test
    void searchDefaultsSortToDistance() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        PageResponse<SearchResponse> response = searchService.search(1L, null, null, null, null, null, null, null, null);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void searchReturnsEmptyWhenNoVendorsInRadius() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(null);

        PageResponse<SearchResponse> response = searchService.search(1L, null, null, null, null, null, null, null, null);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
    }

    @Test
    void searchResolvesCategorySubtreeActiveDescendants() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(approvedVendor));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(activeCategory));
        when(categoryRepository.findByParentId(10L)).thenReturn(List.of(childCategory));
        when(categoryRepository.findByParentId(30L)).thenReturn(Collections.emptyList());
        when(productRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        searchService.search(1L, null, 10L, null, null, null, null, null, null);

        verify(categoryRepository).findById(10L);
        verify(categoryRepository).findByParentId(10L);
        verify(categoryRepository).findByParentId(30L);
    }

    @Test
    void searchThrowsWhenCategoryUnknown() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.search(1L, null, 999L, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unknown category")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void searchThrowsWhenCategoryInactive() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(inactiveCategory));

        assertThatThrownBy(() -> searchService.search(1L, null, 20L, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Category is inactive")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void searchStopsDescendingAtAnInactiveCategory() {
        // The actual pruning rule: an ACTIVE root with an INACTIVE intermediate
        // node must exclude that node's children even when the children are
        // themselves active, because the storefront no longer lists the
        // intermediate (D-17/D-35). The inactive-root case (a total refusal)
        // is covered by searchThrowsWhenCategoryInactive.
        Category activeRoot = Category.builder()
                .id(50L)
                .name("Active Root")
                .slug("active-root")
                .active(true)
                .build();
        Category inactiveMiddle = Category.builder()
                .id(51L)
                .name("Inactive Middle")
                .slug("inactive-middle")
                .active(false)
                .parent(activeRoot)
                .build();

        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(categoryRepository.findById(50L)).thenReturn(Optional.of(activeRoot));
        when(categoryRepository.findByParentId(50L)).thenReturn(List.of(inactiveMiddle));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(approvedVendor));
        when(productRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        // The active root resolves without error — searching under it is legal
        searchService.search(1L, null, 50L, null, null, null, null, null, null);

        // The walk enters the root, sees the inactive middle, and must not
        // descend: querying its children would admit products the storefront
        // does not publish.
        verify(categoryRepository).findById(50L);
        verify(categoryRepository).findByParentId(50L);
        verify(categoryRepository, never()).findByParentId(51L);
    }

    @Test
    void searchFiltersByQueryString() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(approvedVendor));
        when(productRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        searchService.search(1L, "rose", null, null, null, null, null, null, null);

        verify(productRepository).findAll(any(Specification.class));
    }

    @Test
    void searchFiltersByPriceMinAndMax() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(approvedVendor));
        when(productRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        searchService.search(1L, null, null,
                new BigDecimal("50.00"), new BigDecimal("200.00"), null, null, null, null);

        verify(productRepository).findAll(any(Specification.class));
    }

    @Test
    void searchFiltersByVendorId() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(approvedVendor));
        when(productRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        searchService.search(1L, null, null, null, null, null, 5L, null, null);

        verify(productRepository).findAll(any(Specification.class));
    }

    @Test
    void searchComputesDistanceAndFiltersByRadius() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));

        VendorProfile nearVendor = createVendor(1L, "Near Florist",
                new BigDecimal("12.9352"), new BigDecimal("77.6145"), // Same as origin
                new BigDecimal("5.00"));
        VendorProfile farVendor = createVendor(2L, "Far Florist",
                new BigDecimal("13.0000"), new BigDecimal("77.7000"), // ~10km away
                new BigDecimal("1.00")); // radius 1km, won't reach

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(nearVendor, farVendor));

        Product nearProduct = createProduct(200L, "Near Product", activeCategory, nearVendor, ProductStatus.ACTIVE);
        Product farProduct = createProduct(201L, "Far Product", activeCategory, farVendor, ProductStatus.ACTIVE);
        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(nearProduct, farProduct));

        PageResponse<SearchResponse> response = searchService.search(1L, null, null, null, null, "distance", null, null, null);

        // The far product is beyond the far vendor's 1 km delivery radius, so the
        // service refuses it after the Haversine refinement.
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getDistanceKm()).isEqualByComparingTo("0.00");
    }

    @Test
    void searchSortsByPriceAsc() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));

        VendorProfile vendor = createVendor(1L, "Florist",
                new BigDecimal("12.9352"), new BigDecimal("77.6145"),
                new BigDecimal("10.00"));

        Product p1 = createProduct(200L, "Product B", activeCategory, vendor, ProductStatus.ACTIVE);
        p1.setBasePrice(new BigDecimal("200.00"));
        Product p2 = createProduct(201L, "Product A", activeCategory, vendor, ProductStatus.ACTIVE);
        p2.setBasePrice(new BigDecimal("100.00"));

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor));
        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(p1, p2));

        PageResponse<SearchResponse> response = searchService.search(1L, null, null, null, null, "price_asc", null, null, null);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getBasePrice()).isEqualByComparingTo("100.00");
        assertThat(response.getContent().get(1).getBasePrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void searchSortsByPriceDesc() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));

        VendorProfile vendor = createVendor(1L, "Florist",
                new BigDecimal("12.9352"), new BigDecimal("77.6145"),
                new BigDecimal("10.00"));

        Product p1 = createProduct(200L, "Product B", activeCategory, vendor, ProductStatus.ACTIVE);
        p1.setBasePrice(new BigDecimal("200.00"));
        Product p2 = createProduct(201L, "Product A", activeCategory, vendor, ProductStatus.ACTIVE);
        p2.setBasePrice(new BigDecimal("100.00"));

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor));
        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(p1, p2));

        PageResponse<SearchResponse> response = searchService.search(1L, null, null, null, null, "price_desc", null, null, null);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getBasePrice()).isEqualByComparingTo("200.00");
        assertThat(response.getContent().get(1).getBasePrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void searchTieBreaksEqualSortKeysByProductId() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(approvedVendor));

        // Equal base price, equal name, and one shared vendor (therefore equal
        // distance): the id is the only distinguishing key. The candidates are
        // returned in DESCENDING id order so that an unstable sort, or one that
        // reverses the tie-breaker along with the key, would keep that order.
        Product higher = createProduct(201L, "Same Name", activeCategory, approvedVendor, ProductStatus.ACTIVE);
        Product lower = createProduct(200L, "Same Name", activeCategory, approvedVendor, ProductStatus.ACTIVE);
        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(higher, lower));

        for (String sort : List.of("price_asc", "price_desc", "name", "distance")) {
            PageResponse<SearchResponse> response =
                    searchService.search(1L, null, null, null, null, sort, null, 0, 10);

            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getContent().get(0).getId()).isEqualTo(200L);
            assertThat(response.getContent().get(1).getId()).isEqualTo(201L);
        }
    }

    @Test
    void searchSortsByName() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));

        VendorProfile vendor = createVendor(1L, "Florist",
                new BigDecimal("12.9352"), new BigDecimal("77.6145"),
                new BigDecimal("10.00"));

        Product p1 = createProduct(200L, "Zebra Roses", activeCategory, vendor, ProductStatus.ACTIVE);
        Product p2 = createProduct(201L, "Apple Blooms", activeCategory, vendor, ProductStatus.ACTIVE);

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor));
        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(p1, p2));

        PageResponse<SearchResponse> response = searchService.search(1L, null, null, null, null, "name", null, null, null);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getName()).isEqualTo("Apple Blooms");
        assertThat(response.getContent().get(1).getName()).isEqualTo("Zebra Roses");
    }

    @Test
    void searchPaginatesCorrectly() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));

        VendorProfile vendor = createVendor(1L, "Florist",
                new BigDecimal("12.9352"), new BigDecimal("77.6145"),
                new BigDecimal("10.00"));

        List<Product> products = List.of(
                createProduct(200L, "Product 1", activeCategory, vendor, ProductStatus.ACTIVE),
                createProduct(201L, "Product 2", activeCategory, vendor, ProductStatus.ACTIVE),
                createProduct(202L, "Product 3", activeCategory, vendor, ProductStatus.ACTIVE),
                createProduct(203L, "Product 4", activeCategory, vendor, ProductStatus.ACTIVE),
                createProduct(204L, "Product 5", activeCategory, vendor, ProductStatus.ACTIVE)
        );

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor));
        when(productRepository.findAll(any(Specification.class))).thenReturn(products);

        PageResponse<SearchResponse> page0 = searchService.search(1L, null, null, null, null, "distance", null, 0, 2);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.isFirst()).isTrue();
        assertThat(page0.isLast()).isFalse();

        PageResponse<SearchResponse> page1 = searchService.search(1L, null, null, null, null, "distance", null, 1, 2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.isFirst()).isFalse();
        assertThat(page1.isLast()).isFalse();

        PageResponse<SearchResponse> page2 = searchService.search(1L, null, null, null, null, "distance", null, 2, 2);
        assertThat(page2.getContent()).hasSize(1);
        assertThat(page2.isLast()).isTrue();
    }

    @Test
    void searchClampsPageAndSize() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        PageResponse<SearchResponse> response = searchService.search(1L, null, null, null, null, null, null, -1, 200);

        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(100);
    }

    // The status/stock eligibility predicates (ACTIVE, available stock > 0) are
    // Criteria predicates over real SQL, so — following the D-23 precedent —
    // they are asserted by SearchControllerIntegrationTest against real MySQL,
    // not here: a mocked repository cannot demonstrate what the query does.

    private VendorProfile createVendor(Long id, String name,
                                       BigDecimal lat, BigDecimal lng,
                                       BigDecimal radius) {
        ServiceLocation location = ServiceLocation.builder()
                .id(10L + id)
                .city("Bengaluru")
                .area("Area " + id)
                .pincode("5600" + String.format("%02d", id))
                .latitude(lat)
                .longitude(lng)
                .build();

        User user = User.builder()
                .id(100L + id)
                .email("vendor" + id + "@test.com")
                .build();

        return VendorProfile.builder()
                .id(id)
                .businessName(name)
                .description("Description for " + name)
                .serviceLocation(location)
                .latitude(lat)
                .longitude(lng)
                .deliveryRadiusKm(radius)
                .minOrderAmount(new BigDecimal("100.00"))
                .baseDeliveryFee(new BigDecimal("30.00"))
                .perKmFee(new BigDecimal("10.00"))
                .freeDeliveryAbove(new BigDecimal("500.00"))
                .prepTimeMinutes(30)
                .avgRating(new BigDecimal("4.50"))
                .reviewCount(10)
                .acceptingOrders(true)
                .user(user)
                .build();
    }

    private Product createProduct(Long id, String name, Category category,
                                  VendorProfile vendor, ProductStatus status) {
        return Product.builder()
                .id(id)
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-") + "-" + id)
                .description("Description for " + name)
                .basePrice(new BigDecimal("150.00"))
                .status(status)
                .category(category)
                .vendor(vendor)
                .build();
    }
}