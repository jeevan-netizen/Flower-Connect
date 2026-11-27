package com.flowerconnect.b2b;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BStatement.PaymentMode;
import com.flowerconnect.b2b.dto.B2BOrderActionRequest;
import com.flowerconnect.b2b.dto.B2BOrderItemRequest;
import com.flowerconnect.b2b.dto.B2BStatementPreviewResponse;
import com.flowerconnect.b2b.dto.PlaceB2BOrderRequest;
import com.flowerconnect.b2b.dto.CreateB2BStatementRequest;
import com.flowerconnect.b2b.dto.RecordB2BStatementPaymentRequest;
import com.flowerconnect.b2b.repository.B2BOrderRepository;
import com.flowerconnect.b2b.repository.B2BOrderItemRepository;
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
import com.flowerconnect.test.MutableClock;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the B2B statement API (Stage 3 of the B2B module)
 * against a real MySQL instance and the production security chain.
 *
 * <p>Orders are delivered through the real B2B order flow (place, accept, ship,
 * deliver) so each delivered order carries {@code status DELIVERED} and
 * {@code payment_status UNPAID}, which is exactly what a statement covers.
 *
 * <p>The injected {@link MutableClock} is pinned to the current real time so
 * that JWT tokens (whose expiry jjwt validates against the system clock)
 * remain valid during the test; delivered orders are therefore stamped with
 * the current date, which the date-range tests use as the anchor.
 */
