package com.flowerconnect.b2b;

import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import com.flowerconnect.b2b.dto.B2BOrderActionRequest;
import com.flowerconnect.b2b.dto.B2BOrderItemRequest;
import com.flowerconnect.b2b.dto.PlaceB2BOrderRequest;
import com.flowerconnect.b2b.repository.B2BOrderItemRepository;
import com.flowerconnect.b2b.repository.B2BOrderRepository;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the B2B order API (Stage 1 of the B2B module)
 * against a real MySQL instance and the production security chain.
 *
 * <p>Vendor profiles, products and inventory are created directly through
 * repositories and services so the HTTP layer can be exercised end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class B2BOrderIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String KORAMANGALA_PINCODE = "560034";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductService productService;
    @Autowired
    private B2BOrderRepository b2bOrderRepository;
    @Autowired
    private B2BOrderItemRepository b2bOrderItemRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long rosesCategoryId;
    private Long buyerProfileId;
    private String buyerEmail;
    private String buyerToken;
    private Long sellerProfileId;
    private String sellerEmail;
    private String sellerToken;
    private String customerToken;
    private String adminToken;
    private Long productId;
    private Long orderId;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE).orElseThrow().getId();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();

        // Buyer vendor (approved).
        User buyerUser = createUser("b2b-buyer-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        buyerEmail = buyerUser.getEmail();
        VendorProfile buyerProfile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(buyerUser)
                .businessName("Buyer Flowers")
                .addressLine1("1 Buyer Street")
                .serviceLocation(serviceLocationRepository.findById(locationId).orElseThrow())
                .latitude(BigDecimal.valueOf(12.93))
                .longitude(BigDecimal.valueOf(77.62))
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(VendorProfile.Status.APPROVED)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());
        buyerProfileId = buyerProfile.getId();
        buyerToken = tokenFor(buyerUser);

        // Seller vendor (approved).
        User sellerUser = createUser("b2b-seller-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        sellerEmail = sellerUser.getEmail();
        VendorProfile sellerProfile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(sellerUser)
                .businessName("Seller Blooms")
                .addressLine1("2 Seller Street")
                .serviceLocation(serviceLocationRepository.findById(locationId).orElseThrow())
                .latitude(BigDecimal.valueOf(12.94))
                .longitude(BigDecimal.valueOf(77.63))
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(VendorProfile.Status.APPROVED)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());
        sellerProfileId = sellerProfile.getId();
        sellerToken = tokenFor(sellerUser);

        customerToken = tokenFor(createUser("b2b-customer-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER"));
        adminToken = tokenFor(createUser("b2b-admin-" + UUID.randomUUID() + "@test.com", PASSWORD, "ADMIN"));

        // An active product owned by the seller with stock.
        ProductRequest productRequest = ProductRequest.builder()
                .name("B2B Test Rose " + UUID.randomUUID())
                .categoryId(rosesCategoryId)
                .description("Fresh roses for B2B")
                .basePrice(new BigDecimal("199.00"))
                .status(ProductStatus.ACTIVE)
                .build();
        productId = productService.create(sellerEmail, productRequest).getId();
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        inventory.setQuantity(20);
        inventory.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(inventory);
    }

    // ------------------------------------------------------------------
    // Seller search
    // ------------------------------------------------------------------

    @Test
    void searchSellersReturnsApprovedVendorsExcludingSelf() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/b2b/sellers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].businessName", hasItem("Seller Blooms")))
                .andExpect(jsonPath("$[*].vendorId", not(hasItem(buyerProfileId.intValue()))));
    }

    @Test
    void searchSellersSupportsQueryFilter() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/b2b/sellers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .param("query", "Seller"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].businessName", hasItem("Seller Blooms")));
    }

    // ------------------------------------------------------------------
    // Seller products
    // ------------------------------------------------------------------

    @Test
    void listSellerProductsReturnsActiveProductWithPriceAndStock() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/b2b/sellers/{sellerId}/products", sellerProfileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value(startsWith("B2B Test Rose")))
                .andExpect(jsonPath("$[0].unitPrice").value("199.0"))
                .andExpect(jsonPath("$[0].availableStock").value(20));
    }

    // ------------------------------------------------------------------
    // Place order
    // ------------------------------------------------------------------

    @Test
    void placeOrderSucceedsAndSnapshotsPrices() throws Exception {
        PlaceB2BOrderRequest request = new PlaceB2BOrderRequest();
        request.setSellerId(sellerProfileId);
        request.setItems(List.of(new B2BOrderItemRequest(productId, 3)));
        request.setOrderNote("Please wrap nicely");

        mockMvc.perform(post("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.total").value("597.0"))
                .andExpect(jsonPath("$.orderNote").value("Please wrap nicely"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value(startsWith("B2B Test Rose")))
                .andExpect(jsonPath("$.items[0].unitPrice").value("199.0"))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].lineTotal").value("597.0"));
    }

    @Test
    void placeOrderRejectsSelfOrder() throws Exception {
        PlaceB2BOrderRequest request = new PlaceB2BOrderRequest();
        request.setSellerId(buyerProfileId);
        request.setItems(List.of(new B2BOrderItemRequest(productId, 1)));

        mockMvc.perform(post("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void placeOrderRejectsForeignProduct() throws Exception {
        // Create a product owned by another vendor.
        User otherUser = createUser("b2b-other-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        VendorProfile otherProfile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(otherUser)
                .businessName("Other Florist")
                .addressLine1("3 Other Street")
                .serviceLocation(serviceLocationRepository.findById(locationId).orElseThrow())
                .latitude(BigDecimal.TEN)
                .longitude(BigDecimal.TEN)
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(VendorProfile.Status.APPROVED)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());
        Long otherProductId = productService.create(otherUser.getEmail(),
                ProductRequest.builder()
                        .name("Other Product " + UUID.randomUUID())
                        .categoryId(rosesCategoryId)
                        .description("Not for sale")
                        .basePrice(new BigDecimal("50.00"))
                        .status(ProductStatus.ACTIVE)
                        .build()).getId();

        PlaceB2BOrderRequest request = new PlaceB2BOrderRequest();
        request.setSellerId(sellerProfileId);
        request.setItems(List.of(new B2BOrderItemRequest(otherProductId, 1)));

        mockMvc.perform(post("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void placeOrderRejectsInsufficientStock() throws Exception {
        // Stock is 20, request 25.
        PlaceB2BOrderRequest request = new PlaceB2BOrderRequest();
        request.setSellerId(sellerProfileId);
        request.setItems(List.of(new B2BOrderItemRequest(productId, 25)));

        mockMvc.perform(post("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    // ------------------------------------------------------------------
    // List orders
    // ------------------------------------------------------------------

    @Test
    void listOrdersAsBuyer() throws Exception {
        Long orderId = placeOrderAndReturnId();

        mockMvc.perform(get("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .param("role", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId.intValue()));
    }

    @Test
    void listOrdersAsSeller() throws Exception {
        Long orderId = placeOrderAndReturnId();

        mockMvc.perform(get("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId.intValue()));
    }

    @Test
    void listOrdersFiltersByStatus() throws Exception {
        Long orderId = placeOrderAndReturnId();

        mockMvc.perform(get("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .param("role", "BUYER")
                        .param("status", "PLACED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId.intValue()));
    }

    // ------------------------------------------------------------------
    // Get order detail
    // ------------------------------------------------------------------

    @Test
    void getOrderByParticipant() throws Exception {
        Long orderId = placeOrderAndReturnId();

        mockMvc.perform(get("/api/v1/vendors/b2b/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()));

        mockMvc.perform(get("/api/v1/vendors/b2b/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()));
    }

    @Test
    void getOrderRejectsNonParticipant() throws Exception {
        Long orderId = placeOrderAndReturnId();

        // A third vendor cannot read the order.
        User thirdUser = createUser("b2b-third-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        VendorProfile thirdProfile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(thirdUser)
                .businessName("Third Florist")
                .addressLine1("4 Third Street")
                .serviceLocation(serviceLocationRepository.findById(locationId).orElseThrow())
                .latitude(BigDecimal.TEN)
                .longitude(BigDecimal.TEN)
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(VendorProfile.Status.APPROVED)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());
        String thirdToken = tokenFor(thirdUser);

        mockMvc.perform(get("/api/v1/vendors/b2b/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(thirdToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------------
    // Accept
    // ------------------------------------------------------------------

    @Test
    void acceptSucceedsAndDeductsSellerStock() throws Exception {
        Long orderId = placeOrderAndReturnId();

        int movementsBefore = stockMovementCount();
        int quantityBefore = inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();

        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Stock deducted: 20 - 3 = 17.
        assertEquals(quantityBefore - 3, inventoryRepository.findByProductId(productId).orElseThrow().getQuantity());
        assertEquals(movementsBefore + 1, stockMovementCount());
    }

    @Test
    void acceptRejectsNonSeller() throws Exception {
        Long orderId = placeOrderAndReturnId();

        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------------
    // Reject
    // ------------------------------------------------------------------

    @Test
    void rejectSucceeds() throws Exception {
        Long orderId = placeOrderAndReturnId();

        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setReason("Out of stock");

        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/reject", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ------------------------------------------------------------------
    // Cancel
    // ------------------------------------------------------------------

    @Test
    void cancelByBuyerOnPlacedSucceeds() throws Exception {
        Long orderId = placeOrderAndReturnId();

        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setReason("Changed my mind");

        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelBySellerOnAcceptedRestoresStock() throws Exception {
        Long orderId = placeOrderAndReturnId();

        // Accept first (deducts stock).
        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isOk());

        int quantityBefore = inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();

        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setReason("No longer needed");

        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Stock restored: quantity goes back to 20.
        assertEquals(quantityBefore + 3, inventoryRepository.findByProductId(productId).orElseThrow().getQuantity());
    }

    // ------------------------------------------------------------------
    // Ship
    // ------------------------------------------------------------------

    @Test
    void shipSucceedsWithReference() throws Exception {
        Long orderId = placeOrderAndReturnId();
        acceptOrder(orderId);

        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setShipmentReference("TRACK-123");

        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shipmentReference").value("TRACK-123"));
    }

    @Test
    void shipRejectsWithoutReference() throws Exception {
        Long orderId = placeOrderAndReturnId();
        acceptOrder(orderId);

        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setShipmentReference(null);

        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------
    // Deliver
    // ------------------------------------------------------------------

    @Test
    void deliverSucceeds() throws Exception {
        Long orderId = placeOrderAndReturnId();
        acceptOrder(orderId);
        shipOrder(orderId);

        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/deliver", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
    }

    // ------------------------------------------------------------------
    // RBAC
    // ------------------------------------------------------------------

    @Test
    void anonymousCallsAreUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/vendors/b2b/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendors/b2b/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendors/b2b/sellers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerIsForbiddenByNamespaceRule() throws Exception {
        mockMvc.perform(post("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private User createUser(String email, String password, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Test " + roleName)
                .phone("+91" + randomDigits(10))
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
        return user;
    }

    private Long placeOrderAndReturnId() throws Exception {
        PlaceB2BOrderRequest request = new PlaceB2BOrderRequest();
        request.setSellerId(sellerProfileId);
        request.setItems(List.of(new B2BOrderItemRequest(productId, 3)));
        request.setOrderNote("Integration test order");

        MvcResult result = mockMvc.perform(post("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void acceptOrder(Long orderId) throws Exception {
        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isOk());
    }

    private void shipOrder(Long orderId) throws Exception {
        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setShipmentReference("TRACK-INTEGRATION");
        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private String tokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail())
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(auth.getAccessToken());
        return auth.getAccessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private int stockMovementCount() {
        return Math.toIntExact(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_movements", Long.class));
    }

    private static String randomDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return digits.toString();
    }
}
