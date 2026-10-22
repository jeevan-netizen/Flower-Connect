package com.flowerconnect.vendor;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.OrderItem;
import com.flowerconnect.order.repository.OrderItemRepository;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.test.MutableClock;
import com.flowerconnect.vendor.dto.VendorOrderRejectRequest;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the vendor order API (plan Phase 6, tasks 6.2 and
 * 6.4) against a real MySQL instance and the production security chain.
 *
 * <p>Orders are persisted directly through the repositories (the checkout
 * endpoint that creates them belongs to Phase 5 and is already covered by
 * {@code CartApiIntegrationTest}/{@code CheckoutApiIntegrationTest}), so this
 * class can place an order in any {@code OrderStatus} and exercise the
 * vendor's accept / reject / prepare / out-for-delivery / delivered actions
 * without building a full cart first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VendorOrderIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String KORAMANGALA_PINCODE = "560034";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderStateMachine orderStateMachine;
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
    private InventoryRepository inventoryRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long rosesCategoryId;
    private Long vendorProfileId;
    private String vendorEmail;
    private String vendorToken;
    private String customerToken;
    private String adminToken;
    private Long customerUserId;
    private Long productId;
    private Long orderId;
    private Long vendorUserId;

    @Autowired
    private MutableClock mutableClock;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE).orElseThrow().getId();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();

        User vendorUser = createUser("vendor-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        vendorEmail = vendorUser.getEmail();
        vendorUserId = vendorUser.getId();
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
        vendorToken = tokenFor(vendorUser);

        User customerUser = createUser("customer-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER");
        customerUserId = customerUser.getId();
        customerToken = tokenFor(customerUser);
        adminToken = tokenFor(createUser("admin-" + UUID.randomUUID() + "@test.com", PASSWORD, "ADMIN"));

        Long categoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        productId = productService.create(vendorEmail,
                com.flowerconnect.catalog.dto.ProductRequest.builder()
                        .name("Order Probe Rose " + UUID.randomUUID())
                        .categoryId(categoryId)
                        .description("Fresh flowers")
                        .basePrice(new BigDecimal("199.00"))
                        .build()).getId();

        // One order in PLACED with five units reserved, ready to be accepted.
        // Product creation auto-creates inventory (quantity 0), so update it
        // rather than inserting a duplicate row (uq_inventory_product).
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        inventory.setQuantity(10);
        inventory.setReservedQuantity(5);
        inventoryRepository.saveAndFlush(inventory);

        orderId = createOrder(customerUserId, vendorProfileId, OrderStatus.PLACED,
                LocalDateTime.of(2026, 10, 20, 4, 30)); // 2026-10-20 04:30 UTC
        createOrderItem(orderId, productId, vendorProfileId, 5);
    }

    // ------------------------------------------------------------------
    // Accept: commits stock and writes a SALE_ONLINE movement
    // ------------------------------------------------------------------

    @Test
    void acceptCommitsReservedStockAndWritesASaleOnlineMovement() throws Exception {
        int movementsBefore = stockMovementCount();

        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());

        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(5, inventory.getQuantity());   // 10 - 5 committed
        assertEquals(0, inventory.getReservedQuantity()); // 5 - 5 released

        assertEquals(movementsBefore + 1, stockMovementCount());
        StockMovement movement = lastMovement();
        assertEquals(StockMovement.MovementType.SALE_ONLINE, movement.getMovementType());
        assertEquals(-5, movement.getQuantityDelta());
        assertEquals(vendorUserId, movement.getActor().getId());
    }

    @Test
    void acceptReturns409WhenStockIsShortAndLeavesOrderAndReservationUntouched() throws Exception {
        // Stock written down to three units, all of it still reserved (available
        // = 0 < 5 needed): the quantity >= q re-check under the lock must refuse
        // the accept. quantity >= reserved_quantity is required by the DB check,
        // so reserved_quantity is lowered alongside quantity.
        jdbcTemplate.update(
                "UPDATE inventory SET quantity = 3, reserved_quantity = 3 WHERE product_id = ?", productId);

        int movementsBefore = stockMovementCount();

        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PLACED, order.getStatus());

        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(3, inventory.getQuantity()); // untouched
        assertEquals(3, inventory.getReservedQuantity()); // reservation untouched

        assertEquals(movementsBefore, stockMovementCount()); // no movement written
    }

    @Test
    void repeatedAcceptIsRefusedAndDoesNotDoubleDeduct() throws Exception {
        // First accept succeeds.
        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isOk());

        // Repeating the accept on a CONFIRMED order is an illegal transition.
        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());

        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(5, inventory.getQuantity());   // deducted once only
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(1, stockMovementCountOfType(StockMovement.MovementType.SALE_ONLINE));
    }

    // ------------------------------------------------------------------
    // Reject: releases reserved stock only, writes a RELEASE movement
    // ------------------------------------------------------------------

    @Test
    void rejectReleasesReservedStockAndWritesAReleaseMovement() throws Exception {
        int movementsBefore = stockMovementCount();

        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VendorOrderRejectRequest("Out of stock"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.REJECTED, order.getStatus());

        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(10, inventory.getQuantity()); // quantity unchanged
        assertEquals(0, inventory.getReservedQuantity()); // 5 - 5 released

        assertEquals(movementsBefore + 1, stockMovementCount());
        StockMovement movement = lastMovement();
        assertEquals(StockMovement.MovementType.RELEASE, movement.getMovementType());
        assertEquals(-5, movement.getQuantityDelta());
        assertEquals("Order cancelled/rejected - reservation released", movement.getReason());
    }

    @Test
    void rejectRequiresAReason() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(OrderStatus.PLACED, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------------
    // Ownership: foreign vendor 403, missing order 404
    // ------------------------------------------------------------------

    @Test
    void anotherVendorReceives403() throws Exception {
        User otherVendorUser = createUser("other-vendor-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        VendorProfile otherProfile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(otherVendorUser)
                .businessName("Other Blossoms")
                .addressLine1("1 Other Street")
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
        String otherToken = tokenFor(otherVendorUser);

        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // The owner's order is untouched.
        assertEquals(OrderStatus.PLACED, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void missingOrderIs404() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/orders/99999999/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // D-6: a suspended vendor processes existing in-flight orders to completion
    // ------------------------------------------------------------------

    @Test
    void aSuspendedVendorCanAcceptItsOwnExistingOrder() throws Exception {
        // Move the profile to SUSPENDED via the admin endpoint.
        suspendProfile(vendorProfileId);

        // Suspension hides the vendor from discovery and blocks new catalog /
        // inventory work, but must not block processing an existing order.
        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertEquals(OrderStatus.CONFIRMED, orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(5, inventoryRepository.findByProductId(productId).orElseThrow().getQuantity());
        assertEquals(0, inventoryRepository.findByProductId(productId).orElseThrow().getReservedQuantity());
    }

    // ------------------------------------------------------------------
    // Concurrency: double accept has exactly one winner
    // ------------------------------------------------------------------

    @Test
    void concurrentDoubleAcceptHasOneWinnerAndDoesNotDoubleDeduct() throws Exception {
        // Reset the order to PLACED for the concurrency probe.
        orderRepository.findById(orderId).ifPresent(o -> o.setStatus(OrderStatus.PLACED));
        orderRepository.saveAndFlush(orderRepository.findById(orderId).orElseThrow());
        jdbcTemplate.update(
                "UPDATE inventory SET quantity = 10, reserved_quantity = 5 WHERE product_id = ?", productId);

        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Boolean> outcomes = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    try {
                        orderStateMachine.transition(orderId, OrderStatus.CONFIRMED,
                                com.flowerconnect.order.domain.ActorType.VENDOR,
                                userRepository.findByEmail(vendorEmail).orElseThrow().getId(), null);
                        outcomes.add(Boolean.TRUE);
                    } catch (Throwable refused) {
                        outcomes.add(Boolean.FALSE);
                    }
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(30, TimeUnit.SECONDS), "workers did not come up");
        start.countDown();
        assertTrue(done.await(120, TimeUnit.SECONDS), "concurrent accepts did not finish");
        pool.shutdownNow();

        assertTrue(failures.isEmpty(), "unexpected failures: " + failures);
        assertEquals(threads, outcomes.size());
        assertEquals(1L, outcomes.stream().filter(Boolean::booleanValue).count(),
                "exactly one accept may win: " + outcomes);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(5, inventory.getQuantity());   // deducted once
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(1, stockMovementCountOfType(StockMovement.MovementType.SALE_ONLINE));
    }

    // ------------------------------------------------------------------
    // List: pagination, status/date filters, vendor scoping
    // ------------------------------------------------------------------

    @Test
    void listOrdersIsPaginatedAndScopedToTheCallingVendor() throws Exception {
        // A second vendor's order must never appear.
        User otherVendorUser = createUser("scoped-vendor-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        VendorProfile otherProfile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(otherVendorUser)
                .businessName("Scoped Blossoms")
                .addressLine1("1 Scoped Street")
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
        Long otherOrderId = createOrder(customerUserId, otherProfile.getId(), OrderStatus.PLACED,
                LocalDateTime.of(2026, 10, 20, 5, 0));
        String otherToken = tokenFor(otherVendorUser);

        // The calling vendor sees only its own order.
        mockMvc.perform(get("/api/v1/vendor/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken))
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId.intValue()));

        // The other vendor sees only its own order.
        mockMvc.perform(get("/api/v1/vendor/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(otherOrderId.intValue()));
    }

    @Test
    void listOrdersFiltersByStatus() throws Exception {
        Long secondOrderId = createOrder(customerUserId, vendorProfileId, OrderStatus.CONFIRMED,
                LocalDateTime.of(2026, 10, 20, 5, 0));

        mockMvc.perform(get("/api/v1/vendor/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken))
                        .param("status", "PLACED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId.intValue()));

        mockMvc.perform(get("/api/v1/vendor/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken))
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(secondOrderId.intValue()));
    }

    @Test
    void listOrdersFiltersByDateRange() throws Exception {
        createOrder(customerUserId, vendorProfileId, OrderStatus.PLACED,
                LocalDateTime.of(2026, 10, 21, 4, 30)); // next day, excluded

        // toDate is inclusive of the whole day (toDate.atStartOfDay().plusDays(1)),
        // so a same-day toDate excludes the next day's order.
        mockMvc.perform(get("/api/v1/vendor/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken))
                        .param("fromDate", "2026-10-20").param("toDate", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId.intValue()));
    }

    // ------------------------------------------------------------------
    // Today's summary
    // ------------------------------------------------------------------

    @Test
    void todaySummaryReportsCountsAndTotals() throws Exception {
        // Fix the clock at 2026-10-20T10:00Z so the vendor's Asia/Kolkata
        // "today" is 2026-10-20; the PLACED order (created 2026-10-20 04:30
        // UTC = 10:00 Kolkata) falls inside it.
        mutableClock.setInstant(Instant.parse("2026-10-20T10:00:00Z"));

        try {
            mockMvc.perform(get("/api/v1/vendor/orders/summary")
                            .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.today").value("2026-10-20"))
                    .andExpect(jsonPath("$.todayOrderCount").value(1))
                    .andExpect(jsonPath("$.ordersByStatus.PLACED").value(1))
                    // PLACED is not cancelled/rejected, so it counts toward the total.
                    // BigDecimal serialises 199.00 as 199.0 in JSON.
                    .andExpect(jsonPath("$.todayTotalValue").value("199.0"))
                // Not delivered COD, so no expected cash yet.
                .andExpect(jsonPath("$.expectedCodCash").value("0"));
        } finally {
            // Restore the clock to real time so later tests (which mint fresh
            // JWTs) are not validated against a stale instant.
            mutableClock.setInstant(Instant.now());
        }
    }

    // ------------------------------------------------------------------
    // RBAC over HTTP (real tokens, real chain)
    // ------------------------------------------------------------------

    @Test
    void anonymousCallsAreUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/accept"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendor/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendor/orders/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerAndAdminAreForbiddenByTheNamespaceRule() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/orders/" + orderId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vendor/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vendor/orders/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
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

    private void suspendProfile(Long profileId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"integration probe\"}"))
                .andExpect(status().isOk());
    }

    private Long createOrder(Long userId, Long vendorProfileId, OrderStatus status,
                             LocalDateTime createdAtUtc) {
        Order order = orderRepository.saveAndFlush(Order.builder()
                .user(userRepository.findById(userId).orElseThrow())
                .vendor(vendorProfileRepository.findById(vendorProfileId).orElseThrow())
                .status(status)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(new BigDecimal("199.00"))
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(new BigDecimal("199.00"))
                .deliveryAddressLine1("123 Test Street")
                .deliveryLatitude(new BigDecimal("12.9716"))
                .deliveryLongitude(new BigDecimal("77.5946"))
                .build());
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(createdAtUtc), order.getId());
        return order.getId();
    }

    private void createOrderItem(Long orderId, Long productId, Long vendorProfileId, int quantity) {
        orderItemRepository.saveAndFlush(OrderItem.builder()
                .order(orderRepository.findById(orderId).orElseThrow())
                .product(productRepository.findById(productId).orElseThrow())
                .vendorId(vendorProfileId)
                .quantity(quantity)
                .priceSnapshot(new BigDecimal("199.00"))
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private int stockMovementCount() {
        return Math.toIntExact(stockMovementRepository.count());
    }

    private int stockMovementCountOfType(StockMovement.MovementType type) {
        return (int) stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
                .filter(m -> m.getMovementType() == type).count();
    }

    private StockMovement lastMovement() {
        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
        assertFalse(movements.isEmpty(), "expected at least one stock movement");
        return movements.get(0);
    }

    private static String randomDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return digits.toString();
    }
}
