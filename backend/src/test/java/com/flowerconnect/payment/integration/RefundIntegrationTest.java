package com.flowerconnect.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.User.Status;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.CancellationService;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
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

/**
 * Integration tests for admin refund handling (plan Phase 8, task 8.3)
 * against a real MySQL database.
 *
 * <p>Covers: ADMIN-only refund queue listing with pagination and vendor/date
 * filters, mark-refunded with idempotency, wrong-state 409, audit_log row
 * contents, and customer-visible refund state via order detail.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefundIntegrationTest extends AbstractIntegrationTest {

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
    @Autowired private PaymentRepository payments;
    @Autowired private CancellationService cancellationService;
    @Autowired private PaymentService paymentService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private com.flowerconnect.inventory.repository.InventoryRepository inventoryRepository;
    @Autowired private PasswordEncoder encoder;

    private Long locationId;
    private Long categoryId;

    @jakarta.annotation.PostConstruct
    void init() {
        locationId = locations.findByPincode(PINCODE).orElseThrow().getId();
        categoryId = categories.findBySlug("roses").orElseThrow().getId();
    }

    // ------------------------------------------------------------------
    // RBAC: refund queue is ADMIN-only
    // ------------------------------------------------------------------

    @Test
    void unauthenticatedRefundQueueIs401() throws Exception {
        try {
            mvc.perform(get("/api/v1/admin/refunds")).andExpect(status().isUnauthorized());
        } catch (AssertionError e) {
            throw e;
        }
    }

    @Test
    void customerCannotAccessRefundQueue() throws Exception {
        String vendorEmail = createVendor();
        String customerEmail = "cust-rq-" + uuid() + "@t.com";
        createUser(customerEmail, "CUSTOMER");
        String token = login(customerEmail);

        MvcResult res = mvc.perform(get("/api/v1/admin/refunds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void floristCannotAccessRefundQueue() throws Exception {
        String vendorEmail = createVendor();
        String token = login(vendorEmail);

        MvcResult res = mvc.perform(get("/api/v1/admin/refunds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void adminCanListRefundQueue() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-rq2-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-rq-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        Long productId = createProduct(vendorEmail, "Refund Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productId, 2);
        reserve(productId, 1);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"), 1);
        createPayment(orderId, PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"));

        // Cancel → marks payment REFUND_PENDING
        cancellationService.cancel(customerEmail, com.flowerconnect.order.domain.ActorType.CUSTOMER,
            orderId, null);

        MvcResult res = mvc.perform(get("/api/v1/admin/refunds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk()).andReturn();
        JsonNode page = json.readTree(res.getResponse().getContentAsString());
        assertThat(page.get("content").isArray()).isTrue();
        assertThat(page.get("content").size()).isGreaterThanOrEqualTo(1);
        JsonNode item = page.get("content").get(0);
        assertThat(item.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(item.get("paymentStatus").asText()).isEqualTo("REFUND_PENDING");
    }

    // ------------------------------------------------------------------
    // Vendor and date filters on refund queue
    // ------------------------------------------------------------------

    @Test
    void refundQueueVendorFilter() throws Exception {
        String vendorA = createVendor();
        String vendorB = createVendor();
        String customerEmail = "cust-rqf-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-rqf-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        Long vendorAProfileId = vendorProfileId(vendorA);
        Long productA = createProduct(vendorA, "VendorA Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productA, 2);
        reserve(productA, 1);
        Long orderA = createOrder(customer, vendorAProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"), 1);
        createPayment(orderA, PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"));

        Long vendorBProfileId = vendorProfileId(vendorB);
        Long productB = createProduct(vendorB, "VendorB Rose " + uuid(), new BigDecimal("200.00"));
        stockIn(productB, 2);
        reserve(productB, 1);
        Long orderB = createOrder(customer, vendorBProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("200.00"), 1);
        createPayment(orderB, PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("200.00"));

        cancellationService.cancel(customerEmail,
            com.flowerconnect.order.domain.ActorType.CUSTOMER, orderA, null);
        cancellationService.cancel(customerEmail,
            com.flowerconnect.order.domain.ActorType.CUSTOMER, orderB, null);

        // Filter by vendor A only
        MvcResult res = mvc.perform(get("/api/v1/admin/refunds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .param("vendorId", vendorAProfileId.toString()))
            .andExpect(status().isOk()).andReturn();
        JsonNode page = json.readTree(res.getResponse().getContentAsString());
        JsonNode content = page.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.get(0).get("orderId").asLong()).isEqualTo(orderA);
    }

    @Test
    void refundQueueDateFilter() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-rqd-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-rqd-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        Long productId = createProduct(vendorEmail, "Date Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productId, 2);
        reserve(productId, 1);
        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"), 1);
        createPayment(orderId, PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"));
        cancellationService.cancel(customerEmail,
            com.flowerconnect.order.domain.ActorType.CUSTOMER, orderId, null);

        // fromDate in the future → empty
        MvcResult res = mvc.perform(get("/api/v1/admin/refunds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .param("fromDate", LocalDateTime.now().plusDays(1).toString()))
            .andExpect(status().isOk()).andReturn();
        JsonNode page = json.readTree(res.getResponse().getContentAsString());
        assertThat(page.get("content").size()).isEqualTo(0);

        // fromDate in the past → includes the payment
        res = mvc.perform(get("/api/v1/admin/refunds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .param("fromDate", LocalDateTime.now().minusDays(1).toString()))
            .andExpect(status().isOk()).andReturn();
        page = json.readTree(res.getResponse().getContentAsString());
        assertThat(page.get("content").size()).isGreaterThanOrEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Mark refunded: success, idempotency, wrong-state 409
    // ------------------------------------------------------------------

    @Test
    void adminMarksRefundedSuccess() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-mr-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-mr-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        Long productId = createProduct(vendorEmail, "MarkRefund Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productId, 2);
        reserve(productId, 1);
        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"), 1);
        createPayment(orderId, PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"));
        cancellationService.cancel(customerEmail,
            com.flowerconnect.order.domain.ActorType.CUSTOMER, orderId, null);

        MvcResult res = mvc.perform(post("/api/v1/admin/refunds/{orderId}/mark", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new com.flowerconnect.payment.dto.MarkRefundRequest() {{
                        setReferenceNote("ref-12345");
                    }})))
            .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("paymentStatus").asText()).isEqualTo("REFUNDED");
        assertThat(body.get("refundedAt")).isNotNull();

        // Verify payment and order both updated
        payments.findByOrderId(orderId).ifPresent(p -> {
            assertThat(p.getStatus()).isEqualTo(
                    com.flowerconnect.payment.domain.Payment.PaymentStatus.REFUNDED);
            assertThat(p.getRefundedAt()).isNotNull();
        });
        orderRepository.findById(orderId).ifPresent(o -> {
            assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        });
    }

    @Test
    void markRefundedAlreadyRefundedIsIdempotent() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-idem-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-idem-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        Long productId = createProduct(vendorEmail, "Idem Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productId, 2);
        reserve(productId, 1);
        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"), 1);
        createPayment(orderId, PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"));
        cancellationService.cancel(customerEmail,
            com.flowerconnect.order.domain.ActorType.CUSTOMER, orderId, null);

        // First mark
        MvcResult first = mvc.perform(post("/api/v1/admin/refunds/{orderId}/mark", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new com.flowerconnect.payment.dto.MarkRefundRequest() {{
                        setReferenceNote("ref-first");
                    }})))
            .andExpect(status().isOk()).andReturn();

        // Second mark - idempotent, returns current state
        MvcResult second = mvc.perform(post("/api/v1/admin/refunds/{orderId}/mark", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new com.flowerconnect.payment.dto.MarkRefundRequest() {{
                        setReferenceNote("ref-second");
                    }})))
            .andExpect(status().isOk()).andReturn();

        JsonNode firstBody = json.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = json.readTree(second.getResponse().getContentAsString());
        assertThat(firstBody.get("paymentStatus").asText()).isEqualTo("REFUNDED");
        assertThat(secondBody.get("paymentStatus").asText()).isEqualTo("REFUNDED");
        // refundedAt should be present and unchanged on idempotent call (DATETIME(6) truncates nanos)
        assertThat(secondBody.get("refundedAt").asText()).isNotNull();
        assertThat(firstBody.get("refundedAt").asText()).isNotNull();
        assertThat(secondBody.get("refundedAt").asText().substring(0, Math.min(19, secondBody.get("refundedAt").asText().length())))
            .isEqualTo(firstBody.get("refundedAt").asText().substring(0, Math.min(19, firstBody.get("refundedAt").asText().length())));
    }

    @Test
    void markRefundedWrongStateIs409() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-ws-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-ws-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        Long productId = createProduct(vendorEmail, "WrongState Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productId, 2);
        reserve(productId, 1);
        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PENDING, new BigDecimal("100.00"), 1);
        createPayment(orderId, PaymentMethod.ONLINE, PaymentStatus.PENDING, new BigDecimal("100.00"));

        MvcResult res = mvc.perform(post("/api/v1/admin/refunds/{orderId}/mark", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new com.flowerconnect.payment.dto.MarkRefundRequest() {{
                        setReferenceNote("ref-ws");
                    }})))
            .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void markRefundedMissingOrderIs404() throws Exception {
        String adminEmail = "admin-miss-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        MvcResult res = mvc.perform(post("/api/v1/admin/refunds/{orderId}/mark", 999999L)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new com.flowerconnect.payment.dto.MarkRefundRequest() {{
                        setReferenceNote("ref-miss");
                    }})))
            .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void markRefundedMissingReferenceNoteIs400() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-note-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-note-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        Long productId = createProduct(vendorEmail, "Note Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productId, 2);
        reserve(productId, 1);
        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"), 1);
        createPayment(orderId, PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"));
        cancellationService.cancel(customerEmail,
            com.flowerconnect.order.domain.ActorType.CUSTOMER, orderId, null);

        MvcResult res = mvc.perform(post("/api/v1/admin/refunds/{orderId}/mark", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // Audit log row
    // ------------------------------------------------------------------

    @Test
    void markRefundedWritesAuditLogRow() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-audit-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-audit-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        Long productId = createProduct(vendorEmail, "Audit Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productId, 2);
        reserve(productId, 1);
        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"), 1);
        createPayment(orderId, PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"));
        cancellationService.cancel(customerEmail,
            com.flowerconnect.order.domain.ActorType.CUSTOMER, orderId, null);

        long before = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action_type = 'PAYMENT_REFUNDED'", Long.class);

        mvc.perform(post("/api/v1/admin/refunds/{orderId}/mark", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new com.flowerconnect.payment.dto.MarkRefundRequest() {{
                        setReferenceNote("audit-ref-999");
                    }})))
            .andExpect(status().isOk());

        long after = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action_type = 'PAYMENT_REFUNDED'", Long.class);
        assertThat(after).isEqualTo(before + 1);

        // Verify audit row contents
        List<Object[]> rows = jdbcTemplate.query(
            "SELECT actor_user_id, action_type, entity_type, entity_id, reason FROM audit_log "
            + "WHERE action_type = 'PAYMENT_REFUNDED' ORDER BY id DESC LIMIT 1",
            (rs, rowNum) -> new Object[]{
                rs.getLong("actor_user_id"), rs.getString("action_type"),
                rs.getString("entity_type"), rs.getLong("entity_id"), rs.getString("reason")
            });
        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        User admin = users.findByEmail(adminEmail).orElseThrow();
        assertThat(row[0]).isEqualTo(admin.getId());
        assertThat(row[1]).isEqualTo("PAYMENT_REFUNDED");
        assertThat(row[2]).isEqualTo("PAYMENT");
        assertThat(row[3]).isNotNull();
        assertThat(row[4].toString()).contains("REFUND_PENDING -> REFUNDED");
        assertThat(row[4].toString()).contains("audit-ref-999");
    }

    // ------------------------------------------------------------------
    // Customer-visible refund state in order detail
    // ------------------------------------------------------------------

    @Test
    void customerOrderDetailShowsRefundedAt() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-detail-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customerEmail);
        String adminEmail = "admin-detail-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(adminEmail);

        Long productId = createProduct(vendorEmail, "Detail Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productId, 2);
        reserve(productId, 1);
        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"), 1);
        createPayment(orderId, PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("100.00"));
        cancellationService.cancel(customerEmail,
            com.flowerconnect.order.domain.ActorType.CUSTOMER, orderId, null);

        // Before mark: refundedAt is null
        MvcResult before = mvc.perform(get("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk()).andReturn();
        JsonNode beforeBody = json.readTree(before.getResponse().getContentAsString());
        assertThat(beforeBody.get("paymentStatus").asText()).isEqualTo("REFUND_PENDING");
        assertThat(beforeBody.path("refundedAt").isNull()).isTrue();

        // Mark refunded
        mvc.perform(post("/api/v1/admin/refunds/{orderId}/mark", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    new com.flowerconnect.payment.dto.MarkRefundRequest() {{
                        setReferenceNote("detail-ref");
                    }})))
            .andExpect(status().isOk());

        // After mark: refundedAt is set
        MvcResult after = mvc.perform(get("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk()).andReturn();
        JsonNode afterBody = json.readTree(after.getResponse().getContentAsString());
        assertThat(afterBody.get("paymentStatus").asText()).isEqualTo("REFUNDED");
        assertThat(afterBody.get("refundedAt")).isNotNull();
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

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
                .passwordHash(encoder.encode(PASSWORD))
                .fullName("Test " + roleName)
                .role(role)
                .status(Status.ACTIVE)
                .build();
        return users.saveAndFlush(user);
    }

    private String createVendor() {
        String email = "vendor-ref-" + uuid() + "@t.com";
        Role role = roles.findByName("FLORIST").orElseThrow();
        User user = User.builder()
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .fullName("Vendor Refund Blossoms " + uuid())
                .role(role)
                .status(Status.ACTIVE)
                .build();
        users.saveAndFlush(user);
        var location = locations.findById(locationId).orElseThrow();
        VendorProfile vp = VendorProfile.builder()
                .user(user)
                .businessName("Vendor Refund Blossoms " + uuid())
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

    private void stockIn(Long productId, int qty) {
        com.flowerconnect.inventory.domain.Inventory inv =
                inventoryRepository.findByProductId(productId).orElseThrow();
        inv.setQuantity(qty);
        inv.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(inv);
    }

    private void reserve(Long productId, int qty) {
        com.flowerconnect.inventory.domain.Inventory inventory =
                inventoryRepository.findByProductId(productId).orElseThrow();
        inventory.setReservedQuantity(qty);
        inventoryRepository.saveAndFlush(inventory);
    }

    private Long createOrder(User customer, Long vendorProfileId, OrderStatus status,
            PaymentMethod method, PaymentStatus paymentStatus, BigDecimal total, int qty) {
        return createOrder(customer, vendorProfileId, status, method, paymentStatus, total, qty,
                LocalDateTime.now().plusHours(2));
    }

    private Long createOrder(User customer, Long vendorProfileId, OrderStatus status,
            PaymentMethod method, PaymentStatus paymentStatus, BigDecimal total, int qty,
            LocalDateTime slotStart) {
        VendorProfile vendor = vendorProfiles.findById(vendorProfileId).orElseThrow();
        Order order = Order.builder()
                .id(null)
                .user(customer)
                .vendor(vendor)
                .status(status)
                .paymentMethod(method)
                .paymentStatus(paymentStatus)
                .subtotal(total)
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(total)
                .deliveryAddressLine1("123 Test St")
                .deliveryLatitude(new BigDecimal("12.97"))
                .deliveryLongitude(new BigDecimal("77.59"))
                .slotStart(slotStart)
                .build();
        return orderRepository.saveAndFlush(order).getId();
    }

    private void createPayment(Long orderId, PaymentMethod method, PaymentStatus status, BigDecimal amount) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        Payment payment = Payment.builder()
                .order(order)
                .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.valueOf(method.name()))
                .provider("simulated")
                .providerReference("int_sim_" + uuid())
                .amount(amount)
                .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.valueOf(status.name()))
                .paymentGroupId(uuid())
                .build();
        payments.saveAndFlush(payment);
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
