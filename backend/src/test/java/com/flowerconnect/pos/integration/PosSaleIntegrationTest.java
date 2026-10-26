package com.flowerconnect.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.pos.dto.PosSaleResponse;
import com.flowerconnect.pos.repository.PosItemRepository;
import com.flowerconnect.pos.repository.PosTransactionRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the POS walk-in sale API (plan Phase 11, tasks 11.2-11.3)
 * against a real MySQL instance and the production security chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PosSaleIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String PINCODE = "560034";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private PosTransactionRepository posTransactionRepository;
    @Autowired
    private PosItemRepository posItemRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long rosesCategoryId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        customerToken = tokenFor(createUser(uniqueEmail(), "CUSTOMER"));
    }

    // ------------------------------------------------------------------
    // Approval gating and role boundary
    // ------------------------------------------------------------------

    @Test
    void aPendingVendorIsRefusedForPosSales() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.PENDING_APPROVAL);
        Long productId = createProduct(vendor, "Pending Stock Rose");

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 1, "CASH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));

        assertEquals(0L, posTransactionRepository.count());
    }

    @Test
    void aSuspendedVendorIsRefusedForPosSales() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.SUSPENDED);
        Long productId = createProduct(vendor, "Suspension Stock Rose");

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 1, "CASH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));

        assertEquals(0L, posTransactionRepository.count());
    }

    @Test
    void aCustomerIsStoppedByNamespaceRule() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Customer Rose");

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 1, "CASH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anAnonymousCallerIsUnauthenticated() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Anonymous Rose");

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 1, "CASH")))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Product ownership and sellability
    // ------------------------------------------------------------------

    @Test
    void aForeignProductIsForbidden() throws Exception {
        Vendor mine = registerVendor(VendorProfile.Status.APPROVED);
        Vendor theirs = registerVendor(VendorProfile.Status.APPROVED);
        Long theirProduct = createProduct(theirs, "Their Rose");

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(mine.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(theirProduct, 1, "CASH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void aNonexistentProductIsNotFound() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999L, 1, "CASH")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anInactiveProductIsRefused() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Inactive Rose");
        setProductStatus(productId, ProductStatus.INACTIVE);

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 1, "CASH")))
                .andExpect(status().isConflict());
    }

    @Test
    void anExpiredProductIsRefused() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Expired Rose");
        setProductExpired(productId);

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 1, "CASH")))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // Exact stock deduction and movement type / reference
    // ------------------------------------------------------------------

    @Test
    void aSuccessfulSaleDeductsStockAndRecordsSalePosMovements() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Sale Stock Rose");
        stockIn(vendor, productId, 10, "delivery");

        MvcResult result = mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 3, "CASH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.vendorName").value("Integration Probe Blossoms"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.lines[0].productId").value(productId.intValue()))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.lines[0].unitPriceSnapshot").value(299.00))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(897.00))
                .andExpect(jsonPath("$.total").value(897.00))
                .andReturn();

        assertEquals(7L, quantityOf(productId));
        assertEquals(1L, movementsOf(productId, MovementType.SALE_POS));
        assertEquals(1L, posTransactionRepository.count());
        assertEquals(1L, posItemRepository.count());
    }

    @Test
    void clientPriceIsIgnoredPriceComesFromServer() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Server Price Rose");
        stockIn(vendor, productId, 10, "delivery");

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithExtra(productId, 2, "CASH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPriceSnapshot").value(299.00))
                .andExpect(jsonPath("$.total").value(598.00));

        assertEquals(8L, quantityOf(productId));
    }

    // ------------------------------------------------------------------
    // Rollback on shortage
    // ------------------------------------------------------------------

    @Test
    void aShortageInALaterLineRollsBackEverything() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long abundant = createProduct(vendor, "Abundant Rose");
        Long scarce = createProduct(vendor, "Scarce Rose");
        stockIn(vendor, abundant, 10, "delivery");
        stockIn(vendor, scarce, 1, "delivery");

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyTwoLines(abundant, 2, scarce, 5, "CASH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertEquals(0L, posTransactionRepository.count());
        assertEquals(0L, posItemRepository.count());
        assertEquals(10L, quantityOf(abundant));
        assertEquals(1L, quantityOf(scarce));
        assertEquals(0L, movementsOf(abundant));
        assertEquals(0L, movementsOf(scarce));
    }

    // ------------------------------------------------------------------
    // Reserved stock cannot be sold
    // ------------------------------------------------------------------

    @Test
    void reservedStockCannotBeSold() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Reserved Rose");
        stockIn(vendor, productId, 10, "delivery");
        reserve(productId, 9);

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 2, "CASH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertEquals(10L, quantityOf(productId));
        assertEquals(9L, reservedQuantityOf(productId));
        assertEquals(0L, movementsOf(productId));
    }

    // ------------------------------------------------------------------
    // Idempotency
    // ------------------------------------------------------------------

    @Test
    void replaySameKeySameBodyReturnsSameTransactionAndDeductsOnce() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Replay Rose");
        stockIn(vendor, productId, 10, "delivery");
        String key = uniqueKey();

        MvcResult first = mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", key)
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 2, "CASH")))
                .andExpect(status().isOk()).andReturn();
        Long firstId = objectMapper.readValue(
                first.getResponse().getContentAsString(),
                PosSaleResponse.class).getTransactionId();

        MvcResult replay = mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", key)
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 2, "CASH")))
                .andExpect(status().isOk()).andReturn();
        Long replayId = objectMapper.readValue(
                replay.getResponse().getContentAsString(),
                PosSaleResponse.class).getTransactionId();

        assertEquals(firstId, replayId, "replay must return the same transaction");
        assertEquals(8L, quantityOf(productId), "deduction happened once");
        assertEquals(1L, posTransactionRepository.count());
        assertEquals(1L, movementsOf(productId, MovementType.SALE_POS));
    }

    @Test
    void sameKeyDifferentBodyIsRejected() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Conflict Rose");
        stockIn(vendor, productId, 10, "delivery");
        String key = uniqueKey();

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", key)
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 1, "CASH")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", key)
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 2, "CARD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void concurrentIdenticalRequestsCreateOneSale() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Concurrent Rose");
        stockIn(vendor, productId, 10, "delivery");
        String key = uniqueKey();

        int threads = 8;
        Throwable[] failures = new Throwable[1];
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    mockMvc.perform(post("/api/v1/pos/sales")
                            .header("Idempotency-Key", key)
                            .header("Authorization", bearer(vendor.token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(productId, 1, "CASH")));
                } catch (Throwable t) {
                    failures[0] = t;
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1L, posTransactionRepository.count(),
                "exactly one sale may be created for the same key and body");
        assertEquals(9L, quantityOf(productId));
    }

    // ------------------------------------------------------------------
    // Summary
    // ------------------------------------------------------------------

    @Test
    void todaySummaryReturnsCountRevenueAndBreakdown() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Summary Rose");
        stockIn(vendor, productId, 10, "delivery");

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 2, "CASH")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/pos/summary")
                        .header("Authorization", bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(598.00))
                .andExpect(jsonPath("$.breakdownByPaymentMethod[0].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.breakdownByPaymentMethod[0].transactionCount").value(1))
                .andExpect(jsonPath("$.breakdownByPaymentMethod[0].revenue").value(598.00));
    }

    // ------------------------------------------------------------------
    // No ledger footprint
    // ------------------------------------------------------------------

    @Test
    void noOrderPaymentCommissionOrLedgerRowIsCreated() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Ledger Rose");
        stockIn(vendor, productId, 10, "delivery");

        long ordersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        long paymentsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Long.class);
        long orderItemsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Long.class);

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Idempotency-Key", uniqueKey())
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 1, "CARD")))
                .andExpect(status().isOk());

        assertEquals(ordersBefore, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
        assertEquals(paymentsBefore, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Long.class));
        assertEquals(orderItemsBefore, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Long.class));
        assertEquals(1L, posTransactionRepository.count());
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private String body(Long productId, int quantity, String paymentMethod) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", productId, "quantity", quantity)),
                "paymentMethod", paymentMethod));
    }

    private String bodyTwoLines(Long p1, int q1, Long p2, int q2, String paymentMethod) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "items", List.of(
                        Map.of("productId", p1, "quantity", q1),
                        Map.of("productId", p2, "quantity", q2)),
                "paymentMethod", paymentMethod));
    }

    private String bodyWithExtra(Long productId, int quantity, String paymentMethod) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", productId, "quantity", quantity)),
                "paymentMethod", paymentMethod,
                "unitPrice", 99999.99,
                "total", 99999.99));
    }

    private void stockIn(Vendor vendor, Long productId, int quantity, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/vendors/products/" + productId + "/inventory/stock-in")
                        .header("Authorization", bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", quantity, "reason", reason))))
                .andExpect(status().isOk());
    }

    private Long quantityOf(Long productId) {
        return inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();
    }

    private Long reservedQuantityOf(Long productId) {
        return inventoryRepository.findByProductId(productId).orElseThrow().getReservedQuantity();
    }

    private long movementsOf(Long productId) {
        return stockMovementRepository.countByProductId(productId);
    }

    private long movementsOf(Long productId, MovementType type) {
        return stockMovementRepository.countByProductIdAndMovementType(productId, type);
    }

    private Vendor registerVendor(VendorProfile.Status target) throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest(email))))
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
            }
            default -> throw new IllegalArgumentException("unhandled status " + target);
        }

        return new Vendor(email, token, profileId);
    }

    private Long createProduct(Vendor vendor, String name) throws Exception {
        return productService.create(vendor.email(), ProductRequest.builder()
                        .name(name)
                        .categoryId(rosesCategoryId)
                        .description("Integration probe")
                        .basePrice(new BigDecimal("299.00"))
                        .build())
                .getId();
    }

    private void setProductStatus(Long productId, ProductStatus status) {
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStatus(status);
        productRepository.saveAndFlush(product);
    }

    private void setProductExpired(Long productId) {
        Product product = productRepository.findById(productId).orElseThrow();
        Inventory inv = inventoryRepository.findByProductId(productId).orElseThrow();
        inv.setExpiryDate(java.time.LocalDate.of(2020, 1, 1));
        inventoryRepository.saveAndFlush(inv);
    }

    private void reserve(Long productId, int reserved) {
        assertEquals(1, jdbcTemplate.update(
                "UPDATE inventory SET reserved_quantity = ? WHERE product_id = ?", reserved, productId));
    }

    private void approve(Long profileId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/approve")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk());
    }

    private String adminToken() throws Exception {
        return tokenFor(createUser(uniqueEmail(), "ADMIN"));
    }

    private String tokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail())
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk()).andReturn();
        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getAccessToken();
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

    private static String uniqueEmail() {
        return "pos-" + UUID.randomUUID() + "@test.com";
    }

    private static String uniqueKey() {
        return UUID.randomUUID().toString();
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

    private record Vendor(String email, String token, Long profileId) {
    }

    private VendorRegisterRequest registerRequest(String email) {
        VendorRegisterRequest request = new VendorRegisterRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setFullName("Vendor Owner");
        request.setPhone(uniquePhone());
        request.setBusinessName("Integration Probe Blossoms");
        request.setDescription("Fresh flowers delivered daily");
        request.setAddressLine1("12 Test Street");
        request.setServiceLocationId(serviceLocationRepository.findByPincode(PINCODE).orElseThrow().getId());
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
}
