package com.flowerconnect.analytics;

import com.flowerconnect.analytics.dto.AnalyticsDailyTrend;
import com.flowerconnect.analytics.dto.AnalyticsOverviewResponse;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Focused HTTP-level regression coverage for the Admin Analytics API
 * (plan Phase 12, task 12.4) against a real MySQL instance and the
 * production security chain.
 *
 * <p>Delivered orders and their {@code order_status_history} rows are written
 * directly through repositories with explicit timestamps, so the service's
 * use of {@code to_status = 'DELIVERED'} and the date-range predicates are
 * exercised without building a full checkout flow. This guards against the
 * JOIN-after-WHERE SQL defect that previously produced a 500 on
 * {@code /api/v1/admin/analytics/overview}.
 *
 * <p>Each test creates its own orders on dates unique to that test, so the
 * shared Testcontainers database (which accumulates rows across test methods
 * without per-method rollback) cannot distort the assertions.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AdminAnalyticsIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

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
    private OrderRepository orderRepository;
    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long vendorProfileId;
    private User customer;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode("560038").orElseThrow().getId();

        // Vendor: APPROVED and accepting orders (counts toward activeVendors).
        User vendorUser = createUser("va-vendor-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        vendorProfileId = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(vendorUser)
                .businessName("Analytics Florist " + UUID.randomUUID())
                .addressLine1("12 Test Street")
                .serviceLocation(serviceLocationRepository.findById(locationId).orElseThrow())
                .latitude(BigDecimal.valueOf(12.97))
                .longitude(BigDecimal.valueOf(77.64))
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
                .build()).getId();

        // Active customer (counts toward activeCustomers when they have a delivered order).
        customer = createUser("va-customer-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER");
        customerToken = tokenFor(customer);

        adminToken = tokenFor(createUser("va-admin-" + UUID.randomUUID() + "@test.com", PASSWORD, "ADMIN"));
    }

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

    private String tokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new LoginRequest(user.getEmail(), PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(response.getAccessToken()).isNotNull();
        return response.getAccessToken();
    }

    /**
     * Creates one delivered order owned by this test's customer/vendor, with an
     * {@code order_status_history} DELIVERED row stamped at {@code deliveredAtUtc}.
     * Each test should use dates unique to itself so the shared database's
     * accumulated rows (from other test methods) cannot distort its assertions.
     */
    private void createDeliveredOrder(LocalDateTime deliveredAtUtc,
                                      BigDecimal subtotal, BigDecimal deliveryFee,
                                      BigDecimal platformFee, BigDecimal total) {
        Order order = orderRepository.saveAndFlush(Order.builder()
                .user(customer)
                .vendor(vendorProfileRepository.findById(vendorProfileId).orElseThrow())
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.COLLECTED)
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .platformFee(platformFee)
                .total(total)
                .deliveryAddressLine1("123 Test Street")
                .deliveryLatitude(BigDecimal.valueOf(12.97))
                .deliveryLongitude(BigDecimal.valueOf(77.64))
                .build());
        Long orderId = order.getId();
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(deliveredAtUtc.minusHours(1)), orderId);

        orderStatusHistoryRepository.saveAndFlush(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(OrderStatus.CONFIRMED)
                .toStatus(OrderStatus.DELIVERED)
                .reason("Delivered to customer")
                .actorType(ActorType.VENDOR)
                .actor(customer)
                .createdAt(deliveredAtUtc)
                .build());
        jdbcTemplate.update(
                "UPDATE order_status_history SET created_at = ? WHERE order_id = ? AND to_status = 'DELIVERED'",
                java.sql.Timestamp.valueOf(deliveredAtUtc), orderId);
    }

    private static String randomDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return digits.toString();
    }

    // ------------------------------------------------------------------
    // RBAC
    // ------------------------------------------------------------------

    @Test
    @DisplayName("anonymous calls to analytics overview are unauthenticated")
    void overviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("non-admin callers to analytics overview are forbidden")
    void overviewForbidsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("anonymous calls to analytics daily trends are unauthenticated")
    void dailyTrendsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/daily-trends"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Overview
    // ------------------------------------------------------------------

    @Test
    @DisplayName("overview with default date range returns delivered metrics without SQL error")
    void overviewDefaultRangeReturnsMetrics() throws Exception {
        // Default range is the last 30 days (now = 2026-11-20, i.e. 2026-10-22 to 2026-11-20);
        // create orders inside it on dates unique to this test.
        createDeliveredOrder(LocalDateTime.of(2026, 10, 25, 10, 0),
                new BigDecimal("100.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("100.00"));
        createDeliveredOrder(LocalDateTime.of(2026, 10, 26, 14, 0),
                new BigDecimal("50.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("50.00"));

        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeliveredOrders").value(2))
                .andExpect(jsonPath("$.marketplaceSales").value(150.00))
                .andExpect(jsonPath("$.activeVendors").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activeCustomers").value(1))
                .andExpect(jsonPath("$.topVendors").isArray())
                .andExpect(jsonPath("$.topVendors[0].businessName").isNotEmpty());
    }

    @Test
    @DisplayName("overview with explicit date range filters delivered orders")
    void overviewExplicitRangeFiltersOrders() throws Exception {
        // Orders inside [2026-12-05, 2026-12-07) and one outside before it.
        createDeliveredOrder(LocalDateTime.of(2026, 12, 5, 9, 0),
                new BigDecimal("100.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("100.00"));
        createDeliveredOrder(LocalDateTime.of(2026, 12, 6, 9, 0),
                new BigDecimal("50.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("50.00"));
        createDeliveredOrder(LocalDateTime.of(2026, 12, 1, 8, 0),
                new BigDecimal("999.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("999.00"));

        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .param("fromDate", "2026-12-05")
                        .param("toDate", "2026-12-07")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeliveredOrders").value(2))
                .andExpect(jsonPath("$.marketplaceSales").value(150.00))
                .andExpect(jsonPath("$.activeCustomers").value(1));
    }

    @Test
    @DisplayName("overview excludes out-of-range delivered orders")
    void overviewExcludesOutOfRange() throws Exception {
        // In-range orders on 2026-12-10 and 2026-12-11; out-of-range on 2026-12-01.
        createDeliveredOrder(LocalDateTime.of(2026, 12, 10, 10, 0),
                new BigDecimal("70.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("70.00"));
        createDeliveredOrder(LocalDateTime.of(2026, 12, 11, 10, 0),
                new BigDecimal("80.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("80.00"));
        createDeliveredOrder(LocalDateTime.of(2026, 12, 1, 12, 0),
                new BigDecimal("999.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("999.00"));

        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .param("fromDate", "2026-12-10")
                        .param("toDate", "2026-12-12")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeliveredOrders").value(2))
                .andExpect(jsonPath("$.marketplaceSales").value(150.00));
    }

    @Test
    @DisplayName("overview reports active customer and top vendor when matching data exists")
    void overviewReportsActiveCustomerAndTopVendor() throws Exception {
        createDeliveredOrder(LocalDateTime.of(2026, 12, 20, 10, 0),
                new BigDecimal("200.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("200.00"));

        MvcResult result = mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .param("fromDate", "2026-12-20")
                        .param("toDate", "2026-12-21")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        AnalyticsOverviewResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AnalyticsOverviewResponse.class);
        assertThat(response.getActiveCustomers()).isEqualTo(1L);
        assertThat(response.getTopVendors()).hasSize(1);
        assertThat(response.getTopVendors().get(0).getMarketplaceSales())
                .isEqualByComparingTo("200.00");
        assertThat(response.getTopVendors().get(0).getDeliveredOrders()).isEqualTo(1L);
        assertThat(response.getDisputeRate()).isZero();
    }

    // ------------------------------------------------------------------
    // Daily trends
    // ------------------------------------------------------------------

    @Test
    @DisplayName("daily trends for an empty/zero-activity range returns zero-filled series")
    void dailyTrendsEmptyRangeReturnsZeroFilled() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/daily-trends")
                        .param("fromDate", "2026-09-01")
                        .param("toDate", "2026-09-02")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$[0].orderCount").value(0))
                .andExpect(jsonPath("$[0].revenue").value(0.00))
                .andExpect(jsonPath("$[1].date").value("2026-09-02"))
                .andExpect(jsonPath("$[1].orderCount").value(0))
                .andExpect(jsonPath("$[1].revenue").value(0.00));
    }

    @Test
    @DisplayName("daily trends buckets delivered orders by day with revenue")
    void dailyTrendsBucketsByDay() throws Exception {
        createDeliveredOrder(LocalDateTime.of(2026, 12, 15, 10, 0),
                new BigDecimal("100.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("100.00"));
        createDeliveredOrder(LocalDateTime.of(2026, 12, 17, 14, 0),
                new BigDecimal("50.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("50.00"));

        mockMvc.perform(get("/api/v1/admin/analytics/daily-trends")
                        .param("fromDate", "2026-12-15")
                        .param("toDate", "2026-12-17")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026-12-15"))
                .andExpect(jsonPath("$[0].orderCount").value(1))
                .andExpect(jsonPath("$[0].revenue").value(100.00))
                .andExpect(jsonPath("$[1].date").value("2026-12-16"))
                .andExpect(jsonPath("$[1].orderCount").value(0))
                .andExpect(jsonPath("$[2].date").value("2026-12-17"))
                .andExpect(jsonPath("$[2].orderCount").value(1))
                .andExpect(jsonPath("$[2].revenue").value(50.00));
    }
}
