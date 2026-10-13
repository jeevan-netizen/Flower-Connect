package com.flowerconnect.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.cart.dto.AddCartItemRequest;
import com.flowerconnect.cart.dto.UpdateCartItemRequest;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for plan task 5.2 (customer cart API) against a real
 * MySQL instance and the production security chain.
 *
 * <p>This is the only place the cart behavior can be proven: RBAC (the
 * namespace rule in {@code SecurityConfig}), customer isolation (ownership
 * resolved from the JWT subject), eligibility on add (product ACTIVE, vendor
 * APPROVED, accepting orders), stock checks, and the lazy-cart semantics all
 * depend on real SQL and the full filter chain.
 *
 * <p>The shared singleton MySQL container accumulates rows across every IT
 * class, so each test creates its own vendor and customer and scopes its
 * assertions to those rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String KORAMANGALA_PINCODE = "560034";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    private InventoryRepository inventoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long rosesCategoryId;
    private String adminToken;
    private String adminEmail;
    private String customerToken;
    private String customerEmail;
    private String otherCustomerToken;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE)
                .orElseThrow().getId();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        User admin = createUser(uniqueEmail(), "ADMIN");
        adminToken = tokenFor(admin);
        adminEmail = admin.getEmail();
        User customer = createUser(uniqueEmail(), "CUSTOMER");
        customerToken = tokenFor(customer);
        customerEmail = customer.getEmail();
        otherCustomerToken = tokenFor(createUser(uniqueEmail(), "CUSTOMER"));
    }

    // ================================================================ RBAC matrix

    @Test
    void anUnauthenticatedRequestIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aFloristCannotUseTheCustomerCart() throws Exception {
        String floristToken = registerVendor(VendorProfile.Status.APPROVED).token();

        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(floristToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anAdminCannotUseTheCustomerCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ----------------------------------------------------------------- getCart

    @Test
    void getCartReturnsEmptyWhenNoCartExists() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.subtotal").value(0))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.shops").isEmpty())
                .andExpect(jsonPath("$.cartId").doesNotExist());
    }

    // ----------------------------------------------------------------- addItem

    @Test
    void addItemCreatesCartLazilyAndReturns201() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 10, "initial");

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(productId)
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId.intValue()))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.priceSnapshot").value(299.00))
                .andExpect(jsonPath("$.subtotal").value(598.00))
                .andExpect(jsonPath("$.purchasable").value(true));
    }

    @Test
    void addItemToExistingCartIncrementsQuantity() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 10, "initial");

        AddCartItemRequest add1 = AddCartItemRequest.builder()
                .productId(productId).quantity(2).build();
        AddCartItemRequest add2 = AddCartItemRequest.builder()
                .productId(productId).quantity(3).build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(add1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(add2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    void addItemRejectsInsufficientStock() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 1, "minimal");

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(productId).quantity(5).build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void addItemRejectsInactiveProductAsNotFound() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Hidden Rose");
        // Product is DRAFT by default from createProduct; leave it DRAFT to test 404

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(productId).quantity(1).build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void addItemRejectsNonApprovedVendorWithForbidden() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.PENDING_APPROVAL);
        Long productId = createActiveProduct(vendor, "Pending Vendor Rose");

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(productId).quantity(1).build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void addItemRejectsVendorNotAcceptingOrdersWithConflict() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        // Flip accepting_orders to false directly
        VendorProfile vp = vendorProfileRepository.findById(vendor.profileId()).orElseThrow();
        vp.setAcceptingOrders(false);
        vendorProfileRepository.saveAndFlush(vp);

        Long productId = createActiveProduct(vendor, "Closed Vendor Rose");

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(productId).quantity(1).build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ------------------------------------------------------------- updateItem

    @Test
    void updateItemChangesQuantity() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 50, "generous");

        Long itemId = addItemAndReturnId(customerToken, productId, 2);

        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(5).build();

        mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.subtotal").value(1495.00));
    }

    @Test
    void updateItemWithQuantityZeroDeletesTheLine() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 50, "generous");

        Long itemId = addItemAndReturnId(customerToken, productId, 3);

        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(0).build();

        mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertEquals(0, itemCountInDb());
    }

    @Test
    void updateItemRejectsInsufficientStock() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 4, "limited");

        Long itemId = addItemAndReturnId(customerToken, productId, 1);

        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(10).build();

        mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void updateItemOnUnknownIdReturns404() throws Exception {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(5).build();

        mockMvc.perform(patch("/api/v1/cart/items/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ----------------------------------------------------------- removeItem

    @Test
    void removeItemDeletesTheLine() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 50, "generous");

        Long itemId = addItemAndReturnId(customerToken, productId, 3);

        mockMvc.perform(delete("/api/v1/cart/items/" + itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isNoContent());

        assertEquals(0, itemCountInDb());
    }

    // ------------------------------------------------------------- clearCart

    @Test
    void clearCartDeletesAllItems() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId1 = createActiveProduct(vendor, "Clear Rose 1");
        Long productId2 = createActiveProduct(vendor, "Clear Rose 2");
        stockIn(vendor, productId1, 50, "plenty");
        stockIn(vendor, productId2, 50, "plenty");

        addItemAndReturnId(customerToken, productId1, 1);
        addItemAndReturnId(customerToken, productId2, 1);

        assertEquals(2, itemCountInDb());

        mockMvc.perform(delete("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isNoContent());

        assertEquals(0, itemCountInDb());
    }

    // ----------------------------------------------------- customer isolation

    @Test
    void oneCustomerCannotAccessAnotherCustomersCart() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 50, "plenty");

        addItemAndReturnId(customerToken, productId, 2);

        // The other customer has no cart — GET should return empty
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        // The other customer cannot add using a URL referencing the first customer's item id
        // (paths are not customer-scoped, but the service resolves the caller from the JWT)
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AddCartItemRequest.builder().productId(productId).quantity(1).build())))
                .andExpect(status().isCreated());

        // Original customer still has exactly 1 item
        JsonNode cart = getCart(customerToken);
        assertEquals(1, cart.get("items").size());
    }

    // --------------------------------------------------------- getCart with items

    @Test
    void getCartReturnsItemsWithVendorGrouping() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 50, "plenty");

        addItemAndReturnId(customerToken, productId, 3);

        JsonNode cart = getCart(customerToken);

        assertEquals(1, cart.get("items").size());
        assertEquals(1, cart.get("shops").size());
        assertEquals(1, cart.get("shops").get(0).get("itemCount").asInt());
        assertEquals(897.00, cart.get("shops").get(0).get("subtotal").asDouble());
        assertEquals(897.00, cart.get("subtotal").asDouble());
        assertEquals(1, cart.get("totalItems").asInt());
        assertTrue(cart.get("items").get(0).get("purchasable").asBoolean());
    }

    @Test
    void getCartMarksLowStockItemAsNotPurchasable() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor);
        stockIn(vendor, productId, 10, "plenty");

        // Add 5 items while stock is sufficient
        addItemAndReturnId(customerToken, productId, 5);

        // Now drain stock below the cart quantity via a direct inventory update,
        // simulating another customer's purchase or a stock-out
        jdbcTemplate.update("UPDATE inventory SET quantity = 2 WHERE product_id = ?", productId);

        JsonNode cart = getCart(customerToken);

        assertFalse(cart.get("items").get(0).get("purchasable").asBoolean());
        assertEquals("Insufficient stock", cart.get("items").get(0).get("notPurchasableReason").asText());
    }

    // ================================================================ helpers

    /** Records for a vendor fixture with its token and profile id. */
    private record Vendor(String email, String token, Long profileId) {
    }

    private Vendor registerVendor(VendorProfile.Status target) throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerRequest(email))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        Long profileId = vendorProfileRepository.findByUserId(user.getId()).orElseThrow().getId();
        String token = tokenFor(user);

        switch (target) {
            case APPROVED -> approve(profileId);
            case SUSPENDED -> {
                approve(profileId);
                adminAction(profileId, "suspend").andExpect(status().isOk());
            }
            case PENDING_APPROVAL -> {
                // freshly registered
            }
            case REJECTED -> adminAction(profileId, "reject")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        }

        return new Vendor(email, token, profileId);
    }

    private Vendor vendor(VendorProfile.Status target) {
        try {
            return registerVendor(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Long createActiveProduct(Vendor vendor) throws Exception {
        return createActiveProduct(vendor, "Cart Test Rose");
    }

    private Long createActiveProduct(Vendor vendor, String name) throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name(name)
                .categoryId(rosesCategoryId)
                .description("Fresh flowers")
                .basePrice(new BigDecimal("299.00"))
                .status(ProductStatus.ACTIVE)
                .build();

        return productServiceCreate(vendor, request);
    }

    private Long createProduct(Vendor vendor, String name) {
        ProductRequest request = ProductRequest.builder()
                .name(name)
                .categoryId(rosesCategoryId)
                .description("Fresh flowers")
                .basePrice(new BigDecimal("299.00"))
                .status(ProductStatus.DRAFT)
                .build();
        return productServiceCreate(vendor, request);
    }

    @Autowired
    private ProductService productService;

    private Long productServiceCreate(Vendor vendor, ProductRequest request) {
        return productService.create(vendor.email(), request).getId();
    }

    private void stockIn(Vendor vendor, Long productId, int quantity, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/vendors/products/" + productId + "/inventory/stock-in")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", quantity, "reason", reason))))
                .andExpect(status().isOk());
    }

    private Long addItemAndReturnId(String token, Long productId, int quantity) throws Exception {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(productId).quantity(quantity).build();
        String body = mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private JsonNode getCart(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private int itemCountInDb() {
        Long cartId = jdbcTemplate.queryForObject(
                "SELECT id FROM carts WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                Long.class, customerEmail);
        if (cartId == null) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", Integer.class, cartId);
        return count != null ? count : 0;
    }

    private void approve(Long profileId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private ResultActions adminAction(Long profileId, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"cart integration probe\"}"));
    }

    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Test " + roleName)
                .phone(uniquePhone())
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
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

    private VendorRegisterRequest registerRequest(String email) {
        VendorRegisterRequest request = new VendorRegisterRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setFullName("Vendor Owner");
        request.setPhone(uniquePhone());
        request.setBusinessName("Cart Probe Blossoms");
        request.setDescription("Fresh flowers delivered daily");
        request.setAddressLine1("12 Test Street");
        request.setServiceLocationId(locationId);
        request.setDeliveryRadiusKm(new BigDecimal("6.50"));
        request.setMinOrderAmount(new BigDecimal("199.99"));
        request.setBaseDeliveryFee(new BigDecimal("25.50"));
        request.setPerKmFee(new BigDecimal("1.75"));
        request.setPrepTimeMinutes(45);
        request.setSlotDurationMinutes(60);
        request.setMaxOrdersPerSlot(10);
        request.setAcceptingOrders(true);
        return request;
    }

    private static String uniqueEmail() {
        return "cart-" + UUID.randomUUID() + "@test.com";
    }

    private static String uniquePhone() {
        Random random = new Random();
        StringBuilder digits = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            digits.append(random.nextInt(10));
        }
        return "+91" + digits;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
