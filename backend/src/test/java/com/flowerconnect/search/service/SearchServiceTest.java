package com.flowerconnect.search.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.service.GeoService;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.search.dto.PublicProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ServiceLocationRepository serviceLocationRepository;

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private SearchService searchService;

    private ServiceLocation origin;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(
                serviceLocationRepository, vendorProfileRepository,
                productRepository, inventoryRepository, productImageRepository,
                new GeoService());

        origin = ServiceLocation.builder()
                .id(1L)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.9352"))
                .longitude(new BigDecimal("77.6145"))
                .build();
    }

    @Test
    void searchThrowsWhenLocationIdMissing() {
        assertThatThrownBy(() -> searchService.search(null, null, null, null, null, null, null, 0, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void searchThrowsWhenLocationUnknown() {
        when(serviceLocationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.search(999L, null, null, null, null, null, null, 0, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void searchReturnsEmptyPageWhenNoApprovedVendors() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(null);

        PageResponse<PublicProductResponse> response = searchService.search(1L, null, null, null, null, null, null, 0, 20);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
    }

    @Test
    void searchFiltersByRadiusAndSortsByDistance() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));

        VendorProfile nearVendor = vendor(1L, "Near Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"), new BigDecimal("5.00"), true);
        VendorProfile farVendor = vendor(2L, "Far Florist",
                new BigDecimal("12.9500"), new BigDecimal("77.6300"), new BigDecimal("5.00"), true);
        VendorProfile outOfRange = vendor(3L, "Out of Range",
                new BigDecimal("12.9500"), new BigDecimal("77.6300"), new BigDecimal("1.00"), true);

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(nearVendor, farVendor, outOfRange));

        Product nearProduct = product(100L, nearVendor, "Near Roses", new BigDecimal("100.00"));
        Product farProduct = product(200L, farVendor, "Far Lilies", new BigDecimal("200.00"));
        Product outOfRangeProduct = product(300L, outOfRange, "OOR Tulips", new BigDecimal("300.00"));

        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any()))
                .thenReturn(List.of(nearProduct, farProduct, outOfRangeProduct));
        when(inventoryRepository.findAllByProductIdIn(any())).thenReturn(List.of(
                inventory(100L, 10, 0), inventory(200L, 10, 0), inventory(300L, 10, 0)));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of());
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(200L)).thenReturn(List.of());

        PageResponse<PublicProductResponse> response = searchService.search(1L, null, null, null, null, "distance", null, 0, 20);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getName()).isEqualTo("Near Roses");
        assertThat(response.getContent().get(1).getName()).isEqualTo("Far Lilies");
    }

    @Test
    void searchExcludesInactiveProducts() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));

        VendorProfile vendor = vendor(1L, "Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"), new BigDecimal("5.00"), true);
        Product active = product(100L, vendor, "Active", new BigDecimal("100.00"));
        Product draft = product(200L, vendor, "Draft", new BigDecimal("100.00"));
        draft.setStatus(Product.ProductStatus.DRAFT);

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor));
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any()))
                .thenReturn(List.of(active, draft));
        when(inventoryRepository.findAllByProductIdIn(any())).thenReturn(List.of(
                inventory(100L, 10, 0), inventory(200L, 10, 0)));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of());
        PageResponse<PublicProductResponse> response = searchService.search(1L, null, null, null, null, null, null, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getName()).isEqualTo("Active");
    }

    @Test
    void searchExcludesOutOfStockProducts() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));

        VendorProfile vendor = vendor(1L, "Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"), new BigDecimal("5.00"), true);
        Product inStock = product(100L, vendor, "In Stock", new BigDecimal("100.00"));
        Product outOfStock = product(200L, vendor, "Out of Stock", new BigDecimal("100.00"));

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor));
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any()))
                .thenReturn(List.of(inStock, outOfStock));
        when(inventoryRepository.findAllByProductIdIn(any())).thenReturn(List.of(
                inventory(100L, 10, 0), inventory(200L, 0, 0)));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of());

        PageResponse<PublicProductResponse> response = searchService.search(1L, null, null, null, null, null, null, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getName()).isEqualTo("In Stock");
    }

    @Test
    void searchPaginatesAfterSorting() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(new BigDecimal("10.00"));

        VendorProfile vendor = vendor(1L, "Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"), new BigDecimal("5.00"), true);

        List<Product> products = List.of(
                product(100L, vendor, "P1", new BigDecimal("100.00")),
                product(200L, vendor, "P2", new BigDecimal("100.00")),
                product(300L, vendor, "P3", new BigDecimal("100.00")),
                product(400L, vendor, "P4", new BigDecimal("100.00")),
                product(500L, vendor, "P5", new BigDecimal("100.00")));

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor));
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any())).thenReturn(products);
        when(inventoryRepository.findAllByProductIdIn(any())).thenReturn(List.of(
                inventory(100L, 10, 0), inventory(200L, 10, 0),
                inventory(300L, 10, 0), inventory(400L, 10, 0), inventory(500L, 10, 0)));
        // Only the two products on page 0 are rendered, so only their image
        // lookups happen.
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of());
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(200L)).thenReturn(List.of());

        PageResponse<PublicProductResponse> page0 = searchService.search(1L, null, null, null, null, "name", null, 0, 2);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.isFirst()).isTrue();
        assertThat(page0.isLast()).isFalse();
    }

    @Test
    void searchRejectsInvalidSort() throws Exception {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));

        assertThatThrownBy(() -> searchService.search(1L, null, null, null, null, "bogus", null, 0, 20))
                .isInstanceOf(BusinessException.class);
    }

    // -------- helpers --------

    private VendorProfile vendor(Long id, String name, BigDecimal lat, BigDecimal lng,
                                 BigDecimal radius, boolean accepting) {
        ServiceLocation location = ServiceLocation.builder()
                .id(10L + id)
                .city("Bengaluru")
                .area("Area " + id)
                .pincode("5600" + String.format("%02d", id))
                .latitude(lat)
                .longitude(lng)
                .build();
        return VendorProfile.builder()
                .id(id)
                .businessName(name)
                .description("desc")
                .serviceLocation(location)
                .latitude(lat)
                .longitude(lng)
                .deliveryRadiusKm(radius)
                .minOrderAmount(new BigDecimal("100.00"))
                .baseDeliveryFee(new BigDecimal("10.00"))
                .perKmFee(new BigDecimal("2.00"))
                .prepTimeMinutes(30)
                .acceptingOrders(accepting)
                .status(VendorProfile.Status.APPROVED)
                .build();
    }

    private Product product(Long id, VendorProfile vendor, String name, BigDecimal price) {
        Category category = Category.builder()
                .id(1L)
                .name("Flowers")
                .slug("flowers")
                .active(true)
                .build();
        return Product.builder()
                .id(id)
                .vendor(vendor)
                .category(category)
                .name(name)
                .slug(name.toLowerCase().replace(' ', '-'))
                .description("desc")
                .basePrice(price)
                .status(Product.ProductStatus.ACTIVE)
                .build();
    }

    private Inventory inventory(Long productId, int quantity, int reserved) {
        return Inventory.builder()
                .id(productId)
                .product(Product.builder().id(productId).build())
                .quantity(quantity)
                .reservedQuantity(reserved)
                .lowStockThreshold(0)
                .build();
    }
}
