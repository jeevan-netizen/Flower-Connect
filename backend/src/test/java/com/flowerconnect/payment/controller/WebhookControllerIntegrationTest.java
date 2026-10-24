package com.flowerconnect.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.OrderItem;
import com.flowerconnect.order.repository.OrderItemRepository;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.PaymentIntent;
import com.flowerconnect.payment.domain.PaymentIntent.IntentStatus;
import com.flowerconnect.payment.dto.WebhookEvent;
import com.flowerconnect.payment.dto.WebhookResponse;
import com.flowerconnect.payment.repository.PaymentIntentRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.test.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 3: signed payment webhook integration (task 7.4 part 2).
 *
 * <p>Exercises {@code POST /api/v1/payments/webhook} over real HTTP against a
 * real MySQL database: signature verification, group transitions, idempotency,
 * late-event handling, and the "no JWT required" rule.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class WebhookControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String PINCODE = "560034";
    private static final String WEBHOOK_SECRET = "test-webhook-secret-key-for-testing-phase-3-plus-at-least-32-chars";
    private static final String SLOT = futureSlotStart();

    private static String futureSlotStart() {
        ZoneId z = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.now(z);
        ZonedDateTime candidate = ZonedDateTime.of(now.toLocalDate(), java.time.LocalTime.of(12, 0), z);
        if (candidate.isBefore(now.plusMinutes(120))) {
            candidate = ZonedDateTime.of(now.toLocalDate().plusDays(1), java.time.LocalTime.of(12, 0), z);
        }
        return candidate.withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AppProperties appProperties;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private VendorProfileRepository vendors;
    @Autowired private ServiceLocationRepository locations;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductService products;
    @Autowired private ProductRepository productRepo;
    @Autowired private InventoryRepository inventory;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository orderItems;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentIntentRepository intents;
    @Autowired private PasswordEncoder encoder;
    @Autowired private MutableClock mutableClock;

    private Long locationId;
    private Long vendorProfileId;
    private Long categoryId;
    private String vendorEmail;
    private Long webhookProductId;

    @BeforeEach
    void setUp() throws Exception {
        locationId = locations.findByPincode(PINCODE).orElseThrow().getId();
        categoryId = categories.findBySlug("roses").orElseThrow().getId();

        vendorEmail = "vendor-webhook-" + UUID.randomUUID() + "@t.com";
        User vUser = createUser(vendorEmail, "FLORIST");
        VendorProfile vp = vendors.saveAndFlush(VendorProfile.builder()
            .user(vUser)
            .businessName("Webhook Test Vendor")
            .addressLine1("123 Flower St")
            .serviceLocation(locations.findById(locationId).orElseThrow())
            .latitude(new BigDecimal("12.93")).longitude(new BigDecimal("77.62"))
            .deliveryRadiusKm(new BigDecimal("10.00")).status(VendorProfile.Status.APPROVED)
            .reviewCount(0).minOrderAmount(BigDecimal.ZERO).baseDeliveryFee(BigDecimal.ZERO)
            .perKmFee(BigDecimal.ZERO).prepTimeMinutes(30).slotDurationMinutes(60)
            .maxOrdersPerSlot(10).acceptingOrders(true).build());
        vendorProfileId = vp.getId();

        // Force the webhook secret and sync the test clock to wall-clock time so
        // the webhook signature verification is deterministic regardless of YAML
        // binding or context-startup clock drift.
        appProperties.setWebhookSecret(WEBHOOK_SECRET);
        mutableClock.setInstant(Instant.now());
    }

    @AfterEach
    void resetClock() {
        mutableClock.setInstant(Instant.now());
    }

    private User createUser(String email, String roleName) {
        Role r = roles.findByName(roleName).orElseThrow();
        return users.saveAndFlush(User.builder()
            .email(email)
            .passwordHash(encoder.encode(PASSWORD))
            .fullName("Test " + roleName)
            .phone("+91" + java.util.concurrent.ThreadLocalRandom.current().nextLong(1_0000_0000L, 9_9999_9999L))
            .role(r).status(User.Status.ACTIVE).build());
    }

    private String login(User u) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(
                new LoginRequest() {{
                    setEmail(u.getEmail());
                    setPassword(PASSWORD);
                }})))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private Long createProduct(String name, BigDecimal price) {
        return products.create(vendorEmail,
            ProductRequest.builder().name(name).categoryId(categoryId)
                .description("webhook product").basePrice(price).status(ProductStatus.ACTIVE).build()).getId();
    }

    // ------------------------------------------------------------------
    // webhook signing helper
    // ------------------------------------------------------------------

    private MvcResult webhook(String paymentGroupId, String providerEventId, String eventType,
                              String outcome, BigDecimal amount) throws Exception {
        WebhookEvent event = WebhookEvent.builder()
            .providerEventId(providerEventId).paymentGroupId(paymentGroupId)
            .eventType(eventType).outcome(outcome).amount(amount).build();
        String body = json.writeValueAsString(event);
        long now = Instant.now().getEpochSecond();
        String timestamp = Long.toString(now);
        String signature = "v1=" + hex(hmac(WEBHOOK_SECRET, timestamp + body));
        return mvc.perform(post("/api/v1/payments/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.getBytes(StandardCharsets.UTF_8))
            .header("X-FlowerConnect-Timestamp", timestamp)
            .header("X-FlowerConnect-Signature", signature))
            .andReturn();
    }

    private static byte[] hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // fixture: an ONLINE PENDING_PAYMENT order + payment + intent
    // ------------------------------------------------------------------

    @Transactional
    private Long placePendingOnlineOrder(String customerEmail) throws Exception {
        User customer = users.findByEmail(customerEmail).orElseThrow();
        webhookProductId = createProduct("Webhook Product", new BigDecimal("299.00"));
        Inventory inv = inventory.findByProductId(webhookProductId).orElseThrow();
        inv.setQuantity(10);
        inv.setReservedQuantity(1);
        inventory.saveAndFlush(inv);

        Order order = orders.saveAndFlush(Order.builder()
            .user(customer).vendor(vendors.findById(vendorProfileId).orElseThrow())
            .status(OrderStatus.PENDING_PAYMENT)
            .paymentMethod(PaymentMethod.ONLINE)
            .paymentStatus(PaymentStatus.PENDING)
            .subtotal(new BigDecimal("299.00")).deliveryFee(BigDecimal.ZERO)
            .platformFee(BigDecimal.ZERO).total(new BigDecimal("299.00"))
            .deliveryAddressLine1("123 St").deliveryLatitude(new BigDecimal("12.97"))
            .deliveryLongitude(new BigDecimal("77.59"))
            .slotStart(LocalDateTime.now().plusHours(1))
            .clientOrderId(UUID.randomUUID().toString()).build());
        orderItems.saveAndFlush(OrderItem.builder()
            .order(order)
            .product(productRepo.findById(webhookProductId).orElseThrow())
            .vendorId(vendorProfileId)
            .quantity(1)
            .priceSnapshot(new BigDecimal("299.00"))
            .build());

        String groupId = UUID.randomUUID().toString();
        Payment pay = payments.saveAndFlush(Payment.builder()
            .order(orders.findByIdForUpdate(order.getId()).orElseThrow())
            .method(Payment.PaymentMethod.ONLINE).provider("simulated")
            .providerReference("intent_sim_" + UUID.randomUUID())
            .amount(new BigDecimal("299.00")).status(Payment.PaymentStatus.PENDING)
            .paymentGroupId(groupId).build());
        intents.saveAndFlush(PaymentIntent.builder()
            .paymentGroupId(groupId).total(new BigDecimal("299.00"))
            .status(IntentStatus.CREATED)
            .providerReference(pay.getProviderReference())
            .expiresAt(LocalDateTime.now().plusMinutes(30)).build());
        return order.getId();
    }

    // ========================================================================
    // 1) valid success signature -> group placed, payments paid
    // ========================================================================
    @Test
    @Transactional
    void validSuccessSignaturePlacesGroupAndMarksPaid() throws Exception {
        User customer = createUser("wh-c1-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        MvcResult res = webhook(groupId, "evt-success-1", "payment.succeeded", "success",
            new BigDecimal("299.00"));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        WebhookResponse body = json.readValue(res.getResponse().getContentAsString(), WebhookResponse.class);
        assertThat(body.getOutcome()).isEqualTo("accepted");

        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        Payment pay = payments.findByOrderId(orderId).orElseThrow();
        assertThat(pay.getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        assertThat(pay.getPaymentGroupId()).isEqualTo(groupId);

        PaymentIntent intent = intents.findByPaymentGroupId(groupId).orElseThrow();
        assertThat(intent.getStatus()).isEqualTo(IntentStatus.PAID);
    }

    // ========================================================================
    // 2) valid failure signature -> group cancelled, reservations released
    // ========================================================================
    @Test
    @Transactional
    void validFailureSignatureCancelsGroupAndReleasesReservations() throws Exception {
        User customer = createUser("wh-c2-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        int reservedBefore = inventory.findByProductId(webhookProductId).orElseThrow().getReservedQuantity();

        webhook(groupId, "evt-fail-1", "payment.failed", "failure", new BigDecimal("299.00"));

        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);

        Payment pay = payments.findByOrderId(orderId).orElseThrow();
        assertThat(pay.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);

        PaymentIntent intent = intents.findByPaymentGroupId(groupId).orElseThrow();
        assertThat(intent.getStatus()).isEqualTo(IntentStatus.FAILED);

        // Reservation released through the state-machine side effect
        assertThat(inventory.findByProductId(webhookProductId).orElseThrow().getReservedQuantity())
            .isEqualTo(reservedBefore - 1);
    }

    // ========================================================================
    // 3) signature rejections: no state change
    // ========================================================================
    @Test
    @Transactional
    void invalidSignatureIsRejectedWithoutStateChange() throws Exception {
        User customer = createUser("wh-c3-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        MvcResult res = mvc.perform(post("/api/v1/payments/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"providerEventId\":\"evt-x\",\"paymentGroupId\":\"" + groupId +
                "\",\"eventType\":\"payment.succeeded\",\"outcome\":\"success\",\"amount\":\"299.00\"}"
                .getBytes(StandardCharsets.UTF_8))
            .header("X-FlowerConnect-Timestamp", Long.toString(Instant.now().getEpochSecond()))
            .header("X-FlowerConnect-Signature", "v1=deadbeef")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);

        assertThat(orders.findByIdForUpdate(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(payments.findByOrderId(orderId).orElseThrow().getStatus())
            .isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(intents.findByPaymentGroupId(groupId).orElseThrow().getStatus()).isEqualTo(IntentStatus.CREATED);
    }

    @Test
    @Transactional
    void missingSignatureIsRejectedWithoutStateChange() throws Exception {
        User customer = createUser("wh-c4-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        MvcResult res = mvc.perform(post("/api/v1/payments/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"providerEventId\":\"evt-x\",\"paymentGroupId\":\"" + groupId +
                "\",\"eventType\":\"payment.succeeded\",\"outcome\":\"success\",\"amount\":\"299.00\"}"
                .getBytes(StandardCharsets.UTF_8))
            .header("X-FlowerConnect-Timestamp", Long.toString(Instant.now().getEpochSecond()))).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);

        assertThat(orders.findByIdForUpdate(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @Transactional
    void staleTimestampIsRejectedWithoutStateChange() throws Exception {
        User customer = createUser("wh-c5-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        // Move the clock far into the past so the current timestamp is stale.
        mutableClock.setInstant(Instant.parse("2020-01-01T00:00:00Z"));

        MvcResult res = webhook(groupId, "evt-stale", "payment.succeeded", "success",
            new BigDecimal("299.00"));
        assertThat(res.getResponse().getStatus()).isEqualTo(401);

        assertThat(orders.findByIdForUpdate(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @Transactional
    void modifiedBodyIsRejectedWithoutStateChange() throws Exception {
        User customer = createUser("wh-c6-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        String signedBody = json.writeValueAsString(WebhookEvent.builder()
            .providerEventId("evt-mod").paymentGroupId(groupId)
            .eventType("payment.succeeded").outcome("success").amount(new BigDecimal("299.00")).build());
        long now = Instant.now().getEpochSecond();
        String signature = "v1=" + hex(hmac(WEBHOOK_SECRET, Long.toString(now) + signedBody));

        MvcResult res = mvc.perform(post("/api/v1/payments/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"providerEventId\":\"evt-mod\",\"paymentGroupId\":\"" + groupId +
                "\",\"eventType\":\"payment.succeeded\",\"outcome\":\"success\",\"amount\":\"9999.00\"}"
                .getBytes(StandardCharsets.UTF_8))
            .header("X-FlowerConnect-Timestamp", Long.toString(now))
            .header("X-FlowerConnect-Signature", signature)).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);

        assertThat(orders.findByIdForUpdate(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @Transactional
    void unknownIntentIsNotFoundWithoutLeakage() throws Exception {
        MvcResult res = webhook("no-such-group", "evt-unknown", "payment.succeeded", "success",
            new BigDecimal("299.00"));
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // ========================================================================
    // 4) idempotency: duplicate event id -> 200, no second side effect
    // ========================================================================
    @Test
    @Transactional
    void duplicateEventIdIsAcknowledgedWithoutSideEffects() throws Exception {
        User customer = createUser("wh-c7-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        MvcResult first = webhook(groupId, "evt-dup-1", "payment.succeeded", "success",
            new BigDecimal("299.00"));
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(orders.findByIdForUpdate(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PLACED);

        MvcResult second = webhook(groupId, "evt-dup-1", "payment.succeeded", "success",
            new BigDecimal("299.00"));
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        WebhookResponse body = json.readValue(second.getResponse().getContentAsString(), WebhookResponse.class);
        assertThat(body.getOutcome()).isEqualTo("duplicate");

        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payments.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        assertThat(intents.findByPaymentGroupId(groupId).orElseThrow().getStatus()).isEqualTo(IntentStatus.PAID);
    }

    // ========================================================================
    // 5) late success after cancellation -> REFUND_PENDING, no resurrection
    // ========================================================================
    @Test
    @Transactional
    void lateSuccessAfterCancellationSetsRefundPendingAndDoesNotResurrect() throws Exception {
        User customer = createUser("wh-c8-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        webhook(groupId, "evt-first-fail", "payment.failed", "failure", new BigDecimal("299.00"));
        assertThat(orders.findByIdForUpdate(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payments.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);

        MvcResult res = webhook(groupId, "evt-late-success", "payment.succeeded", "success",
            new BigDecimal("299.00"));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(payments.findByOrderId(orderId).orElseThrow().getStatus())
            .isEqualTo(Payment.PaymentStatus.REFUND_PENDING);
        assertThat(intents.findByPaymentGroupId(groupId).orElseThrow().getStatus()).isEqualTo(IntentStatus.FAILED);
    }

    // ========================================================================
    // 6) failure after success -> no regression, idempotent response
    // ========================================================================
    @Test
    @Transactional
    void failureAfterSuccessDoesNotRegress() throws Exception {
        User customer = createUser("wh-c9-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        webhook(groupId, "evt-first-success", "payment.succeeded", "success", new BigDecimal("299.00"));
        assertThat(orders.findByIdForUpdate(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(payments.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(Payment.PaymentStatus.PAID);

        MvcResult res = webhook(groupId, "evt-late-fail", "payment.failed", "failure", new BigDecimal("299.00"));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        WebhookResponse body = json.readValue(res.getResponse().getContentAsString(), WebhookResponse.class);
        assertThat(body.getOutcome()).isEqualTo("ignored");

        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payments.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        assertThat(intents.findByPaymentGroupId(groupId).orElseThrow().getStatus()).isEqualTo(IntentStatus.PAID);
    }

    // ========================================================================
    // 7) webhook requires no JWT; other payment routes stay protected
    // ========================================================================
    @Test
    @Transactional
    void webhookRequiresNoJwt() throws Exception {
        User customer = createUser("wh-c10-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        MvcResult res = webhook(groupId, "evt-no-jwt", "payment.succeeded", "success",
            new BigDecimal("299.00"));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @Transactional
    void paymentStateEndpointRequiresJwt() throws Exception {
        User customer = createUser("wh-c11-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        mvc.perform(get("/api/v1/payments/" + orderId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void resumeEndpointRequiresJwt() throws Exception {
        User customer = createUser("wh-c12-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        mvc.perform(post("/api/v1/payments/" + orderId + "/resume"))
            .andExpect(status().isUnauthorized());
    }
}
