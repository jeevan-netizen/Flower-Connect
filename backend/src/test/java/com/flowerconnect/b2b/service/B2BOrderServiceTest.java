package com.flowerconnect.b2b.service;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BOrderItem;
import com.flowerconnect.b2b.dto.B2BOrderActionRequest;
import com.flowerconnect.b2b.dto.B2BOrderItemRequest;
import com.flowerconnect.b2b.dto.B2BOrderItemResponse;
import com.flowerconnect.b2b.dto.B2BOrderResponse;
import com.flowerconnect.b2b.dto.PlaceB2BOrderRequest;
import com.flowerconnect.b2b.repository.B2BOrderItemRepository;
import com.flowerconnect.b2b.repository.B2BOrderRepository;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.StockDelta;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.service.InventoryService;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link B2BOrderService}.
 *
 * <p>Everything decidable without a database is asserted here: every valid
 * transition, every invalid transition (409 CONFLICT), self-order prevention,
 * foreign-product rejection, insufficient-stock rejection, stock deduction on
 * accept, stock restoration on cancellation after acceptance, and
 * server-computed totals with stable snapshots.
 *
 * <p>HTTP wiring, RBAC, and the pessimistic lock are covered by
 * {@code B2BOrderIntegrationTest} and {@code B2BOrderControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class B2BOrderServiceTest {

    private static final String BUYER_EMAIL = "buyer@test.com";
    private static final String SELLER_EMAIL = "seller@test.com";
    private static final String OTHER_EMAIL = "other@test.com";
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final Long OTHER_ID = 3L;
    private static final Long PRODUCT_ID = 42L;
    private static final Long OTHER_PRODUCT_ID = 43L;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private B2BOrderRepository orderRepository;
    @Mock
    private B2BOrderItemRepository orderItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InventoryService inventoryService;

    private B2BOrderService service;

    @BeforeEach
    void setUp() {
        service = new B2BOrderService(
                orderRepository, orderItemRepository, productRepository,
                inventoryRepository, vendorProfileRepository, userRepository,
                inventoryService, CLOCK);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private VendorProfile approvedVendor(Long id, String email, String businessName) {
        VendorProfile v = VendorProfile.builder()
                .id(id)
                .businessName(businessName)
                .status(VendorProfile.Status.APPROVED)
                .build();
        when(vendorProfileRepository.findByUserEmail(email)).thenReturn(Optional.of(v));
        when(vendorProfileRepository.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    private Product activeProduct(Long id, Long vendorId, String name, BigDecimal price) {
        VendorProfile vendor = VendorProfile.builder().id(vendorId).build();
        return Product.builder()
                .id(id)
                .vendor(vendor)
                .name(name)
                .basePrice(price)
                .status(ProductStatus.ACTIVE)
                .build();
    }

    private B2BOrder newOrder(Long id, Status status) {
        VendorProfile buyer = VendorProfile.builder().id(BUYER_ID).businessName("Buyer").build();
        VendorProfile seller = VendorProfile.builder().id(SELLER_ID).businessName("Seller").build();
        return B2BOrder.builder()
                .id(id)
                .orderNo("B2B-" + id)
                .buyer(buyer)
                .seller(seller)
                .status(status)
                .paymentStatus(com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus.UNPAID)
                .total(BigDecimal.ZERO)
                .placedAt(java.time.LocalDateTime.now(CLOCK))
                .build();
    }

    // ------------------------------------------------------------------
    // Place order
    // ------------------------------------------------------------------

    @Test
    void placeOrderSucceedsAndSnapshotsPrices() {
        approvedVendor(BUYER_ID, BUYER_EMAIL, "Buyer Florist");
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        Product product = activeProduct(PRODUCT_ID, SELLER_ID, "Red Roses", new BigDecimal("199.00"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(com.flowerconnect.inventory.domain.Inventory.builder()
                        .id(1L).product(product).quantity(10).reservedQuantity(0).build()));
        when(orderRepository.saveAndFlush(any(B2BOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PlaceB2BOrderRequest request = PlaceB2BOrderRequest.builder()
                .sellerId(SELLER_ID)
                .items(List.of(new B2BOrderItemRequest(PRODUCT_ID, 3)))
                .orderNote("Please wrap nicely")
                .build();

        B2BOrderResponse response = service.placeOrder(BUYER_EMAIL, request);

        assertThat(response.getStatus()).isEqualTo(Status.PLACED);
        assertThat(response.getPaymentStatus()).isEqualTo(com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus.UNPAID);
        assertThat(response.getTotal()).isEqualByComparingTo("597.00");
        assertThat(response.getBuyerId()).isEqualTo(BUYER_ID);
        assertThat(response.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(response.getOrderNote()).isEqualTo("Please wrap nicely");

        List<B2BOrderItemResponse> items = response.getItems();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductName()).isEqualTo("Red Roses");
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("199.00");
        assertThat(items.get(0).getQuantity()).isEqualTo(3);
        assertThat(items.get(0).getLineTotal()).isEqualByComparingTo("597.00");

        verify(orderRepository).saveAndFlush(any(B2BOrder.class));
        // Stock is NOT deducted at placement.
        verify(inventoryService, never()).deductForB2BAccept(anyList(), any(), anyString());
    }

    @Test
    void placeOrderRejectsSelfOrder() {
        approvedVendor(BUYER_ID, BUYER_EMAIL, "Same Florist");
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Other Florist");

        PlaceB2BOrderRequest request = PlaceB2BOrderRequest.builder()
                .sellerId(BUYER_ID)
                .items(List.of(new B2BOrderItemRequest(PRODUCT_ID, 1)))
                .build();

        assertThatThrownBy(() -> service.placeOrder(BUYER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void placeOrderRejectsForeignProduct() {
        approvedVendor(BUYER_ID, BUYER_EMAIL, "Buyer Florist");
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        // Product belongs to a different vendor (OTHER_ID), not the seller.
        Product foreignProduct = activeProduct(PRODUCT_ID, OTHER_ID, "Not Yours", new BigDecimal("50.00"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreignProduct));

        PlaceB2BOrderRequest request = PlaceB2BOrderRequest.builder()
                .sellerId(SELLER_ID)
                .items(List.of(new B2BOrderItemRequest(PRODUCT_ID, 1)))
                .build();

        assertThatThrownBy(() -> service.placeOrder(BUYER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void placeOrderRejectsInactiveProduct() {
        approvedVendor(BUYER_ID, BUYER_EMAIL, "Buyer Florist");
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        Product inactive = Product.builder()
                .id(PRODUCT_ID)
                .vendor(VendorProfile.builder().id(SELLER_ID).build())
                .name("Dried Out")
                .basePrice(new BigDecimal("10.00"))
                .status(ProductStatus.INACTIVE)
                .build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(inactive));

        PlaceB2BOrderRequest request = PlaceB2BOrderRequest.builder()
                .sellerId(SELLER_ID)
                .items(List.of(new B2BOrderItemRequest(PRODUCT_ID, 1)))
                .build();

        assertThatThrownBy(() -> service.placeOrder(BUYER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void placeOrderRejectsInsufficientStock() {
        approvedVendor(BUYER_ID, BUYER_EMAIL, "Buyer Florist");
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        Product product = activeProduct(PRODUCT_ID, SELLER_ID, "Low Stock", new BigDecimal("10.00"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        // Only 1 unit available, requesting 5.
        when(inventoryRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(com.flowerconnect.inventory.domain.Inventory.builder()
                        .id(1L).product(product).quantity(1).reservedQuantity(0).build()));

        PlaceB2BOrderRequest request = PlaceB2BOrderRequest.builder()
                .sellerId(SELLER_ID)
                .items(List.of(new B2BOrderItemRequest(PRODUCT_ID, 5)))
                .build();

        assertThatThrownBy(() -> service.placeOrder(BUYER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    void placeOrderRejectsZeroQuantity() {
        approvedVendor(BUYER_ID, BUYER_EMAIL, "Buyer Florist");
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        Product product = activeProduct(PRODUCT_ID, SELLER_ID, "Item", new BigDecimal("10.00"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(com.flowerconnect.inventory.domain.Inventory.builder()
                        .id(1L).product(product).quantity(10).reservedQuantity(0).build()));

        PlaceB2BOrderRequest request = PlaceB2BOrderRequest.builder()
                .sellerId(SELLER_ID)
                .items(List.of(new B2BOrderItemRequest(PRODUCT_ID, 0)))
                .build();

        assertThatThrownBy(() -> service.placeOrder(BUYER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // ------------------------------------------------------------------
    // Accept
    // ------------------------------------------------------------------

    @Test
    void acceptSucceedsAndDeductsStock() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        Product product = activeProduct(PRODUCT_ID, SELLER_ID, "Red Roses", new BigDecimal("199.00"));
        when(userRepository.findByEmail(SELLER_EMAIL)).thenReturn(Optional.of(User.builder().id(SELLER_ID).build()));

        B2BOrder order = newOrder(10L, Status.PLACED);
        order.getItems().clear();
        order.addItem(B2BOrderItem.builder()
                .product(product).productName("Red Roses").unit("kg")
                .quantity(3).unitPrice(new BigDecimal("199.00")).lineTotal(new BigDecimal("597.00")).build());
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(B2BOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        B2BOrderResponse response = service.accept(SELLER_EMAIL, 10L);

        assertThat(response.getStatus()).isEqualTo(Status.ACCEPTED);
        verify(inventoryService).deductForB2BAccept(
                eq(List.of(new StockDelta(PRODUCT_ID, 3))),
                any(User.class),
                eq("B2B order accepted - stock deducted"));
    }

    @Test
    void acceptRejectsNonSeller() {
        approvedVendor(OTHER_ID, OTHER_EMAIL, "Other Florist");

        B2BOrder order = newOrder(10L, Status.PLACED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.accept(OTHER_EMAIL, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void acceptRejectsNonPlacedOrder() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        B2BOrder order = newOrder(10L, Status.ACCEPTED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.accept(SELLER_EMAIL, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void acceptRejectsInsufficientStockAndLeavesOrderUnchanged() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        Product product = activeProduct(PRODUCT_ID, SELLER_ID, "Item", new BigDecimal("10.00"));
        when(userRepository.findByEmail(SELLER_EMAIL)).thenReturn(Optional.of(User.builder().id(SELLER_ID).build()));

        B2BOrder order = newOrder(10L, Status.PLACED);
        order.getItems().clear();
        order.addItem(B2BOrderItem.builder()
                .product(product).quantity(5).unit("kg").unitPrice(new BigDecimal("10.00"))
                .lineTotal(new BigDecimal("50.00")).build());
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "not enough"))
                .when(inventoryService).deductForB2BAccept(anyList(), any(), anyString());

        assertThatThrownBy(() -> service.accept(SELLER_EMAIL, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_STOCK);

        // Order must still be PLACED (not accepted).
        assertThat(order.getStatus()).isEqualTo(Status.PLACED);
    }

    // ------------------------------------------------------------------
    // Reject
    // ------------------------------------------------------------------

    @Test
    void rejectSucceeds() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        B2BOrder order = newOrder(10L, Status.PLACED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(B2BOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        B2BOrderResponse response = service.reject(SELLER_EMAIL, 10L, "Out of stock");

        assertThat(response.getStatus()).isEqualTo(Status.REJECTED);
    }

    @Test
    void rejectRejectsNonSeller() {
        approvedVendor(OTHER_ID, OTHER_EMAIL, "Other Florist");

        B2BOrder order = newOrder(10L, Status.PLACED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reject(OTHER_EMAIL, 10L, "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void rejectRejectsNonPlacedOrder() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        B2BOrder order = newOrder(10L, Status.ACCEPTED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reject(SELLER_EMAIL, 10L, "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Cancel
    // ------------------------------------------------------------------

    @Test
    void cancelByBuyerOnPlacedSucceeds() {
        approvedVendor(BUYER_ID, BUYER_EMAIL, "Buyer Florist");

        B2BOrder order = newOrder(10L, Status.PLACED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(B2BOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        B2BOrderResponse response = service.cancel(BUYER_EMAIL, 10L, "Changed my mind");

        assertThat(response.getStatus()).isEqualTo(Status.CANCELLED);
        // Stock was never reserved, so no restock call.
        verify(inventoryService, never()).restockCancelledOrder(anyList(), any(), anyString());
    }

    @Test
    void cancelBySellerOnAcceptedRestoresStock() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        Product product = activeProduct(PRODUCT_ID, SELLER_ID, "Item", new BigDecimal("10.00"));
        when(userRepository.findByEmail(SELLER_EMAIL)).thenReturn(Optional.of(User.builder().id(SELLER_ID).build()));

        B2BOrder order = newOrder(10L, Status.ACCEPTED);
        order.getItems().clear();
        order.addItem(B2BOrderItem.builder()
                .product(product).quantity(3).unit("kg").unitPrice(new BigDecimal("10.00"))
                .lineTotal(new BigDecimal("30.00")).build());
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(B2BOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        B2BOrderResponse response = service.cancel(SELLER_EMAIL, 10L, "No longer needed");

        assertThat(response.getStatus()).isEqualTo(Status.CANCELLED);
        verify(inventoryService).restockCancelledOrder(
                eq(List.of(new StockDelta(PRODUCT_ID, 3))),
                any(User.class),
                anyString());
    }

    @Test
    void cancelByBuyerOnAcceptedRestoresStock() {
        approvedVendor(BUYER_ID, BUYER_EMAIL, "Buyer Florist");

        Product product = activeProduct(PRODUCT_ID, SELLER_ID, "Item", new BigDecimal("10.00"));
        when(userRepository.findByEmail(BUYER_EMAIL)).thenReturn(Optional.of(User.builder().id(BUYER_ID).build()));

        B2BOrder order = newOrder(10L, Status.ACCEPTED);
        order.getItems().clear();
        order.addItem(B2BOrderItem.builder()
                .product(product).quantity(2).unit("kg").unitPrice(new BigDecimal("10.00"))
                .lineTotal(new BigDecimal("20.00")).build());
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(B2BOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        B2BOrderResponse response = service.cancel(BUYER_EMAIL, 10L, "Buyer cancelled");

        assertThat(response.getStatus()).isEqualTo(Status.CANCELLED);
        verify(inventoryService).restockCancelledOrder(
                eq(List.of(new StockDelta(PRODUCT_ID, 2))),
                any(User.class),
                anyString());
    }

    @Test
    void cancelRejectsAfterShipped() {
        approvedVendor(BUYER_ID, BUYER_EMAIL, "Buyer Florist");

        B2BOrder order = newOrder(10L, Status.SHIPPED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(BUYER_EMAIL, 10L, "too late"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void cancelRejectsSellerOnPlaced() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        B2BOrder order = newOrder(10L, Status.PLACED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(SELLER_EMAIL, 10L, "not allowed"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void cancelRejectsNonParticipant() {
        approvedVendor(OTHER_ID, OTHER_EMAIL, "Other Florist");

        B2BOrder order = newOrder(10L, Status.PLACED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(OTHER_EMAIL, 10L, "nope"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // Ship
    // ------------------------------------------------------------------

    @Test
    void shipSucceedsWithReference() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        B2BOrder order = newOrder(10L, Status.ACCEPTED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(B2BOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setShipmentReference("TRACK-123");

        B2BOrderResponse response = service.ship(SELLER_EMAIL, 10L, request);

        assertThat(response.getStatus()).isEqualTo(Status.SHIPPED);
        assertThat(response.getShipmentReference()).isEqualTo("TRACK-123");
        assertThat(response.getShippedAt()).isNotNull();
    }

    @Test
    void shipRejectsWithoutReference() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        B2BOrder order = newOrder(10L, Status.ACCEPTED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setShipmentReference(null);

        assertThatThrownBy(() -> service.ship(SELLER_EMAIL, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void shipRejectsNonAcceptedOrder() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        B2BOrder order = newOrder(10L, Status.PLACED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setShipmentReference("TRACK-123");

        assertThatThrownBy(() -> service.ship(SELLER_EMAIL, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Deliver
    // ------------------------------------------------------------------

    @Test
    void deliverSucceeds() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        B2BOrder order = newOrder(10L, Status.SHIPPED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(B2BOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        B2BOrderResponse response = service.deliver(SELLER_EMAIL, 10L);

        assertThat(response.getStatus()).isEqualTo(Status.DELIVERED);
        assertThat(response.getDeliveredAt()).isNotNull();
        // Payment stays UNPAID.
        assertThat(response.getPaymentStatus()).isEqualTo(com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus.UNPAID);
    }

    @Test
    void deliverRejectsNonShippedOrder() {
        approvedVendor(SELLER_ID, SELLER_EMAIL, "Seller Florist");

        B2BOrder order = newOrder(10L, Status.ACCEPTED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.deliver(SELLER_EMAIL, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
