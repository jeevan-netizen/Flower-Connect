package com.flowerconnect.checkout.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.cart.dto.AddCartItemRequest;
import com.flowerconnect.customer.domain.Address;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.payment.domain.PaymentIntent;
import com.flowerconnect.payment.domain.PaymentIntent.IntentStatus;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.repository.PaymentIntentRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2: Online checkout + simulated payment integration (task 7.4 part 1).
 *
 * <p>Covers ONLINE checkout creating PENDING_PAYMENT orders, shared payment
 * group/intent, resume transitions, RBAC on payment endpoints, and slot
 * capacity inclusion of PENDING_PAYMENT orders.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OnlineCheckoutIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String PINCODE = "560034";
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
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private VendorProfileRepository vendors;
    @Autowired private VendorHoursRepository vendorHoursRepo;
    @Autowired private ServiceLocationRepository locations;
    @Autowired private AddressRepository addressRepo;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductService products;
    @Autowired private ProductRepository productRepo;
    @Autowired private InventoryRepository inventory;
    @Autowired private StockMovementRepository movements;
    @Autowired private OrderRepository orders;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentIntentRepository intents;
    @Autowired private PasswordEncoder encoder;

    private Long locationId;
    private Long vendorProfileId;
    private Long categoryId;
    private String vendorEmail;
    private String vendorToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        locationId = locations.findByPincode(PINCODE).orElseThrow().getId();
        categoryId = categories.findBySlug("roses").orElseThrow().getId();

        // Create approved vendor
        vendorEmail = "vendor-online-" + UUID.randomUUID() + "@t.com";
        User vUser = createUser(vendorEmail, "FLORIST");
        vendorToken = login(vUser);
        VendorProfile vp = vendors.saveAndFlush(VendorProfile.builder()
            .user(vUser)
            .businessName("Online Test Vendor")
            .addressLine1("123 Flower St")
            .serviceLocation(locations.findById(locationId).orElseThrow())
            .latitude(new BigDecimal("12.93"))
            .longitude(new BigDecimal("77.62"))
            .deliveryRadiusKm(new BigDecimal("10.00"))
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
        vendorProfileId = vp.getId();

        // Vendor hours open 09:00-18:00 all days so any slot day is valid
        for (DayOfWeek dow : DayOfWeek.values()) {
            vendorHoursRepo.saveAndFlush(VendorHours.builder()
                .vendorProfile(vp)
                .weekday(dow)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .closed(false)
                .build());
        }
    }

    @AfterEach
    void resetClock() {
    }

    private User createUser(String email, String roleName) {
        Role r = roles.findByName(roleName).orElseThrow();
        return users.saveAndFlush(User.builder()
            .email(email)
            .passwordHash(encoder.encode(PASSWORD))
            .fullName("Test " + roleName)
            .phone(uniquePhone())
            .role(r)
            .status(User.Status.ACTIVE)
            .build());
    }

    private String login(User u) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(
                new com.flowerconnect.security.dto.LoginRequest() {{
                    setEmail(u.getEmail());
                    setPassword(PASSWORD);
                }})))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode auth = json.readTree(res.getResponse().getContentAsString());
        assertThat(auth.get("accessToken").asText()).isNotEmpty();
        return auth.get("accessToken").asText();
    }

    private static String uniquePhone() {
        return "+91" + java.util.concurrent.ThreadLocalRandom.current()
            .nextLong(1_0000_0000L, 9_9999_9999L);
    }

    private String bearer(String token) { return "Bearer " + token; }

    private Long createProduct(String vendorEmail, String name, BigDecimal price) {
        ProductRequest req = ProductRequest.builder()
            .name(name)
            .categoryId(categoryId)
            .description("Test product for online checkout")
            .basePrice(price)
            .status(ProductStatus.ACTIVE)
            .build();
        return products.create(vendorEmail, req).getId();
    }

    private void stockIn(Long productId, int qty) throws Exception {
        // Set up inventory directly via repository (Product.create already
        // seeds a row with quantity 0, so update it rather than insert).
        com.flowerconnect.inventory.domain.Inventory inv =
            inventory.findByProductId(productId).orElseThrow();
        inv.setQuantity(qty);
        inv.setReservedQuantity(0);
        inventory.saveAndFlush(inv);
    }

    private Long createAddress(User user, Long svcLocationId) {
        ServiceLocation loc = locations.findById(svcLocationId).orElseThrow();
        return addressRepo.saveAndFlush(Address.builder()
            .user(user).serviceLocation(loc).label("Home")
            .line1("123 Test St").latitude(loc.getLatitude()).longitude(loc.getLongitude())
            .defaultAddress(true).build()).getId();
    }

    private void addToCart(String token, Long productId, int qty) throws Exception {
        mvc.perform(post("/api/v1/cart/items")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(
                AddCartItemRequest.builder().productId(productId).quantity(qty).build())))
            .andExpect(status().isCreated());
    }

    // ========================================================================
    // 1) ONLINE checkout with a single vendor
    // ========================================================================
    @Test
    void onlineCheckoutSingleVendorCreatesPendingPaymentOrderAndSharedIntent() throws Exception {
        User customer = createUser("online-c1-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        String cToken = login(customer);
        Long productId = createProduct(vendorEmail, "Online Rose", new BigDecimal("299.00"));
        stockIn(productId, 10);

        Long addressId = createAddress(customer, locationId);
        addToCart(cToken, productId, 1);

        // Quote
        String quoteBody = json.writeValueAsString(
            new com.flowerconnect.checkout.dto.CheckoutQuoteRequest() {{
                setPaymentMethod(com.flowerconnect.order.domain.Order.PaymentMethod.ONLINE);
                setAddressId(addressId);
                setItems(List.of(
                    new com.flowerconnect.checkout.dto.CheckoutQuoteRequest.QuoteItem() {{
                        setVendorId(vendorProfileId);
                        setSlotStart(SLOT);
                    }}
                ));
            }}
        );

        MvcResult quoteRes = mvc.perform(post("/api/v1/checkout/quote")
            .header(HttpHeaders.AUTHORIZATION, bearer(cToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(quoteBody))
            .andExpect(status().isOk())
            .andReturn();

        String quoteStr = quoteRes.getResponse().getContentAsString();
        JsonNode quote = json.readTree(quoteStr);
        String quoteHash = quote.get("quoteHash").asText();

        // Act - Checkout with ONLINE
        UUID clientId = UUID.randomUUID();
        String checkoutBody = json.writeValueAsString(
            new com.flowerconnect.checkout.dto.CheckoutRequest() {{
                setAddressId(addressId);
                setClientOrderId(clientId);
                setQuoteHash(quoteHash);
                setConfirmPriceChanges(true);
                setPaymentMethod(com.flowerconnect.order.domain.Order.PaymentMethod.ONLINE);
                setItems(List.of(
                    new com.flowerconnect.checkout.dto.CheckoutRequest.CheckoutItem() {{
                        setVendorId(vendorProfileId);
                        setSlotStart(SLOT);
                    }}
                ));
            }}
        );

        MvcResult checkoutRes = mvc.perform(post("/api/v1/checkout")
            .header(HttpHeaders.AUTHORIZATION, bearer(cToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkoutBody))
            .andReturn();
        String checkoutBodyResp = checkoutRes.getResponse().getContentAsString();
        assertThat(checkoutRes.getResponse().getStatus())
            .overridingErrorMessage("Expected 200 but was %d. Body: %s"
                .formatted(checkoutRes.getResponse().getStatus(), checkoutBodyResp))
            .isEqualTo(200);

        JsonNode result = json.readTree(checkoutRes.getResponse().getContentAsString());

        // Assert HTTP response
        assertThat(result.get("orders").size()).isEqualTo(1);
        assertThat(result.get("orders").get(0).get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.get("orders").get(0).get("paymentMethod").asText()).isEqualTo("ONLINE");
        assertThat(result.get("paymentMethod").asText()).isEqualTo("ONLINE");
        assertThat(result.get("paymentGroupId").asText()).isNotEmpty();
        assertThat(result.get("intentReference").asText()).startsWith("intent_sim_");
        assertThat(result.get("intentExpiresAt").asText()).isNotEmpty();

        // Assert DB state
        Long orderId = result.get("orders").get(0).get("orderId").asLong();
        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getPaymentMethod()).isEqualTo(com.flowerconnect.order.domain.Order.PaymentMethod.ONLINE);
        assertThat(order.getPaymentStatus()).isEqualTo(com.flowerconnect.order.domain.Order.PaymentStatus.PENDING);

        Payment pay = payments.findByOrderId(orderId).orElseThrow();
        assertThat(pay.getMethod()).isEqualTo(com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE);
        assertThat(pay.getStatus()).isEqualTo(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING);
        assertThat(pay.getPaymentGroupId()).isEqualTo(result.get("paymentGroupId").asText());

        PaymentIntent intent = intents.findByPaymentGroupId(result.get("paymentGroupId").asText()).orElseThrow();
        assertThat(intent.getStatus()).isEqualTo(IntentStatus.CREATED);
        assertThat(intent.getTotal()).isEqualByComparingTo(new BigDecimal(result.get("total").asText()));

        Inventory inv = inventory.findByProductId(productId).orElseThrow();
        assertThat(inv.getQuantity()).isEqualTo(10);
        assertThat(inv.getReservedQuantity()).isEqualTo(1);

        StockMovement mov = movements.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
            .filter(m -> m.getMovementType() == StockMovement.MovementType.RESERVE)
            .findFirst().orElseThrow();
        assertThat(mov.getQuantityDelta()).isEqualTo(1);
        assertThat(mov.getReferenceId()).isEqualTo(orderId);
    }

    // ========================================================================
    // 2) Multi-vendor checkout: shared group/intent
    // ========================================================================
    @Test
    void onlineCheckoutMultiVendorSharesSingleGroupAndIntent() throws Exception {
        User vendor2User = createUser("vendor-multi-" + UUID.randomUUID() + "@t.com", "FLORIST");
        VendorProfile vp2 = vendors.saveAndFlush(VendorProfile.builder()
            .user(vendor2User).businessName("Multi Test Vendor 2")
            .addressLine1("456 Bloom Ave").serviceLocation(locations.findById(locationId).orElseThrow())
            .latitude(new BigDecimal("12.94")).longitude(new BigDecimal("77.63"))
            .deliveryRadiusKm(new BigDecimal("10.00")).status(VendorProfile.Status.APPROVED)
            .reviewCount(0).minOrderAmount(BigDecimal.ZERO).baseDeliveryFee(BigDecimal.ZERO)
            .perKmFee(BigDecimal.ZERO).prepTimeMinutes(30).slotDurationMinutes(60)
            .maxOrdersPerSlot(10).acceptingOrders(true).build());

        // Vendor hours open 09:00-18:00 all days for vendor 2
        for (DayOfWeek dow : DayOfWeek.values()) {
            vendorHoursRepo.saveAndFlush(VendorHours.builder()
                .vendorProfile(vp2)
                .weekday(dow)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .closed(false)
                .build());
        }

        User customer = createUser("online-c2-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        String cToken = login(customer);
        Long p1 = createProduct(vendorEmail, "Multi Rose 1", new BigDecimal("199.00"));
        Long p2 = createProduct(vendor2User.getEmail(), "Multi Rose 2", new BigDecimal("249.00"));
        stockIn(p1, 5);
        stockIn(p2, 5);

        Long addressId = createAddress(customer, locationId);
        addToCart(cToken, p1, 1);
        addToCart(cToken, p2, 1);

        String quoteBody = json.writeValueAsString(
            new com.flowerconnect.checkout.dto.CheckoutQuoteRequest() {{
                setAddressId(addressId);
                setItems(List.of(
                    new com.flowerconnect.checkout.dto.CheckoutQuoteRequest.QuoteItem() {{
                        setVendorId(vendorProfileId); setSlotStart(SLOT);
                    }},
                    new com.flowerconnect.checkout.dto.CheckoutQuoteRequest.QuoteItem() {{
                        setVendorId(vp2.getId()); setSlotStart(SLOT);
                    }}
                ));
            }}
        );

        MvcResult quoteRes = mvc.perform(post("/api/v1/checkout/quote")
            .header(HttpHeaders.AUTHORIZATION, bearer(cToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(quoteBody))
            .andReturn();
        String quoteBodyResp = quoteRes.getResponse().getContentAsString();
        assertThat(quoteRes.getResponse().getStatus())
            .overridingErrorMessage("Expected 200 but was %d. Body: %s"
                .formatted(quoteRes.getResponse().getStatus(), quoteBodyResp))
            .isEqualTo(200);

        String quoteHash = json.readTree(quoteRes.getResponse().getContentAsString()).get("quoteHash").asText();

        UUID clientId = UUID.randomUUID();
        String checkoutBody = json.writeValueAsString(
            new com.flowerconnect.checkout.dto.CheckoutRequest() {{
                setAddressId(addressId);
                setClientOrderId(clientId);
                setQuoteHash(quoteHash);
                setConfirmPriceChanges(true);
                setPaymentMethod(com.flowerconnect.order.domain.Order.PaymentMethod.ONLINE);
                setItems(List.of(
                    new com.flowerconnect.checkout.dto.CheckoutRequest.CheckoutItem() {{
                        setVendorId(vendorProfileId); setSlotStart(SLOT);
                    }},
                    new com.flowerconnect.checkout.dto.CheckoutRequest.CheckoutItem() {{
                        setVendorId(vp2.getId()); setSlotStart(SLOT);
                    }}
                ));
            }}
        );

        MvcResult checkoutRes = mvc.perform(post("/api/v1/checkout")
            .header(HttpHeaders.AUTHORIZATION, bearer(cToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkoutBody))
            .andReturn();
        String checkoutBodyResp = checkoutRes.getResponse().getContentAsString();
        assertThat(checkoutRes.getResponse().getStatus())
            .overridingErrorMessage("Expected 200 but was %d. Body: %s"
                .formatted(checkoutRes.getResponse().getStatus(), checkoutBodyResp))
            .isEqualTo(200);

        JsonNode result = json.readTree(checkoutRes.getResponse().getContentAsString());

        assertThat(result.get("orders").size()).isEqualTo(2);
        String groupId = result.get("paymentGroupId").asText();
        assertThat(groupId).isNotEmpty();
        assertThat(intents.findByPaymentGroupId(groupId)).isPresent();

        PaymentIntent shared = intents.findByPaymentGroupId(groupId).orElseThrow();
        assertThat(shared.getStatus()).isEqualTo(IntentStatus.CREATED);

        for (JsonNode orderNode : result.get("orders")) {
            assertThat(orderNode.get("status").asText()).isEqualTo("PENDING_PAYMENT");
            Payment pay = payments.findByOrderId(orderNode.get("orderId").asLong()).orElseThrow();
            assertThat(pay.getPaymentGroupId()).isEqualTo(groupId);
        }
    }

    // ========================================================================
    // 3) RBAC: florist rejected on payment state endpoint
    // ========================================================================
    @Test
    void fetchPaymentStateRejectsFloristWith403() throws Exception {
        mvc.perform(get("/api/v1/payments/1"))
            .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    // 4) RBAC: admin rejected on resume endpoint
    // ========================================================================
    @Test
    void resumePaymentRejectsAdminWith403() throws Exception {
        mvc.perform(post("/api/v1/payments/1/resume"))
            .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    // 5) Resume payment transitions PENDING_PAYMENT -> PLACED
    // ========================================================================
    @Test
    void resumePaymentReturnsPendingStateWithoutTransitioning() throws Exception {
        User c = createUser("resume-c-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        String t = login(c);
        Long pId = createProduct(vendorEmail, "Resume Test", new BigDecimal("199.00"));
        stockIn(pId, 10);
        Long addr = createAddress(c, locationId);
        addToCart(t, pId, 1);

        Long orderId = orders.saveAndFlush(Order.builder()
            .user(c).vendor(vendors.findById(vendorProfileId).orElseThrow())
            .status(OrderStatus.PENDING_PAYMENT)
            .paymentMethod(com.flowerconnect.order.domain.Order.PaymentMethod.ONLINE)
            .paymentStatus(com.flowerconnect.order.domain.Order.PaymentStatus.PENDING)
            .subtotal(new BigDecimal("199.00")).deliveryFee(BigDecimal.ZERO)
            .platformFee(BigDecimal.ZERO).total(new BigDecimal("199.00"))
            .deliveryAddressLine1("123 St").deliveryLatitude(new BigDecimal("12.97"))
            .deliveryLongitude(new BigDecimal("77.59")).slotStart(LocalDateTime.now().plusHours(1))
            .clientOrderId(UUID.randomUUID().toString())
            .build()).getId();

        Payment pay = payments.saveAndFlush(com.flowerconnect.payment.domain.Payment.builder()
            .order(orders.findByIdForUpdate(orderId).orElseThrow())
            .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE)
            .provider("simulated")
            .providerReference("intent_sim_" + UUID.randomUUID())
            .amount(new BigDecimal("199.00"))
            .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING)
            .paymentGroupId(UUID.randomUUID().toString())
            .build());

        PaymentIntent it = intents.saveAndFlush(PaymentIntent.builder()
            .paymentGroupId(pay.getPaymentGroupId())
            .total(new BigDecimal("199.00"))
            .status(IntentStatus.CREATED)
            .providerReference(pay.getProviderReference())
            .expiresAt(LocalDateTime.now().plusMinutes(30))
            .build());

        // Resume is a status refresh only: it must NOT complete the payment or
        // transition the order. Only a valid signed webhook success event may do that.
        mvc.perform(post("/api/v1/payments/" + orderId + "/resume")
            .header(HttpHeaders.AUTHORIZATION, bearer(t)))
            .andExpect(status().isOk());

        Order updated = orders.findByIdForUpdate(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        Payment updatedPay = payments.findByPaymentGroupId(pay.getPaymentGroupId()).stream().findFirst().orElseThrow();
        assertThat(updatedPay.getStatus()).isEqualTo(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING);

        PaymentIntent updatedIntent = intents.findByPaymentGroupId(pay.getPaymentGroupId()).orElseThrow();
        assertThat(updatedIntent.getStatus()).isEqualTo(IntentStatus.CREATED);
    }

    // ========================================================================
    // 6) Slot capacity includes PENDING_PAYMENT orders
    // ========================================================================
    @Test
    void pendingPaymentOrderCountsForSlotCapacity() throws Exception {
        User c = createUser("slot-c-" + UUID.randomUUID() + "@t.com", "CUSTOMER");

        Order pending = orders.saveAndFlush(Order.builder()
            .user(c).vendor(vendors.findById(vendorProfileId).orElseThrow())
            .status(OrderStatus.PENDING_PAYMENT)
            .paymentMethod(com.flowerconnect.order.domain.Order.PaymentMethod.ONLINE)
            .paymentStatus(com.flowerconnect.order.domain.Order.PaymentStatus.PENDING)
            .subtotal(new BigDecimal("100.00")).deliveryFee(BigDecimal.ZERO)
            .platformFee(BigDecimal.ZERO).total(new BigDecimal("100.00"))
            .deliveryAddressLine1("123 St").deliveryLatitude(new BigDecimal("12.97"))
            .deliveryLongitude(new BigDecimal("77.59")).slotStart(LocalDateTime.parse(SLOT))
            .clientOrderId(UUID.randomUUID().toString())
            .build());

        long countBefore = orders.countBookedOrdersForSlot(vendorProfileId, LocalDateTime.parse(SLOT));
        assertThat(countBefore).isGreaterThanOrEqualTo(1);
    }

    // ========================================================================
    // 7) RBAC: foreign order fetch is forbidden
    // ========================================================================
    @Test
    void fetchPaymentStateRejectsForeignOrderWith403() throws Exception {
        User c1 = createUser("opt-c1-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        User c2 = createUser("opt-c2-" + UUID.randomUUID() + "@t.com", "CUSTOMER");
        String t1 = login(c1);
        String t2 = login(c2);

        Long pId = createProduct(vendorEmail, "Test", new BigDecimal("100.00"));
        stockIn(pId, 5);
        Long addr = createAddress(c1, locationId);
        addToCart(t1, pId, 1);

        Long orderId = orders.saveAndFlush(Order.builder()
            .user(c1).vendor(vendors.findById(vendorProfileId).orElseThrow())
            .status(OrderStatus.PENDING_PAYMENT)
            .paymentMethod(com.flowerconnect.order.domain.Order.PaymentMethod.ONLINE)
            .paymentStatus(com.flowerconnect.order.domain.Order.PaymentStatus.PENDING)
            .subtotal(new BigDecimal("100.00")).deliveryFee(BigDecimal.ZERO)
            .platformFee(BigDecimal.ZERO).total(new BigDecimal("100.00"))
            .deliveryAddressLine1("123 St").deliveryLatitude(new BigDecimal("12.97"))
            .deliveryLongitude(new BigDecimal("77.59")).slotStart(LocalDateTime.now().plusHours(1))
            .clientOrderId(UUID.randomUUID().toString())
            .build()).getId();

        payments.saveAndFlush(com.flowerconnect.payment.domain.Payment.builder()
            .order(orders.findByIdForUpdate(orderId).orElseThrow())
            .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE)
            .provider("simulated")
            .providerReference("intent_sim_" + UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING)
            .paymentGroupId(UUID.randomUUID().toString())
            .build());

        mvc.perform(get("/api/v1/payments/" + orderId)
            .header(HttpHeaders.AUTHORIZATION, bearer(t2)))
            .andExpect(status().isForbidden());
    }
}