package com.flowerconnect.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.User.Status;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.CancellationService;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.vendor.service.VendorOrderService;
import com.flowerconnect.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.annotation.PostConstruct;

/**
 * Integration tests for order cancellation (plan Phase 8, tasks 8.1 and 8.2)
 * against a real MySQL database.
 *
 * <p>The class is deliberately not {@code @Transactional}: every test commits
 * to the shared Testcontainers database, so fixtures use unique emails, product
 * names and order ids to stay isolated. Cancellation is exercised through the
 * {@code POST /api/v1/orders/{id}/cancel} endpoint so the production filter
 * chain, controller and service all run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CancellationIntegrationTest extends AbstractIntegrationTest {

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
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository payments;
    @Autowired private CancellationService cancellationService;
    @Autowired private VendorOrderService vendorOrderService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long categoryId;

    @PostConstruct
    void init() {
        locationId = locations.findByPincode(PINCODE).orElseThrow().getId();
        categoryId = categories.findBySlug("roses").orElseThrow().getId();
    }

    // ------------------------------------------------------------------
    // Happy matrix: who may cancel what, and the stock effect
    // ------------------------------------------------------------------

    @Test
    void customerCancelsOwnPlacedCodOrderReleasesReservation() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-cancel-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Cancel Rose " + uuid(), new BigDecimal("100.00"));
        stockIn(productId, 10);
        reserve(productId, 3);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("100.00"), 3);

        MvcResult res = cancel(token, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(body.get("refundPending").asBoolean()).isFalse();

        orderRepository.findById(orderId).ifPresent(o -> assertThat(o.getStatus())
            .isEqualTo(OrderStatus.CANCELLED));
        // Reservation released: quantity untouched, reserved_quantity back to zero.
        assertThat(quantity(productId)).isEqualTo(10);
        assertThat(reserved(productId)).isEqualTo(0);
        // Exactly one RELEASE movement, one per item.
        assertThat(releaseMovements(productId)).hasSize(1);
        assertThat(releaseMovements(productId).get(0).getQuantityDelta()).isEqualTo(-3);
    }

    @Test
    void vendorRejectsOwnPlacedOrderReleasesReservation() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-rej-" + uuid() + "@t.com";
        createUser(customerEmail, "CUSTOMER");
        Long productId = createProduct(vendorEmail, "Reject Rose " + uuid(), new BigDecimal("50.00"));
        stockIn(productId, 5);
        reserve(productId, 2);

        Long orderId = createOrder(lookup(customerEmail), vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("50.00"), 2);
        String token = login(lookup(vendorEmail));

        MvcResult res = cancel(token, orderId, "out of stock");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(res.getResponse().getContentAsString()).get("status").asText())
            .isEqualTo("REJECTED");

        assertThat(quantity(productId)).isEqualTo(5);
        assertThat(reserved(productId)).isEqualTo(0);
        assertThat(releaseMovements(productId)).hasSize(1);
    }

    @Test
    void adminCancelsAnyPlacedOrder() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-admin-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-cancel-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        Long productId = createProduct(vendorEmail, "Admin Rose " + uuid(), new BigDecimal("75.00"));
        stockIn(productId, 4);
        reserve(productId, 4);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("75.00"), 4);

        MvcResult res = cancel(adminToken, orderId, "fraud");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(res.getResponse().getContentAsString()).get("status").asText())
            .isEqualTo("CANCELLED");
        assertThat(reserved(productId)).isEqualTo(0);
    }

    @Test
    void confirmedOrderCancelRestocks() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-conf-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Restock Rose " + uuid(), new BigDecimal("30.00"));
        stockIn(productId, 10);
        // Accepted: quantity deducted, reservation committed.
        reserve(productId, 3);
        jdbcTemplate.update("UPDATE inventory SET quantity = 7, reserved_quantity = 3 WHERE product_id = ?",
                productId);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.CONFIRMED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("30.00"), 3);

        MvcResult res = cancel(token, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(res.getResponse().getContentAsString()).get("status").asText())
            .isEqualTo("CANCELLED");
        // Restock: quantity back to 10, reserved stays 3, one RESTOCK movement +3.
        assertThat(quantity(productId)).isEqualTo(10);
        assertThat(reserved(productId)).isEqualTo(3);
        assertThat(restockMovements(productId)).hasSize(1);
        assertThat(restockMovements(productId).get(0).getQuantityDelta()).isEqualTo(3);
    }

    @Test
    void preparingOrderCancelDoesNotRestock() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-prep-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Prep Rose " + uuid(), new BigDecimal("40.00"));
        stockIn(productId, 10);
        jdbcTemplate.update("UPDATE inventory SET quantity = 6, reserved_quantity = 4 WHERE product_id = ?",
                productId);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PREPARING,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("40.00"), 4);

        MvcResult res = cancel(token, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(res.getResponse().getContentAsString()).get("status").asText())
            .isEqualTo("CANCELLED");
        // No restock: quantity stays 6, reserved stays 4, no movement written.
        assertThat(quantity(productId)).isEqualTo(6);
        assertThat(reserved(productId)).isEqualTo(4);
        assertThat(stockMovements(productId)).isEqualTo(0);
    }

    @Test
    void outForDeliveryCancelIsAdminOnlyAndDoesNotRestock() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-del-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-del-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Delivery Rose " + uuid(), new BigDecimal("55.00"));
        stockIn(productId, 10);
        jdbcTemplate.update("UPDATE inventory SET quantity = 5, reserved_quantity = 5 WHERE product_id = ?",
                productId);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.OUT_FOR_DELIVERY,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("55.00"), 5);

        // A vendor may not cancel an out-for-delivery order.
        String vendorToken = login(lookup(vendorEmail));
        MvcResult vendorRes = cancel(vendorToken, orderId, "failed");
        assertThat(vendorRes.getResponse().getStatus()).isEqualTo(403);

        // An admin may cancel it, with no restock.
        MvcResult res = cancel(adminToken, orderId, "failed delivery");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(res.getResponse().getContentAsString()).get("status").asText())
            .isEqualTo("CANCELLED");
        assertThat(quantity(productId)).isEqualTo(5);
        assertThat(reserved(productId)).isEqualTo(5);
        assertThat(stockMovements(productId)).isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // Paid online and COD payment behaviour
    // ------------------------------------------------------------------

    @Test
    void paidOnlineCancellationBecomesRefundPending() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-online-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Online Rose " + uuid(), new BigDecimal("200.00"));
        stockIn(productId, 6);
        reserve(productId, 2);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.ONLINE, PaymentStatus.PAID, new BigDecimal("200.00"), 2);
        createPayment(orderId, com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE,
            com.flowerconnect.payment.domain.Payment.PaymentStatus.PAID, new BigDecimal("200.00"));

        MvcResult res = cancel(token, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(body.get("refundPending").asBoolean()).isTrue();
        assertThat(body.get("paymentStatus").asText()).isEqualTo("REFUND_PENDING");

        payments.findByOrderId(orderId).ifPresent(p -> assertThat(p.getStatus())
            .isEqualTo(Payment.PaymentStatus.REFUND_PENDING));
        assertThat(reserved(productId)).isEqualTo(0);
    }

    @Test
    void codCancellationBeforeDeliveryLeavesPaymentPending() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-cod-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Cod Rose " + uuid(), new BigDecimal("90.00"));
        stockIn(productId, 5);
        reserve(productId, 2);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("90.00"), 2);

        MvcResult res = cancel(token, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(body.get("refundPending").asBoolean()).isFalse();
        assertThat(body.get("paymentStatus").asText()).isEqualTo("PENDING");

        // No money moved: COD payment stays PENDING (Option B), no refund-pending.
        payments.findByOrderId(orderId).ifPresent(p -> assertThat(p.getStatus())
            .isEqualTo(PaymentStatus.PENDING));
    }

    // ------------------------------------------------------------------
    // Ownership and RBAC
    // ------------------------------------------------------------------

    @Test
    void customerCannotCancelAnotherCustomersOrder() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String ownerEmail = "cust-own-" + uuid() + "@t.com";
        User owner = createUser(ownerEmail, "CUSTOMER");
        String thiefEmail = "cust-thief-" + uuid() + "@t.com";
        User thief = createUser(thiefEmail, "CUSTOMER");
        String thiefToken = login(thief);

        Long productId = createProduct(vendorEmail, "Theft Rose " + uuid(), new BigDecimal("10.00"));
        stockIn(productId, 2);
        reserve(productId, 1);

        Long orderId = createOrder(owner, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("10.00"), 1);

        MvcResult res = cancel(thiefToken, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void vendorCannotCancelAnotherVendorOrder() throws Exception {
        String victimVendor = createVendor();
        Long victimVendorProfileId = vendorProfileId(victimVendor);
        String attackerVendor = createVendor();
        String attackerToken = login(lookup(attackerVendor));
        String customerEmail = "cust-vv-" + uuid() + "@t.com";
        createUser(customerEmail, "CUSTOMER");

        Long productId = createProduct(victimVendor, "Vv Rose " + uuid(), new BigDecimal("10.00"));
        stockIn(productId, 1);
        reserve(productId, 1);

        Long orderId = createOrder(lookup(customerEmail), victimVendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("10.00"), 1);

        MvcResult res = cancel(attackerToken, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void adminCanCancelAnyOrderIncludingAnotherVendor() throws Exception {
        String victimVendor = createVendor();
        Long victimVendorProfileId = vendorProfileId(victimVendor);
        String customerEmail = "cust-adminvv-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String adminEmail = "admin-vv-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        Long productId = createProduct(victimVendor, "AdminVv Rose " + uuid(), new BigDecimal("10.00"));
        stockIn(productId, 1);
        reserve(productId, 1);

        Long orderId = createOrder(customer, victimVendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("10.00"), 1);

        MvcResult res = cancel(adminToken, orderId, "admin override");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unauthenticatedCancelIsRejectedWith401() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-401-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        Long productId = createProduct(vendorEmail, "Unauth Rose " + uuid(), new BigDecimal("10.00"));
        stockIn(productId, 1);
        reserve(productId, 1);
        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("10.00"), 1);

        try {
            mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new com.flowerconnect.order.dto.CancelOrderRequest())))
                .andExpect(status().isUnauthorized());
        } catch (AssertionError e) {
            // 401 is the expected RBAC outcome; rethrow only if something else.
            throw e;
        }
    }

    @Test
    void missingOrderIs404() throws Exception {
        String customerEmail = "cust-miss-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        MvcResult res = cancel(token, 999999L, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void vendorRequiresACancellationReason() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-noreas-" + uuid() + "@t.com";
        createUser(customerEmail, "CUSTOMER");
        Long productId = createProduct(vendorEmail, "Noreas Rose " + uuid(), new BigDecimal("10.00"));
        stockIn(productId, 1);
        reserve(productId, 1);

        Long orderId = createOrder(lookup(customerEmail), vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("10.00"), 1);
        String token = login(lookup(vendorEmail));

        MvcResult res = cancel(token, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void customerMayCancelWithoutReason() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-noreas2-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Noreas2 Rose " + uuid(), new BigDecimal("10.00"));
        stockIn(productId, 1);
        reserve(productId, 1);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("10.00"), 1);

        MvcResult res = cancel(token, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void repeatedCancellationOfTerminalOrderIs409() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-dup-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Dup Rose " + uuid(), new BigDecimal("10.00"));
        stockIn(productId, 1);
        reserve(productId, 1);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("10.00"), 1);

        MvcResult first = cancel(token, orderId, null);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        MvcResult second = cancel(token, orderId, null);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    // ------------------------------------------------------------------
    // Slot capacity and suspended vendor
    // ------------------------------------------------------------------

    @Test
    void cancellationFreesSlotCapacity() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-slot-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Slot Rose " + uuid(), new BigDecimal("12.00"));
        stockIn(productId, 3);
        reserve(productId, 1);
        LocalDateTime slot = LocalDateTime.now().plusHours(2);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("12.00"), 1, slot);

        long before = orderRepository.countBookedOrdersForSlot(vendorProfileId, slot);
        assertThat(before).isGreaterThanOrEqualTo(1);

        MvcResult res = cancel(token, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        long after = orderRepository.countBookedOrdersForSlot(vendorProfileId, slot);
        assertThat(after).isEqualTo(before - 1);
    }

    @Test
    void suspendedVendorMayCancelInFlightOrder() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        // Suspend the vendor: D-6 says in-flight orders continue.
        jdbcTemplate.update("UPDATE vendor_profiles SET status = 'SUSPENDED' WHERE id = ?", vendorProfileId);

        String customerEmail = "cust-susp-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Susp Rose " + uuid(), new BigDecimal("10.00"));
        stockIn(productId, 1);
        reserve(productId, 1);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("10.00"), 1);

        MvcResult res = cancel(token, orderId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(res.getResponse().getContentAsString()).get("status").asText())
            .isEqualTo("CANCELLED");
    }

    // ------------------------------------------------------------------
    // Cancel vs accept race: the order row lock serialises the two
    // ------------------------------------------------------------------

    @Test
    void cancelAndAcceptRaceSerialisesOnTheOrderRow() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-race-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String productName = "Race Rose " + uuid();
        Long productId = createProduct(vendorEmail, productName, new BigDecimal("10.00"));
        stockIn(productId, 2);
        reserve(productId, 1);

        Long orderId = createOrder(customer, vendorProfileId, OrderStatus.PLACED,
                PaymentMethod.COD, PaymentStatus.PENDING, new BigDecimal("10.00"), 1);

        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        List<String> outcomes = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        pool.submit(() -> {
            try {
                ready.countDown();
                start.await();
                cancellationService.cancel(customerEmail, ActorType.CUSTOMER, orderId, null);
                outcomes.add("cancel-ok");
            } catch (Throwable t) {
                failures.add(t);
                outcomes.add("cancel-" + t.getClass().getSimpleName());
            } finally {
                done.countDown();
            }
        });
        pool.submit(() -> {
            try {
                ready.countDown();
                start.await();
                vendorOrderService.accept(vendorEmail, orderId);
                outcomes.add("accept-ok");
            } catch (Throwable t) {
                failures.add(t);
                outcomes.add("accept-" + t.getClass().getSimpleName());
            } finally {
                done.countDown();
            }
        });

        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(failures).isEmpty();
        // Exactly one of the two transitions may succeed; the loser sees 409.
        long ok = outcomes.stream().filter(s -> s.endsWith("-ok")).count();
        assertThat(ok).isEqualTo(1);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isIn(OrderStatus.CANCELLED, OrderStatus.CONFIRMED);
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private MvcResult cancel(String token, Long orderId, String reason) throws Exception {
        com.flowerconnect.order.dto.CancelOrderRequest req =
                com.flowerconnect.order.dto.CancelOrderRequest.builder().reason(reason).build();
        return mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andReturn();
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

    private void stockIn(Long productId, int qty) {
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        inventory.setQuantity(qty);
        inventory.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(inventory);
    }

    private void reserve(Long productId, int qty) {
        com.flowerconnect.inventory.domain.Inventory inventory =
                inventoryRepository.findByProductId(productId).orElseThrow();
        inventory.setReservedQuantity(qty);
        inventoryRepository.saveAndFlush(inventory);
    }

    private int quantity(Long productId) {
        return inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();
    }

    private int reserved(Long productId) {
        return inventoryRepository.findByProductId(productId).orElseThrow().getReservedQuantity();
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

    private void createPayment(Long orderId, com.flowerconnect.payment.domain.Payment.PaymentMethod method,
            com.flowerconnect.payment.domain.Payment.PaymentStatus status, BigDecimal amount) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        Payment payment = Payment.builder()
                .order(order)
                .method(method)
                .provider("simulated")
                .providerReference("int_sim_" + uuid())
                .amount(amount)
                .status(status)
                .paymentGroupId(uuid())
                .build();
        payments.saveAndFlush(payment);
    }

    private long stockMovements(Long productId) {
        return countMovements(productId);
    }

    private long countMovements(Long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE product_id = ?", Long.class, productId);
    }

    private List<com.flowerconnect.inventory.domain.StockMovement> releaseMovements(Long productId) {
        return jdbcTemplate.query(
                "SELECT * FROM stock_movements WHERE product_id = ? AND movement_type = 'RELEASE'",
                (rs, rowNum) -> com.flowerconnect.inventory.domain.StockMovement.builder()
                        .quantityDelta(rs.getInt("quantity_delta"))
                        .build(), productId);
    }

    private List<com.flowerconnect.inventory.domain.StockMovement> restockMovements(Long productId) {
        return jdbcTemplate.query(
                "SELECT * FROM stock_movements WHERE product_id = ? AND movement_type = 'RESTOCK'",
                (rs, rowNum) -> com.flowerconnect.inventory.domain.StockMovement.builder()
                        .quantityDelta(rs.getInt("quantity_delta"))
                        .build(), productId);
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
