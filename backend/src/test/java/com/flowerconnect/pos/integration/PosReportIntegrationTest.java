package com.flowerconnect.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.pos.dto.PosReportResponse;
import com.flowerconnect.pos.repository.PosItemRepository;
import com.flowerconnect.pos.repository.PosTransactionRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.test.MutableClock;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the POS reports API (plan Phase 11, task 11.5)
 * against a real MySQL instance and the production security chain.
 *
 * <p>Daily/weekly/range reports are verified for correct UTC-day conversion
 * from the vendor's timezone, payment-method breakdown, top-products ranking
 * and limit, date-range validation (365-day cap), vendor isolation, and
 * approval/role gating.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PosReportIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String PINCODE = "560034";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MutableClock clock;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductService productService;
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
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long rosesCategoryId;
    private String customerToken;
    private Instant clockAtStart;

    @BeforeEach
    void setUp() throws Exception {
        clockAtStart = clock.instant();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        customerToken = tokenFor(createUser(uniqueEmail(), "CUSTOMER"));
    }

    @AfterEach
    void restoreClock() {
        clock.setInstant(clockAtStart);
    }

    // ------------------------------------------------------------------
    // Daily report
    // ------------------------------------------------------------------

    @Test
    void dailyReportReturnsCountRevenueAndBreakdown() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        Long t1 = insertTransaction(vendor.profileId(), new BigDecimal("598.00"), "CASH",
                LocalDateTime.of(2024, 1, 15, 10, 0, 0));
        Long t2 = insertTransaction(vendor.profileId(), new BigDecimal("1200.00"), "CARD",
                LocalDateTime.of(2024, 1, 15, 14, 30, 0));
        Long t3 = insertTransaction(vendor.profileId(), new BigDecimal("300.00"), "UPI",
                LocalDateTime.of(2024, 1, 15, 16, 0, 0));

        PosReportResponse response = getReport(vendor, "daily", LocalDate.of(2024, 1, 15), null, null);

        assertEquals(LocalDate.of(2024, 1, 15), response.getDate());
        assertEquals("Asia/Kolkata", response.getTimezone());
        assertEquals(3, response.getTransactionCount());
        assertEquals(new BigDecimal("2098.00"), response.getTotalRevenue());

        PosReportResponse.PosPaymentBreakdown cash = findBreakdown(response, "CASH");
        assertEquals(1, cash.getTransactionCount());
        assertEquals(new BigDecimal("598.00"), cash.getRevenue());

        PosReportResponse.PosPaymentBreakdown card = findBreakdown(response, "CARD");
        assertEquals(1, card.getTransactionCount());
        assertEquals(new BigDecimal("1200.00"), card.getRevenue());

        PosReportResponse.PosPaymentBreakdown upi = findBreakdown(response, "UPI");
        assertEquals(1, upi.getTransactionCount());
        assertEquals(new BigDecimal("300.00"), upi.getRevenue());

        assertTrue(response.getTopProducts().isEmpty());
    }

    @Test
    void dailyReportExcludesTransactionsOutsideIstDay() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        // date=2024-01-15 IST → UTC interval [2024-01-14T18:30:00, 2024-01-15T18:30:00)
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 14, 20, 0, 0));  // in range ✓
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 15, 10, 0, 0));  // in range ✓
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 15, 18, 30, 0));  // at boundary → excluded ✗
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 15, 19, 0, 0));  // after boundary → excluded ✗
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 14, 18, 0, 0));  // before start → excluded ✗

        PosReportResponse response = getReport(vendor, "daily", LocalDate.of(2024, 1, 15), null, null);

        assertEquals(2, response.getTransactionCount());
        assertEquals(new BigDecimal("2.00"), response.getTotalRevenue());
    }

    @Test
    void defaultDateUsesVendorTimezoneNotUtc() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        clock.setInstant(Instant.parse("2024-01-15T23:00:00Z")); // UTC=Jan 15, IST=Jan 16 04:30

        PosReportResponse response = getReport(vendor, "daily", null, null, null);

        assertEquals(LocalDate.of(2024, 1, 16), response.getDate());
    }

    @Test
    void emptyReportReturnsZeros() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        PosReportResponse response = getReport(vendor, "daily", LocalDate.of(2024, 1, 15), null, null);

        assertEquals(LocalDate.of(2024, 1, 15), response.getDate());
        assertEquals(0, response.getTransactionCount());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getTotalRevenue());
        assertTrue(response.getBreakdownByPaymentMethod().isEmpty());
        assertTrue(response.getTopProducts().isEmpty());
    }

    // ------------------------------------------------------------------
    // Weekly report
    // ------------------------------------------------------------------

    @Test
    void weeklyReportCoversSevenDays() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        // startDate=2024-01-15 IST → UTC interval [2024-01-14T18:30:00, 2024-01-21T18:30:00)
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 14, 20, 0, 0));  // in range ✓ (IST Jan 15)
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 15, 10, 0, 0));  // in range ✓
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 17, 5, 0, 0));   // in range ✓
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 20, 10, 0, 0));  // in range ✓
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 21, 19, 0, 0));  // out of range ✗ (IST Jan 22)
        insertTransaction(vendor.profileId(), BigDecimal.ONE, "CASH",
                LocalDateTime.of(2024, 1, 14, 18, 0, 0));  // out of range ✗ (IST Jan 14 23:30)

        PosReportResponse response = getReport(vendor, "weekly", LocalDate.of(2024, 1, 15), null, null);

        assertEquals(LocalDate.of(2024, 1, 15), response.getDate());
        assertEquals(4, response.getTransactionCount());
        assertEquals(new BigDecimal("4.00"), response.getTotalRevenue());
    }

    // ------------------------------------------------------------------
    // Top products
    // ------------------------------------------------------------------

    @Test
    void topProductsSortedByQuantityWithLimit() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        Long roseId = createProduct(vendor, "Rose", new BigDecimal("100.00"));
        Long tulipId = createProduct(vendor, "Tulip", new BigDecimal("200.00"));
        Long orchidId = createProduct(vendor, "Orchid", new BigDecimal("300.00"));
        Long lilyId = createProduct(vendor, "Lily", new BigDecimal("50.00"));

        // T1: Rose qty 3 (=300), Tulip qty 1 (=200)
        Long t1 = insertTransaction(vendor.profileId(), new BigDecimal("500.00"), "CASH",
                LocalDateTime.of(2024, 1, 15, 10, 0, 0));
        insertItem(t1, roseId, "Rose", new BigDecimal("100.00"), 3, new BigDecimal("300.00"));
        insertItem(t1, tulipId, "Tulip", new BigDecimal("200.00"), 1, new BigDecimal("200.00"));

        // T2: Rose qty 2 (=200), Orchid qty 5 (=1500)
        Long t2 = insertTransaction(vendor.profileId(), new BigDecimal("1700.00"), "CARD",
                LocalDateTime.of(2024, 1, 15, 12, 0, 0));
        insertItem(t2, roseId, "Rose", new BigDecimal("100.00"), 2, new BigDecimal("200.00"));
        insertItem(t2, orchidId, "Orchid", new BigDecimal("300.00"), 5, new BigDecimal("1500.00"));

        // T3: Lily qty 10 (=500)
        Long t3 = insertTransaction(vendor.profileId(), new BigDecimal("500.00"), "UPI",
                LocalDateTime.of(2024, 1, 15, 14, 0, 0));
        insertItem(t3, lilyId, "Lily", new BigDecimal("50.00"), 10, new BigDecimal("500.00"));

        // Aggregated: Lily(qty10, rev500), Orchid(qty5, rev1500), Rose(qty5, rev500), Tulip(qty1, rev200)
        PosReportResponse response = getReport(vendor, "daily", LocalDate.of(2024, 1, 15), null, null);

        List<PosReportResponse.TopProduct> topProducts = response.getTopProducts();
        assertEquals(4, topProducts.size());

        assertEquals(lilyId, topProducts.get(0).getProductId());
        assertEquals(10, topProducts.get(0).getQuantitySold());
        assertEquals(new BigDecimal("500.00"), topProducts.get(0).getRevenue());

        assertEquals(orchidId, topProducts.get(1).getProductId());
        assertEquals(5, topProducts.get(1).getQuantitySold());
        assertEquals(new BigDecimal("1500.00"), topProducts.get(1).getRevenue());

        assertEquals(roseId, topProducts.get(2).getProductId());
        assertEquals(5, topProducts.get(2).getQuantitySold());
        assertEquals(new BigDecimal("500.00"), topProducts.get(2).getRevenue());

        assertEquals(tulipId, topProducts.get(3).getProductId());
        assertEquals(1, topProducts.get(3).getQuantitySold());
        assertEquals(new BigDecimal("200.00"), topProducts.get(3).getRevenue());
    }

    // ------------------------------------------------------------------
    // Range report
    // ------------------------------------------------------------------

    @Test
    void rangeReportValidRangeReturnsTransactions() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        insertTransaction(vendor.profileId(), new BigDecimal("100.00"), "CASH",
                LocalDateTime.of(2024, 1, 15, 10, 0, 0));
        insertTransaction(vendor.profileId(), new BigDecimal("200.00"), "CARD",
                LocalDateTime.of(2024, 1, 16, 12, 0, 0));

        PosReportResponse response = getReport(vendor, "range", null,
                LocalDate.of(2024, 1, 14), LocalDate.of(2024, 1, 16));

        assertEquals(LocalDate.of(2024, 1, 14), response.getDate());
        assertEquals(2, response.getTransactionCount());
        assertEquals(new BigDecimal("300.00"), response.getTotalRevenue());
    }

    @Test
    void unknownPeriodReturns400() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/pos/reports")
                        .param("period", "monthly")
                        .header("Authorization", bearer(vendor.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rangeExceedingMaxDaysReturns400() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/pos/reports")
                        .param("period", "range")
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2025-01-01")
                        .header("Authorization", bearer(vendor.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rangeWithMissingDatesReturns400() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/pos/reports")
                        .param("period", "range")
                        .header("Authorization", bearer(vendor.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rangeWithEndDateBeforeStartDateReturns400() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/pos/reports")
                        .param("period", "range")
                        .param("fromDate", "2024-01-10")
                        .param("toDate", "2024-01-05")
                        .header("Authorization", bearer(vendor.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------
    // Vendor isolation
    // ------------------------------------------------------------------

    @Test
    void vendorIsolation() throws Exception {
        Vendor mine = registerVendor(VendorProfile.Status.APPROVED);
        Vendor theirs = registerVendor(VendorProfile.Status.APPROVED);

        insertTransaction(mine.profileId(), new BigDecimal("100.00"), "CASH",
                LocalDateTime.of(2024, 1, 15, 10, 0, 0));
        insertTransaction(mine.profileId(), new BigDecimal("200.00"), "CASH",
                LocalDateTime.of(2024, 1, 15, 12, 0, 0));
        insertTransaction(theirs.profileId(), new BigDecimal("500.00"), "CASH",
                LocalDateTime.of(2024, 1, 15, 14, 0, 0));

        PosReportResponse mineReport = getReport(mine, "daily", LocalDate.of(2024, 1, 15), null, null);
        assertEquals(2, mineReport.getTransactionCount());
        assertEquals(new BigDecimal("300.00"), mineReport.getTotalRevenue());

        PosReportResponse theirsReport = getReport(theirs, "daily", LocalDate.of(2024, 1, 15), null, null);
        assertEquals(1, theirsReport.getTransactionCount());
        assertEquals(new BigDecimal("500.00"), theirsReport.getTotalRevenue());
    }

    // ------------------------------------------------------------------
    // Approval gating and role boundary
    // ------------------------------------------------------------------

    @Test
    void pendingVendorIsRefused() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.PENDING_APPROVAL);

        mockMvc.perform(get("/api/v1/pos/reports")
                        .header("Authorization", bearer(vendor.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));
    }

    @Test
    void suspendedVendorIsRefused() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.SUSPENDED);

        mockMvc.perform(get("/api/v1/pos/reports")
                        .header("Authorization", bearer(vendor.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));
    }

    @Test
    void customerIsStoppedByNamespaceRule() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/pos/reports")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anonymousCallerIsUnauthenticated() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/pos/reports"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private PosReportResponse getReport(Vendor vendor, String period, LocalDate date,
                                        LocalDate fromDate, LocalDate toDate) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder =
                get("/api/v1/pos/reports")
                        .param("period", period)
                        .header("Authorization", bearer(vendor.token()));
        if (date != null) {
            builder = builder.param("date", date.toString());
        }
        if (fromDate != null) {
            builder = builder.param("fromDate", fromDate.toString());
        }
        if (toDate != null) {
            builder = builder.param("toDate", toDate.toString());
        }

        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk()).andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), PosReportResponse.class);
    }

    private PosReportResponse.PosPaymentBreakdown findBreakdown(PosReportResponse response,
                                                                String method) {
        return response.getBreakdownByPaymentMethod().stream()
                .filter(b -> method.equals(b.getPaymentMethod()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No breakdown for payment method: " + method));
    }

    private Long insertTransaction(Long profileId, BigDecimal total, String paymentMethod,
                                   LocalDateTime createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO pos_transactions (vendor_id, idempotency_key, request_fingerprint, " +
                            "total, payment_method, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, profileId);
            ps.setString(2, uniqueKey());
            ps.setString(3, "fp");
            ps.setBigDecimal(4, total);
            ps.setString(5, paymentMethod);
            ps.setObject(6, createdAt);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertItem(Long transactionId, Long productId, String productName,
                            BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        jdbcTemplate.update(
                "INSERT INTO pos_items (transaction_id, product_id, product_name_snapshot, " +
                        "unit_price_snapshot, quantity, line_total) VALUES (?, ?, ?, ?, ?, ?)",
                transactionId, productId, productName, unitPrice, quantity, lineTotal);
    }

    private Long createProduct(Vendor vendor, String name, BigDecimal basePrice) {
        return productService.create(vendor.email(), ProductRequest.builder()
                        .name(name)
                        .categoryId(rosesCategoryId)
                        .description("Integration probe")
                        .basePrice(basePrice)
                        .build())
                .getId();
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
                mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/suspend")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "test suspension"))))
                        .andExpect(status().isOk());
            }
            case PENDING_APPROVAL -> {
            }
            default -> throw new IllegalArgumentException("unhandled status " + target);
        }

        return new Vendor(email, token, profileId);
    }

    private void approve(Long profileId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/approve")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk());
    }

    private String adminToken() throws Exception {
        return tokenFor(createUser(uniqueEmail(), "ADMIN"));
    }

    private org.springframework.test.web.servlet.ResultActions adminAction(Long profileId, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/" + action)
                        .header("Authorization", bearer(adminToken())));
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
        return "pos-report-" + UUID.randomUUID() + "@test.com";
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
