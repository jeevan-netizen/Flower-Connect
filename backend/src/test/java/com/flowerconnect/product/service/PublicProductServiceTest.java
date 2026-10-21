package com.flowerconnect.product.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.geo.service.GeoService;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.search.dto.PublicProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProductServiceTest {

    @Mock
    private ServiceLocationRepository serviceLocationRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private PublicProductService publicProductService;

    private ServiceLocation origin;

    @BeforeEach
    void setUp() {
        publicProductService = new PublicProductService(
                serviceLocationRepository, productRepository,
                inventoryRepository, productImageRepository, new GeoService());

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
    void detailThrowsWhenLocationMissing() {
        assertThatThrownBy(() -> publicProductService.productDetail(100L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void detailThrowsWhenLocationUnknown() {
        when(serviceLocationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publicProductService.productDetail(100L, 999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void detailThrowsWhenProductUnknown() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publicProductService.productDetail(999L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void detailThrowsWhenProductInactive() {
        VendorProfile vendor = vendor(1L, "Florist", VendorProfile.Status.APPROVED, true);
        vendor.setServiceLocation(origin);
        Product inactive = product(100L, vendor, "Inactive", Product.ProductStatus.DRAFT);
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(productRepository.findById(100L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> publicProductService.productDetail(100L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void detailThrowsWhenOutOfStock() {
        VendorProfile vendor = vendor(1L, "Florist", VendorProfile.Status.APPROVED, true);
        vendor.setServiceLocation(origin);
        Product active = product(100L, vendor, "Active", Product.ProductStatus.ACTIVE);
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(productRepository.findById(100L)).thenReturn(Optional.of(active));
        when(inventoryRepository.findByProductId(100L))
                .thenReturn(Optional.of(Inventory.builder().quantity(0).reservedQuantity(0).build()));

        assertThatThrownBy(() -> publicProductService.productDetail(100L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void detailThrowsWhenVendorNotApproved() {
        VendorProfile pending = vendor(1L, "Pending", VendorProfile.Status.PENDING_APPROVAL, true);
        pending.setServiceLocation(origin);
        Product active = product(100L, pending, "Active", Product.ProductStatus.ACTIVE);
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(productRepository.findById(100L)).thenReturn(Optional.of(active));
        when(inventoryRepository.findByProductId(100L))
                .thenReturn(Optional.of(Inventory.builder().quantity(10).reservedQuantity(0).build()));

        assertThatThrownBy(() -> publicProductService.productDetail(100L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void detailThrowsWhenOutsideRadius() {
        VendorProfile vendor = vendor(1L, "Florist", VendorProfile.Status.APPROVED, true);
        // 1 degree away (~111 km), radius 5 km -> outside
        vendor.setLatitude(new BigDecimal("13.9352"));
        vendor.setLongitude(new BigDecimal("77.6145"));
        vendor.setDeliveryRadiusKm(new BigDecimal("5.00"));
        vendor.setServiceLocation(origin);
        Product active = product(100L, vendor, "Active", Product.ProductStatus.ACTIVE);
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(productRepository.findById(100L)).thenReturn(Optional.of(active));
        when(inventoryRepository.findByProductId(100L))
                .thenReturn(Optional.of(Inventory.builder().quantity(10).reservedQuantity(0).build()));

        assertThatThrownBy(() -> publicProductService.productDetail(100L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void detailReturnsProductWhenEligible() {
        VendorProfile vendor = vendor(1L, "Florist", VendorProfile.Status.APPROVED, true);
        vendor.setServiceLocation(origin);
        Product active = product(100L, vendor, "Roses", Product.ProductStatus.ACTIVE);
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(productRepository.findById(100L)).thenReturn(Optional.of(active));
        when(inventoryRepository.findByProductId(100L))
                .thenReturn(Optional.of(Inventory.builder().quantity(10).reservedQuantity(2).build()));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of());

        PublicProductResponse response = publicProductService.productDetail(100L, 1L);

        assertThat(response.getName()).isEqualTo("Roses");
        assertThat(response.getAvailable()).isEqualTo(8);
        assertThat(response.getImages()).isEmpty();
    }

    // -------- helpers --------

    private VendorProfile vendor(Long id, String name, VendorProfile.Status status, boolean accepting) {
        return VendorProfile.builder()
                .id(id)
                .businessName(name)
                .description("desc")
                .latitude(new BigDecimal("12.9352"))
                .longitude(new BigDecimal("77.6145"))
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .minOrderAmount(new BigDecimal("100.00"))
                .baseDeliveryFee(new BigDecimal("10.00"))
                .perKmFee(new BigDecimal("2.00"))
                .prepTimeMinutes(30)
                .acceptingOrders(accepting)
                .status(status)
                .build();
    }

    private Product product(Long id, VendorProfile vendor, String name, Product.ProductStatus status) {
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
                .basePrice(new BigDecimal("100.00"))
                .status(status)
                .build();
    }
}
