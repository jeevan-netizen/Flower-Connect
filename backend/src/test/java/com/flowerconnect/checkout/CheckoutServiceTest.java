package com.flowerconnect.checkout.service;

import com.flowerconnect.cart.domain.Cart;
import com.flowerconnect.cart.domain.CartItem;
import com.flowerconnect.cart.repository.CartItemRepository;
import com.flowerconnect.cart.repository.CartRepository;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.checkout.dto.CheckoutItemQuote;
import com.flowerconnect.checkout.dto.CheckoutQuoteRequest;
import com.flowerconnect.checkout.dto.CheckoutQuoteResponse;
import com.flowerconnect.checkout.dto.CheckoutShopQuote;
import com.flowerconnect.config.CheckoutProperties;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private ServiceLocationRepository serviceLocationRepository;
    @Mock
    private CheckoutProperties checkoutProperties;

    @InjectMocks
    private CheckoutService checkoutService;

    private User customer;
    private Cart cart;
    private VendorProfile vendor;
    private Product product;
    private Inventory inventory;
    private ServiceLocation serviceLocation;
    private AddressRepository.AddressWithLocation addressWithLocation;

    @BeforeEach
    void setUp() {
        Role customerRole = Role.builder().id(1L).name("CUSTOMER").build();
        customer = User.builder()
                .id(10L)
                .email("customer@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test Customer")
                .phone("+1234567890")
                .role(customerRole)
                .status(User.Status.ACTIVE)
                .build();

        cart = Cart.builder().id(1L).user(customer).build();

        serviceLocation = ServiceLocation.builder()
                .id(1L)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.9352"))
                .longitude(new BigDecimal("77.6245"))
                .build();

        vendor = VendorProfile.builder()
                .id(3L)
                .user(customer)
                .businessName("Test Florist")
                .status(VendorProfile.Status.APPROVED)
                .acceptingOrders(true)
                .serviceLocation(serviceLocation)
                .latitude(new BigDecimal("12.9352"))
                .longitude(new BigDecimal("77.6245"))
                .deliveryRadiusKm(new BigDecimal("10.00"))
                .minOrderAmount(new BigDecimal("200.00"))
                .baseDeliveryFee(new BigDecimal("30.00"))
                .perKmFee(new BigDecimal("5.00"))
                .freeDeliveryAbove(new BigDecimal("1000.00"))
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .build();

        product = Product.builder()
                .id(20L)
                .vendor(vendor)
                .name("Red Rose Bunch")
                .slug("red-rose-bunch")
                .basePrice(new BigDecimal("299.00"))
                .status(Product.ProductStatus.ACTIVE)
                .build();

        inventory = Inventory.builder()
                .id(1L)
                .product(product)
                .quantity(50)
                .reservedQuantity(0)
                .lowStockThreshold(10)
                .build();

        // Address projection mock
        addressWithLocation = mock(AddressRepository.AddressWithLocation.class);
        when(addressWithLocation.getId()).thenReturn(5L);
        when(addressWithLocation.getLatitude()).thenReturn(new BigDecimal("12.9500"));
        when(addressWithLocation.getLongitude()).thenReturn(new BigDecimal("77.6500"));
        when(addressWithLocation.getServiceLocation()).thenReturn(serviceLocation);

        when(checkoutProperties.getPlatformFee()).thenReturn(BigDecimal.ZERO);
    }

    // --------------------------------------------------------------------- generateQuote - empty cart

    @Test
    void generateQuoteRejectsEmptyCart() {
        when(userRepository.findByEmailWithRole("customer@test.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> checkoutService.generateQuote("customer@test.com", sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .hasMessageContaining("Cart is empty");
    }

    @Test
    void generateQuoteRejectsNoCart() {
        when(userRepository.findByEmailWithRole("customer@test.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.generateQuote("customer@test.com", sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .hasMessageContaining("Cart is empty");
    }

    // --------------------------------------------------------------------- generateQuote - address validation

    @Test
    void generateQuoteRejectsUnknownAddress() {
        when(userRepository.findByEmailWithRole("customer@test.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(sampleCartItem()));
        when(addressRepository.findByIdAndUserIdWithLocation(999L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.generateQuote("customer@test.com", sampleRequest(999L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND)
                .hasMessageContaining("Address not found");
    }

    // --------------------------------------------------------------------- generateQuote - vendor validation

    @Test
    void generateQuoteFlagsNonApprovedVendor() {
        VendorProfile pendingVendor = VendorProfile.builder()
                .id(3L).user(customer).businessName("Pending")
                .status(VendorProfile.Status.PENDING_APPROVAL).acceptingOrders(true)
                .serviceLocation(serviceLocation)
                .latitude(new BigDecimal("12.9352")).longitude(new BigDecimal("77.6245"))
                .deliveryRadiusKm(new BigDecimal("10.00")).minOrderAmount(new BigDecimal("200.00"))
                .baseDeliveryFee(new BigDecimal("30.00")).perKmFee(new BigDecimal("5.00"))
                .prepTimeMinutes(30).slotDurationMinutes(60).maxOrdersPerSlot(10).build();

        setupCommonMocks(pendingVendor);
        when(vendorProfileRepository.findById(3L)).thenReturn(Optional.of(pendingVendor));

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        assertThat(response.isHasErrors()).isTrue();
        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.getErrors()).contains("Vendor is not approved");
    }

    @Test
    void generateQuoteFlagsVendorNotAcceptingOrders() {
        vendor.setAcceptingOrders(false);
        setupCommonMocks(vendor);

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        assertThat(response.isHasErrors()).isTrue();
        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.getErrors()).contains("Vendor is not accepting orders");
    }

    // --------------------------------------------------------------------- generateQuote - distance and radius

    @Test
    void generateQuoteFlagsAddressOutsideRadius() {
        // Address far from vendor (50km away, vendor radius is 10km)
        AddressRepository.AddressWithLocation farAddress = mock(AddressRepository.AddressWithLocation.class);
        when(farAddress.getId()).thenReturn(5L);
        when(farAddress.getLatitude()).thenReturn(new BigDecimal("13.5000")); // ~60km north
        when(farAddress.getLongitude()).thenReturn(new BigDecimal("77.6245"));
        when(farAddress.getServiceLocation()).thenReturn(serviceLocation);

        setupCommonMocks(vendor);
        when(addressRepository.findByIdAndUserIdWithLocation(5L, 10L)).thenReturn(Optional.of(farAddress));

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        assertThat(response.isHasErrors()).isTrue();
        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.isWithinRadius()).isFalse();
        assertThat(shop.getErrors()).contains("Address is outside this vendor's delivery radius");
    }

    @Test
    void generateQuoteWithinRadius() {
        setupCommonMocks(vendor);

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.isWithinRadius()).isTrue();
        assertThat(shop.getDistanceKm()).isNotNull();
    }

    // --------------------------------------------------------------------- generateQuote - minimum order

    @Test
    void generateQuoteFlagsBelowMinimumOrder() {
        vendor.setMinOrderAmount(new BigDecimal("1000.00")); // subtotal will be 598.00
        setupCommonMocks(vendor);

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        assertThat(response.isHasErrors()).isTrue();
        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.isMinOrderMet()).isFalse();
        assertThat(shop.getErrors()).anyMatch(e -> e.contains("minimum order"));
    }

    @Test
    void generateQuoteMeetsMinimumOrder() {
        vendor.setMinOrderAmount(new BigDecimal("100.00"));
        setupCommonMocks(vendor);

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.isMinOrderMet()).isTrue();
    }

    // --------------------------------------------------------------------- generateQuote - slot validation (basic)

    @Test
    void generateQuoteFlagsInvalidSlotFormat() {
        setupCommonMocks(vendor);
        CheckoutQuoteRequest request = sampleRequest();
        request.getItems().get(0).setSlotStart("not-a-date");

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", request);

        assertThat(response.isHasErrors()).isTrue();
        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.isSlotAvailable()).isFalse();
        assertThat(shop.getErrors()).contains("Invalid slotStart format (expected ISO-8601)");
    }

    @Test
    void generateQuoteFlagsPastSlot() {
        setupCommonMocks(vendor);
        CheckoutQuoteRequest request = sampleRequest();
        request.getItems().get(0).setSlotStart("2020-01-01T10:00:00");

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", request);

        assertThat(response.isHasErrors()).isTrue();
        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.isSlotAvailable()).isFalse();
        assertThat(shop.getErrors()).contains("Selected slot is in the past");
    }

    // --------------------------------------------------------------------- generateQuote - price drift detection

    @Test
    void generateQuoteDetectsPriceIncrease() {
        // Product current price is higher than cart snapshot
        Product currentProduct = Product.builder()
                .id(20L).vendor(vendor).name("Red Rose Bunch").slug("red-rose-bunch")
                .basePrice(new BigDecimal("350.00")) // higher than snapshot 299.00
                .status(Product.ProductStatus.ACTIVE).build();

        setupCommonMocks(vendor);
        when(productRepository.findById(20L)).thenReturn(Optional.of(currentProduct));

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutShopQuote shop = response.getShops().get(0);
        CheckoutItemQuote item = shop.getItems().get(0);
        assertThat(item.isPriceChanged()).isTrue();
        assertThat(item.getPriceSnapshot()).isEqualByComparingTo(new BigDecimal("299.00"));
        assertThat(item.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(response.isHasPriceChanges()).isTrue();
        assertThat(shop.getWarnings()).contains("price_changed_since_add_to_cart");
    }

    @Test
    void generateQuoteDetectsPriceDecrease() {
        Product currentProduct = Product.builder()
                .id(20L).vendor(vendor).name("Red Rose Bunch").slug("red-rose-bunch")
                .basePrice(new BigDecimal("250.00")) // lower than snapshot
                .status(Product.ProductStatus.ACTIVE).build();

        setupCommonMocks(vendor);
        when(productRepository.findById(20L)).thenReturn(Optional.of(currentProduct));

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutItemQuote item = response.getShops().get(0).getItems().get(0);
        assertThat(item.isPriceChanged()).isTrue();
        assertThat(response.isHasPriceChanges()).isTrue();
    }

    @Test
    void generateQuoteNoPriceChangeWhenPricesMatch() {
        Product currentProduct = Product.builder()
                .id(20L).vendor(vendor).name("Red Rose Bunch").slug("red-rose-bunch")
                .basePrice(new BigDecimal("299.00")) // same as snapshot
                .status(Product.ProductStatus.ACTIVE).build();

        setupCommonMocks(vendor);
        when(productRepository.findById(20L)).thenReturn(Optional.of(currentProduct));

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutItemQuote item = response.getShops().get(0).getItems().get(0);
        assertThat(item.isPriceChanged()).isFalse();
        assertThat(response.isHasPriceChanges()).isFalse();
    }

    // --------------------------------------------------------------------- generateQuote - stock validation

    @Test
    void generateQuoteFlagsInsufficientStock() {
        inventory.setQuantity(1); // available = 1, but cart has 2
        setupCommonMocks(vendor);

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutItemQuote item = response.getShops().get(0).getItems().get(0);
        assertThat(item.isPurchasable()).isFalse();
        assertThat(item.getNotPurchasableReason()).isEqualTo("Insufficient stock");
    }

    @Test
    void generateQuoteFlagsInactiveProduct() {
        Product inactiveProduct = Product.builder()
                .id(20L).vendor(vendor).name("Red Rose Bunch").slug("red-rose-bunch")
                .basePrice(new BigDecimal("299.00"))
                .status(Product.ProductStatus.INACTIVE).build();

        setupCommonMocks(vendor);
        when(productRepository.findById(20L)).thenReturn(Optional.of(inactiveProduct));

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutItemQuote item = response.getShops().get(0).getItems().get(0);
        assertThat(item.isPurchasable()).isFalse();
        assertThat(item.getNotPurchasableReason()).isEqualTo("Product is no longer available");
    }

    // --------------------------------------------------------------------- generateQuote - totals calculation

    @Test
    void generateQuoteCalculatesCorrectSubtotalAndDeliveryFee() {
        setupCommonMocks(vendor);

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutShopQuote shop = response.getShops().get(0);
        // 2 × 299.00 = 598.00 subtotal
        assertThat(shop.getSubtotal()).isEqualByComparingTo(new BigDecimal("598.00"));
        // Delivery fee = base (30) + perKm (5) × distance
        assertThat(shop.getDeliveryFee()).isNotNull();
        assertThat(shop.getDeliveryFee().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        // Total = subtotal + deliveryFee + platformFee (0)
        assertThat(shop.getTotal()).isEqualByComparingTo(shop.getSubtotal().add(shop.getDeliveryFee()));
    }

    @Test
    void generateQuoteAppliesFreeDeliveryAboveThreshold() {
        vendor.setFreeDeliveryAbove(new BigDecimal("500.00"));
        vendor.setMinOrderAmount(new BigDecimal("100.00"));
        setupCommonMocks(vendor);

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void generateQuoteUsesPlatformFeeFromConfig() {
        when(checkoutProperties.getPlatformFee()).thenReturn(new BigDecimal("10.00"));
        setupCommonMocks(vendor);

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.getPlatformFee()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(shop.getTotal()).isEqualByComparingTo(
                shop.getSubtotal().add(shop.getDeliveryFee()).add(new BigDecimal("10.00")).setScale(2, RoundingMode.HALF_UP)
        );
    }

    @Test
    void generateQuoteUsesPlatformFeeFromRequestOverride() {
        when(checkoutProperties.getPlatformFee()).thenReturn(new BigDecimal("10.00")); // config says 10
        setupCommonMocks(vendor);

        CheckoutQuoteRequest request = sampleRequest();
        request.setPlatformFee(new BigDecimal("5.00")); // request overrides to 5

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", request);

        CheckoutShopQuote shop = response.getShops().get(0);
        assertThat(shop.getPlatformFee()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(shop.getTotal()).isEqualByComparingTo(
                shop.getSubtotal().add(shop.getDeliveryFee()).add(new BigDecimal("5.00")).setScale(2, RoundingMode.HALF_UP)
        );
    }

    @Test
    void generateQuoteGrandTotalIsSumOfShopTotals() {
        // Add a second vendor
        VendorProfile vendor2 = VendorProfile.builder()
                .id(4L).user(customer).businessName("Second Florist")
                .status(VendorProfile.Status.APPROVED).acceptingOrders(true)
                .serviceLocation(serviceLocation)
                .latitude(new BigDecimal("12.9400")).longitude(new BigDecimal("77.6300"))
                .deliveryRadiusKm(new BigDecimal("10.00")).minOrderAmount(new BigDecimal("100.00"))
                .baseDeliveryFee(new BigDecimal("25.00")).perKmFee(new BigDecimal("4.00"))
                .prepTimeMinutes(30).slotDurationMinutes(60).maxOrdersPerSlot(10).build();

        CartItem item2 = CartItem.builder()
                .id(200L).cart(cart).product(product).vendorId(4L)
                .quantity(1).priceSnapshot(new BigDecimal("150.00")).build();

        setupCommonMocks(vendor);
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(sampleCartItem(), item2));
        when(vendorProfileRepository.findById(4L)).thenReturn(Optional.of(vendor2));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(20L)).thenReturn(Optional.of(inventory));

        CheckoutQuoteRequest request = sampleRequest();
        request.getItems().add(CheckoutQuoteRequest.QuoteItem.builder().vendorId(4L).slotStart("2026-10-20T10:00:00").build());

        CheckoutQuoteResponse response = checkoutService.generateQuote("customer@test.com", request);

        assertThat(response.getShops()).hasSize(2);
        BigDecimal expectedTotal = response.getShops().stream()
                .map(CheckoutShopQuote::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(response.getTotal()).isEqualByComparingTo(expectedTotal.setScale(2, RoundingMode.HALF_UP));
    }

    // --------------------------------------------------------------------- generateQuote - request validation

    @Test
    void generateQuoteRejectsMismatchedVendorIds() {
        setupCommonMocks(vendor);
        CheckoutQuoteRequest request = sampleRequest();
        request.getItems().get(0).setVendorId(999L); // not in cart

        assertThatThrownBy(() -> checkoutService.generateQuote("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .hasMessageContaining("Slot selection must include exactly one entry per vendor");
    }

    // --------------------------------------------------------------------- helpers

    private void setupCommonMocks(VendorProfile v) {
        when(userRepository.findByEmailWithRole("customer@test.com")).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(sampleCartItem()));
        when(addressRepository.findByIdAndUserIdWithLocation(5L, 10L)).thenReturn(Optional.of(addressWithLocation));
        when(vendorProfileRepository.findById(v.getId())).thenReturn(Optional.of(v));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(20L)).thenReturn(Optional.of(inventory));
    }

    private CartItem sampleCartItem() {
        return CartItem.builder()
                .id(100L).cart(cart).product(product).vendorId(3L)
                .quantity(2).priceSnapshot(new BigDecimal("299.00")).build();
    }

    private CheckoutQuoteRequest sampleRequest() {
        return sampleRequest(5L);
    }

    private CheckoutQuoteRequest sampleRequest(Long addressId) {
        return CheckoutQuoteRequest.builder()
                .addressId(addressId)
                .items(new ArrayList<>(List.of(CheckoutQuoteRequest.QuoteItem.builder()
                        .vendorId(3L)
                        .slotStart("2026-10-20T10:00:00")
                        .build())))
                .platformFee(null)
                .build();
    }
}