package com.flowerconnect.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.OrderItem;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.repository.OrderItemRepository;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.PaymentIntent;
import com.flowerconnect.payment.domain.PaymentIntent.IntentStatus;
import com.flowerconnect.payment.dto.WebhookResponse;
import com.flowerconnect.payment.repository.PaymentIntentRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 3: simulated test-mode pay/fail endpoints (task 7.4 part 2).
 *
 * <p>Exercises {@code POST /api/v1/payments/simulator/{orderId}/pay|fail} over
 * real HTTP: customer authentication + ownership required, events delivered
 * through the signed webhook handling path, group transitions applied.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class SimulatorControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String PINCODE = "560034";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private VendorProfileRepository vendors;
    @Autowired private ServiceLocationRepository locations;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductService products;
    @Autowired private InventoryRepository inventory;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository orderItems;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentIntentRepository intents;
    @Autowired private PasswordEncoder encoder;

    private Long locationId;
    private Long vendorProfileId;
    private Long categoryId;
    private String vendorEmail;
    private Long simProductId;

    @BeforeEach
    void setUp() throws Exception {
        locationId = locations.findByPincode(PINCODE).orElseThrow().getId();
        categoryId = categories.findBySlug("roses").orElseThrow().getId();

        vendorEmail = "vendor-sim-" + UUID.randomUUID() + "@t.com";
        User vUser = createUser(vendorEmail, "FLORIST");
        VendorProfile vp = vendors.saveAndFlush(VendorProfile.builder()
            .user(vUser)
            .businessName("Simulator Test Vendor")
            .addressLine1("123 Flower St")
            .serviceLocation(locations.findById(locationId).orElseThrow())
            .latitude(new BigDecimal("12.93")).longitude(new BigDecimal("77.62"))
            .deliveryRadiusKm(new BigDecimal("10.00")).status(VendorProfile.Status.APPROVED)
            .reviewCount(0).minOrderAmount(BigDecimal.ZERO).baseDeliveryFee(BigDecimal.ZERO)
            .perKmFee(BigDecimal.ZERO).prepTimeMinutes(30).slotDurationMinutes(60)
            .maxOrdersPerSlot(10).acceptingOrders(true).build());
        vendorProfileId = vp.getId();
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
                .description("sim product").basePrice(price).status(ProductStatus.ACTIVE).build()).getId();
    }

    @Transactional
    private Long placePendingOnlineOrder(String customerEmail) throws Exception {
        User customer = users.findByEmail(customerEmail).orElseThrow();
        Long productId = createProduct("Sim Product", new BigDecimal("149.00"));
        simProductId = productId;
        Product product = productRepository.findById(productId).orElseThrow();
        Inventory inv = inventory.findByProductId(productId).orElseThrow();
        inv.setQuantity(10);
        inv.setReservedQuantity(1);
        inventory.saveAndFlush(inv);

        Order order = orders.saveAndFlush(Order.builder()
            .user(customer).vendor(vendors.findById(vendorProfileId).orElseThrow())
            .status(OrderStatus.PENDING_PAYMENT)
            .paymentMethod(PaymentMethod.ONLINE)
            .paymentStatus(PaymentStatus.PENDING)
            .subtotal(new BigDecimal("149.00")).deliveryFee(BigDecimal.ZERO)
            .platformFee(BigDecimal.ZERO).total(new BigDecimal("149.00"))
            .deliveryAddressLine1("123 St").deliveryLatitude(new BigDecimal("12.97"))
            .deliveryLongitude(new BigDecimal("77.59"))
            .slotStart(LocalDateTime.now().plusHours(1))
            .clientOrderId(UUID.randomUUID().toString()).build());
        orderItems.saveAndFlush(OrderItem.builder()
            .order(order).product(product).vendorId(vendorProfileId)
            .quantity(1).priceSnapshot(new BigDecimal("149.00")).build());

        String groupId = UUID.randomUUID().toString();
        Payment pay = payments.saveAndFlush(Payment.builder()
            .order(order)
            .method(Payment.PaymentMethod.ONLINE).provider("simulated")
            .providerReference("intent_sim_" + UUID.randomUUID())
            .amount(new BigDecimal("149.00")).status(Payment.PaymentStatus.PENDING)
            .paymentGroupId(groupId).build());
        intents.saveAndFlush(PaymentIntent.builder()
            .paymentGroupId(groupId).total(new BigDecimal("149.00"))
            .status(IntentStatus.CREATED)
            .providerReference(pay.getProviderReference())
            .expiresAt(LocalDateTime.now().plusMinutes(30)).build());
        return order.getId();
    }

    private MvcResult simulator(String orderId, String action, String token) throws Exception {
        return mvc.perform(post("/api/v1/payments/simulator/" + orderId + "/" + action)
            .header("Authorization", "Bearer " + token)).andReturn();
    }

    @Test
    @Transactional
    void simulatorPayPlacesGroupAndMarksPaid() throws Exception {
        User customer = createUser("sim-c1-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        String cToken = login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        MvcResult res = simulator(orderId.toString(), "pay", cToken);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        WebhookResponse body = json.readValue(res.getResponse().getContentAsString(), WebhookResponse.class);
        assertThat(body.getOutcome()).isEqualTo("accepted");

        Order order = orders.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payments.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        assertThat(intents.findByPaymentGroupId(groupId).orElseThrow().getStatus()).isEqualTo(IntentStatus.PAID);
    }

    @Test
    @Transactional
    void simulatorFailCancelsGroupAndReleasesReservations() throws Exception {
        User customer = createUser("sim-c2-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        String cToken = login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        String groupId = payments.findByOrderId(orderId).orElseThrow().getPaymentGroupId();

        int reservedBefore = inventory.findByProductId(simProductId).orElseThrow().getReservedQuantity();

        MvcResult res = simulator(orderId.toString(), "fail", cToken);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Order order = orders.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payments.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(intents.findByPaymentGroupId(groupId).orElseThrow().getStatus()).isEqualTo(IntentStatus.FAILED);
        assertThat(inventory.findByProductId(simProductId).orElseThrow().getReservedQuantity())
            .isEqualTo(reservedBefore - 1);
    }

    @Test
    @Transactional
    void simulatorRequiresAuthentication() throws Exception {
        User customer = createUser("sim-c3-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        mvc.perform(post("/api/v1/payments/simulator/" + orderId + "/pay"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void simulatorRejectsForeignOrder() throws Exception {
        User owner = createUser("sim-c4-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        User other = createUser("sim-c5-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        String otherToken = login(other);
        Long orderId = placePendingOnlineOrder(owner.getEmail());
        mvc.perform(post("/api/v1/payments/simulator/" + orderId + "/pay")
            .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void simulatorRejectsNonPendingOrder() throws Exception {
        User customer = createUser("sim-c6-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        String cToken = login(customer);
        Long orderId = placePendingOnlineOrder(customer.getEmail());
        // First pay places the order.
        simulator(orderId.toString(), "pay", cToken);
        // A second pay on the now-PLACED order is a late event: idempotent, no regression.
        MvcResult res = simulator(orderId.toString(), "pay", cToken);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        WebhookResponse body = json.readValue(res.getResponse().getContentAsString(), WebhookResponse.class);
        assertThat(body.getOutcome()).isEqualTo("ignored");
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PLACED);
    }
}
