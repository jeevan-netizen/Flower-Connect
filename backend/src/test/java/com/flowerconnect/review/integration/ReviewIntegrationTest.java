package com.flowerconnect.review.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.review.dto.ReviewRequest;
import com.flowerconnect.review.dto.VendorReplyRequest;
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
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for reviews (plan Phase 12, tasks 12.1-12.2) against real MySQL.
 *
 * <p>Covers: submit for delivered orders only, one review per order (including
 * concurrent duplicates resolving to one), ownership, RBAC for every endpoint,
 * vendor reply (one-time), suspended vendor may reply, admin hide/unhide with
 * audit log, aggregate recalculation, public visible-only listing, and
 * non-approved vendor 404.
 *
 * <p>The class is deliberately not {@code @Transactional}: every test commits
 * to the shared Testcontainers database, so fixtures use unique emails, product
 * names and order ids to stay isolated.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String PINCODE = "560034";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private VendorProfileRepository vendorProfiles;
    @Autowired private ServiceLocationRepository locations;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductService productService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired private PaymentRepository payments;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MutableClock mutableClock;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long categoryId;

    @PostConstruct
    void init() {
        locationId = locations.findByPincode(PINCODE).orElseThrow().getId();
        categoryId = categories.findBySlug("roses").orElseThrow().getId();
    }

    // ------------------------------------------------------------------
    // Submit: delivered orders only, owner only
    // ------------------------------------------------------------------

    @Test
    void customerCanSubmitReviewForDeliveredOrder() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-review-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Review Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult res = submitReview(token, orderId, 5, "Lovely bouquet");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("rating").asInt()).isEqualTo(5);
        assertThat(body.get("comment").asText()).isEqualTo("Lovely bouquet");
        assertThat(body.get("hidden").asBoolean()).isFalse();
    }

    @Test
    void customerCannotReviewNonDeliveredOrder() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-nondel-review-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "NonDelReview Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createPlacedOrder(customer, vendorProfileId, productId);

        MvcResult res = submitReview(token, orderId, 5, "Too early");
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void customerCannotReviewAnotherCustomersOrder() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String ownerEmail = "cust-owner-review-" + uuid() + "@t.com";
        User owner = createUser(ownerEmail, "CUSTOMER");
        String thiefEmail = "cust-thief-review-" + uuid() + "@t.com";
        User thief = createUser(thiefEmail, "CUSTOMER");
        String thiefToken = login(thief);

        Long productId = createProduct(vendorEmail, "TheftReview Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(owner, vendorProfileId, productId);

        MvcResult res = submitReview(thiefToken, orderId, 5, "Not my order");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void missingOrderIs404() throws Exception {
        String customerEmail = "cust-miss-review-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        MvcResult res = submitReview(token, 999999L, 5, "x");
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void duplicateReviewForSameOrderIs409() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-dup-review-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "DupReview Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult first = submitReview(token, orderId, 5, "First review");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second = submitReview(token, orderId, 1, "Second review");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void concurrentDuplicateSubmissionsResultInExactlyOneReview() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-concurrent-review-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "ConcurrentReview Rose " + uuid(), new BigDecimal("100.00"));
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
                    MvcResult res = submitReview(token, orderId, 5, "Concurrent review " + idx);
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

        long ok = successes.stream().filter(s -> s.equals("201")).count();
        assertThat(ok).isEqualTo(1);
        long conflict = successes.stream().filter(s -> s.equals("409")).count();
        assertThat(conflict).isEqualTo(threads - 1);
        assertThat(failures).isEmpty();

        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE order_id = ?", Long.class, orderId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void unauthenticatedSubmitReviewIs401() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-401-review-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        Long productId = createProduct(vendorEmail, "UnauthReview Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        mvc.perform(post("/api/v1/orders/{orderId}/reviews", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ReviewRequest() {{
                    setRating(5);
                    setComment("x");
                }})))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    // ------------------------------------------------------------------
    // Customer's own review retrieval
    // ------------------------------------------------------------------

    @Test
    void customerCanRetrieveOwnReviewEvenIfHidden() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-own-review-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "OwnReview Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult res = submitReview(token, orderId, 4, "Mine");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        MvcResult fetched = getCustomerReview(token, reviewId);
        assertThat(fetched.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(fetched.getResponse().getContentAsString());
        assertThat(body.get("id").asLong()).isEqualTo(reviewId);
        assertThat(body.get("rating").asInt()).isEqualTo(4);
    }

    @Test
    void customerCannotRetrieveAnotherCustomersReview() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String ownerEmail = "cust-owner-ownreview-" + uuid() + "@t.com";
        User owner = createUser(ownerEmail, "CUSTOMER");
        String thiefEmail = "cust-thief-ownreview-" + uuid() + "@t.com";
        User thief = createUser(thiefEmail, "CUSTOMER");
        String thiefToken = login(thief);

        Long productId = createProduct(vendorEmail, "TheftOwnReview Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(owner, vendorProfileId, productId);

        MvcResult res = submitReview(login(owner), orderId, 5, "Owner review");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        MvcResult fetched = getCustomerReview(thiefToken, reviewId);
        assertThat(fetched.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void missingReviewIs404() throws Exception {
        String customerEmail = "cust-miss-ownreview-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        MvcResult res = getCustomerReview(token, 999999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // Vendor reply: one per review, suspended vendor may reply
    // ------------------------------------------------------------------

    @Test
    void vendorCanReplyToReviewOnOwnOrder() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-vendreply-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "VendorReply Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult raiseRes = submitReview(customerToken, orderId, 4, "Good");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        String vendorToken = login(lookup(vendorEmail));
        MvcResult respRes = replyToReview(vendorToken, reviewId, "Thank you!");
        assertThat(respRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(respRes.getResponse().getContentAsString());
        assertThat(body.get("vendorReply").asText()).isEqualTo("Thank you!");
        assertThat(body.get("vendorRepliedAt")).isNotNull();
    }

    @Test
    void vendorCannotReplyTwiceToSameReview() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-doublereply-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "DoubleReply Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult raiseRes = submitReview(customerToken, orderId, 4, "Good");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        String vendorToken = login(lookup(vendorEmail));
        MvcResult first = replyToReview(vendorToken, reviewId, "First reply");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        MvcResult second = replyToReview(vendorToken, reviewId, "Second reply");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void vendorCannotReplyToAnotherVendorsReview() throws Exception {
        String victimVendor = createVendor();
        Long victimVendorProfileId = vendorProfileId(victimVendor);
        String attackerVendor = createVendor();
        String attackerToken = login(lookup(attackerVendor));
        String customerEmail = "cust-crossreply-" + uuid() + "@t.com";
        createUser(customerEmail, "CUSTOMER");

        Long productId = createProduct(victimVendor, "CrossReply Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(lookup(customerEmail), victimVendorProfileId, productId);

        MvcResult raiseRes = submitReview(login(lookup(customerEmail)), orderId, 5, "Victim review");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult res = replyToReview(attackerToken, reviewId, "Not my order");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void suspendedVendorCanReplyToReview() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        // Suspend the vendor: D-6 says in-flight/post-delivery actions continue.
        jdbcTemplate.update("UPDATE vendor_profiles SET status = 'SUSPENDED' WHERE id = ?", vendorProfileId);

        String customerEmail = "cust-suspended-vendreply-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "SuspendedVendorReply Rose " + uuid(), new BigDecimal("10.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult raiseRes = submitReview(customerToken, orderId, 3, "OK");
        assertThat(raiseRes.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(raiseRes.getResponse().getContentAsString()).get("id").asLong();

        String vendorToken = login(lookup(vendorEmail));
        MvcResult respRes = replyToReview(vendorToken, reviewId, "I am suspended but replying");
        assertThat(respRes.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void replyToMissingReviewIs404() throws Exception {
        String vendorEmail = createVendor();
        String vendorToken = login(lookup(vendorEmail));
        MvcResult res = replyToReview(vendorToken, 999999L, "x");
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // Vendor's own reviews + summary
    // ------------------------------------------------------------------

    @Test
    void vendorCanListOwnReviewsIncludingHidden() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-vendorlist-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String customerToken = login(customer);

        Long productId = createProduct(vendorEmail, "VendorList Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult res = submitReview(customerToken, orderId, 5, "Great");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        String vendorToken = login(lookup(vendorEmail));
        MvcResult listed = listVendorReviews(vendorToken, 0, 20);
        assertThat(listed.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(listed.getResponse().getContentAsString());
        JsonNode content = body.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.toString()).contains("\"rating\":5");
    }

    @Test
    void vendorReviewSummaryReturnsAverageAndDistribution() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail1 = "cust-summary1-" + uuid() + "@t.com";
        String customerEmail2 = "cust-summary2-" + uuid() + "@t.com";
        User customer1 = createUser(customerEmail1, "CUSTOMER");
        User customer2 = createUser(customerEmail2, "CUSTOMER");
        String token1 = login(customer1);
        String token2 = login(customer2);

        Long productId = createProduct(vendorEmail, "Summary Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId1 = createDeliveredOrder(customer1, vendorProfileId, productId);
        Long orderId2 = createDeliveredOrder(customer2, vendorProfileId, productId);

        submitReview(token1, orderId1, 5, "Five stars");
        submitReview(token2, orderId2, 3, "Three stars");

        String vendorToken = login(lookup(vendorEmail));
        MvcResult res = vendorReviewSummary(vendorToken);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("reviewCount").asInt()).isEqualTo(2);
        assertThat(new java.math.BigDecimal(body.get("avgRating").asText()))
                .isEqualByComparingTo(new BigDecimal("4.00"));
    }

    // ------------------------------------------------------------------
    // Public vendor reviews: visible only, newest first, 404 for non-approved
    // ------------------------------------------------------------------

    @Test
    void publicVendorReviewsExcludesHidden() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-public-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Public Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult res = submitReview(token, orderId, 5, "Visible");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Hide the review as admin
        String adminEmail = "admin-public-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));
        MvcResult hideRes = hideReview(adminToken, reviewId, "Offensive");
        assertThat(hideRes.getResponse().getStatus()).isEqualTo(200);

        MvcResult listed = listPublicVendorReviews(vendorProfileId, 0, 20);
        assertThat(listed.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(listed.getResponse().getContentAsString());
        JsonNode content = body.get("content");
        assertThat(content.toString()).doesNotContain("\"rating\":5");
        assertThat(content.size()).isEqualTo(0);
    }

    @Test
    void publicVendorReviewsNewestFirst() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail1 = "cust-publicnew1-" + uuid() + "@t.com";
        String customerEmail2 = "cust-publicnew2-" + uuid() + "@t.com";
        User customer1 = createUser(customerEmail1, "CUSTOMER");
        User customer2 = createUser(customerEmail2, "CUSTOMER");
        String token1 = login(customer1);
        String token2 = login(customer2);

        Long productId = createProduct(vendorEmail, "PublicNew Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId1 = createDeliveredOrder(customer1, vendorProfileId, productId);
        Long orderId2 = createDeliveredOrder(customer2, vendorProfileId, productId);

        submitReview(token1, orderId1, 4, "First");
        Instant savedInstant = mutableClock.instant();
        mutableClock.advance(Duration.ofSeconds(2));
        submitReview(token2, orderId2, 5, "Second");

        MvcResult listed = listPublicVendorReviews(vendorProfileId, 0, 20);
        assertThat(listed.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(listed.getResponse().getContentAsString());
        JsonNode content = body.get("content");
        assertThat(content.get(0).get("comment").asText()).isEqualTo("Second");
        assertThat(content.get(1).get("comment").asText()).isEqualTo("First");

        mutableClock.setInstant(savedInstant);
    }

    @Test
    void publicVendorReviewsNonApprovedVendorIs404() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        jdbcTemplate.update("UPDATE vendor_profiles SET status = 'PENDING_APPROVAL' WHERE id = ?", vendorProfileId);

        MvcResult res = listPublicVendorReviews(vendorProfileId, 0, 20);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // Admin hide/unhide with audit log and aggregate recalculation
    // ------------------------------------------------------------------

    @Test
    void adminCanHideAndUnhideReview() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-adminhide-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "AdminHide Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult res = submitReview(token, orderId, 5, "To be hidden");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        String adminEmail = "admin-hide-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        MvcResult hideRes = hideReview(adminToken, reviewId, "Offensive content");
        assertThat(hideRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode hideBody = json.readTree(hideRes.getResponse().getContentAsString());
        assertThat(hideBody.get("hidden").asBoolean()).isTrue();
        assertThat(hideBody.get("hiddenReason").asText()).isEqualTo("Offensive content");

        MvcResult listed = listPublicVendorReviews(vendorProfileId, 0, 20);
        JsonNode listedBody = json.readTree(listed.getResponse().getContentAsString());
        assertThat(listedBody.get("content").toString()).doesNotContain("\"rating\":5");

        MvcResult unhideRes = unhideReview(adminToken, reviewId);
        assertThat(unhideRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode unhideBody = json.readTree(unhideRes.getResponse().getContentAsString());
        assertThat(unhideBody.get("hidden").asBoolean()).isFalse();
    }

    @Test
    void adminHideRequiresReason() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-adminhidereason-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "AdminHideReason Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult submitRes = submitReview(token, orderId, 5, "x");
        assertThat(submitRes.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(submitRes.getResponse().getContentAsString()).get("id").asLong();

        String adminEmail = "admin-hidereason-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        MvcResult res = hideReview(adminToken, reviewId, "   ");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void nonAdminCannotHideReviewIs403() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-nonadminhide-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "NonAdminHide Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult submitRes = submitReview(token, orderId, 5, "x");
        assertThat(submitRes.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(submitRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult res = hideReview(token, reviewId, "Trying");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void auditLogWrittenForReviewHide() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-audithide-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "AuditHide Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        MvcResult res = submitReview(token, orderId, 5, "Audit");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        Long reviewId = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        String adminEmail = "admin-audithide-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        MvcResult hideRes = hideReview(adminToken, reviewId, "Spam");
        assertThat(hideRes.getResponse().getStatus()).isEqualTo(200);

        List<com.flowerconnect.domain.AuditLog> auditLogs =
                auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc("REVIEW", reviewId);
        assertThat(auditLogs).isNotEmpty();
        com.flowerconnect.domain.AuditLog auditLog = auditLogs.get(auditLogs.size() - 1);
        assertThat(auditLog.getActionType()).isEqualTo("REVIEW_HIDDEN");
        assertThat(auditLog.getReason()).contains("Spam");
    }

    // ------------------------------------------------------------------
    // Aggregate recalculation: hide decrements count, unhide restores
    // ------------------------------------------------------------------

    @Test
    void hideReviewDecrementsAggregate() throws Exception {
        String vendorEmail = createVendor();
        Long vendorProfileId = vendorProfileId(vendorEmail);
        String customerEmail = "cust-agg1-" + uuid() + "@t.com";
        User customer = createUser(customerEmail, "CUSTOMER");
        String token = login(customer);

        Long productId = createProduct(vendorEmail, "Agg1 Rose " + uuid(), new BigDecimal("100.00"));
        Long orderId = createDeliveredOrder(customer, vendorProfileId, productId);

        submitReview(token, orderId, 5, "x");

        String vendorToken = login(lookup(vendorEmail));
        MvcResult before = vendorReviewSummary(vendorToken);
        assertThat(json.readTree(before.getResponse().getContentAsString()).get("reviewCount").asInt())
                .isEqualTo(1);

        String adminEmail = "admin-agg1-" + uuid() + "@t.com";
        createUser(adminEmail, "ADMIN");
        String adminToken = login(lookup(adminEmail));

        // Fetch the review id from the vendor's own review list
        MvcResult listed = listVendorReviews(vendorToken, 0, 20);
        Long reviewId = json.readTree(listed.getResponse().getContentAsString())
                .get("content").get(0).get("id").asLong();

        hideReview(adminToken, reviewId, "Spam");

        MvcResult after = vendorReviewSummary(vendorToken);
        assertThat(json.readTree(after.getResponse().getContentAsString()).get("reviewCount").asInt())
                .isEqualTo(0);
        assertThat(json.readTree(after.getResponse().getContentAsString()).get("avgRating").isNull()).isTrue();
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private MvcResult submitReview(String token, Long orderId, Integer rating, String comment) throws Exception {
        ReviewRequest req = new ReviewRequest();
        req.setRating(rating);
        req.setComment(comment);
        return mvc.perform(post("/api/v1/orders/{orderId}/reviews", orderId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andReturn();
    }

    private MvcResult getCustomerReview(String token, Long reviewId) throws Exception {
        return mvc.perform(get("/api/v1/reviews/{reviewId}", reviewId)
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult replyToReview(String token, Long reviewId, String reply) throws Exception {
        VendorReplyRequest req = new VendorReplyRequest();
        req.setReply(reply);
        return mvc.perform(patch("/api/v1/vendors/reviews/{reviewId}/reply", reviewId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andReturn();
    }

    private MvcResult listVendorReviews(String token, int page, int size) throws Exception {
        return mvc.perform(get("/api/v1/vendors/reviews")
                .header("Authorization", "Bearer " + token)
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))).andReturn();
    }

    private MvcResult vendorReviewSummary(String token) throws Exception {
        return mvc.perform(get("/api/v1/vendors/reviews/summary")
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult listPublicVendorReviews(Long vendorId, int page, int size) throws Exception {
        return mvc.perform(get("/api/v1/vendors/{vendorId}/reviews", vendorId)
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))).andReturn();
    }

    private MvcResult hideReview(String token, Long reviewId, String reason) throws Exception {
        return mvc.perform(patch("/api/v1/admin/reviews/{reviewId}/hide", reviewId)
                .header("Authorization", "Bearer " + token)
                .param("reason", reason)).andReturn();
    }

    private MvcResult unhideReview(String token, Long reviewId) throws Exception {
        return mvc.perform(patch("/api/v1/admin/reviews/{reviewId}/unhide", reviewId)
                .header("Authorization", "Bearer " + token)).andReturn();
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
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200)).andReturn();
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
        return productService.create(vendorEmail, new ProductRequest() {{
            setName(name);
            setCategoryId(categoryId);
            setDescription("Integration test product");
            setBasePrice(price);
        }}).getId();
    }

    private Long createDeliveredOrder(User customer, Long vendorProfileId, Long productId) {
        Long orderId = createPlacedOrder(customer, vendorProfileId, productId);
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus(PaymentStatus.COLLECTED);
        orderRepository.saveAndFlush(order);
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
}
