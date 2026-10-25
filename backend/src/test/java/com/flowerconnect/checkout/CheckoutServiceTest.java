package com.flowerconnect.checkout.service;

import com.flowerconnect.cart.domain.Cart;
import com.flowerconnect.cart.domain.CartItem;
import com.flowerconnect.cart.repository.CartItemRepository;
import com.flowerconnect.cart.repository.CartRepository;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.checkout.domain.CheckoutIdempotency;
import com.flowerconnect.checkout.dto.CheckoutItemQuote;
import com.flowerconnect.checkout.dto.CheckoutQuoteRequest;
import com.flowerconnect.checkout.dto.CheckoutQuoteResponse;
import com.flowerconnect.checkout.dto.CheckoutRequest;
import com.flowerconnect.checkout.dto.CheckoutResponse;
import com.flowerconnect.checkout.dto.CheckoutShopQuote;
import com.flowerconnect.config.CheckoutProperties;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.OrderItem;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.payment.domain.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
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
    @Mock
    private com.flowerconnect.order.repository.OrderRepository orderRepository;
    @Mock
    private com.flowerconnect.order.repository.OrderItemRepository orderItemRepository;
    @Mock
    private com.flowerconnect.order.repository.OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private com.flowerconnect.checkout.repository.CheckoutIdempotencyRepository idempotencyRepository;
    @Mock
    private com.flowerconnect.inventory.repository.StockMovementRepository stockMovementRepository;
    @Mock
    private java.time.Clock clock;
    @Mock
    private SlotValidationService slotValidationService;
    @Mock
    private com.flowerconnect.payment.service.PaymentService paymentService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

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
        when(checkoutProperties.getQuoteSigningSecret()).thenReturn("test-secret-key-for-quote-signing-at-least-32-chars-long");
        when(clock.instant()).thenReturn(java.time.Instant.parse("2026-10-15T10:00:00Z"));
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);

        SlotValidationService.ValidatedSlot defaultSlot = new SlotValidationService.ValidatedSlot(
                LocalDateTime.parse("2026-10-20T10:00:00"),
                LocalDateTime.parse("2026-10-20T11:00:00"),
                LocalDateTime.parse("2026-10-20T10:00:00").atZone(ZoneId.of("Asia/Kolkata"))
        );
        when(slotValidationService.parseAndNormalize(anyString())).thenAnswer(inv -> {
            String str = inv.getArgument(0);
            if (str == null || str.isBlank()) {
                return LocalDateTime.parse("2026-10-20T10:00:00");
            }
            return LocalDateTime.parse(str.replace("Z", ""));
        });
        when(slotValidationService.validateSlot(any(VendorProfile.class), anyString())).thenReturn(defaultSlot);
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
        when(slotValidationService.validateSlot(any(), eq("not-a-date")))
                .thenThrow(BusinessException.badRequest("Invalid slotStart format (expected ISO-8601)"));

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
        when(slotValidationService.validateSlot(any(), eq("2020-01-01T10:00:00")))
                .thenThrow(BusinessException.badRequest("Selected slot is in the past"));

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

    // --------------------------------------------------------------------- checkout tests

    @Test
    void checkoutSingleVendorSuccess() {
        setupCommonMocks(vendor);
        setupCheckoutMocks();

        CheckoutQuoteResponse quote = checkoutService.generateQuote("customer@test.com", sampleRequest());
        UUID clientOrderId = UUID.randomUUID();

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(clientOrderId)
                .quoteHash(quote.getQuoteHash())
                .items(List.of(CheckoutRequest.CheckoutItem.builder()
                        .vendorId(3L)
                        .slotStart("2026-10-20T10:00:00")
                        .build()))
                .build();

        CheckoutResponse response = checkoutService.checkout("customer@test.com", request);

        assertThat(response).isNotNull();
        assertThat(response.isIdempotentReplay()).isFalse();
        assertThat(response.getClientOrderId()).isEqualTo(clientOrderId);
        assertThat(response.getOrders()).hasSize(1);

        CheckoutResponse.OrderSummary orderSummary = response.getOrders().get(0);
        assertThat(orderSummary.getVendorId()).isEqualTo(3L);
        assertThat(orderSummary.getStatus()).isEqualTo("PLACED");
        assertThat(orderSummary.getPaymentMethod()).isEqualTo("COD");
        assertThat(orderSummary.getPaymentStatus()).isEqualTo("PENDING");
        assertThat(orderSummary.getSubtotal()).isEqualByComparingTo(new BigDecimal("598.00"));

        // Verify inventory reservation
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);

        // Verify cart items deleted
        verify(cartItemRepository).deleteAllByCartId(1L);

        // Verify COD payment row created
        verify(paymentService).createCodPayment(any(Order.class), any(BigDecimal.class));

        // Verify stock movement recorded
        verify(stockMovementRepository).saveAndFlush(argThat(sm ->
                sm.getMovementType() == StockMovement.MovementType.RESERVE
                        && sm.getQuantityDelta() == 2
                        && "order".equals(sm.getReferenceType())));

        // Verify idempotency record saved (first as pending, then as completed)
        verify(idempotencyRepository, times(2)).saveAndFlush(any(CheckoutIdempotency.class));
    }

    @Test
    void checkoutMultiVendorSuccess() {
        VendorProfile vendor2 = VendorProfile.builder()
                .id(4L).user(customer).businessName("Second Florist")
                .status(VendorProfile.Status.APPROVED).acceptingOrders(true)
                .serviceLocation(serviceLocation)
                .latitude(new BigDecimal("12.9400")).longitude(new BigDecimal("77.6300"))
                .deliveryRadiusKm(new BigDecimal("10.00")).minOrderAmount(new BigDecimal("100.00"))
                .baseDeliveryFee(new BigDecimal("25.00")).perKmFee(new BigDecimal("4.00"))
                .prepTimeMinutes(30).slotDurationMinutes(60).maxOrdersPerSlot(10).build();

        Product product2 = Product.builder()
                .id(21L).vendor(vendor2).name("Tulip Bunch").slug("tulip-bunch")
                .basePrice(new BigDecimal("150.00")).status(Product.ProductStatus.ACTIVE).build();

        Inventory inventory2 = Inventory.builder()
                .id(2L).product(product2).quantity(30).reservedQuantity(0).lowStockThreshold(5).build();

        CartItem item2 = CartItem.builder()
                .id(200L).cart(cart).product(product2).vendorId(4L)
                .quantity(1).priceSnapshot(new BigDecimal("150.00")).build();

        setupCommonMocks(vendor);
        setupCheckoutMocks();

        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(sampleCartItem(), item2));
        when(vendorProfileRepository.findById(4L)).thenReturn(Optional.of(vendor2));
        when(vendorProfileRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(vendor2));
        when(productRepository.findById(21L)).thenReturn(Optional.of(product2));
        when(inventoryRepository.findByProductId(21L)).thenReturn(Optional.of(inventory2));
        when(inventoryRepository.findByProductIdForUpdate(21L)).thenReturn(Optional.of(inventory2));

        CheckoutQuoteRequest quoteRequest = sampleRequest();
        quoteRequest.getItems().add(CheckoutQuoteRequest.QuoteItem.builder()
                .vendorId(4L).slotStart("2026-10-20T10:00:00").build());

        CheckoutQuoteResponse quote = checkoutService.generateQuote("customer@test.com", quoteRequest);
        UUID clientOrderId = UUID.randomUUID();

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(clientOrderId)
                .quoteHash(quote.getQuoteHash())
                .items(List.of(
                        CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build(),
                        CheckoutRequest.CheckoutItem.builder().vendorId(4L).slotStart("2026-10-20T10:00:00").build()
                ))
                .build();

        CheckoutResponse response = checkoutService.checkout("customer@test.com", request);

        assertThat(response).isNotNull();
        assertThat(response.getOrders()).hasSize(2);
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(inventory2.getReservedQuantity()).isEqualTo(1);
        verify(orderRepository, times(2)).saveAndFlush(any(Order.class));
    }

    @Test
    void checkoutRejectsNonCustomer() {
        Role floristRole = Role.builder().id(2L).name("FLORIST").build();
        User floristUser = User.builder().id(11L).email("florist@test.com").role(floristRole).status(User.Status.ACTIVE).build();
        when(userRepository.findByEmailWithRole("florist@test.com")).thenReturn(Optional.of(floristUser));

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L).clientOrderId(UUID.randomUUID()).quoteHash("dummy").items(List.of()).build();

        assertThatThrownBy(() -> checkoutService.checkout("florist@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("Only customers can checkout");
    }

    @Test
    void checkoutRejectsStaleQuoteHash() {
        setupCommonMocks(vendor);
        setupCheckoutMocks();

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(UUID.randomUUID())
                .quoteHash("invalid-hash-that-does-not-match")
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build()))
                .build();

        assertThatThrownBy(() -> checkoutService.checkout("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT)
                .hasMessageContaining("Quote confirmation invalid or stale");
    }

    @Test
    void checkoutRejectsPriceDriftWithoutConfirmation() {
        setupCommonMocks(vendor);
        setupCheckoutMocks();

        CheckoutQuoteResponse quote = checkoutService.generateQuote("customer@test.com", sampleRequest());

        // Product price changed from 299.00 to 350.00
        Product updatedProduct = Product.builder()
                .id(20L).vendor(vendor).name("Red Rose Bunch").slug("red-rose-bunch")
                .basePrice(new BigDecimal("350.00")).status(Product.ProductStatus.ACTIVE).build();
        when(productRepository.findById(20L)).thenReturn(Optional.of(updatedProduct));

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(UUID.randomUUID())
                .quoteHash(quote.getQuoteHash())
                .confirmPriceChanges(false)
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build()))
                .build();

        assertThatThrownBy(() -> checkoutService.checkout("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT)
                .hasMessageContaining("Price has changed for product");
    }

    @Test
    void checkoutAcceptsPriceDriftWithConfirmation() {
        setupCommonMocks(vendor);
        setupCheckoutMocks();

        Product updatedProduct = Product.builder()
                .id(20L).vendor(vendor).name("Red Rose Bunch").slug("red-rose-bunch")
                .basePrice(new BigDecimal("350.00")).status(Product.ProductStatus.ACTIVE).build();
        when(productRepository.findById(20L)).thenReturn(Optional.of(updatedProduct));

        CartItem cartItem = sampleCartItem();
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(cartItem));

        CheckoutQuoteResponse quote = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(UUID.randomUUID())
                .quoteHash(quote.getQuoteHash())
                .confirmPriceChanges(true)
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build()))
                .build();

        CheckoutResponse response = checkoutService.checkout("customer@test.com", request);

        assertThat(response).isNotNull();
        assertThat(response.getOrders().get(0).getSubtotal()).isEqualByComparingTo(new BigDecimal("700.00"));
        verify(cartItemRepository).save(cartItem);
        assertThat(cartItem.getPriceSnapshot()).isEqualByComparingTo(new BigDecimal("350.00"));
    }

    @Test
    void checkoutRejectsWhenSlotIsFull() {
        setupCommonMocks(vendor);
        setupCheckoutMocks();

        when(orderRepository.countBookedOrdersForSlot(eq(3L), any(LocalDateTime.class))).thenReturn(10L);

        CheckoutQuoteResponse quote = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(UUID.randomUUID())
                .quoteHash(quote.getQuoteHash())
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build()))
                .build();

        assertThatThrownBy(() -> checkoutService.checkout("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLOT_FULL)
                .hasMessageContaining("Delivery slot is full");
    }

    @Test
    void checkoutRejectsWhenInsufficientStock() {
        setupCommonMocks(vendor);
        setupCheckoutMocks();

        // Inventory has only 1 available, cart needs 2
        Inventory lowStock = Inventory.builder()
                .id(1L).product(product).quantity(1).reservedQuantity(0).lowStockThreshold(5).build();
        when(inventoryRepository.findByProductIdForUpdate(20L)).thenReturn(Optional.of(lowStock));

        CheckoutQuoteResponse quote = checkoutService.generateQuote("customer@test.com", sampleRequest());

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(UUID.randomUUID())
                .quoteHash(quote.getQuoteHash())
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build()))
                .build();

        assertThatThrownBy(() -> checkoutService.checkout("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK)
                .hasMessageContaining("Only 1 units of Red Rose Bunch are available");
    }

    @Test
    void checkoutReplaysCachedResultOnIdenticalClientOrderId() {
        setupCommonMocks(vendor);

        UUID clientOrderId = UUID.randomUUID();
        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(clientOrderId)
                .quoteHash("matching-quote-hash")
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build()))
                .build();

        String expectedFingerprint = computeFingerprint(10L, clientOrderId, 5L, request.getItems(), "matching-quote-hash");

        CheckoutIdempotency completedRecord = CheckoutIdempotency.builder()
                .id(100L)
                .userId(10L)
                .clientOrderId(clientOrderId.toString())
                .checkoutFingerprint(expectedFingerprint)
                .createdAt(LocalDateTime.parse("2026-10-15T10:00:00"))
                .completedAt(LocalDateTime.parse("2026-10-15T10:00:01"))
                .build();

        when(idempotencyRepository.findByUserIdAndClientOrderId(10L, clientOrderId.toString()))
                .thenReturn(Optional.of(completedRecord));

        Order existingOrder = Order.builder()
                .id(501L)
                .user(customer)
                .vendor(vendor)
                .clientOrderId(clientOrderId.toString())
                .status(Order.OrderStatus.PLACED)
                .paymentMethod(Order.PaymentMethod.COD)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .subtotal(new BigDecimal("598.00"))
                .deliveryFee(new BigDecimal("45.00"))
                .platformFee(BigDecimal.ZERO)
                .total(new BigDecimal("643.00"))
                .slotStart(LocalDateTime.parse("2026-10-20T10:00:00"))
                .slotEnd(LocalDateTime.parse("2026-10-20T11:00:00"))
                .build();

        OrderItem existingItem = OrderItem.builder()
                .id(601L)
                .order(existingOrder)
                .product(product)
                .vendorId(3L)
                .quantity(2)
                .priceSnapshot(new BigDecimal("299.00"))
                .build();

        when(orderRepository.findByUserIdAndClientOrderId(10L, clientOrderId.toString()))
                .thenReturn(List.of(existingOrder));
        when(orderItemRepository.findByOrderId(501L)).thenReturn(List.of(existingItem));

        CheckoutResponse response = checkoutService.checkout("customer@test.com", request);

        assertThat(response).isNotNull();
        assertThat(response.isIdempotentReplay()).isTrue();
        assertThat(response.getOrders()).hasSize(1);
        assertThat(response.getOrders().get(0).getOrderId()).isEqualTo(501L);
        assertThat(response.getTotal()).isEqualByComparingTo(new BigDecimal("643.00"));

        // No new orders created
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
    }

    @Test
    void checkoutRejectsReplayWhenFingerprintDiffers() {
        setupCommonMocks(vendor);

        UUID clientOrderId = UUID.randomUUID();
        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(clientOrderId)
                .quoteHash("matching-quote-hash")
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build()))
                .build();

        CheckoutIdempotency completedRecordWithDiffFingerprint = CheckoutIdempotency.builder()
                .id(100L)
                .userId(10L)
                .clientOrderId(clientOrderId.toString())
                .checkoutFingerprint("DIFFERENT_FINGERPRINT")
                .createdAt(LocalDateTime.parse("2026-10-15T10:00:00"))
                .completedAt(LocalDateTime.parse("2026-10-15T10:00:01"))
                .build();

        when(idempotencyRepository.findByUserIdAndClientOrderId(10L, clientOrderId.toString()))
                .thenReturn(Optional.of(completedRecordWithDiffFingerprint));

        assertThatThrownBy(() -> checkoutService.checkout("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT)
                .hasMessageContaining("already completed with a different payload");
    }

    @Test
    void checkoutRejectsReplayWhenCheckoutIsPending() {
        setupCommonMocks(vendor);

        UUID clientOrderId = UUID.randomUUID();
        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(clientOrderId)
                .quoteHash("matching-quote-hash")
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build()))
                .build();

        CheckoutIdempotency pendingRecord = CheckoutIdempotency.builder()
                .id(100L)
                .userId(10L)
                .clientOrderId(clientOrderId.toString())
                .checkoutFingerprint("mock-fingerprint")
                .createdAt(LocalDateTime.parse("2026-10-15T10:00:00"))
                .completedAt(null)
                .build();

        when(idempotencyRepository.findByUserIdAndClientOrderId(10L, clientOrderId.toString()))
                .thenReturn(Optional.of(pendingRecord));

        assertThatThrownBy(() -> checkoutService.checkout("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT)
                .hasMessageContaining("already in progress");
    }

    @Test
    void checkoutRejectsSlotViolatingLeadTimeWithoutSideEffects() {
        setupCommonMocks(vendor);
        setupCheckoutMocks();

        when(slotValidationService.validateSlot(any(VendorProfile.class), anyString()))
                .thenThrow(BusinessException.badRequest("Selected slot violates minimum lead time"));

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(UUID.randomUUID())
                .quoteHash("dummy-hash")
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-20T10:00:00").build()))
                .build();

        assertThatThrownBy(() -> checkoutService.checkout("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .hasMessageContaining("Selected slot violates minimum lead time");

        // Verify all mutations and side effects are prevented
        verify(orderRepository, never()).save(any());
        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderItemRepository, never()).save(any());
        verify(orderItemRepository, never()).saveAndFlush(any());
        verify(cartItemRepository, never()).deleteAllByCartId(any());
        verify(stockMovementRepository, never()).save(any());
        verify(stockMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkoutRejectsSlotBeyondHorizonWithoutSideEffects() {
        setupCommonMocks(vendor);
        setupCheckoutMocks();

        when(slotValidationService.validateSlot(any(VendorProfile.class), anyString()))
                .thenThrow(BusinessException.badRequest("Date is beyond the booking horizon"));

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(UUID.randomUUID())
                .quoteHash("dummy-hash")
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("2026-10-30T10:00:00").build()))
                .build();

        assertThatThrownBy(() -> checkoutService.checkout("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .hasMessageContaining("Date is beyond the booking horizon");

        verify(orderRepository, never()).saveAndFlush(any());
        verify(cartItemRepository, never()).deleteAllByCartId(any());
        verify(stockMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkoutRejectsMalformedSlotStartWithoutSideEffects() {
        setupCommonMocks(vendor);
        setupCheckoutMocks();

        doThrow(BusinessException.badRequest("Invalid slotStart format (expected ISO-8601)"))
                .when(slotValidationService).parseAndNormalize(anyString());

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(5L)
                .clientOrderId(UUID.randomUUID())
                .quoteHash("dummy-hash")
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(3L).slotStart("malformed-timestamp").build()))
                .build();

        assertThatThrownBy(() -> checkoutService.checkout("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .hasMessageContaining("Invalid slotStart format (expected ISO-8601)");

        verify(orderRepository, never()).saveAndFlush(any());
        verify(cartItemRepository, never()).deleteAllByCartId(any());
        verify(stockMovementRepository, never()).saveAndFlush(any());
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

    private void setupCheckoutMocks() {
        when(inventoryRepository.findByProductIdForUpdate(20L)).thenReturn(Optional.of(inventory));
        when(vendorProfileRepository.findByIdForUpdate(vendor.getId())).thenReturn(Optional.of(vendor));
        when(orderRepository.countBookedOrdersForSlot(eq(vendor.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(idempotencyRepository.findByUserIdAndClientOrderId(eq(10L), anyString())).thenReturn(Optional.empty());
        when(idempotencyRepository.save(any(CheckoutIdempotency.class))).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyRepository.saveAndFlush(any(CheckoutIdempotency.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1001L);
            return o;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItem.class))).thenAnswer(inv -> {
            OrderItem oi = inv.getArgument(0);
            oi.setId(2001L);
            return oi;
        });
        when(stockMovementRepository.saveAndFlush(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderStatusHistoryRepository.saveAndFlush(any(OrderStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentService.createCodPayment(any(Order.class), any(BigDecimal.class)))
                .thenAnswer(inv -> {
                    Order o = inv.getArgument(0);
                    return Payment.builder()
                            .id(3001L)
                            .order(o)
                            .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.COD)
                            .provider("cod")
                            .amount(o.getTotal())
                            .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING)
                            .build();
                });
    }

    private String computeFingerprint(Long userId, UUID clientOrderId, Long addressId,
                                      List<CheckoutRequest.CheckoutItem> items, String quoteHash) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("user:").append(userId).append("|");
            sb.append("clientOrderId:").append(clientOrderId).append("|");
            sb.append("address:").append(addressId).append("|");
            sb.append("quoteHash:").append(quoteHash).append("|");
            sb.append("paymentMethod:COD|");
            sb.append("items:");
            List<CheckoutRequest.CheckoutItem> sorted = new ArrayList<>(items);
            sorted.sort(Comparator.comparing(CheckoutRequest.CheckoutItem::getVendorId));
            for (CheckoutRequest.CheckoutItem item : sorted) {
                sb.append(item.getVendorId()).append(":").append(item.getSlotStart()).append(";");
            }
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    "test-secret-key-for-quote-signing-at-least-32-chars-long".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
                .build();
    }
}