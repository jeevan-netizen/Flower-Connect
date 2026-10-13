package com.flowerconnect.cart.service;

import com.flowerconnect.cart.domain.Cart;
import com.flowerconnect.cart.domain.CartItem;
import com.flowerconnect.cart.dto.AddCartItemRequest;
import com.flowerconnect.cart.dto.UpdateCartItemRequest;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.cart.repository.CartItemRepository;
import com.flowerconnect.cart.repository.CartRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CartService} — eligibility logic, quantity semantics,
 * price snapshot, and customer isolation of the cart. The service's four
 * repository dependencies are mocked; integration-level behavior against real
 * MySQL is covered by {@code CartApiIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

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

    @InjectMocks
    private CartService cartService;

    private User customer;
    private Product activeProduct;
    private VendorProfile approvedVendor;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(10L)
                .email("customer@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test Customer")
                .phone("+1234567890")
                .role(Role.builder().id(1L).name("CUSTOMER").build())
                .status(User.Status.ACTIVE)
                .build();

        approvedVendor = VendorProfile.builder()
                .id(3L)
                .user(customer)
                .businessName("Test Florist")
                .status(VendorProfile.Status.APPROVED)
                .acceptingOrders(true)
                .build();

        activeProduct = Product.builder()
                .id(20L)
                .vendor(approvedVendor)
                .name("Red Rose Bunch")
                .slug("red-rose-bunch")
                .basePrice(new BigDecimal("299.00"))
                .status(Product.ProductStatus.ACTIVE)
                .build();

        inventory = Inventory.builder()
                .id(1L)
                .product(activeProduct)
                .quantity(50)
                .reservedQuantity(0)
                .lowStockThreshold(10)
                .build();

        lenient().when(userRepository.findByEmailWithRole("customer@test.com"))
                .thenReturn(Optional.of(customer));
        lenient().when(productRepository.findById(20L)).thenReturn(Optional.of(activeProduct));
        lenient().when(vendorProfileRepository.findById(3L)).thenReturn(Optional.of(approvedVendor));
        lenient().when(inventoryRepository.findByProductId(20L)).thenReturn(Optional.of(inventory));
    }

    // ------------------------------------------------------------------ getCart

    @Test
    void getCartReturnsEmptyWhenNoCartExists() {
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.empty());

        var response = cartService.getCart("customer@test.com");

        assertThat(response.getTotalItems()).isZero();
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getShops()).isEmpty();
    }

    @Test
    void getCartReturnsEmptyWhenCartHasNoItems() {
        Cart emptyCart = Cart.builder().id(1L).user(customer).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(emptyCart));
        when(cartItemRepository.countByCartId(1L)).thenReturn(0L);

        var response = cartService.getCart("customer@test.com");

        assertThat(response.getTotalItems()).isZero();
    }

    @Test
    void getCartReturnsItemsWithVendorInfo() {
        Cart cart = Cart.builder().id(1L).user(customer).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(
                CartItem.builder().id(100L).cart(cart).product(activeProduct)
                        .vendorId(3L).quantity(2).priceSnapshot(new BigDecimal("299.00"))
                        .build()
        ));
        when(cartItemRepository.countByCartId(1L)).thenReturn(1L);
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct));
        when(vendorProfileRepository.findAllById(any())).thenReturn(List.of(approvedVendor));
        when(inventoryRepository.findAllByProductIdIn(any())).thenReturn(List.of(inventory));

        var response = cartService.getCart("customer@test.com");

        assertThat(response.getTotalItems()).isEqualTo(1);
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("598.00"));
        assertThat(response.getItems()).hasSize(1);

        var item = response.getItems().get(0);
        assertThat(item.getProductName()).isEqualTo("Red Rose Bunch");
        assertThat(item.getVendorName()).isEqualTo("Test Florist");
        assertThat(item.isPurchasable()).isTrue();
        assertThat(item.getNotPurchasableReason()).isNull();
    }

    @Test
    void getCartMarksInactiveProductAsNotPurchasable() {
        Product inactiveProduct = Product.builder()
                .id(21L).vendor(approvedVendor)
                .name("Inactive Rose").slug("inactive-rose")
                .basePrice(new BigDecimal("100.00"))
                .status(Product.ProductStatus.INACTIVE)
                .build();
        Cart cart = Cart.builder().id(1L).user(customer).build();
        CartItem item = CartItem.builder().id(100L).cart(cart).product(inactiveProduct)
                .vendorId(3L).quantity(1).priceSnapshot(new BigDecimal("100.00")).build();

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(item));
        when(cartItemRepository.countByCartId(1L)).thenReturn(1L);
        when(productRepository.findAllById(any())).thenReturn(List.of(inactiveProduct));
        when(vendorProfileRepository.findAllById(any())).thenReturn(List.of(approvedVendor));
        when(inventoryRepository.findAllByProductIdIn(any())).thenReturn(List.of(inventory));

        var response = cartService.getCart("customer@test.com");

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).isPurchasable()).isFalse();
        assertThat(response.getItems().get(0).getNotPurchasableReason())
                .isEqualTo("Product is no longer available");
    }

    @Test
    void getCartMarksLowStockItemAsNotPurchasable() {
        Cart cart = Cart.builder().id(1L).user(customer).build();
        CartItem item = CartItem.builder().id(100L).cart(cart).product(activeProduct)
                .vendorId(3L).quantity(5).priceSnapshot(new BigDecimal("299.00")).build();
        inventory.setQuantity(3);  // available = 3 - 0 = 3, but requested = 5

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(item));
        when(cartItemRepository.countByCartId(1L)).thenReturn(1L);
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct));
        when(vendorProfileRepository.findAllById(any())).thenReturn(List.of(approvedVendor));
        when(inventoryRepository.findAllByProductIdIn(any())).thenReturn(List.of(inventory));

        var response = cartService.getCart("customer@test.com");

        assertThat(response.getItems().get(0).isPurchasable()).isFalse();
        assertThat(response.getItems().get(0).getNotPurchasableReason())
                .isEqualTo("Insufficient stock");
    }

    // ------------------------------------------------------------------ addItem

    @Test
    void addItemCreatesCartIfNoneExists() {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(20L).quantity(2).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.empty());

        Cart newCart = Cart.builder().id(1L).user(customer).build();
        when(cartRepository.saveAndFlush(any(Cart.class))).thenReturn(newCart);

        CartItem savedItem = CartItem.builder().id(100L).cart(newCart).product(activeProduct)
                .vendorId(3L).quantity(2).priceSnapshot(new BigDecimal("299.00")).build();
        when(cartItemRepository.saveAndFlush(any(CartItem.class))).thenReturn(savedItem);

        var response = cartService.addItem("customer@test.com", request);

        assertThat(response.getQuantity()).isEqualTo(2);
        assertThat(response.getPriceSnapshot()).isEqualByComparingTo(new BigDecimal("299.00"));
        verify(cartRepository).saveAndFlush(any(Cart.class));
    }

    @Test
    void addItemIncrementsQuantityForExistingProduct() {
        Cart cart = Cart.builder().id(1L).user(customer).build();
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(20L).quantity(3).build();

        CartItem existing = CartItem.builder().id(100L).cart(cart).product(activeProduct)
                .vendorId(3L).quantity(2).priceSnapshot(new BigDecimal("299.00")).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(1L, 20L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.saveAndFlush(any(CartItem.class))).thenReturn(existing);

        var response = cartService.addItem("customer@test.com", request);

        assertThat(response.getQuantity()).isEqualTo(5);
    }

    @Test
    void addItemRejectsInsufficientStock() {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(20L).quantity(100).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.flowerconnect.exception.ErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    void addItemRejectsInactiveProductAsNotFound() {
        Product inactiveProduct = Product.builder()
                .id(21L).vendor(approvedVendor)
                .name("Inactive").slug("inactive")
                .basePrice(new BigDecimal("100.00"))
                .status(Product.ProductStatus.INACTIVE)
                .build();
        when(productRepository.findById(21L)).thenReturn(Optional.of(inactiveProduct));

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(21L).quantity(2).build();

        assertThatThrownBy(() -> cartService.addItem("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.flowerconnect.exception.ErrorCode.NOT_FOUND);
    }

    @Test
    void addItemRejectsNonApprovedVendorWithForbidden() {
        VendorProfile pendingVendor = VendorProfile.builder()
                .id(5L).user(customer)
                .businessName("Pending Florist")
                .status(VendorProfile.Status.PENDING_APPROVAL)
                .acceptingOrders(true).build();
        Product product = Product.builder()
                .id(22L).vendor(pendingVendor)
                .name("Pending Product").slug("pending-product")
                .basePrice(new BigDecimal("100.00"))
                .status(Product.ProductStatus.ACTIVE)
                .build();
        when(productRepository.findById(22L)).thenReturn(Optional.of(product));
        when(vendorProfileRepository.findById(5L)).thenReturn(Optional.of(pendingVendor));

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(22L).quantity(1).build();

        assertThatThrownBy(() -> cartService.addItem("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.flowerconnect.exception.ErrorCode.FORBIDDEN);
    }

    @Test
    void addItemRejectsVendorNotAcceptingOrdersWithConflict() {
        VendorProfile notAcceptingVendor = VendorProfile.builder()
                .id(6L).user(customer)
                .businessName("Closed Florist")
                .status(VendorProfile.Status.APPROVED)
                .acceptingOrders(false).build();
        Product product = Product.builder()
                .id(23L).vendor(notAcceptingVendor)
                .name("Closed Product").slug("closed-product")
                .basePrice(new BigDecimal("100.00"))
                .status(Product.ProductStatus.ACTIVE)
                .build();
        when(productRepository.findById(23L)).thenReturn(Optional.of(product));
        when(vendorProfileRepository.findById(6L)).thenReturn(Optional.of(notAcceptingVendor));

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(23L).quantity(1).build();

        assertThatThrownBy(() -> cartService.addItem("customer@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.flowerconnect.exception.ErrorCode.CONFLICT);
    }

    // ------------------------------------------------------------- updateItem

    @Test
    void updateItemIncreasesQuantity() {
        Cart cart = Cart.builder().id(1L).user(customer).build();
        CartItem item = CartItem.builder().id(100L).cart(cart).product(activeProduct)
                .vendorId(3L).quantity(2).priceSnapshot(new BigDecimal("299.00")).build();

        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(productRepository.findById(20L)).thenReturn(Optional.of(activeProduct));
        when(cartItemRepository.saveAndFlush(any(CartItem.class))).thenReturn(item);

        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(5).build();
        var response = cartService.updateItem("customer@test.com", 100L, request);

        assertThat(response.getQuantity()).isEqualTo(5);
        verify(cartItemRepository).saveAndFlush(item);
    }

    @Test
    void updateItemWithQuantityZeroDeletesTheLine() {
        Cart cart = Cart.builder().id(1L).user(customer).build();
        CartItem item = CartItem.builder().id(100L).cart(cart).product(activeProduct)
                .vendorId(3L).quantity(3).priceSnapshot(new BigDecimal("299.00")).build();

        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(0).build();
        var response = cartService.updateItem("customer@test.com", 100L, request);

        assertThat(response).isNull();
        verify(cartItemRepository).delete(item);
        verify(cartItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateItemRejectsInsufficientStock() {
        Cart cart = Cart.builder().id(1L).user(customer).build();
        CartItem item = CartItem.builder().id(100L).cart(cart).product(activeProduct)
                .vendorId(3L).quantity(1).priceSnapshot(new BigDecimal("299.00")).build();

        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(productRepository.findById(20L)).thenReturn(Optional.of(activeProduct));
        inventory.setQuantity(2);

        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(5).build();
        assertThatThrownBy(() -> cartService.updateItem("customer@test.com", 100L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.flowerconnect.exception.ErrorCode.INSUFFICIENT_STOCK);
    }

    // ----------------------------------------------------------- removeItem

    @Test
    void removeItemDeletesTheCartItem() {
        Cart cart = Cart.builder().id(1L).user(customer).build();
        CartItem item = CartItem.builder().id(100L).cart(cart).product(activeProduct)
                .vendorId(3L).quantity(1).priceSnapshot(new BigDecimal("299.00")).build();

        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

        cartService.removeItem("customer@test.com", 100L);

        verify(cartItemRepository).delete(item);
    }

    @Test
    void removeItemRejectsForeignCartItemWithNotFound() {
        User otherCustomer = User.builder().id(99L).email("other@test.com")
                .role(Role.builder().id(1L).name("CUSTOMER").build())
                .status(User.Status.ACTIVE).build();
        Cart cart = Cart.builder().id(1L).user(otherCustomer).build();
        CartItem item = CartItem.builder().id(100L).cart(cart).product(activeProduct)
                .vendorId(3L).quantity(1).priceSnapshot(new BigDecimal("299.00")).build();

        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.removeItem("customer@test.com", 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.flowerconnect.exception.ErrorCode.NOT_FOUND);
    }

    // ------------------------------------------------------------- clearCart

    @Test
    void clearCartDeletesAllItems() {
        Cart cart = Cart.builder().id(1L).user(customer).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));

        cartService.clearCart("customer@test.com");

        verify(cartItemRepository).deleteAllByCartId(1L);
    }

    @Test
    void clearCartOnEmptyCartDoesNothing() {
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.empty());

        cartService.clearCart("customer@test.com");

        verifyNoInteractions(cartItemRepository);
    }

    // ----------------------------------------------------------- edge cases

    @Test
    void unknownCustomerEmailThrows() {
        when(userRepository.findByEmailWithRole("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCart("unknown@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authenticated user not found");
    }
}
