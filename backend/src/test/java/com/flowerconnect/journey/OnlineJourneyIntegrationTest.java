package com.flowerconnect.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.billing.domain.SettlementDirection;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.repository.CommissionRepository;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.billing.service.BillingLedgerService;
import com.flowerconnect.billing.service.SettlementService;
import com.flowerconnect.cart.dto.AddCartItemRequest;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.checkout.dto.CheckoutRequest;
import com.flowerconnect.checkout.dto.CheckoutQuoteRequest;
import com.flowerconnect.customer.domain.Address;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.PaymentIntent;
import com.flowerconnect.payment.domain.PaymentIntent.IntentStatus;
import com.flowerconnect.payment.dto.WebhookResponse;
import com.flowerconnect.payment.repository.PaymentIntentRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.security.dto.RegisterRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full simulated-online customer journey against a real MySQL instance and the
 * production security chain (plan task 14.1):
 *
 * <ol>
 *   <li>register a customer</li>
 *   <li>check out an online order (PENDING_PAYMENT, shared payment group/intent)</li>
 *   <li>pay via the non-prod simulator (signed webhook path): PLACED / PAID</li>
 *   <li>verify duplicate webhook delivery is idempotent</li>
 *   <li>verify stock and payment state after payment</li>
 *   <li>vendor accepts and delivers; commission and ledger entries created</li>
 *   <li>run settlement and verify totals</li>
 *   <li>unauthorized simulator use is denied</li>
 * </ol>
 *
 * <p>The payment simulator is {@code !prod}-profile gated and is not active in
 * production (see {@code SimulatorProdAbsenceIntegrationTest}). Money is
 * asserted with {@link BigDecimal} (no floating point).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OnlineJourneyIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String PINCODE = "560034";
    private static final BigDecimal PRODUCT_PRICE = new BigDecimal("299.00");
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("10.00");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private VendorProfileRepository vendorProfiles;
    @Autowired private VendorHoursRepository vendorHoursRepository;
    @Autowired private ServiceLocationRepository locations;
    @Autowired private CategoryRepository categories;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductService products;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentIntentRepository intents;
    @Autowired private CommissionRepository commissionRepository;
    @Autowired private SettlementEntryRepository settlementEntryRepository;
    @Autowired private BillingLedgerService billingLedgerService;
    @Autowired private SettlementService settlementService;
    @Autowired private OrderStateMachine orderStateMachine;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long categoryId;
    private String slot;

    @PostConstruct
    void init() {
        locationId = locations.findByPincode(PINCODE).orElseThrow().getId();
        categoryId = categories.findBySlug("roses").orElseThrow().getId();
        slot = futureSlotStart();
    }

    @Test
    void fullOnlineJourneyRegisterToSettlement() throws Exception {
        // ------------------------------------------------------------------
        // 1. Register a customer
        // ------------------------------------------------------------------
        String customerEmail = "journey-online-customer-" + uuid() + "@t.com";
        String customerToken = registerCustomer(customerEmail, "Journey Online Customer");

        // ------------------------------------------------------------------
        // 2. Set up an approved vendor with a product and stock (fixture)
        // ------------------------------------------------------------------
        VendorSetup vendor = createApprovedVendorWithProduct("Journey Online Vendor", PRODUCT_PRICE);

        // ------------------------------------------------------------------
        // 3. Set the customer's delivery address at the same location
        // ------------------------------------------------------------------
        Long addressId = createAddress(customerEmail, locationId);

        // ------------------------------------------------------------------
        // 4. Add the product to the cart and check out ONLINE
        // ------------------------------------------------------------------
        addToCart(customerToken, vendor.productId, 1);
        JsonNode quote = requestQuote(customerToken, addressId, vendor.profileId, vendor.productId);
        assertThat(quote.get("hasErrors").asBoolean()).isFalse();
        String quoteHash = quote.get("quoteHash").asText();

        UUID clientOrderId = UUID.randomUUID();
        JsonNode result = checkout(customerToken, addressId, vendor.profileId, vendor.productId,
            quoteHash, clientOrderId, PaymentMethod.ONLINE);

        assertThat(result.get("idempotentReplay").asBoolean()).isFalse();
        assertThat(result.get("orders").size()).isEqualTo(1);
        JsonNode orderSummary = result.get("orders").get(0);
        Long orderId = orderSummary.get("orderId").asLong();
        assertThat(orderSummary.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(orderSummary.get("paymentMethod").asText()).isEqualTo("ONLINE");
        assertThat(orderSummary.get("paymentStatus").asText()).isEqualTo("PENDING");
        assertThat(new BigDecimal(orderSummary.get("subtotal").asText()))
            .isEqualByComparingTo(PRODUCT_PRICE);
        assertThat(result.get("paymentMethod").asText()).isEqualTo("ONLINE");
        assertThat(result.get("paymentGroupId").asText()).isNotEmpty();
        assertThat(result.get("intentReference").asText()).startsWith("intent_sim_");

        // Cart is cleared after checkout
        JsonNode cart = getCart(customerToken);
        assertThat(cart.get("totalItems").asInt()).isEqualTo(0);

        // ------------------------------------------------------------------
        // 5. Verify PENDING_PAYMENT state and stock reservation
        // ------------------------------------------------------------------
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.ONLINE);

        Payment payment = payments.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getMethod())
            .isEqualTo(com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE);
        assertThat(payment.getStatus())
            .isEqualTo(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING);
        assertThat(payment.getPaymentGroupId()).isEqualTo(result.get("paymentGroupId").asText());

        PaymentIntent intent = intents.findByPaymentGroupId(payment.getPaymentGroupId()).orElseThrow();
        assertThat(intent.getStatus()).isEqualTo(IntentStatus.CREATED);

        Inventory inv = inventoryRepository.findByProductId(vendor.productId).orElseThrow();
        assertThat(inv.getQuantity()).isEqualTo(10);
        assertThat(inv.getReservedQuantity()).isEqualTo(1);

        // ------------------------------------------------------------------
        // 6. Pay via the non-prod simulator (signed webhook path)
        // ------------------------------------------------------------------
        MvcResult pay = simulatorPay(orderId, customerToken);
        assertThat(pay.getResponse().getStatus()).isEqualTo(200);
        WebhookResponse payBody = json.readValue(pay.getResponse().getContentAsString(), WebhookResponse.class);
        assertThat(payBody.getOutcome()).isEqualTo("accepted");

        order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        payment = payments.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus())
            .isEqualTo(com.flowerconnect.payment.domain.Payment.PaymentStatus.PAID);

        intent = intents.findByPaymentGroupId(payment.getPaymentGroupId()).orElseThrow();
        assertThat(intent.getStatus()).isEqualTo(IntentStatus.PAID);

        // Stock is still reserved (committed only on vendor accept), not yet deducted
        inv = inventoryRepository.findByProductId(vendor.productId).orElseThrow();
        assertThat(inv.getQuantity()).isEqualTo(10);
        assertThat(inv.getReservedQuantity()).isEqualTo(1);

        // ------------------------------------------------------------------
        // 7. Duplicate webhook/simulator delivery is idempotent
        // ------------------------------------------------------------------
        MvcResult duplicate = simulatorPay(orderId, customerToken);
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(200);
        WebhookResponse dupBody = json.readValue(duplicate.getResponse().getContentAsString(), WebhookResponse.class);
        assertThat(dupBody.getOutcome()).isEqualTo("ignored");
        order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        // Exactly one payment and one intent; no duplicate side effects
        assertThat(payments.findByOrderId(orderId)).isPresent();
        assertThat(intents.findByPaymentGroupId(payment.getPaymentGroupId())).isPresent();

        // ------------------------------------------------------------------
        // 8. Unauthorized simulator use is denied
        // ------------------------------------------------------------------
        // No token -> 401
        mvc.perform(post("/api/v1/payments/simulator/" + orderId + "/pay"))
            .andExpect(status().isUnauthorized());
        // Foreign customer -> 403
        String foreignEmail = "journey-online-foreign-" + uuid() + "@t.com";
        User foreign = createUser(foreignEmail, "CUSTOMER");
        String foreignToken = login(foreign.getEmail());
        mvc.perform(post("/api/v1/payments/simulator/" + orderId + "/pay")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
            .andExpect(status().isForbidden());

        // ------------------------------------------------------------------
        // 9. Vendor accepts: PLACED -> CONFIRMED, stock committed
        // ------------------------------------------------------------------
        orderStateMachine.transition(orderId, OrderStatus.CONFIRMED, ActorType.VENDOR,
            vendor.userId, null);
        order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        inv = inventoryRepository.findByProductId(vendor.productId).orElseThrow();
        assertThat(inv.getQuantity()).isEqualTo(9);
        assertThat(inv.getReservedQuantity()).isEqualTo(0);

        // ------------------------------------------------------------------
        // 10. Progress to delivery:
        //     CONFIRMED -> PREPARING -> OUT_FOR_DELIVERY -> DELIVERED
        // ------------------------------------------------------------------
        orderStateMachine.transition(orderId, OrderStatus.PREPARING, ActorType.VENDOR, vendor.userId, null);
        orderStateMachine.transition(orderId, OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendor.userId, null);
        orderStateMachine.transition(orderId, OrderStatus.DELIVERED, ActorType.VENDOR, vendor.userId, null);

        order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        payment = payments.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getMethod())
            .isEqualTo(com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE);
        assertThat(payment.getStatus())
            .isEqualTo(com.flowerconnect.payment.domain.Payment.PaymentStatus.PAID);

        // ------------------------------------------------------------------
        // 11. Verify commission and ledger entries created on delivery
        // ------------------------------------------------------------------
        commissionRepository.findByOrderId(orderId).ifPresentOrElse(
            commission -> {
                assertThat(commission.getCommissionRate()).isEqualByComparingTo(COMMISSION_RATE);
                assertThat(commission.getSubtotal()).isEqualByComparingTo(PRODUCT_PRICE);
                assertThat(commission.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("29.90"));
                assertThat(commission.getDeliveryFee()).isEqualByComparingTo(new BigDecimal("30.00"));
                assertThat(commission.getPlatformFee()).isEqualByComparingTo(BigDecimal.ZERO);
            },
            () -> { throw new AssertionError("Expected a commission row for delivered order " + orderId); });

        settlementEntryRepository.findByOrderId(orderId).ifPresentOrElse(
            entry -> {
                assertThat(entry.getType()).isEqualTo(SettlementEntryType.ONLINE_ORDER_CREDIT);
                // ONLINE_ORDER_CREDIT = +(subtotal - commission + deliveryFee)
                // = 299.00 - 29.90 + 30.00 = 299.10
                assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("299.10"));
            },
            () -> { throw new AssertionError("Expected a settlement ledger entry for order " + orderId); });

        BigDecimal outstanding = billingLedgerService.getOutstandingBalance(vendor.profileId);
        assertThat(outstanding).isEqualByComparingTo(new BigDecimal("299.10"));

        // ------------------------------------------------------------------
        // 12. Run settlement generation/netting and verify totals
        // ------------------------------------------------------------------
        LocalDate today = LocalDate.now();
        com.flowerconnect.billing.domain.Settlement settlement =
            settlementService.runSettlement(vendor.profileId, today.minusDays(7), today);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(settlement.getTotalCredits()).isEqualByComparingTo(new BigDecimal("299.10"));
        assertThat(settlement.getTotalDues()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(settlement.getNetAmount()).isEqualByComparingTo(new BigDecimal("299.10"));
        assertThat(settlement.getDirection()).isEqualTo(SettlementDirection.PLATFORM_PAYS_VENDOR);

        settlementEntryRepository.findByOrderId(orderId).ifPresent(entry ->
            assertThat(entry.getSettlement().getId()).isEqualTo(settlement.getId()));

        Role adminRole = roles.findByName("ADMIN").orElseThrow();
        User admin = User.builder()
            .email("admin-" + uuid() + "@t.com")
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .fullName("Journey Admin")
            .phone("+91" + uuidDigits(10))
            .role(adminRole)
            .status(User.Status.ACTIVE)
            .build();
        users.saveAndFlush(admin);
        String refNote = "NEFT-" + uuid();
        com.flowerconnect.billing.domain.Settlement finalized =
            settlementService.finalizeSettlement(settlement.getId(), admin.getEmail(), refNote);
        assertThat(finalized.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(billingLedgerService.getOutstandingBalance(vendor.profileId))
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String registerCustomer(String email, String fullName) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .email(email)
            .password(PASSWORD)
            .fullName(fullName)
            .phone("+91" + uuidDigits(10))
            .build();
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();
        AuthResponse auth = json.readValue(res.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(auth.getAccessToken()).isNotEmpty();
        return auth.getAccessToken();
    }

    private String login(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest() {{
                    setEmail(email);
                    setPassword(PASSWORD);
                }})))
            .andExpect(status().isOk())
            .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private User createUser(String email, String roleName) {
        Role role = roles.findByName(roleName).orElseThrow();
        User user = User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .fullName("Test " + roleName)
            .phone("+91" + uuidDigits(10))
            .role(role)
            .status(User.Status.ACTIVE)
            .build();
        return users.saveAndFlush(user);
    }

    private record VendorSetup(String email, Long userId, Long profileId, Long productId) {}

    private VendorSetup createApprovedVendorWithProduct(String businessName, BigDecimal price) {
        String email = "journey-online-vendor-" + uuid() + "@t.com";
        Role florist = roles.findByName("FLORIST").orElseThrow();
        User user = User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .fullName(businessName + " Owner")
            .phone("+91" + uuidDigits(10))
            .role(florist)
            .status(User.Status.ACTIVE)
            .build();
        users.saveAndFlush(user);

        ServiceLocation loc = locations.findById(locationId).orElseThrow();
        VendorProfile vp = VendorProfile.builder()
            .user(user)
            .businessName(businessName)
            .addressLine1("123 Journey Street")
            .serviceLocation(loc)
            .latitude(loc.getLatitude())
            .longitude(loc.getLongitude())
            .deliveryRadiusKm(new BigDecimal("10.00"))
            .status(VendorProfile.Status.APPROVED)
            .commissionRate(COMMISSION_RATE)
            .reviewCount(0)
            .minOrderAmount(new BigDecimal("100.00"))
            .baseDeliveryFee(new BigDecimal("30.00"))
            .perKmFee(new BigDecimal("5.00"))
            .prepTimeMinutes(30)
            .slotDurationMinutes(60)
            .maxOrdersPerSlot(10)
            .acceptingOrders(true)
            .build();
        vendorProfiles.saveAndFlush(vp);

        for (DayOfWeek dow : DayOfWeek.values()) {
            vendorHoursRepository.saveAndFlush(VendorHours.builder()
                .vendorProfile(vp)
                .weekday(dow)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .closed(false)
                .build());
        }

        Long productId = products.create(email, ProductRequest.builder()
            .name(businessName + " Rose " + uuid())
            .categoryId(categoryId)
            .description("Journey online product")
            .basePrice(price)
            .status(ProductStatus.ACTIVE)
            .build()).getId();

        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        inventory.setQuantity(10);
        inventory.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(inventory);

        return new VendorSetup(email, user.getId(), vp.getId(), productId);
    }

    private Long createAddress(String email, Long serviceLocationId) {
        User user = users.findByEmail(email).orElseThrow();
        ServiceLocation loc = locations.findById(serviceLocationId).orElseThrow();
        return addressRepository.saveAndFlush(Address.builder()
            .user(user)
            .serviceLocation(loc)
            .label("Home")
            .line1("123 Journey Street")
            .latitude(loc.getLatitude())
            .longitude(loc.getLongitude())
            .defaultAddress(true)
            .build()).getId();
    }

    private void addToCart(String token, Long productId, int quantity) throws Exception {
        mvc.perform(post("/api/v1/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                    AddCartItemRequest.builder().productId(productId).quantity(quantity).build())))
            .andExpect(status().isCreated());
    }

    private JsonNode requestQuote(String token, Long addressId, Long vendorProfileId, Long productId)
        throws Exception {
        String body = json.writeValueAsString(
            new CheckoutQuoteRequest() {{
                setAddressId(addressId);
                setItems(List.of(
                    new CheckoutQuoteRequest.QuoteItem() {{
                        setVendorId(vendorProfileId);
                        setSlotStart(slot);
                    }}
                ));
            }}
        );
        String response = mvc.perform(post("/api/v1/checkout/quote")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode checkout(String token, Long addressId, Long vendorProfileId, Long productId,
                              String quoteHash, UUID clientOrderId, PaymentMethod method) throws Exception {
        CheckoutRequest request = CheckoutRequest.builder()
            .addressId(addressId)
            .clientOrderId(clientOrderId)
            .quoteHash(quoteHash)
            .confirmPriceChanges(true)
            .paymentMethod(method)
            .items(List.of(CheckoutRequest.CheckoutItem.builder()
                .vendorId(vendorProfileId)
                .slotStart(slot)
                .build()))
            .build();
        MvcResult res = mvc.perform(post("/api/v1/checkout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
            .andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus())
            .overridingErrorMessage("Expected 200 but was %d. Body: %s"
                .formatted(res.getResponse().getStatus(), body))
            .isEqualTo(200);
        return json.readTree(body);
    }

    private MvcResult simulatorPay(Long orderId, String token) throws Exception {
        return mvc.perform(post("/api/v1/payments/simulator/" + orderId + "/pay")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    }

    private JsonNode getCart(String token) throws Exception {
        String body = mvc.perform(get("/api/v1/cart")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static String futureSlotStart() {
        ZoneId vendorZone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.now(vendorZone);
        ZonedDateTime candidate = ZonedDateTime.of(now.toLocalDate(), LocalTime.of(12, 0), vendorZone);
        if (candidate.isBefore(now.plusMinutes(120))) {
            candidate = ZonedDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(12, 0), vendorZone);
        }
        return candidate.withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String uuidDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < count; i++) {
            digits.append(random.nextInt(10));
        }
        return digits.toString();
    }
}
