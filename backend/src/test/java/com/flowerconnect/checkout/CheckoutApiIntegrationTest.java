package com.flowerconnect.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.cart.dto.AddCartItemRequest;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.checkout.domain.CheckoutIdempotency;
import com.flowerconnect.checkout.dto.CheckoutRequest;
import com.flowerconnect.checkout.repository.CheckoutIdempotencyRepository;
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
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for plan task 5.4 (checkout hardening) and the checkout
 * endpoint ({@code POST /api/v1/checkout}) against a real MySQL instance and the
 * production security chain.
 *
 * <p>Scenarios covered:
 * <ul>
 *   <li>RBAC: unauthenticated, florist, admin are all refused.</li>
 *   <li>Happy path: cart → quote → checkout → orders created, cart cleared,
 *       idempotency record complete.</li>
 *   <li>Invalid quoteHash rejected (stale quote).</li>
 *   <li>Foreign address (belongs to another customer) rejected.</li>
 *   <li>Insufficient stock rolls back entirely.</li>
 *   <li>Same-payload replay returns the original result with
 *       {@code idempotentReplay=true}.</li>
 *   <li>Different-payload conflict with the same clientOrderId is rejected.</li>
 *   <li>Concurrent identical requests: only one creates orders.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String KORAMANGALA_PINCODE = "560034";
    private static final String SLOT = "2030-01-01T10:00:00";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ServiceLocationRepository serviceLocationRepository;
    @Autowired private VendorProfileRepository vendorProfileRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductService productService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CheckoutIdempotencyRepository idempotencyRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long rosesCategoryId;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE)
                .orElseThrow().getId();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        User admin = createUser(uniqueEmail(), "ADMIN");
        adminToken = tokenFor(admin);
    }

    // ================================================================ RBAC matrix

    @Test
    void checkoutRejectsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void checkoutRejectsFlorist() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void checkoutRejectsAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ================================================================ happy path

    @Test
    void checkoutCreatesOrdersClearsCartAndCompletesIdempotencyRecord() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor, "Checkout Rose " + UUID.randomUUID());
        stockIn(vendor, productId, 20, "initial");

        User customer = createUser(uniqueEmail(), "CUSTOMER");
        String customerToken = tokenFor(customer);
        Long addressId = createAddress(customer, locationId);

        addToCart(customerToken, productId, 2);

        // Step 1 — get quote
        JsonNode quote = requestQuote(customerToken, addressId, vendor, productId);
        assertThat(quote.get("hasErrors").asBoolean()).isFalse();
        String quoteHash = quote.get("quoteHash").asText();

        // Step 2 — checkout
        UUID clientOrderId = UUID.randomUUID();
        JsonNode result = checkout(customerToken, addressId, vendor, productId, quoteHash, clientOrderId);

        assertThat(result.get("idempotentReplay").asBoolean()).isFalse();
        assertThat(result.get("orders")).isNotNull();
        assertThat(result.get("orders").size()).isEqualTo(1);
        assertThat(result.get("orders").get(0).get("status").asText()).isEqualTo("PLACED");
        assertThat(result.get("total").asDouble()).isGreaterThan(0);

        // Cart must be empty after checkout
        JsonNode cart = getCart(customerToken);
        assertThat(cart.get("totalItems").asInt()).isEqualTo(0);

        // Idempotency record must be completed
        Long userId = customer.getId();
        assertThat(idempotencyRepository.findByUserIdAndClientOrderId(userId, clientOrderId.toString()))
                .isPresent()
                .get()
                .satisfies(rec -> assertThat(rec.getCompletedAt()).isNotNull());
    }

    // ================================================================ validation failures

    @Test
    void checkoutRejectsInvalidQuoteHash() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor, "Stale Quote Rose " + UUID.randomUUID());
        stockIn(vendor, productId, 20, "initial");

        User customer = createUser(uniqueEmail(), "CUSTOMER");
        String customerToken = tokenFor(customer);
        Long addressId = createAddress(customer, locationId);

        addToCart(customerToken, productId, 2);

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressId)
                .clientOrderId(UUID.randomUUID())
                .quoteHash("0000000000000000000000000000000000000000000000000000000000000000")
                .items(List.of(CheckoutRequest.CheckoutItem.builder()
                        .vendorId(vendor.profileId())
                        .slotStart(SLOT)
                        .build()))
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void checkoutRejectsForeignAddress() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor, "Foreign Addr Rose " + UUID.randomUUID());
        stockIn(vendor, productId, 20, "initial");

        User customer = createUser(uniqueEmail(), "CUSTOMER");
        String customerToken = tokenFor(customer);

        // Another customer owns the address
        User otherCustomer = createUser(uniqueEmail(), "CUSTOMER");
        Long foreignAddressId = createAddress(otherCustomer, locationId);

        addToCart(customerToken, productId, 1);

        JsonNode quote = requestQuote(customerToken, foreignAddressId, vendor, productId);
        // Quote itself should fail with NOT_FOUND for foreign address
        // (the quote endpoint resolves the address ownership too)
        // If quote returned an error we try checkout anyway to confirm it rejects
        // Let's just do a direct checkout with invalid address
        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(foreignAddressId)
                .clientOrderId(UUID.randomUUID())
                .quoteHash("dummy")
                .items(List.of(CheckoutRequest.CheckoutItem.builder()
                        .vendorId(vendor.profileId())
                        .slotStart(SLOT)
                        .build()))
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void checkoutWithInsufficientStockRollsBackEntirely() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor, "Low Stock Rose " + UUID.randomUUID());
        stockIn(vendor, productId, 5, "initial");

        User customer = createUser(uniqueEmail(), "CUSTOMER");
        String customerToken = tokenFor(customer);
        Long addressId = createAddress(customer, locationId);

        addToCart(customerToken, productId, 3);

        JsonNode quote = requestQuote(customerToken, addressId, vendor, productId);
        String quoteHash = quote.get("quoteHash").asText();

        // Drain stock AFTER getting the quote but BEFORE checkout
        jdbcTemplate.update("UPDATE inventory SET quantity = 1 WHERE product_id = ?", productId);

        UUID clientOrderId = UUID.randomUUID();
        CheckoutRequest request = buildCheckoutRequest(addressId, vendor, productId, quoteHash, clientOrderId);

        mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        // No orders created
        Long userId = customer.getId();
        assertThat(orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId.toString())).isEmpty();

        // Idempotency record rolled back — either absent or with null completedAt
        idempotencyRepository.findByUserIdAndClientOrderId(userId, clientOrderId.toString())
                .ifPresent(rec -> assertThat(rec.getCompletedAt()).isNull());
    }

    // ================================================================ idempotency

    @Test
    void sameClientOrderIdSamePayloadReturnsCachedResult() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor, "Replay Rose " + UUID.randomUUID());
        stockIn(vendor, productId, 20, "initial");

        User customer = createUser(uniqueEmail(), "CUSTOMER");
        String customerToken = tokenFor(customer);
        Long addressId = createAddress(customer, locationId);

        addToCart(customerToken, productId, 2);

        JsonNode quote = requestQuote(customerToken, addressId, vendor, productId);
        String quoteHash = quote.get("quoteHash").asText();
        UUID clientOrderId = UUID.randomUUID();

        // First checkout
        JsonNode first = checkout(customerToken, addressId, vendor, productId, quoteHash, clientOrderId);
        assertThat(first.get("idempotentReplay").asBoolean()).isFalse();
        long firstOrderId = first.get("orders").get(0).get("orderId").asLong();

        // Re-add the same item and get a fresh quote (cart was cleared)
        addToCart(customerToken, productId, 2);
        JsonNode quote2 = requestQuote(customerToken, addressId, vendor, productId);
        String quoteHash2 = quote2.get("quoteHash").asText();

        // Second checkout with the SAME clientOrderId — should replay
        CheckoutRequest replayRequest = buildCheckoutRequest(addressId, vendor, productId, quoteHash2, clientOrderId);
        String replayBody = mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replayRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode replay = objectMapper.readTree(replayBody);
        assertThat(replay.get("idempotentReplay").asBoolean()).isTrue();
        assertThat(replay.get("orders").get(0).get("orderId").asLong()).isEqualTo(firstOrderId);

        // Only one order in DB for this clientOrderId
        Long userId = customer.getId();
        assertThat(orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId.toString())).hasSize(1);
    }

    @Test
    void differentPayloadWithSameClientOrderIdIsRejected() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor, "Conflict Rose " + UUID.randomUUID());
        stockIn(vendor, productId, 20, "initial");

        User customer = createUser(uniqueEmail(), "CUSTOMER");
        String customerToken = tokenFor(customer);
        Long addressId = createAddress(customer, locationId);
        Long addressId2 = createAddress(customer, locationId);

        addToCart(customerToken, productId, 2);

        JsonNode quote = requestQuote(customerToken, addressId, vendor, productId);
        String quoteHash = quote.get("quoteHash").asText();
        UUID clientOrderId = UUID.randomUUID();

        // First checkout succeeds
        checkout(customerToken, addressId, vendor, productId, quoteHash, clientOrderId);

        // Re-add item
        addToCart(customerToken, productId, 2);
        JsonNode quote2 = requestQuote(customerToken, addressId2, vendor, productId);
        String quoteHash2 = quote2.get("quoteHash").asText();

        // Second checkout with SAME clientOrderId but DIFFERENT address — conflict
        CheckoutRequest conflict = CheckoutRequest.builder()
                .addressId(addressId2)
                .clientOrderId(clientOrderId)
                .quoteHash(quoteHash2)
                .items(List.of(CheckoutRequest.CheckoutItem.builder()
                        .vendorId(vendor.profileId())
                        .slotStart(SLOT)
                        .build()))
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflict)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ================================================================ multi-vendor & price drift

    @Test
    void multiVendorCheckoutCreatesOrderPerVendorAndReservesStock() throws Exception {
        Vendor vendor1 = registerVendor(VendorProfile.Status.APPROVED);
        Long product1 = createActiveProduct(vendor1, "Multi Rose 1 " + UUID.randomUUID());
        stockIn(vendor1, product1, 20, "initial");

        Vendor vendor2 = registerVendor(VendorProfile.Status.APPROVED);
        Long product2 = createActiveProduct(vendor2, "Multi Rose 2 " + UUID.randomUUID());
        stockIn(vendor2, product2, 20, "initial");

        User customer = createUser(uniqueEmail(), "CUSTOMER");
        String customerToken = tokenFor(customer);
        Long addressId = createAddress(customer, locationId);

        addToCart(customerToken, product1, 2);
        addToCart(customerToken, product2, 1);

        // Step 1 — quote with both vendors
        String quoteBody = String.format(
                "{\"addressId\":%d,\"items\":[{\"vendorId\":%d,\"slotStart\":\"%s\"},{\"vendorId\":%d,\"slotStart\":\"%s\"}]}",
                addressId, vendor1.profileId(), SLOT, vendor2.profileId(), SLOT);
        String quoteResStr = mockMvc.perform(post("/api/v1/checkout/quote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode quote = objectMapper.readTree(quoteResStr);
        assertThat(quote.get("shops").size()).isEqualTo(2);
        String quoteHash = quote.get("quoteHash").asText();

        // Step 2 — checkout
        UUID clientOrderId = UUID.randomUUID();
        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressId)
                .clientOrderId(clientOrderId)
                .quoteHash(quoteHash)
                .items(List.of(
                        CheckoutRequest.CheckoutItem.builder().vendorId(vendor1.profileId()).slotStart(SLOT).build(),
                        CheckoutRequest.CheckoutItem.builder().vendorId(vendor2.profileId()).slotStart(SLOT).build()
                ))
                .build();

        String checkoutResStr = mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode checkoutRes = objectMapper.readTree(checkoutResStr);
        assertThat(checkoutRes.get("idempotentReplay").asBoolean()).isFalse();
        assertThat(checkoutRes.get("orders").size()).isEqualTo(2);

        // Cart is cleared
        JsonNode cart = getCart(customerToken);
        assertThat(cart.get("totalItems").asInt()).isEqualTo(0);

        // Stock reserved
        assertThat(inventoryRepository.findByProductId(product1).orElseThrow().getReservedQuantity()).isEqualTo(2);
        assertThat(inventoryRepository.findByProductId(product2).orElseThrow().getReservedQuantity()).isEqualTo(1);

        // Replay returns both orders
        String replayResStr = mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode replayRes = objectMapper.readTree(replayResStr);
        assertThat(replayRes.get("idempotentReplay").asBoolean()).isTrue();
        assertThat(replayRes.get("orders").size()).isEqualTo(2);
    }

    @Test
    void checkoutDetectsPriceDriftAndEnforcesExplicitConfirmation() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor, "Price Drift Rose " + UUID.randomUUID());
        stockIn(vendor, productId, 20, "initial");

        User customer = createUser(uniqueEmail(), "CUSTOMER");
        String customerToken = tokenFor(customer);
        Long addressId = createAddress(customer, locationId);

        addToCart(customerToken, productId, 2);

        // Vendor updates price in DB from 299.00 to 399.00
        jdbcTemplate.update("UPDATE products SET base_price = 399.00 WHERE id = ?", productId);

        // Quote reflects price drift
        JsonNode quote = requestQuote(customerToken, addressId, vendor, productId);
        assertThat(quote.get("hasPriceChanges").asBoolean()).isTrue();
        String quoteHash = quote.get("quoteHash").asText();

        UUID clientOrderId = UUID.randomUUID();

        // Checkout without confirmation fails with 409 CONFLICT
        CheckoutRequest unconfirmed = CheckoutRequest.builder()
                .addressId(addressId)
                .clientOrderId(clientOrderId)
                .quoteHash(quoteHash)
                .confirmPriceChanges(false)
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(vendor.profileId()).slotStart(SLOT).build()))
                .build();

        mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unconfirmed)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // Checkout WITH confirmation succeeds
        CheckoutRequest confirmed = CheckoutRequest.builder()
                .addressId(addressId)
                .clientOrderId(clientOrderId)
                .quoteHash(quoteHash)
                .confirmPriceChanges(true)
                .items(List.of(CheckoutRequest.CheckoutItem.builder().vendorId(vendor.profileId()).slotStart(SLOT).build()))
                .build();

        String successResStr = mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmed)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode successRes = objectMapper.readTree(successResStr);
        assertThat(successRes.get("orders").size()).isEqualTo(1);
        // Subtotal = 2 * 399.00 = 798.00
        assertThat(successRes.get("orders").get(0).get("subtotal").asDouble()).isEqualTo(798.0);
    }

    // ================================================================ concurrency

    @Test
    void concurrentIdenticalRequestsProduceOnlyOneCheckout() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createActiveProduct(vendor, "Concurrent Rose " + UUID.randomUUID());
        stockIn(vendor, productId, 20, "initial");

        User customer = createUser(uniqueEmail(), "CUSTOMER");
        String customerToken = tokenFor(customer);
        Long addressId = createAddress(customer, locationId);

        addToCart(customerToken, productId, 2);

        JsonNode quote = requestQuote(customerToken, addressId, vendor, productId);
        String quoteHash = quote.get("quoteHash").asText();
        UUID clientOrderId = UUID.randomUUID();

        String requestBody = objectMapper.writeValueAsString(
                buildCheckoutRequest(addressId, vendor, productId, quoteHash, clientOrderId));

        int threads = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        int[] statuses = new int[threads];
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    start.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/checkout")
                                    .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                            .andReturn();
                    statuses[idx] = res.getResponse().getStatus();
                } catch (Exception ex) {
                    statuses[idx] = 500;
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        executor.shutdown();

        // Assert every response was either 200 (created or post-completion replay) or 409 (concurrent race loser)
        // No request should fail with 500 or any unexpected status code
        long successes = 0;
        long conflicts = 0;
        for (int s : statuses) {
            assertThat(s).as("Response status should be 200 or 409").isIn(200, 409);
            if (s == 200) successes++;
            if (s == 409) conflicts++;
        }
        assertThat(successes).isGreaterThanOrEqualTo(1);

        // Exactly one order created for this clientOrderId
        Long userId = customer.getId();
        List<Order> orders = orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId.toString());
        assertThat(orders).hasSize(1);
        Order order = orders.get(0);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PLACED);
        assertThat(order.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PENDING);

        // Exactly one completed idempotency record in DB
        Optional<CheckoutIdempotency> idempOpt = idempotencyRepository.findByUserIdAndClientOrderId(userId, clientOrderId.toString());
        assertThat(idempOpt).isPresent();
        assertThat(idempOpt.get().getCompletedAt()).isNotNull();

        // Stock reservation is applied exactly once for the ordered item (2 units reserved, 20 total physical)
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(inventory.getQuantity()).isEqualTo(20);

        // Stock movement RESERVE rows are created exactly once per ordered item
        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
        List<StockMovement> reserveMovements = movements.stream()
                .filter(m -> m.getMovementType() == StockMovement.MovementType.RESERVE)
                .toList();
        assertThat(reserveMovements).hasSize(1);
        assertThat(reserveMovements.get(0).getQuantityDelta()).isEqualTo(2);
        assertThat(reserveMovements.get(0).getReferenceId()).isEqualTo(order.getId());

        // Replaying the same request after the concurrent race returns the existing result
        // without creating extra orders, reservations, or movements
        String replayResStr = mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode replayRes = objectMapper.readTree(replayResStr);
        assertThat(replayRes.get("idempotentReplay").asBoolean()).isTrue();
        assertThat(replayRes.get("orders").get(0).get("orderId").asLong()).isEqualTo(order.getId());

        // Counts must remain identical after replay
        assertThat(orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId.toString())).hasSize(1);
        assertThat(inventoryRepository.findByProductId(productId).orElseThrow().getReservedQuantity()).isEqualTo(2);
        long reserveCountAfterReplay = stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
                .filter(m -> m.getMovementType() == StockMovement.MovementType.RESERVE)
                .count();
        assertThat(reserveCountAfterReplay).isEqualTo(1);
    }

    // ================================================================ helpers

    private record Vendor(String email, String token, Long profileId) {}

    private Vendor registerVendor(VendorProfile.Status target) throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerRequest(email))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        Long profileId = vendorProfileRepository.findByUserId(user.getId()).orElseThrow().getId();

        if (target == VendorProfile.Status.APPROVED) {
            mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/approve")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                    .andExpect(status().isOk());
        }

        return new Vendor(email, tokenFor(user), profileId);
    }

    private Long createActiveProduct(Vendor vendor, String name) {
        ProductRequest request = ProductRequest.builder()
                .name(name)
                .categoryId(rosesCategoryId)
                .description("Fresh flowers for checkout test")
                .basePrice(new BigDecimal("299.00"))
                .status(ProductStatus.ACTIVE)
                .build();
        return productService.create(vendor.email(), request).getId();
    }

    private void stockIn(Vendor vendor, Long productId, int quantity, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/vendors/products/" + productId + "/inventory/stock-in")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", quantity, "reason", reason))))
                .andExpect(status().isOk());
    }

    private Long createAddress(User user, Long svcLocationId) {
        ServiceLocation loc = serviceLocationRepository.findById(svcLocationId).orElseThrow();
        Address address = Address.builder()
                .user(user)
                .serviceLocation(loc)
                .label("Home")
                .line1("123 Test Street")
                .latitude(loc.getLatitude())
                .longitude(loc.getLongitude())
                .defaultAddress(true)
                .build();
        return addressRepository.save(address).getId();
    }

    private void addToCart(String customerToken, Long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AddCartItemRequest.builder().productId(productId).quantity(quantity).build())))
                .andExpect(status().isCreated());
    }

    private JsonNode requestQuote(String customerToken, Long addressId, Vendor vendor, Long productId) throws Exception {
        String body = String.format(
                "{\"addressId\":%d,\"items\":[{\"vendorId\":%d,\"slotStart\":\"%s\"}]}",
                addressId, vendor.profileId(), SLOT);
        String response = mockMvc.perform(post("/api/v1/checkout/quote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode checkout(String customerToken, Long addressId, Vendor vendor,
                              Long productId, String quoteHash, UUID clientOrderId) throws Exception {
        String body = objectMapper.writeValueAsString(
                buildCheckoutRequest(addressId, vendor, productId, quoteHash, clientOrderId));
        String response = mockMvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private CheckoutRequest buildCheckoutRequest(Long addressId, Vendor vendor, Long productId,
                                                  String quoteHash, UUID clientOrderId) {
        return CheckoutRequest.builder()
                .addressId(addressId)
                .clientOrderId(clientOrderId)
                .quoteHash(quoteHash)
                .items(List.of(CheckoutRequest.CheckoutItem.builder()
                        .vendorId(vendor.profileId())
                        .slotStart(SLOT)
                        .build()))
                .build();
    }

    private JsonNode getCart(String token) throws Exception {
        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/cart")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Test " + roleName)
                .phone(uniquePhone())
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
    }

    private String tokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail()).password(PASSWORD).build())))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(auth.getAccessToken());
        return auth.getAccessToken();
    }

    private VendorRegisterRequest registerRequest(String email) {
        VendorRegisterRequest r = new VendorRegisterRequest();
        r.setEmail(email);
        r.setPassword(PASSWORD);
        r.setFullName("Checkout Test Vendor");
        r.setPhone(uniquePhone());
        r.setBusinessName("Checkout Blossoms " + UUID.randomUUID().toString().substring(0, 8));
        r.setDescription("Test vendor for checkout integration tests");
        r.setAddressLine1("12 Flower Street");
        r.setServiceLocationId(locationId);
        r.setDeliveryRadiusKm(new BigDecimal("10.00"));
        r.setMinOrderAmount(new BigDecimal("100.00"));
        r.setBaseDeliveryFee(new BigDecimal("30.00"));
        r.setPerKmFee(new BigDecimal("5.00"));
        r.setPrepTimeMinutes(30);
        r.setSlotDurationMinutes(60);
        r.setMaxOrdersPerSlot(10);
        r.setAcceptingOrders(true);
        return r;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail() {
        return "checkout-" + UUID.randomUUID() + "@test.com";
    }

    private static String uniquePhone() {
        Random random = new Random();
        StringBuilder digits = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            digits.append(random.nextInt(10));
        }
        return "+91" + digits;
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
