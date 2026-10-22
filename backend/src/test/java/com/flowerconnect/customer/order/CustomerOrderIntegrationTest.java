package com.flowerconnect.customer.order;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.OrderItem;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.repository.OrderItemRepository;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the customer order API (plan Phase 6, task 6.3)
 * against a real MySQL instance and the production security chain.
 *
 * <p>Orders, items and status history are persisted directly through the
 * repositories (checkout belongs to Phase 5), so this class can place an
 * order in any {@code OrderStatus} and exercise the customer's list and
 * detail endpoints without building a full cart first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerOrderIntegrationTest extends AbstractIntegrationTest {

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
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long vendorProfileId;
    private String vendorEmail;
    private Long productId;
    private Long customerAUserId;
    private String customerAToken;
    private Long customerBUserId;
    private String customerBToken;
    private String adminToken;
    private Long orderAId;
    private Long orderA2Id;
    private Long orderA3Id;
    private Long orderBId;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE).orElseThrow().getId();

        // Customer A and Customer B, plus an admin for RBAC checks.
        User customerA = createUser("customer-a-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER");
        customerAUserId = customerA.getId();
        customerAToken = tokenFor(customerA);

        User customerB = createUser("customer-b-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER");
        customerBUserId = customerB.getId();
        customerBToken = tokenFor(customerB);

        adminToken = tokenFor(createUser("admin-" + UUID.randomUUID() + "@test.com", PASSWORD, "ADMIN"));

        // An approved vendor whose name appears on the customer's orders.
        User vendorUser = createUser("vendor-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        vendorEmail = vendorUser.getEmail();
        VendorProfile profile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(vendorUser)
                .businessName("Order Probe Blossoms")
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

        Long categoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        productId = productService.create(vendorEmail,
                ProductRequest.builder()
                        .name("Red Roses " + UUID.randomUUID())
                        .categoryId(categoryId)
                        .description("Fresh flowers")
                        .basePrice(new BigDecimal("99.50"))
                        .build()).getId();

        // Customer A: three orders, newest first.
        orderAId = createOrder(customerAUserId, vendorProfileId, OrderStatus.PLACED,
                LocalDateTime.of(2026, 10, 20, 4, 30),
                new BigDecimal("199.00"), new BigDecimal("199.00"),
                LocalDateTime.of(2026, 10, 20, 5, 0), LocalDateTime.of(2026, 10, 20, 6, 0));
        createOrderItem(orderAId, productId, vendorProfileId, 2, new BigDecimal("99.50"));
        appendHistory(orderAId, null, OrderStatus.PLACED, ActorType.SYSTEM, null, null,
                LocalDateTime.of(2026, 10, 20, 4, 30));
        appendHistory(orderAId, OrderStatus.PLACED, OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUser, null,
                LocalDateTime.of(2026, 10, 20, 5, 0));

        orderA2Id = createOrder(customerAUserId, vendorProfileId, OrderStatus.CONFIRMED,
                LocalDateTime.of(2026, 10, 19, 10, 0),
                new BigDecimal("250.00"), new BigDecimal("250.00"), null, null);
        createOrderItem(orderA2Id, productId, vendorProfileId, 1, new BigDecimal("250.00"));

        orderA3Id = createOrder(customerAUserId, vendorProfileId, OrderStatus.DELIVERED,
                LocalDateTime.of(2026, 10, 18, 8, 0),
                new BigDecimal("300.00"), new BigDecimal("300.00"), null, null);
        createOrderItem(orderA3Id, productId, vendorProfileId, 3, new BigDecimal("100.00"));

        // Customer B: one order that Customer A must never see.
        orderBId = createOrder(customerBUserId, vendorProfileId, OrderStatus.PLACED,
                LocalDateTime.of(2026, 10, 20, 6, 0),
                new BigDecimal("50.00"), new BigDecimal("50.00"), null, null);
        createOrderItem(orderBId, productId, vendorProfileId, 1, new BigDecimal("50.00"));
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    @Test
    void customerSeesOnlyTheirOwnOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[*].id",
                        Matchers.containsInAnyOrder(
                                orderAId.intValue(), orderA2Id.intValue(), orderA3Id.intValue())))
                // Customer B's order is absent.
                .andExpect(jsonPath("$.content[*].id",
                        Matchers.not(Matchers.hasItem(orderBId.intValue()))));
    }

    @Test
    void listIsNewestFirstAndPaginated() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken))
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderAId.intValue()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void statusFilterReturnsOnlyMatchingOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken))
                        .param("status", "PLACED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderAId.intValue()))
                .andExpect(jsonPath("$.content[0].status").value("PLACED"));
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @Test
    void detailIncludesSnapshotFeesVendorSlotPaymentAndTimeline() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk())
                // Identity and payment.
                .andExpect(jsonPath("$.id").value(orderAId.intValue()))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                // Vendor name from the snapshotted profile.
                .andExpect(jsonPath("$.vendorName").value("Order Probe Blossoms"))
                // Fee breakdown in BigDecimal. Match numerically (scale-insensitive)
                // because Jackson normalizes trailing zeros on some BigDecimal values.
                .andExpect(jsonPath("$.subtotal").value(money("199.00")))
                .andExpect(jsonPath("$.deliveryFee").value(money("0.00")))
                .andExpect(jsonPath("$.platformFee").value(money("0.00")))
                .andExpect(jsonPath("$.total").value(money("199.00")))
                // Address snapshot.
                .andExpect(jsonPath("$.deliveryAddressLine1").value("123 Test Street"))
                // Delivery slot.
                .andExpect(jsonPath("$.slotStart").value(Matchers.startsWith("2026-10-20T05:00")))
                .andExpect(jsonPath("$.slotEnd").value(Matchers.startsWith("2026-10-20T06:00")))
                // Items: name, price, quantity, subtotal.
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value(Matchers.startsWith("Red Roses")))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].priceSnapshot").value(money("99.50")))
                .andExpect(jsonPath("$.items[0].subtotal").value(money("199.00")))
                // Timeline: chronological, initial PLACED has null fromStatus.
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andExpect(jsonPath("$.timeline[0].fromStatus").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.timeline[0].toStatus").value("PLACED"))
                .andExpect(jsonPath("$.timeline[0].actorType").value("SYSTEM"))
                .andExpect(jsonPath("$.timeline[1].fromStatus").value("PLACED"))
                .andExpect(jsonPath("$.timeline[1].toStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.timeline[1].actorType").value("VENDOR"))
                .andExpect(jsonPath("$.timeline[1].actorName").value("Test FLORIST"));
    }

    @Test
    void foreignOrderIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderBId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonexistentOrderIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // RBAC
    // ------------------------------------------------------------------

    @Test
    void rbacRefusesNonCustomersAndAnonymousCallers() throws Exception {
        // Customer B is allowed to list (their own orders, empty page).
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerBToken)))
                .andExpect(status().isOk());
        // Customer B cannot read customer A's order.
        mockMvc.perform(get("/api/v1/orders/" + orderAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerBToken)))
                .andExpect(status().isForbidden());
        // Admin is refused by the CUSTOMER namespace rule.
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        // Customer A is allowed to list.
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk());
        // Anonymous is refused.
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
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

    private Long createOrder(Long userId, Long vendorProfileId, OrderStatus status,
                             LocalDateTime createdAtUtc, BigDecimal subtotal, BigDecimal total,
                             LocalDateTime slotStart, LocalDateTime slotEnd) {
        Order order = orderRepository.saveAndFlush(Order.builder()
                .user(userRepository.findById(userId).orElseThrow())
                .vendor(vendorProfileRepository.findById(vendorProfileId).orElseThrow())
                .status(status)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(subtotal)
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(total)
                .slotStart(slotStart)
                .slotEnd(slotEnd)
                .deliveryAddressLine1("123 Test Street")
                .deliveryLatitude(new BigDecimal("12.9716"))
                .deliveryLongitude(new BigDecimal("77.5946"))
                .build());
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(createdAtUtc), order.getId());
        return order.getId();
    }

    private void createOrderItem(Long orderId, Long productId, Long vendorProfileId,
                                 int quantity, BigDecimal unitPrice) {
        orderItemRepository.saveAndFlush(OrderItem.builder()
                .order(orderRepository.findById(orderId).orElseThrow())
                .product(productRepository.findById(productId).orElseThrow())
                .vendorId(vendorProfileId)
                .quantity(quantity)
                .priceSnapshot(unitPrice)
                .build());
    }

    private void appendHistory(Long orderId, OrderStatus fromStatus, OrderStatus toStatus,
                               ActorType actorType, User actor, String reason,
                               LocalDateTime createdAt) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(orderRepository.findById(orderId).orElseThrow())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorType(actorType)
                .actor(actor)
                .reason(reason)
                .createdAt(createdAt)
                .build();
        orderStatusHistoryRepository.saveAndFlush(history);
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

    private static String randomDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return digits.toString();
    }

    /**
     * A Hamcrest matcher that compares a JSON number numerically against an
     * expected decimal string, ignoring scale (so {@code 199.0} equals
     * {@code 199.00}). Jackson serializes {@link BigDecimal} with an
     * inconsistent scale, so exact string matching is fragile.
     */
    private static Matcher<Object> money(String expected) {
        return new TypeSafeMatcher<Object>() {
            private final BigDecimal expectedValue = new BigDecimal(expected);

            @Override
            protected boolean matchesSafely(Object item) {
                return expectedValue.compareTo(new BigDecimal(item.toString())) == 0;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a monetary value equal to " + expected);
            }
        };
    }
}
