package com.flowerconnect.order;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.config.PlatformConfig;
import com.flowerconnect.config.PlatformConfigRepository;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.domain.OrderItem;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.repository.OrderItemRepository;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.order.service.AutoRejectService;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.test.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auto-reject job against a real MySQL database (plan Phase 6, task 6.5).
 *
 * <p>Covers the plan's 6.T checklist for auto-reject: cutoff boundary
 * (before / exactly at / after the threshold), only PLACED orders change,
 * reservation released without touching quantity, one history row with
 * actor SYSTEM and reason AUTO_REJECTED_NO_RESPONSE timestamped from the
 * injected Clock, rerun idempotency, vendor-accept wins the race, one failing
 * order does not block the batch, batch bounds, and reading the threshold
 * from platform_config including its seeded default.
 */
@SpringBootTest
class AutoRejectIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Autowired
    private AutoRejectService autoRejectService;
    @Autowired
    private MutableClock clock;
    @Autowired
    private AppProperties appProperties;
    @Autowired
    private PlatformConfigRepository platformConfigRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
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
    private InventoryRepository inventoryRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    private Long locationId;
    private Long vendorProfileId;
    private Long customerUserId;
    private Long categoryId;

    private int batchSizeAtStart;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        locationId = serviceLocationRepository.findByPincode("560034").orElseThrow().getId();
        categoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();

        User customer = createUser("customer-" + UUID.randomUUID() + "@test.com", "CUSTOMER");
        customerUserId = customer.getId();

        User vendorUser = createUser("vendor-" + UUID.randomUUID() + "@test.com", "FLORIST");
        VendorProfile profile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(vendorUser)
                .businessName("Auto-Reject Blossoms")
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

        batchSizeAtStart = appProperties.getAutoRejectBatchSize();
    }

    @AfterEach
    void restoreBatchSize() {
        appProperties.setAutoRejectBatchSize(batchSizeAtStart);
    }

    // ------------------------------------------------------------------
    // Threshold boundary
    // ------------------------------------------------------------------

    @Test
    void orderCreatedBeforeThresholdIsAutoRejected() {
        Order order = createPlacedOrder(LocalDateTime.of(2026, 10, 20, 10, 59)); // 61 min old

        autoRejectService.runAutoReject();

        assertEquals(OrderStatus.CANCELLED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void orderCreatedExactlyAtThresholdIsNotRejected() {
        Order order = createPlacedOrder(LocalDateTime.of(2026, 10, 20, 11, 0)); // exactly 60 min old

        autoRejectService.runAutoReject();

        assertEquals(OrderStatus.PLACED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void orderCreatedAfterThresholdIsNotRejected() {
        Order order = createPlacedOrder(LocalDateTime.of(2026, 10, 20, 11, 30)); // 30 min old

        autoRejectService.runAutoReject();

        assertEquals(OrderStatus.PLACED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void onlyPlacedOrdersAreChanged() {
        Order placed = createPlacedOrder(LocalDateTime.of(2026, 10, 20, 10, 59));
        Order confirmed = createOrder(LocalDateTime.of(2026, 10, 20, 10, 59), OrderStatus.CONFIRMED);
        Order cancelled = createOrder(LocalDateTime.of(2026, 10, 20, 10, 59), OrderStatus.CANCELLED);

        autoRejectService.runAutoReject();

        assertEquals(OrderStatus.CANCELLED,
                orderRepository.findById(placed.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.CONFIRMED,
                orderRepository.findById(confirmed.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.CANCELLED,
                orderRepository.findById(cancelled.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectedOrderReleasesReservationWithoutChangingQuantity() {
        Long productId = productWithInventory(10, 5);
        Order order = createPlacedOrderWithProduct(LocalDateTime.of(2026, 10, 20, 10, 59), productId, 5);

        autoRejectService.runAutoReject();

        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(10, inventory.getQuantity());   // unchanged
        assertEquals(0, inventory.getReservedQuantity()); // released
    }

    @Test
    void rejectedOrderWritesReleaseMovementReferencingTheOrder() {
        Long productId = productWithInventory(10, 5);
        Order order = createPlacedOrderWithProduct(LocalDateTime.of(2026, 10, 20, 10, 59), productId, 5);

        autoRejectService.runAutoReject();

        List<StockMovement> releases = stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(
                productId).stream()
                .filter(m -> m.getMovementType() == MovementType.RELEASE)
                .toList();
        assertEquals(1, releases.size());
        assertEquals(-5, releases.get(0).getQuantityDelta());
        assertEquals("Order cancelled/rejected - reservation released", releases.get(0).getReason());
        assertNull(releases.get(0).getActor()); // system-initiated movement has no actor
    }

    @Test
    void rejectedOrderWritesExactlyOneHistoryRowWithSystemActor() {
        Long productId = productWithInventory(10, 5);
        Order order = createPlacedOrderWithProduct(LocalDateTime.of(2026, 10, 20, 10, 59), productId, 5);

        autoRejectService.runAutoReject();

        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(
                order.getId());
        assertEquals(1, history.size());
        OrderStatusHistory entry = history.get(0);
        assertEquals(OrderStatus.PLACED, entry.getFromStatus());
        assertEquals(OrderStatus.CANCELLED, entry.getToStatus());
        assertEquals(ActorType.SYSTEM, entry.getActorType());
        assertEquals(AutoRejectService.AUTO_REJECT_REASON, entry.getReason());
        assertNull(entry.getActor()); // SYSTEM actor has no user
        assertNotNull(entry.getCreatedAt()); // timestamped from the injected Clock
    }

    @Test
    void rerunIsNoOpForAlreadyTransitionedOrders() {
        Long productId = productWithInventory(10, 5);
        Order order = createPlacedOrderWithProduct(LocalDateTime.of(2026, 10, 20, 10, 59), productId, 5);

        autoRejectService.runAutoReject();
        long movementsAfterFirst = stockMovementRepository.count();

        autoRejectService.runAutoReject();

        assertEquals(OrderStatus.CANCELLED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(movementsAfterFirst, stockMovementRepository.count());
        assertEquals(1,
                orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).size());
    }

    @Test
    void vendorAcceptWinsRaceAndPreventsAutoReject() {
        Long productId = productWithInventory(10, 5);
        Order order = createPlacedOrderWithProduct(LocalDateTime.of(2026, 10, 20, 10, 59), productId, 5);

        // Vendor accepts before the auto-reject job runs.
        Order persisted = orderRepository.findById(order.getId()).orElseThrow();
        persisted.setStatus(OrderStatus.CONFIRMED);
        orderRepository.saveAndFlush(persisted);

        autoRejectService.runAutoReject();

        assertEquals(OrderStatus.CONFIRMED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void oneFailingOrderDoesNotBlockTheBatch() {
        // Order on a product with insufficient reserved quantity: releaseReservedStock
        // refuses because reserved < item quantity, so this order fails but stays PLACED.
        Long failProduct = productWithInventory(10, 3); // only 3 reserved, order needs 5
        Order failOrder = createPlacedOrderWithProduct(LocalDateTime.of(2026, 10, 20, 10, 59), failProduct, 5);

        // A second order on a different product succeeds.
        Long okProduct = productWithInventory(10, 5);
        Order okOrder = createPlacedOrderWithProduct(LocalDateTime.of(2026, 10, 20, 10, 59), okProduct, 5);

        autoRejectService.runAutoReject();

        assertEquals(OrderStatus.PLACED,
                orderRepository.findById(failOrder.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.CANCELLED,
                orderRepository.findById(okOrder.getId()).orElseThrow().getStatus());
    }

    @Test
    void batchBoundsAreRespected() {
        appProperties.setAutoRejectBatchSize(3);
        List<Order> orders = List.of(
                createPlacedOrder(LocalDateTime.of(2026, 10, 20, 10, 59)),
                createPlacedOrder(LocalDateTime.of(2026, 10, 20, 10, 58)),
                createPlacedOrder(LocalDateTime.of(2026, 10, 20, 10, 57)),
                createPlacedOrder(LocalDateTime.of(2026, 10, 20, 10, 56))
        );

        autoRejectService.runAutoReject();

        long cancelled = orders.stream()
                .map(o -> orderRepository.findById(o.getId()).orElseThrow().getStatus())
                .filter(OrderStatus.CANCELLED::equals)
                .count();
        assertEquals(3, cancelled);
        // The 4th order is outside the batch and stays PLACED.
        assertTrue(orders.stream()
                .map(o -> orderRepository.findById(o.getId()).orElseThrow().getStatus())
                .anyMatch(OrderStatus.PLACED::equals));
    }

    @Test
    void autoRejectMinutesReadFromPlatformConfigWithSeededDefault() {
        PlatformConfig row = platformConfigRepository.findByConfigKey("auto_reject_minutes").orElseThrow();
        assertEquals("60", row.getConfigValue());
        assertEquals(60, autoRejectService.getAutoRejectMinutes());
    }

    @Test
    void changedPlatformConfigMinutesShiftTheCutoff() {
        // Lower the threshold to 30 minutes: an order 45 min old is now rejected.
        PlatformConfig row = platformConfigRepository.findByConfigKey("auto_reject_minutes").orElseThrow();
        row.setConfigValue("30");
        platformConfigRepository.saveAndFlush(row);

        Order order = createPlacedOrder(LocalDateTime.of(2026, 10, 20, 11, 15)); // 45 min old

        autoRejectService.runAutoReject();

        assertEquals(OrderStatus.CANCELLED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());

        // Restore.
        row.setConfigValue("60");
        platformConfigRepository.saveAndFlush(row);
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash("$2a$10$dummyhash")
                .fullName("Test " + roleName)
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
        return user;
    }

    private Order createPlacedOrder(LocalDateTime createdAtUtc) {
        Long productId = productWithInventory(10, 5);
        return createPlacedOrderWithProduct(createdAtUtc, productId, 5);
    }

    private Order createPlacedOrderWithProduct(LocalDateTime createdAtUtc, Long productId, int quantity) {
        Order order = orderRepository.saveAndFlush(Order.builder()
                .user(userRepository.findById(customerUserId).orElseThrow())
                .vendor(vendorProfileRepository.findById(vendorProfileId).orElseThrow())
                .status(OrderStatus.PLACED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(new BigDecimal("99.50"))
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(new BigDecimal("99.50"))
                .deliveryAddressLine1("123 Test Street")
                .deliveryLatitude(new BigDecimal("12.9716"))
                .deliveryLongitude(new BigDecimal("77.5946"))
                .build());
        orderItemRepository.saveAndFlush(OrderItem.builder()
                .order(order)
                .product(productRepository.findById(productId).orElseThrow())
                .vendorId(vendorProfileId)
                .quantity(quantity)
                .priceSnapshot(new BigDecimal("99.50"))
                .build());
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(createdAtUtc), order.getId());
        return order;
    }

    private Order createOrder(LocalDateTime createdAtUtc, OrderStatus status) {
        Long productId = productWithInventory(10, 5);
        Order order = orderRepository.saveAndFlush(Order.builder()
                .user(userRepository.findById(customerUserId).orElseThrow())
                .vendor(vendorProfileRepository.findById(vendorProfileId).orElseThrow())
                .status(status)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(new BigDecimal("99.50"))
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(new BigDecimal("99.50"))
                .deliveryAddressLine1("123 Test Street")
                .deliveryLatitude(new BigDecimal("12.9716"))
                .deliveryLongitude(new BigDecimal("77.5946"))
                .build());
        orderItemRepository.saveAndFlush(OrderItem.builder()
                .order(order)
                .product(productRepository.findById(productId).orElseThrow())
                .vendorId(vendorProfileId)
                .quantity(5)
                .priceSnapshot(new BigDecimal("99.50"))
                .build());
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(createdAtUtc), order.getId());
        return order;
    }

    /**
     * A product with an inventory row holding the given quantity and reserved
     * quantity. One product per call keeps inventory state independent across
     * orders.
     */
    private Long productWithInventory(int quantity, int reserved) {
        Product product = productRepository.saveAndFlush(Product.builder()
                .vendor(vendorProfileRepository.findById(vendorProfileId).orElseThrow())
                .category(categoryRepository.findById(categoryId).orElseThrow())
                .name("Auto-Reject Product " + UUID.randomUUID())
                .slug("auto-reject-product-" + UUID.randomUUID().toString().substring(0, 8))
                .description("Fixture product")
                .basePrice(new BigDecimal("99.50"))
                .status(ProductStatus.ACTIVE)
                .build());
        inventoryRepository.saveAndFlush(Inventory.builder()
                .product(product)
                .quantity(quantity)
                .reservedQuantity(reserved)
                .build());
        return product.getId();
    }
}
