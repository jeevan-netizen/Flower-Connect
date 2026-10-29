package com.flowerconnect.vendor.analytics;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.pos.domain.PosTransaction;
import com.flowerconnect.pos.repository.PosTransactionRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the vendor analytics API (plan Phase 12, task 12.5)
 * against a real MySQL instance and the production security chain.
 *
 * <p>Orders and POS transactions are persisted directly through repositories.
 * Delivered/cancelled timestamps come from {@code order_status_history} rows
 * written explicitly in the fixture, so the service's use of
 * {@code to_status = 'DELIVERED'} / {@code to_status IN ('CANCELLED','REJECTED')}
 * is exercised without building a full checkout flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VendorAnalyticsIntegrationTest extends AbstractIntegrationTest {

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
    private InventoryRepository inventoryRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired
    private PosTransactionRepository posTransactionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long vendorProfileId;
    private String vendorEmail;
    private String vendorToken;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE).orElseThrow().getId();

        User vendorUser = createUser("vendor-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        vendorEmail = vendorUser.getEmail();
        VendorProfile profile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(vendorUser)
                .businessName("Analytics Florist")
                .addressLine1("12 Test Street")
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
        vendorProfileId = profile.getId();
        vendorToken = tokenFor(vendorUser);

        User customerUser = createUser("customer-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER");
        customerToken = tokenFor(customerUser);
        adminToken = tokenFor(createUser("admin-" + UUID.randomUUID() + "@test.com", PASSWORD, "ADMIN"));

        Long categoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();

        // Product A: Roses
        var roses = productService.create(vendorEmail,
                ProductRequest.builder()
                        .name("Analytics Roses " + UUID.randomUUID())
                        .categoryId(categoryId)
                        .description("Fresh roses")
                        .basePrice(new BigDecimal("100.00"))
                        .build());
        Inventory rosesInv = inventoryRepository.findByProductId(roses.getId()).orElseThrow();
        rosesInv.setQuantity(50);
        rosesInv.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(rosesInv);

        // Product B: Tulips
        var tulips = productService.create(vendorEmail,
                ProductRequest.builder()
                        .name("Analytics Tulips " + UUID.randomUUID())
                        .categoryId(categoryId)
                        .description("Fresh tulips")
                        .basePrice(new BigDecimal("50.00"))
                        .build());
        Inventory tulipsInv = inventoryRepository.findByProductId(tulips.getId()).orElseThrow();
        tulipsInv.setQuantity(50);
        tulipsInv.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(tulipsInv);

        Long customerUserId = customerUser.getId();

        // Delivered ONLINE order: $100, 2 units Roses, delivered 2026-11-03 10:00 UTC
        Long order1 = createDeliveredOrder(customerUserId, vendorProfileId, PaymentMethod.ONLINE,
                LocalDateTime.of(2026, 11, 3, 10, 0), new BigDecimal("100.00"),
                List.of(new OrderItemLine(roses.getId(), "Roses", 2, new BigDecimal("100.00"))));

        // Delivered COD order: $50, 1 unit Tulips, delivered 2026-11-05 14:00 UTC
        Long order2 = createDeliveredOrder(customerUserId, vendorProfileId, PaymentMethod.COD,
                LocalDateTime.of(2026, 11, 5, 14, 0), new BigDecimal("50.00"),
                List.of(new OrderItemLine(tulips.getId(), "Tulips", 1, new BigDecimal("50.00"))));

        // Delivered ONLINE order: $200, 5 units Roses, delivered 2026-11-06 09:00 UTC (same customer as order1)
        Long order3 = createDeliveredOrder(customerUserId, vendorProfileId, PaymentMethod.ONLINE,
                LocalDateTime.of(2026, 11, 6, 9, 0), new BigDecimal("200.00"),
                List.of(new OrderItemLine(roses.getId(), "Roses", 5, new BigDecimal("200.00"))));

        // Cancelled order: $75, cancelled 2026-11-04 11:00 UTC
        createCancelledOrder(customerUserId, vendorProfileId,
                LocalDateTime.of(2026, 11, 4, 11, 0), new BigDecimal("75.00"));

        // POS transactions (commission-free, separate section)
        PosTransaction cash = posTransactionRepository.saveAndFlush(PosTransaction.builder()
                .vendor(profile)
                .idempotencyKey("idem-cash-1")
                .requestFingerprint("fp-cash-1")
                .paymentMethod("CASH")
                .total(new BigDecimal("30.00"))
                .build());
        jdbcTemplate.update(
                "UPDATE pos_transactions SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 11, 2, 9, 0)), cash.getId());
        PosTransaction upi = posTransactionRepository.saveAndFlush(PosTransaction.builder()
                .vendor(profile)
                .idempotencyKey("idem-upi-1")
                .requestFingerprint("fp-upi-1")
                .paymentMethod("UPI")
                .total(new BigDecimal("70.00"))
                .build());
        jdbcTemplate.update(
                "UPDATE pos_transactions SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 11, 4, 16, 0)), upi.getId());
    }

    // ------------------------------------------------------------------
    // Overview
    // ------------------------------------------------------------------

    @Test
    void overviewReturnsMarketplaceAndPosMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/analytics/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken))
                        .param("fromDate", "2026-11-01").param("toDate", "2026-11-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredOrders").value(3))
                .andExpect(jsonPath("$.marketplaceRevenue").value("350.0"))
                .andExpect(jsonPath("$.onlineOrders").value(2))
                .andExpect(jsonPath("$.onlineRevenue").value("300.0"))
                .andExpect(jsonPath("$.codOrders").value(1))
                .andExpect(jsonPath("$.codRevenue").value("50.0"))
                .andExpect(jsonPath("$.cancelledOrders").value(1))
                .andExpect(jsonPath("$.cancelledRevenue").value("75.0"))
                // 1 repeater (customer of order1+order3) out of 1 customer = 1.0
                .andExpect(jsonPath("$.repeatCustomerRate").value(1.0))
                // POS section: 2 transactions, $100 total, CASH+UPI breakdown
                .andExpect(jsonPath("$.pos.transactionCount").value(2))
                .andExpect(jsonPath("$.pos.totalRevenue").value("100.0"))
                .andExpect(jsonPath("$.pos.breakdownByPaymentMethod.length()").value(2));
    }

    @Test
    void overviewZeroDataReturnsNullRepeatRateAndEmptyLists() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/analytics/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken))
                        .param("fromDate", "2026-12-01").param("toDate", "2026-12-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredOrders").value(0))
                .andExpect(jsonPath("$.marketplaceRevenue").value("0.0"))
                .andExpect(jsonPath("$.onlineOrders").value(0))
                .andExpect(jsonPath("$.codOrders").value(0))
                .andExpect(jsonPath("$.cancelledOrders").value(0))
                .andExpect(jsonPath("$.repeatCustomerRate").isEmpty())
                .andExpect(jsonPath("$.topProductsByUnits.length()").value(0))
                .andExpect(jsonPath("$.topProductsByRevenue.length()").value(0))
                .andExpect(jsonPath("$.pos.transactionCount").value(0))
                .andExpect(jsonPath("$.pos.totalRevenue").value("0.0"));
    }

    // ------------------------------------------------------------------
    // Daily trends
    // ------------------------------------------------------------------

    @Test
    void dailyTrendsZeroFillsEmptyDays() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/analytics/daily-trends")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken))
                        .param("fromDate", "2026-11-01").param("toDate", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Nov 1: no activity
                .andExpect(jsonPath("$[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$[0].orderCount").value(0))
                .andExpect(jsonPath("$[0].revenue").value("0.0"))
                // Nov 2: no activity
                .andExpect(jsonPath("$[1].date").value("2026-11-02"))
                .andExpect(jsonPath("$[1].orderCount").value(0))
                .andExpect(jsonPath("$[1].revenue").value("0.0"))
                // Nov 3: order1 delivered ($100)
                .andExpect(jsonPath("$[2].date").value("2026-11-03"))
                .andExpect(jsonPath("$[2].orderCount").value(1))
                .andExpect(jsonPath("$[2].revenue").value("100.0"));
    }

    // ------------------------------------------------------------------
    // RBAC
    // ------------------------------------------------------------------

    @Test
    void anonymousCallsAreUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/analytics/overview"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendor/analytics/daily-trends"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerAndAdminAreForbiddenByNamespaceRule() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/analytics/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vendor/analytics/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vendor/analytics/daily-trends")
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

    private Long createDeliveredOrder(Long userId, Long vendorProfileId, PaymentMethod paymentMethod,
                                      LocalDateTime deliveredAtUtc, BigDecimal total,
                                      List<OrderItemLine> items) {
        User user = userRepository.findById(userId).orElseThrow();
        Order order = orderRepository.saveAndFlush(Order.builder()
                .user(user)
                .vendor(vendorProfileRepository.findById(vendorProfileId).orElseThrow())
                .status(OrderStatus.DELIVERED)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.COLLECTED)
                .subtotal(total)
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(total)
                .deliveryAddressLine1("123 Test Street")
                .deliveryLatitude(BigDecimal.valueOf(12.9716))
                .deliveryLongitude(BigDecimal.valueOf(77.5946))
                .build());
        // Override created_at so the order is within the test range
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(deliveredAtUtc.minusHours(1)), order.getId());

        // order_status_history row recording the DELIVERED transition
        orderStatusHistoryRepository.saveAndFlush(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(OrderStatus.CONFIRMED)
                .toStatus(OrderStatus.DELIVERED)
                .reason("Delivered to customer")
                .actorType(ActorType.VENDOR)
                .actor(user)
                .createdAt(deliveredAtUtc)
                .build());
        // Override created_at on order_status_history (@CreationTimestamp would
        // otherwise overwrite it with now())
        jdbcTemplate.update(
                "UPDATE order_status_history SET created_at = ? WHERE order_id = ? AND to_status = 'DELIVERED'",
                java.sql.Timestamp.valueOf(deliveredAtUtc), order.getId());

        for (OrderItemLine item : items) {
            saveOrderItem(order, item.productId, item.quantity, item.lineTotal);
        }

        return order.getId();
    }

    private void createCancelledOrder(Long userId, Long vendorProfileId,
                                      LocalDateTime cancelledAtUtc, BigDecimal total) {
        User user = userRepository.findById(userId).orElseThrow();
        Order order = orderRepository.saveAndFlush(Order.builder()
                .user(user)
                .vendor(vendorProfileRepository.findById(vendorProfileId).orElseThrow())
                .status(OrderStatus.CANCELLED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(total)
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(total)
                .deliveryAddressLine1("123 Test Street")
                .deliveryLatitude(BigDecimal.valueOf(12.9716))
                .deliveryLongitude(BigDecimal.valueOf(77.5946))
                .build());
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(cancelledAtUtc.minusHours(1)), order.getId());

        orderStatusHistoryRepository.saveAndFlush(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(OrderStatus.PLACED)
                .toStatus(OrderStatus.CANCELLED)
                .reason("Customer cancelled")
                .actorType(ActorType.CUSTOMER)
                .actor(user)
                .createdAt(cancelledAtUtc)
                .build());
        jdbcTemplate.update(
                "UPDATE order_status_history SET created_at = ? WHERE order_id = ? AND to_status = 'CANCELLED'",
                java.sql.Timestamp.valueOf(cancelledAtUtc), order.getId());
    }

    private void saveOrderItem(Order order, Long productId, int quantity, BigDecimal lineTotal) {
        jdbcTemplate.update(
                "INSERT INTO order_items (order_id, product_id, vendor_id, quantity, price_snapshot) " +
                        "VALUES (?, ?, ?, ?, ?)",
                order.getId(), productId, vendorProfileId, quantity,
                lineTotal.divide(new BigDecimal(quantity), 2, java.math.RoundingMode.HALF_UP));
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

    private String randomDigits(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append((int) (Math.random() * 10));
        return sb.toString();
    }

    private record OrderItemLine(Long productId, String productName, int quantity, BigDecimal lineTotal) {
    }
}