@SpringBootTest
@AutoConfigureMockMvc
class B2BStatementIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String KORAMANGALA_PINCODE = "560034";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;
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
    private String thirdToken;
    private String customerToken;
    private String adminToken;
    private Long productId;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        // Pin the clock to the current real time so JWT tokens remain valid
        // (jjwt validates expiry against the system clock, not the injected clock).
        clock.setInstant(Instant.now());
        today = LocalDate.now(clock);

        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE).orElseThrow().getId();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();

        // Buyer vendor (approved).
        User buyerUser = createUser("b2b-stmt-buyer-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        buyerEmail = buyerUser.getEmail();
        buyerProfileId = createVendor(buyerUser, "Buyer Florist").getId();
        buyerToken = tokenFor(buyerUser);

        // Seller vendor (approved).
        User sellerUser = createUser("b2b-stmt-seller-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        sellerEmail = sellerUser.getEmail();
        sellerProfileId = createVendor(sellerUser, "Seller Florist").getId();
        sellerToken = tokenFor(sellerUser);

        // Third vendor (approved, not a party to any statement).
        User thirdUser = createUser("b2b-stmt-third-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        createVendor(thirdUser, "Third Florist");
        thirdToken = tokenFor(thirdUser);

        customerToken = tokenFor(createUser("b2b-stmt-customer-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER"));
        adminToken = tokenFor(createUser("b2b-stmt-admin-" + UUID.randomUUID() + "@test.com", PASSWORD, "ADMIN"));

        // Active product owned by the seller with stock.
        productId = createProduct(sellerEmail, "B2B Statement Rose " + UUID.randomUUID(), new BigDecimal("199.00"));
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        inventory.setQuantity(100);
        inventory.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(inventory);
    }

    // ------------------------------------------------------------------
    // Preview
    // ------------------------------------------------------------------

    @Test
    void previewTotalEqualsSumOfEligibleOrders() throws Exception {
        Long orderA = deliverOrderOn(new BigDecimal("199.00"), 1);
        Long orderB = deliverOrderOn(new BigDecimal("398.00"), 2);

        // Both orders delivered today; preview the whole of today.
        MvcResult result = mockMvc.perform(get("/api/v1/vendors/b2b/statements/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("buyerId", buyerProfileId.toString())
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.totalAmount").value("597.0"))
                .andReturn();

        B2BStatementPreviewResponse preview = objectMapper.readValue(
                result.getResponse().getContentAsString(), B2BStatementPreviewResponse.class);
        assertThat(preview.getTotalAmount()).isEqualByComparingTo("597.00");
        assertThat(preview.getOrders()).extracting("orderNo")
                .contains(b2bOrderRepository.findById(orderA).orElseThrow().getOrderNo(),
                        b2bOrderRepository.findById(orderB).orElseThrow().getOrderNo());
    }

    @Test
    void previewDefaultsToLast15DaysWhenRangeOmitted() throws Exception {
        Long orderA = deliverOrderOn(new BigDecimal("199.00"), 1);
        Long orderB = deliverOrderOn(new BigDecimal("199.00"), 1);

        // Defaults: to = today, from = today - 15 days. Both orders delivered
        // today are in range.
        mockMvc.perform(get("/api/v1/vendors/b2b/statements/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("buyerId", buyerProfileId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.totalAmount").value("398.0"));
    }

    @Test
    void previewExcludesNonDeliveredOrders() throws Exception {
        Long placedId = placeOrderAndReturnId(); // PLACED, never delivered

        mockMvc.perform(get("/api/v1/vendors/b2b/statements/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("buyerId", buyerProfileId.toString())
                        .param("from", today.minusDays(10).toString())
                        .param("to", today.plusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(0))
                .andExpect(jsonPath("$.totalAmount").value("0"));

        assertThat(b2bOrderRepository.findById(placedId).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void previewExcludesOutOfRangeOrders() throws Exception {
        deliverOrderOn(new BigDecimal("199.00"), 1);

        // Range far in the past excludes today's delivery.
        mockMvc.perform(get("/api/v1/vendors/b2b/statements/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("buyerId", buyerProfileId.toString())
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(0));
    }

    @Test
    void previewExcludesWrongBuyer() throws Exception {
        User otherBuyer = createUser("b2b-stmt-otherbuyer-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        Long otherBuyerProfileId = createVendor(otherBuyer, "Other Buyer Florist").getId();
        String otherBuyerToken = tokenFor(otherBuyer);
        Long otherOrder = placeOrder(otherBuyerToken, sellerProfileId, 1);
        acceptOrder(sellerToken, otherOrder);
        shipOrder(sellerToken, otherOrder);
        deliverOrder(sellerToken, otherOrder);

        mockMvc.perform(get("/api/v1/vendors/b2b/statements/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("buyerId", buyerProfileId.toString())
                        .param("from", today.minusDays(10).toString())
                        .param("to", today.plusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(0));
    }

    @Test
    void previewExcludesAlreadyStatementedOrders() throws Exception {
        Long orderId = deliverOrderOn(new BigDecimal("199.00"), 1);
        createStatementAndReturnId(sellerToken, buyerProfileId,
                today.minusDays(10), today.plusDays(10));

        mockMvc.perform(get("/api/v1/vendors/b2b/statements/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("buyerId", buyerProfileId.toString())
                        .param("from", today.minusDays(10).toString())
                        .param("to", today.plusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(0));
    }

    // ------------------------------------------------------------------
    // Create statement
    // ------------------------------------------------------------------

    @Test
    void createStatementTotalEqualsSumOfEligibleOrders() throws Exception {
        Long orderA = deliverOrderOn(new BigDecimal("199.00"), 1);
        Long orderB = deliverOrderOn(new BigDecimal("398.00"), 2);

        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(buyerProfileId);
        request.setFrom(today);
        request.setTo(today);

        mockMvc.perform(post("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.totalAmount").value("597.0"))
                .andExpect(jsonPath("$.orders.length()").value(2));

        int linksForOrderA = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM b2b_statement_orders WHERE order_id = ?",
                Integer.class, orderA);
        int linksForOrderB = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM b2b_statement_orders WHERE order_id = ?",
                Integer.class, orderB);
        assertEquals(1, linksForOrderA);
        assertEquals(1, linksForOrderB);
    }

    @Test
    void createStatementRejectsEmpty() throws Exception {
        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(buyerProfileId);
        request.setFrom(today.minusDays(10));
        request.setTo(today.plusDays(10));

        mockMvc.perform(post("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createStatementRejectsSelf() throws Exception {
        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(sellerProfileId);

        mockMvc.perform(post("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createStatementRejectsInvalidRange() throws Exception {
        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(buyerProfileId);
        request.setFrom(today);
        request.setTo(today.minusDays(1));

        mockMvc.perform(post("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void twoStatementsCannotContainTheSameOrder() throws Exception {
        Long orderId = deliverOrderOn(new BigDecimal("199.00"), 1);

        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(buyerProfileId);
        request.setFrom(today.minusDays(10));
        request.setTo(today.plusDays(10));
        String body = objectMapper.writeValueAsString(request);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        Callable<MvcResult> task = () -> {
            latch.countDown();
            latch.await();
            return mockMvc.perform(post("/api/v1/vendors/b2b/statements")
                    .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andReturn();
        };
        List<Callable<MvcResult>> tasks = List.of(task, task);
        List<Future<MvcResult>> results = executor.invokeAll(tasks);
        executor.shutdown();

        int s1 = results.get(0).get().getResponse().getStatus();
        int s2 = results.get(1).get().getResponse().getStatus();
        List<Integer> statuses = List.of(s1, s2);

        long created = statuses.stream().filter(s -> s == 201).count();
        assertEquals(1, created);
        int other = statuses.stream().filter(s -> s != 201).findFirst().orElseThrow();
        assertThat(other).isIn(400, 409);

        int statementsForOrder = jdbcTemplate.queryForObject(
                "SELECT COUNT(s.id) FROM b2b_statements s " +
                        "JOIN b2b_statement_orders so ON so.statement_id = s.id " +
                        "WHERE so.order_id = ?",
                Integer.class, orderId);
        assertEquals(1, statementsForOrder);

        int linksForOrder = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM b2b_statement_orders WHERE order_id = ?",
                Integer.class, orderId);
        assertEquals(1, linksForOrder);
    }

    // ------------------------------------------------------------------
    // List and detail
    // ------------------------------------------------------------------

    @Test
    void listAndDetailVisibleToBothParties() throws Exception {
        deliverOrderOn(new BigDecimal("199.00"), 1);
        Long statementId = createStatementAndReturnId(sellerToken, buyerProfileId,
                today.minusDays(10), today.plusDays(10));

        mockMvc.perform(get("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(statementId.intValue()));
        mockMvc.perform(get("/api/v1/vendors/b2b/statements/{statementId}", statementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(statementId.intValue()));

        mockMvc.perform(get("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .param("role", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(statementId.intValue()));
        mockMvc.perform(get("/api/v1/vendors/b2b/statements/{statementId}", statementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void listRoleFilteringCannotBypassOwnership() throws Exception {
        deliverOrderOn(new BigDecimal("199.00"), 1);
        createStatementAndReturnId(sellerToken, buyerProfileId,
                today.minusDays(10), today.plusDays(10));

        mockMvc.perform(get("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .param("role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("role", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void thirdVendorCannotViewStatement() throws Exception {
        deliverOrderOn(new BigDecimal("199.00"), 1);
        Long statementId = createStatementAndReturnId(sellerToken, buyerProfileId,
                today.minusDays(10), today.plusDays(10));

        mockMvc.perform(get("/api/v1/vendors/b2b/statements/{statementId}", statementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(thirdToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(thirdToken))
                        .param("role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void invalidRoleIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("role", "CUSTOMER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------
    // Record payment
    // ------------------------------------------------------------------

    @Test
    void recordPaymentMarksStatementAndOrdersPaid() throws Exception {
        deliverOrderOn(new BigDecimal("199.00"), 1);
        Long statementId = createStatementAndReturnId(sellerToken, buyerProfileId,
                today.minusDays(10), today.plusDays(10));
        Long orderId = b2bOrderRepository.findByBuyerIdAndStatus(buyerProfileId, Status.DELIVERED).stream()
                .findFirst().orElseThrow().getId();

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(PaymentMode.UPI);
        request.setPaymentNote("Received via UPI");

        mockMvc.perform(post("/api/v1/vendors/b2b/statements/{statementId}/payment", statementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMode").value("UPI"))
                .andExpect(jsonPath("$.paymentNote").value("Received via UPI"));

        assertThat(b2bOrderRepository.findById(orderId).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.PAID);

        mockMvc.perform(get("/api/v1/vendors/b2b/statements/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .param("buyerId", buyerProfileId.toString())
                        .param("from", today.minusDays(10).toString())
                        .param("to", today.plusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(0));
    }

    @Test
    void recordPaymentRejectsAlreadyPaid() throws Exception {
        deliverOrderOn(new BigDecimal("199.00"), 1);
        Long statementId = createStatementAndReturnId(sellerToken, buyerProfileId,
                today.minusDays(10), today.plusDays(10));
        payStatement(sellerToken, statementId);

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(PaymentMode.CASH);

        mockMvc.perform(post("/api/v1/vendors/b2b/statements/{statementId}/payment", statementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void recordPaymentRejectsBuyer() throws Exception {
        deliverOrderOn(new BigDecimal("199.00"), 1);
        Long statementId = createStatementAndReturnId(sellerToken, buyerProfileId,
                today.minusDays(10), today.plusDays(10));

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(PaymentMode.CASH);

        mockMvc.perform(post("/api/v1/vendors/b2b/statements/{statementId}/payment", statementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void recordPaymentRejectsMissingStatement() throws Exception {
        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(PaymentMode.CASH);

        mockMvc.perform(post("/api/v1/vendors/b2b/statements/999999/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // RBAC
    // ------------------------------------------------------------------

    @Test
    void anonymousCallsAreUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/b2b/statements/preview"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/vendors/b2b/statements"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendors/b2b/statements"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendors/b2b/statements/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/vendors/b2b/statements/1/payment"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerIsForbiddenByNamespaceRule() throws Exception {
        mockMvc.perform(post("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private User createUser(String email, String password, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Test " + roleName)
                .phone("+91" + randomDigits(10))
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
    }

    private VendorProfile createVendor(User user, String businessName) {
        return vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(user)
                .businessName(businessName)
                .addressLine1("1 Test Street")
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
    }

    private Long createProduct(String vendorEmail, String name, BigDecimal price) {
        ProductRequest productRequest = ProductRequest.builder()
                .name(name)
                .categoryId(rosesCategoryId)
                .description("Fresh roses for B2B statement tests")
                .basePrice(price)
                .status(ProductStatus.ACTIVE)
                .build();
        return productService.create(vendorEmail, productRequest).getId();
    }

    private Long placeOrder(String token, Long sellerId, int quantity) throws Exception {
        PlaceB2BOrderRequest request = new PlaceB2BOrderRequest();
        request.setSellerId(sellerId);
        request.setItems(List.of(new B2BOrderItemRequest(productId, quantity)));
        request.setOrderNote("Statement test order");

        MvcResult result = mockMvc.perform(post("/api/v1/vendors/b2b/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long placeOrderAndReturnId() throws Exception {
        return placeOrder(buyerToken, sellerProfileId, 1);
    }

    private void acceptOrder(String token, Long orderId) throws Exception {
        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void shipOrder(String token, Long orderId) throws Exception {
        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setShipmentReference("TRACK-STMT");
        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void deliverOrder(String token, Long orderId) throws Exception {
        mockMvc.perform(post("/api/v1/vendors/b2b/orders/{orderId}/deliver", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    /**
     * Places, accepts, ships and delivers an order, stamping {@code deliveredAt}
     * with the current clock (pinned to real time in {@link #setUp}). Returns
     * the order id.
     */
    private Long deliverOrderOn(BigDecimal expectedTotal, int quantity) throws Exception {
        Long orderId = placeOrder(buyerToken, sellerProfileId, quantity);
        acceptOrder(sellerToken, orderId);
        shipOrder(sellerToken, orderId);
        deliverOrder(sellerToken, orderId);
        assertThat(b2bOrderRepository.findById(orderId).orElseThrow().getTotal())
                .isEqualByComparingTo(expectedTotal);
        return orderId;
    }

    private Long createStatementAndReturnId(String token, Long buyerId, LocalDate from, LocalDate to)
            throws Exception {
        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(buyerId);
        request.setFrom(from);
        request.setTo(to);

        MvcResult result = mockMvc.perform(post("/api/v1/vendors/b2b/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void payStatement(String token, Long statementId) throws Exception {
        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(PaymentMode.CASH);
        request.setPaymentNote("Cash payment");

        mockMvc.perform(post("/api/v1/vendors/b2b/statements/{statementId}/payment", statementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
        return auth.getAccessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String randomDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return digits.toString();
    }
}
