package com.flowerconnect.storefront.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.storefront.dto.StorefrontResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorefrontServiceTest {

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @Mock
    private VendorHoursRepository vendorHoursRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private StorefrontService storefrontService;

    @BeforeEach
    void setUp() {
        storefrontService = new StorefrontService(
                vendorProfileRepository, vendorHoursRepository,
                productRepository, inventoryRepository, productImageRepository);
    }

    @Test
    void storefrontThrowsWhenVendorUnknown() {
        when(vendorProfileRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storefrontService.storefront(999L, 0, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void storefrontThrowsWhenVendorNotApproved() {
        VendorProfile pending = vendor(1L, "Pending Florist", VendorProfile.Status.PENDING_APPROVAL, true);
        when(vendorProfileRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> storefrontService.storefront(1L, 0, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void storefrontThrowsWhenVendorNotAccepting() {
        VendorProfile notAccepting = vendor(1L, "Closed Florist", VendorProfile.Status.APPROVED, false);
        when(vendorProfileRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(notAccepting));

        assertThatThrownBy(() -> storefrontService.storefront(1L, 0, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void storefrontReturnsProfileAndProducts() {
        VendorProfile vendor = vendor(1L, "Approved Florist", VendorProfile.Status.APPROVED, true);
        vendor.setServiceLocation(loc(1L, "Bengaluru", "Koramangala", "560034",
                new BigDecimal("12.9352"), new BigDecimal("77.6145")));
        when(vendorProfileRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(vendor));
        when(vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(1L)).thenReturn(List.of(
                hours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), false)));

        Product p1 = product(100L, vendor, "Roses", new BigDecimal("100.00"), Product.ProductStatus.ACTIVE);
        Product p2 = product(200L, vendor, "Draft Item", new BigDecimal("50.00"), Product.ProductStatus.DRAFT);
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any()))
                .thenReturn(List.of(p1, p2));
        when(inventoryRepository.findAllByProductIdIn(any())).thenReturn(List.of(
                inventory(100L, 10, 0), inventory(200L, 10, 0)));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of());

        StorefrontResponse response = storefrontService.storefront(1L, 0, 20);

        assertThat(response.getBusinessName()).isEqualTo("Approved Florist");
        assertThat(response.getAcceptingOrders()).isTrue();
        assertThat(response.getHours()).hasSize(1);
        assertThat(response.getHours().get(0).getWeekday()).isEqualTo("MONDAY");
        assertThat(response.getProducts().getContent()).hasSize(1);
        assertThat(response.getProducts().getContent().get(0).getName()).isEqualTo("Roses");
    }

    @Test
    void storefrontExcludesOutOfStockAndInactive() {
        VendorProfile vendor = vendor(1L, "Approved Florist", VendorProfile.Status.APPROVED, true);
        vendor.setServiceLocation(loc(1L, "Bengaluru", "Koramangala", "560034",
                new BigDecimal("12.9352"), new BigDecimal("77.6145")));
        when(vendorProfileRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(vendor));
        when(vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(1L)).thenReturn(List.of());

        Product inStock = product(100L, vendor, "In Stock", new BigDecimal("100.00"), Product.ProductStatus.ACTIVE);
        Product outOfStock = product(200L, vendor, "Out of Stock", new BigDecimal("50.00"), Product.ProductStatus.ACTIVE);
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any()))
                .thenReturn(List.of(inStock, outOfStock));
        when(inventoryRepository.findAllByProductIdIn(any())).thenReturn(List.of(
                inventory(100L, 10, 0), inventory(200L, 0, 0)));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of());

        StorefrontResponse response = storefrontService.storefront(1L, 0, 20);

        assertThat(response.getProducts().getContent()).hasSize(1);
        assertThat(response.getProducts().getContent().get(0).getName()).isEqualTo("In Stock");
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

    private ServiceLocation loc(Long id, String city, String area, String pincode,
                                BigDecimal lat, BigDecimal lng) {
        return ServiceLocation.builder()
                .id(id)
                .city(city)
                .area(area)
                .pincode(pincode)
                .latitude(lat)
                .longitude(lng)
                .build();
    }
    private Product product(Long id, VendorProfile vendor, String name, BigDecimal price,
                            Product.ProductStatus status) {
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
                .status(status)
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

    private VendorHours hours(DayOfWeek weekday, LocalTime open, LocalTime close, boolean closed) {
        return VendorHours.builder()
                .id(1L)
                .weekday(weekday)
                .openTime(open)
                .closeTime(close)
                .closed(closed)
                .build();
    }
}
