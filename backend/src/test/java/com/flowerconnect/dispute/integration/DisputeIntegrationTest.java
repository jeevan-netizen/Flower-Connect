package com.flowerconnect.dispute.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.config.PlatformConfig;
import com.flowerconnect.config.PlatformConfigRepository;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.User.Status;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.dispute.dto.RaiseDisputeRequest;
import com.flowerconnect.dispute.dto.ResolveDisputeRequest;
import com.flowerconnect.dispute.dto.VendorDisputeResponseRequest;
import com.flowerconnect.dispute.event.DisputeResolved;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.test.MutableClock;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Integration tests for disputes (plan Phase 8, task 8.4) against real MySQL.
 *
 * <p>Covers: dispute window boundary (last-allowed instant vs one second after),
 * non-delivered orders (409), duplicate and concurrent dispute creation,
 * one-time vendor response, ownership/visibility, role access, and admin queue
 * filtering.
 *
 * <p>The class is deliberately not {@code @Transactional}: every test commits
 * to the shared Testcontainers database, so fixtures use unique emails, product
 * names and order ids to stay isolated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class DisputeIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String PINCODE = "560034";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private VendorProfileRepository vendorProfiles;
    @Autowired private ServiceLocationRepository locations;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductService products;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired private PaymentRepository payments;
    @Autowired private PlatformConfigRepository platformConfigRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MutableClock mutableClock;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired private org.springframework.test.context.event.ApplicationEvents applicationEvents;

    private Long locationId;
    private Long categoryId;

    @PostConstruct
    void init() {
        locationId = locations.findByPincode(PINCODE).orElseThrow().getId();
        categoryId = categories.findBySlug("roses").orElseThrow().getId();
    }

    // ------------------------------------------------------------------
    // Customer raise: window boundary (last-allowed instant vs one second after)
    // ------------------------------------------------------------------

    @Test
    void customerCanRaiseDisputeWithinWindow() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-dispute-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Dispute Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult res = raiseDispute(token, orderId, "ITEM_NOT_AS_DESCRIBED", "The flowers were wilted");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("OPEN");
        assertThat(body.get("reasonCode").asText()).isEqualTo("ITEM_NOT_AS_DESCRIBED");
        assertThat(body.get("customerEmail").asText()).isEqualTo(customerEmail);
    }

    @Test
    void customerCannotRaiseDisputeAfterWindowExpires() throws Exception {
        // Default window is 7 days. Deliver the order, then advance the clock
        // 8 days past delivery so the window has clearly expired.
        Instant savedInstant = mutableClock.instant();
        try {
            String vendorEmail = createVendor();
            Long vendorProfileId = vendorProfileId(vendorEmail);
            String customerEmail = "cust-expired-" + uuid() + "@t.com";
            User customer = createUser(customerEmail, "CUSTOMER");
            String token = login(customer);

            Long productId = createProduct(vendorEmail, "Expired Rose " + uuid(), new BigDecimal("100.00"));
            Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

            // Advance the clock 8 days past delivery (window is 7 days).
            mutableClock.advance(java.time.Duration.ofDays(8));

            MvcResult res = raiseDispute(token, orderId, "ITEM_NOT_AS_DESCRIBED", "Too late");
            assertThat(res.getResponse().getStatus()).isEqualTo(409);
        } finally {
            mutableClock.setInstant(savedInstant);
        }
    }

    @Test
    void nonDeliveredOrderCannotBeDisputed() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-nondel-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "NonDelivered Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createPlacedOrder(customer, vendorProfileId, productId);

        MvcResult res = raiseDispute(token, orderId, "ITEM_NOT_AS_DESCRIBED", "Not delivered yet");
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void duplicateDisputeForSameOrderIs409() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-dup-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Dup Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult first = raiseDispute(token, orderId, "ITEM_NOT_AS_DESCRIBED", "First dispute");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        MvcResult second = raiseDispute(token, orderId, "ITEM_DAMAGED", "Second dispute");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void concurrentDisputeCreationResultsInExactlyOne() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-concurrent-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Concurrent Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        var successes = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var failures = new java.util.concurrent.CopyOnWriteArrayList<String>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    MvcResult res = raiseDispute(token, orderId, "ITEM_NOT_AS_DESCRIBED", "Concurrent dispute " + idx);
                    successes.add(String.valueOf(res.getResponse().getStatus()));
                } catch (Throwable t) {
                    failures.add(t.getClass().getSimpleName());
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Exactly one should succeed (200), the rest get 409 (unique constraint).
        long ok = successes.stream().filter(s -> s.equals("200")).count();
        assertThat(ok).isEqualTo(1);
        long conflict = successes.stream().filter(s -> s.equals("409")).count();
        assertThat(conflict).isEqualTo(threads - 1);
        assertThat(failures).isEmpty();

        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM disputes WHERE order_id = ?", Long.class, orderId);
        assertThat(count).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Vendor response: one-time response, OPEN -> UNDER_REVIEW
    // ------------------------------------------------------------------

    @Test
    void vendorRespondsToOpenDisputeMovesToUnderReview() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-vendresp-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "VendorResp Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_NOT_AS_DESCRIBED", "Wilted flowers");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Vendor responds
        String vendorToken = login(lookup(vendorEmail));
        MvcResult respRes = respondToDispute(vendorToken, disputeId, "We offered a replacement");
        assertThat(respRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(respRes.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(body.get("vendorResponse").asText()).isEqualTo("We offered a replacement");
    }

    @Test
    void vendorCannotRespondTwiceToSameDispute() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-double-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "DoubleResp Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_NOT_AS_DESCRIBED", "Wilted");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        String vendorToken = login(lookup(vendorEmail));
        MvcResult first = respondToDispute(vendorToken, disputeId, "First response");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        MvcResult second = respondToDispute(vendorToken, disputeId, "Second response");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void suspendedVendorCanRespondToInFlightDispute() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        // Suspend the vendor: D-6 says in-flight orders continue.
        jdbcTemplate.update("UPDATE vendor_profiles SET status = 'SUSPENDED' WHERE id = ?", vendorProfileId);

        String customerEmail = "cust-suspended-vendor-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "SuspendedVendor Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_NOT_AS_DESCRIBED", "Wilted");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        String vendorToken = login(lookup(vendorEmail));
        MvcResult respRes = respondToDispute(vendorToken, disputeId, "I am suspended but responding");
        assertThat(respRes.getResponse().getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Ownership and RBAC
    // ------------------------------------------------------------------

    @Test
    void customerCannotSeeAnotherCustomersDispute() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String ownerEmail = "cust-owner-" + uuid() + "@t.com";
        User owner = createUser(ownerEmail, "CUSTOMER");
        String thiefEmail = "cust-thief-" + uuid() + "@t.com";
        User thief = createUser(thiefEmail, "CUSTOMER");
        String thiefToken = login(thief);

        Long productId = createProduct(vendorEmail, "TheftDispute Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(owner, vendorProfileId, productId);

        MvcResult raiseRes = raiseDispute(login(owner), orderId, "ITEM_NOT_AS_DESCRIBED", "Wilted");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult res = getDispute(thiefToken, disputeId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void vendorCannotSeeAnotherVendorsDispute() throws Exception {
        String victimVendor = createVendor();
        Long victimVendorProfileId = vendorProfileId(victimVendor);
        String attackerVendor = createVendor();
        String attackerToken = login(lookup(attackerVendor));
        String customerEmail = "cust-crossvendor-" + uuid() + "@t.com";
        createUser(customerEmail, "CUSTOMER");

        Long productId = createProduct(victimVendor, "CrossVendor Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(lookup(customerEmail), victimVendorProfileId, productId);

        MvcResult raiseRes = raiseDispute(login(lookup(customerEmail)), orderId, "ITEM_NOT_AS_DESCRIBED", "Wilted");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult res = getDispute(attackerToken, disputeId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void vendorCannotRespondToAnotherVendorsDispute() throws Exception {
        String victimVendor = createVendor();
        Long victimVendorProfileId = vendorProfileId(victimVendor);
        String attackerVendor = createVendor();
        String attackerToken = login(lookup(attackerVendor));
        String customerEmail = "cust-crossresp-" + uuid() + "@t.com";
        createUser(customerEmail, "CUSTOMER");

        Long productId = createProduct(victimVendor, "CrossResp Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(lookup(customerEmail), victimVendorProfileId, productId);

        MvcResult raiseRes = raiseDispute(login(lookup(customerEmail)), orderId, "ITEM_NOT_AS_DESCRIBED", "Wilted");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult res = respondToDispute(attackerToken, disputeId, "Not my order");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void unauthenticatedRaiseDisputeIs401() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-401-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        Long productId = createProduct(vendorEmail, "UnauthDispute Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        mvc.perform(post("/api/v1/disputes/orders/{orderId}", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RaiseDisputeRequest("ITEM_NOT_AS_DESCRIBED", "x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingDisputeIs404() throws Exception {
        String customerEmail = "cust-miss-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        MvcResult res = getDispute(token, 999999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void invalidReasonCodeIs400() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-badreason-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "BadReason Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult res = raiseDispute(token, orderId, "INVALID_REASON", "Bad reason code");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void emptyDescriptionIs400() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-emptydesc-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "EmptyDesc Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult res = raiseDispute(token, orderId, "ITEM_NOT_AS_DESCRIBED", "   ");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // Admin queue filtering
    // ------------------------------------------------------------------

    @Test
    void adminSeesAllDisputesInQueue() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-adminqueue-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-queue-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        Long productId = createProduct(vendorEmail, "AdminQueue Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        raiseDispute(login(customer), orderId, "ITEM_NOT_AS_DESCRIBED", "Wilted");

        String adminToken2 = login(lookup(adminEmail));
        MvcResult res = listAdminDisputes(adminToken2, null, null, null, 0, 20);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void adminFiltersByStatus() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-adminfilter-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-filter-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        Long productId = createProduct(vendorEmail, "AdminFilter Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        raiseDispute(login(customer), orderId, "ITEM_NOT_AS_DESCRIBED", "Wilted");

        // Filter by OPEN status
        MvcResult res = listAdminDisputes(adminToken, DisputeStatus.OPEN, null, null, 0, 20);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : body.get("content")) {
            assertThat(item.get("status").asText()).isEqualTo("OPEN");
        }
    }

    @Test
    void adminCanSeeDisputeDetail() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-admindetail-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-detail-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        Long productId = createProduct(vendorEmail, "AdminDetail Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult raiseRes = raiseDispute(login(customer), orderId, "ITEM_DAMAGED", "Crushed petals");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult res = getAdminDispute(adminToken, disputeId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("id").asLong()).isEqualTo(disputeId);
        assertThat(body.get("customerEmail").asText()).isEqualTo(customerEmail);
        assertThat(body.get("vendorResponse").isNull()).isTrue();
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private MvcResult raiseDispute(String token, Long orderId, String reasonCode, String description) throws Exception {
        RaiseDisputeRequest req = new RaiseDisputeRequest(reasonCode, description);
        return mvc.perform(post("/api/v1/disputes/orders/{orderId}", orderId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andReturn();
    }

    private MvcResult respondToDispute(String token, Long disputeId, String response) throws Exception {
        VendorDisputeResponseRequest req = new VendorDisputeResponseRequest(response);
        return mvc.perform(post("/api/v1/vendor/disputes/{disputeId}/respond", disputeId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andReturn();
    }

    private MvcResult getDispute(String token, Long disputeId) throws Exception {
        return mvc.perform(get("/api/v1/disputes/{disputeId}", disputeId)
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult getAdminDispute(String token, Long disputeId) throws Exception {
        return mvc.perform(get("/api/v1/admin/disputes/{disputeId}", disputeId)
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult listAdminDisputes(String token, DisputeStatus status,
            java.time.LocalDateTime from, java.time.LocalDateTime to, int page, int size) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder =
                get("/api/v1/admin/disputes")
                        .header("Authorization", "Bearer " + token);
        if (status != null) {
            builder = builder.param("status", status.name());
        }
        if (from != null) {
            builder = builder.param("from", from.toString());
        }
        if (to != null) {
            builder = builder.param("to", to.toString());
        }
        builder = builder.param("page", String.valueOf(page)).param("size", String.valueOf(size));
        return mvc.perform(builder).andReturn();
    }

    private String login(User user) throws Exception {
        return login(user.getEmail());
    }

    private String login(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest() {{
                    setEmail(email);
                    setPassword(PASSWORD);
                }})))
            .andExpect(status().isOk()).andReturn();
        JsonNode auth = json.readTree(res.getResponse().getContentAsString());
        return auth.get("accessToken").asText();
    }

    private User lookup(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private User createUser(String email, String roleName) {
        Role role = roles.findByName(roleName).orElseThrow();
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Test " + roleName)
                .role(role)
                .status(Status.ACTIVE)
                .build();
        return users.saveAndFlush(user);
    }

    private String createVendor() {
        String email = "vendor-" + uuid() + "@t.com";
        Role role = roles.findByName("FLORIST").orElseThrow();
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Vendor " + uuid())
                .role(role)
                .status(Status.ACTIVE)
                .build();
        users.saveAndFlush(user);
        var location = locations.findById(locationId).orElseThrow();
        VendorProfile vp = VendorProfile.builder()
                .user(user)
                .businessName("Vendor Blossoms " + uuid())
                .addressLine1("1 Test Street")
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
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
                .build();
        vendorProfiles.saveAndFlush(vp);
        return email;
    }

    private Long vendorProfileId(String vendorEmail) {
        return vendorProfiles.findByUserEmail(vendorEmail).orElseThrow().getId();
    }

    private Long createProduct(String vendorEmail, String name, BigDecimal price) {
        return products.create(vendorEmail, ProductRequest.builder()
                .name(name)
                .categoryId(categoryId)
                .description("Integration test product")
                .basePrice(price)
                .build()).getId();
    }

    private Long createDeliveredOrder(User customer, Long vendorProfileId, Long productId) {
        // Create PLACED order, then transition to DELIVERED (status + history entry).
        Long orderId = createPlacedOrder(customer, vendorProfileId, productId);
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus(PaymentStatus.COLLECTED);
        orderRepository.saveAndFlush(order);
        // Create payment record for COD order (created at checkout in Phase 5)
        Payment codPayment = Payment.builder()
                .order(order)
                .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.COD)
                .provider("cod")
                .amount(order.getTotal())
                .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.COLLECTED)
                .collectedAt(LocalDateTime.now(mutableClock))
                .build();
        payments.saveAndFlush(codPayment);
        LocalDateTime deliveredAt = LocalDateTime.now(mutableClock);
        appendHistory(orderId, OrderStatus.PLACED, OrderStatus.DELIVERED, ActorType.VENDOR, customer,
                "Order delivered", deliveredAt);
        return orderId;
    }

    private Long createPlacedOrder(User customer, Long vendorProfileId, Long productId) {
        VendorProfile vendor = vendorProfiles.findById(vendorProfileId).orElseThrow();
        Order order = Order.builder()
                .id(null)
                .user(customer)
                .vendor(vendor)
                .status(OrderStatus.PLACED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(new BigDecimal("100.00"))
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(new BigDecimal("100.00"))
                .deliveryAddressLine1("123 Test St")
                .deliveryLatitude(new BigDecimal("12.97"))
                .deliveryLongitude(new BigDecimal("77.59"))
                .build();
        return orderRepository.saveAndFlush(order).getId();
    }

    private void appendHistory(Long orderId, OrderStatus fromStatus, OrderStatus toStatus,
            ActorType actorType, User actor, String reason, LocalDateTime createdAt) {
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

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ------------------------------------------------------------------
    // Task 8.5: Dispute resolution tests
    // ------------------------------------------------------------------

    @Test
    void adminResolvesDisputeWithFullRefundForOnlineOrder() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-online-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveOnline Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOnlineOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_NOT_AS_DESCRIBED", "Wilted flowers");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Admin resolves with full refund
        String adminEmail = "admin-resolve-online-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_REFUND,
                "Full refund granted - item not as described",
                new BigDecimal("100.00"));
        MvcResult res = resolveDispute(adminToken, disputeId, request);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("RESOLVED_REFUND");
        assertThat(new BigDecimal(body.get("refundAmount").asText())).isEqualByComparingTo(new BigDecimal("100.00"));

        // Payment should be marked refunded
        Payment payment = payments.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("100.00");

        // Order status should remain DELIVERED
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // Event published
        assertThat(applicationEvents.stream(DisputeResolved.class)
                .anyMatch(e -> e.getDisputeId().equals(disputeId)
                        && e.getStatus() == DisputeStatus.RESOLVED_REFUND
                        && e.getRefundAmount().compareTo(new BigDecimal("100.00")) == 0
                        && e.getPaymentMethod() == Order.PaymentMethod.ONLINE)).isTrue();
    }

    @Test
    void adminResolvesDisputeWithPartialRefundForOnlineOrder() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-partial-online-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolvePartialOnline Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOnlineOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_DAMAGED", "Damaged petals");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Admin resolves with partial refund
        String adminEmail = "admin-resolve-partial-online-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_PARTIAL,
                "Partial refund granted - item damaged",
                new BigDecimal("30.00"));
        MvcResult res = resolveDispute(adminToken, disputeId, request);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("RESOLVED_PARTIAL");
        assertThat(new BigDecimal(body.get("refundAmount").asText())).isEqualByComparingTo(new BigDecimal("30.00"));

        // Payment should be marked refunded with partial amount
        Payment payment = payments.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("30.00");

        // Order status should remain DELIVERED
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // Event published
        assertThat(applicationEvents.stream(DisputeResolved.class)
                .anyMatch(e -> e.getDisputeId().equals(disputeId)
                        && e.getStatus() == DisputeStatus.RESOLVED_PARTIAL
                        && e.getRefundAmount().compareTo(new BigDecimal("30.00")) == 0
                        && e.getPaymentMethod() == Order.PaymentMethod.ONLINE)).isTrue();
    }

    @Test
    void adminResolvesDisputeWithRejectNoPaymentEffect() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-reject-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveReject Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOnlineOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "LATE_DELIVERY", "Late delivery");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Admin rejects dispute
        String adminEmail = "admin-resolve-reject-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_REJECTED,
                "Rejected - complaint unfounded",
                null);
        MvcResult res = resolveDispute(adminToken, disputeId, request);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("RESOLVED_REJECTED");
        assertThat(body.get("refundAmount").isNull()).isTrue();

        // Payment should NOT be marked refunded (still PAID)
        Payment payment = payments.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        assertThat(payment.getRefundedAmount()).isNull();

        // Order status should remain DELIVERED
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // Event published with null refund amount
        assertThat(applicationEvents.stream(DisputeResolved.class)
                .anyMatch(e -> e.getDisputeId().equals(disputeId)
                        && e.getStatus() == DisputeStatus.RESOLVED_REJECTED
                        && e.getRefundAmount() == null)).isTrue();
    }

    @Test
    void adminResolvesCODDisputeFullRefundPaymentUnchanged() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-cod-full-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveCODFull Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId); // COD

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_NOT_AS_DESCRIBED", "Wrong item");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Admin resolves with full refund
        String adminEmail = "admin-resolve-cod-full-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_REFUND,
                "Full refund for COD order - out of band",
                new BigDecimal("100.00"));
        MvcResult res = resolveDispute(adminToken, disputeId, request);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("RESOLVED_REFUND");
        assertThat(new BigDecimal(body.get("refundAmount").asText())).isEqualByComparingTo(new BigDecimal("100.00"));

        // COD payment should remain COLLECTED
        Payment payment = payments.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COLLECTED);
        assertThat(payment.getRefundedAmount()).isNull();

        // Order status should remain DELIVERED
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // Event published with COD payment method
        assertThat(applicationEvents.stream(DisputeResolved.class)
                .anyMatch(e -> e.getDisputeId().equals(disputeId)
                        && e.getStatus() == DisputeStatus.RESOLVED_REFUND
                        && e.getRefundAmount().compareTo(new BigDecimal("100.00")) == 0
                        && e.getPaymentMethod() == Order.PaymentMethod.COD)).isTrue();
    }

    @Test
    void adminResolvesCODDisputePartialRefundPaymentUnchanged() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-cod-partial-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveCODPartial Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId); // COD

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "QUALITY_COMPLAINT", "Poor quality");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Admin resolves with partial refund
        String adminEmail = "admin-resolve-cod-partial-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_PARTIAL,
                "Partial refund for COD order - out of band",
                new BigDecimal("45.50"));
        MvcResult res = resolveDispute(adminToken, disputeId, request);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("RESOLVED_PARTIAL");
        assertThat(new BigDecimal(body.get("refundAmount").asText())).isEqualByComparingTo(new BigDecimal("45.50"));

        // COD payment should remain COLLECTED
        Payment payment = payments.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COLLECTED);
        assertThat(payment.getRefundedAmount()).isNull();

        // Order status should remain DELIVERED
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void concurrentDisputeResolutionResultsInExactlyOneWinner() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-concurrent-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveConcurrent Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOnlineOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_NOT_AS_DESCRIBED", "Concurrent test");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Admin resolves - need two admin tokens to simulate concurrent requests
        String adminEmail1 = "admin-concurrent-1-" + uuid() + "@t.com";
        String adminEmail2 = "admin-concurrent-2-" + uuid() + "@t.com";
        createUser(adminEmail1, "ADMIN");
        createUser(adminEmail2, "ADMIN");
        String adminToken1 = login(lookup(adminEmail1));
        String adminToken2 = login(lookup(adminEmail2));

        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        var successes = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var failures = new java.util.concurrent.CopyOnWriteArrayList<String>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            final String token = (i % 2 == 0) ? adminToken1 : adminToken2;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    ResolveDisputeRequest req = new ResolveDisputeRequest(
                            DisputeStatus.RESOLVED_REFUND,
                            "Concurrent resolution attempt",
                            new BigDecimal("100.00"));
                    MvcResult res = resolveDispute(token, disputeId, req);
                    successes.add(String.valueOf(res.getResponse().getStatus()));
                } catch (Throwable t) {
                    failures.add(t.getClass().getSimpleName());
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Exactly one should succeed (200), rest get 409 (already resolved)
        long ok = successes.stream().filter(s -> s.equals("200")).count();
        assertThat(ok).isEqualTo(1);
        long conflict = successes.stream().filter(s -> s.equals("409")).count();
        assertThat(conflict).isEqualTo(threads - 1);
        assertThat(failures).isEmpty();

        // Only one dispute row should exist
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM disputes WHERE id = ?", Long.class, disputeId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void repeatResolutionOnResolvedDisputeIs409() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-repeat-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveRepeat Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOnlineOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_NOT_AS_DESCRIBED", "Repeat test");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Admin resolves
        String adminEmail = "admin-resolve-repeat-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_REFUND,
                "First resolution",
                new BigDecimal("100.00"));
        MvcResult first = resolveDispute(adminToken, disputeId, request);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        // Second resolution attempt should be 409
        ResolveDisputeRequest request2 = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_REJECTED,
                "Second resolution attempt",
                null);
        MvcResult second = resolveDispute(adminToken, disputeId, request2);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void partialRefundAmountBoundsAndRounding() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-bounds-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveBounds Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOnlineOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_DAMAGED", "Bounds test");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        String adminEmail = "admin-resolve-bounds-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        // Refund amount = 0 should be 400
        ResolveDisputeRequest zeroReq = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_PARTIAL, "Zero refund", BigDecimal.ZERO);
        MvcResult zeroRes = resolveDispute(adminToken, disputeId, zeroReq);
        assertThat(zeroRes.getResponse().getStatus()).isEqualTo(400);

        // Refund amount negative should be 400
        ResolveDisputeRequest negReq = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_PARTIAL, "Negative refund", new BigDecimal("-10.00"));
        MvcResult negRes = resolveDispute(adminToken, disputeId, negReq);
        assertThat(negRes.getResponse().getStatus()).isEqualTo(400);

        // Refund amount > order total should be 400
        ResolveDisputeRequest overReq = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_PARTIAL, "Over refund", new BigDecimal("150.00"));
        MvcResult overRes = resolveDispute(adminToken, disputeId, overReq);
        assertThat(overRes.getResponse().getStatus()).isEqualTo(400);

        // Valid partial amount should succeed
        ResolveDisputeRequest validReq = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_PARTIAL, "Valid partial", new BigDecimal("25.00"));
        MvcResult validRes = resolveDispute(adminToken, disputeId, validReq);
        assertThat(validRes.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void refundAmountRoundedToTwoDecimalsHALF_UP() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-round-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveRound Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOnlineOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_DAMAGED", "Rounding test");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        String adminEmail = "admin-resolve-round-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        // Refund amount with more than 2 decimals should be rounded HALF_UP
        ResolveDisputeRequest roundReq = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_PARTIAL, "Rounded refund", new BigDecimal("33.333"));
        MvcResult roundRes = resolveDispute(adminToken, disputeId, roundReq);
        assertThat(roundRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(roundRes.getResponse().getContentAsString());
        assertThat(body.get("refundAmount").asText()).isEqualTo("33.33");
    }

    @Test
    void nonAdminCannotResolveDisputeIs403() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-nonadmin-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveNonAdmin Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOnlineOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_NOT_AS_DESCRIBED", "Non-admin test");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Customer tries to resolve - should be 403
        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_REFUND,
                "Unauthorized resolution attempt",
                new BigDecimal("100.00"));
        MvcResult res = resolveDispute(customerToken, disputeId, request);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);

        // Vendor tries to resolve - should be 403
        String vendorToken = login(lookup(vendorEmail));
        MvcResult vendorRes = resolveDispute(vendorToken, disputeId, request);
        assertThat(vendorRes.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void auditLogWrittenForDisputeResolution() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-resolve-audit-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "ResolveAudit Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOnlineOrder(customer, vendorProfileId, productId);

        // Customer raises dispute
        MvcResult raiseRes = raiseDispute(customerToken, orderId, "ITEM_NOT_AS_DESCRIBED", "Audit test");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(200);
        Long disputeId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        // Admin resolves
        String adminEmail = "admin-resolve-audit-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        ResolveDisputeRequest request = new ResolveDisputeRequest(
                DisputeStatus.RESOLVED_PARTIAL,
                "Audit trail test - partial refund",
                new BigDecimal("20.00"));
        MvcResult res = resolveDispute(adminToken, disputeId, request);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        // Verify audit log row
        List<AuditLog> auditLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc("DISPUTE", disputeId);
        assertThat(auditLogs).isNotEmpty();
        AuditLog auditLog = auditLogs.get(auditLogs.size() - 1);
        assertThat(auditLog.getActionType()).isEqualTo("DISPUTE_RESOLVED");
        assertThat(auditLog.getActor().getId()).isEqualTo(users.findByEmail(adminEmail).orElseThrow().getId());
        assertThat(auditLog.getReason()).contains("RESOLVED_PARTIAL");
        assertThat(auditLog.getReason()).contains("20.00");
        assertThat(auditLog.getCreatedAt()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Additional harness methods for dispute resolution
    // ------------------------------------------------------------------

    private Long createDeliveredOnlineOrder(User customer, Long vendorProfileId, Long productId) {
        VendorProfile vendor = vendorProfiles.findById(vendorProfileId).orElseThrow();
        Order order = Order.builder()
                .id(null)
                .user(customer)
                .vendor(vendor)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PAID)
                .subtotal(new BigDecimal("100.00"))
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(new BigDecimal("100.00"))
                .deliveryAddressLine1("123 Test St")
                .deliveryLatitude(new BigDecimal("12.97"))
                .deliveryLongitude(new BigDecimal("77.59"))
                .build();
        Long orderId = orderRepository.saveAndFlush(order).getId();
        // Create payment record for online order
        Payment payment = Payment.builder()
                .order(order)
                .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE)
                .provider("simulated")
                .amount(new BigDecimal("100.00"))
                .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.PAID)
                .build();
        payments.saveAndFlush(payment);
        LocalDateTime deliveredAt = LocalDateTime.now(mutableClock);
        appendHistory(orderId, OrderStatus.PLACED, OrderStatus.DELIVERED, ActorType.VENDOR, customer,
                "Order delivered", deliveredAt);
        return orderId;
    }

    private MvcResult resolveDispute(String token, Long disputeId, ResolveDisputeRequest request) throws Exception {
        return mvc.perform(post("/api/v1/admin/disputes/{disputeId}/resolve", disputeId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andReturn();
    }

    private void createAdmin(String email) {
        createUser(email, "ADMIN");
    }
}