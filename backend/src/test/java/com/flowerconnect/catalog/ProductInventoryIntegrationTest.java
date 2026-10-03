package com.flowerconnect.catalog;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.ProductImage;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.dto.ProductResponse;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.UnexpectedRollbackException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the product / inventory data model
 * (plan tasks 3.2–3.4) against a real MySQL instance.
 *
 * <p>Covers the product ⇒ inventory creation invariant, the
 * no-initial-stock-movement rule, slug generation (sequential
 * and concurrent collision), and every database-level constraint
 * the migrations add: the slug unique key, the inventory CHECKs
 * and one-row-per-product rule, the stock movement type
 * vocabulary (a native ENUM), and the product foreign keys.
 * The one-primary-image rule is a service-level invariant
 * (D-21), so its read-side helper is exercised here instead.
 */
@SpringBootTest
class ProductInventoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductImageRepository productImageRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private EntityManager entityManager;

    private VendorProfile vendor;
    private String vendorEmail;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        vendor = createVendor();
        categoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
    }

    // ------------------------------------------------------------------
    // Product ⇒ inventory invariant (task 3.3)
    // ------------------------------------------------------------------

    @Test
    void creatingAProductCreatesItsInventoryRowAtZero() {
        ProductResponse response = productService.create(
                vendorEmail, request("Hybrid Tea"));

        Inventory inventory = inventoryRepository
                .findByProductId(response.getId()).orElseThrow();
        assertEquals(0, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(0, inventory.getLowStockThreshold());
        assertEquals(0, inventory.getAvailable());
        assertNull(inventory.getExpiryDate());
    }

    @Test
    void creatingAProductWritesNoStockMovement() {
        ProductResponse response = productService.create(
                vendorEmail, request("Quiet Rose"));

        assertEquals(0, stockMovementRepository.countByProductId(response.getId()));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE product_id = ?",
                Long.class, response.getId()));
    }

    // ------------------------------------------------------------------
    // Slug generation (task 3.2)
    // ------------------------------------------------------------------

    @Test
    void sequentialSlugCollisionsGetNumericSuffixes() {
        // The name is unique to this test: the integration
        // database accumulates rows across the whole run, so a
        // name another test already used would collide before
        // this test's first create and shift the whole sequence.
        ProductResponse first = productService.create(
                vendorEmail, request("Sequential Hybrid Tea"));
        ProductResponse second = productService.create(
                vendorEmail, request("Sequential Hybrid Tea"));
        ProductResponse third = productService.create(
                vendorEmail, request("Sequential Hybrid Tea"));

        assertEquals("sequential-hybrid-tea", first.getSlug());
        assertEquals("sequential-hybrid-tea-2", second.getSlug());
        assertEquals("sequential-hybrid-tea-3", third.getSlug());
    }

    @Test
    void concurrentCreatesWithTheSameNameAllSucceedWithUniqueSlugs() throws Exception {
        int threadCount = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<String> slugs = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ProductResponse response = createWithDeadlockRetry(
                            vendorEmail, request("Concurrent Rose"));
                    slugs.add(response.getSlug());
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS),
                "concurrent creates did not finish in time");
        pool.shutdownNow();

        assertTrue(failures.isEmpty(),
                () -> "concurrent creates failed: " + failures);
        assertEquals(threadCount, slugs.size());
        assertEquals(threadCount, slugs.stream().distinct().count(),
                "slugs must be unique under concurrency: " + slugs);
        assertTrue(slugs.contains("concurrent-rose"),
                "one create must win the base slug: " + slugs);
    }

    @Test
    void slugIsUniqueAtTheDatabaseLevel() {
        ProductResponse created = productService.create(
                vendorEmail, request("Unique Rose"));
        assertEquals("unique-rose", created.getSlug());

        assertConstraintViolation("uq_products_slug", () ->
                jdbcTemplate.update(
                        "INSERT INTO products (vendor_id, category_id, name, slug, base_price, status) "
                                + "VALUES (?, ?, ?, ?, ?, 'DRAFT')",
                        vendor.getId(), categoryId, "Another Rose",
                        "unique-rose", new BigDecimal("199.00")));
    }

    // ------------------------------------------------------------------
    // Inventory constraints (task 3.3)
    // ------------------------------------------------------------------

    @Test
    void inventoryRejectsANegativeQuantity() {
        Long productId = insertRawProduct("check-quantity-rose");
        assertConstraintViolation("ck_inventory_quantity", () ->
                jdbcTemplate.update(
                        "INSERT INTO inventory (product_id, quantity, reserved_quantity, low_stock_threshold) "
                                + "VALUES (?, -1, 0, 0)",
                        productId));
    }

    @Test
    void inventoryRejectsANegativeReservedQuantity() {
        Long productId = insertRawProduct("check-reserved-rose");
        assertConstraintViolation("ck_inventory_reserved", () ->
                jdbcTemplate.update(
                        "INSERT INTO inventory (product_id, quantity, reserved_quantity, low_stock_threshold) "
                                + "VALUES (?, 5, -1, 0)",
                        productId));
    }

    @Test
    void inventoryRejectsANegativeLowStockThreshold() {
        Long productId = insertRawProduct("check-threshold-rose");
        assertConstraintViolation("ck_inventory_threshold", () ->
                jdbcTemplate.update(
                        "INSERT INTO inventory (product_id, quantity, reserved_quantity, low_stock_threshold) "
                                + "VALUES (?, 5, 0, -1)",
                        productId));
    }

    @Test
    void inventoryRejectsReservedQuantityAboveQuantity() {
        Long productId = insertRawProduct("check-reserved-le-quantity-rose");
        assertConstraintViolation("ck_inventory_reserved_le_quantity", () ->
                jdbcTemplate.update(
                        "INSERT INTO inventory (product_id, quantity, reserved_quantity, low_stock_threshold) "
                                + "VALUES (?, 5, 6, 0)",
                        productId));
    }

    @Test
    void inventoryAllowsOnlyOneRowPerProduct() {
        Long productId = createProductId("Single Rose");
        assertConstraintViolation("uq_inventory_product", () ->
                jdbcTemplate.update(
                        "INSERT INTO inventory (product_id, quantity, reserved_quantity, low_stock_threshold) "
                                + "VALUES (?, 1, 0, 0)",
                        productId));
    }

    // ------------------------------------------------------------------
    // Product image constraints (task 3.2)
    // ------------------------------------------------------------------

    @Test
    void productImagesAreOrderedAndThePrimaryCountIsQueryable() {
        Long productId = createProductId("Cover Rose");

        // One primary image...
        jdbcTemplate.update(
                "INSERT INTO product_images (product_id, storage_key, mime_type, file_size, sort_order, is_primary) "
                        + "VALUES (?, 'cover/rose.jpg', 'image/jpeg', 1024, 0, b'1')",
                productId);
        // ...and any number of non-primary images, in order.
        jdbcTemplate.update(
                "INSERT INTO product_images (product_id, storage_key, mime_type, file_size, sort_order, is_primary) "
                        + "VALUES (?, 'cover/rose-side.jpg', 'image/jpeg', 2048, 1, b'0')",
                productId);
        jdbcTemplate.update(
                "INSERT INTO product_images (product_id, storage_key, mime_type, file_size, sort_order, is_primary) "
                        + "VALUES (?, 'cover/rose-bud.jpg', 'image/jpeg', 512, 2, b'0')",
                productId);

        // The read-side helper the service-level one-primary
        // check (D-21) is built on.
        assertEquals(1, productImageRepository.countByProductIdAndPrimaryIsTrue(productId));
        // Images come back in (sort_order, id) order.
        List<String> keys = productImageRepository
                .findByProductIdOrderBySortOrderAscIdAsc(productId)
                .stream()
                .map(ProductImage::getStorageKey)
                .toList();
        assertEquals(List.of("cover/rose.jpg", "cover/rose-side.jpg", "cover/rose-bud.jpg"), keys);
    }

    // ------------------------------------------------------------------
    // Stock movement constraints (task 3.4)
    // ------------------------------------------------------------------

    @Test
    void stockMovementsRejectAnUnknownMovementType() {
        Long productId = createProductId("Log Rose");
        // movement_type is a native ENUM column, so an unknown
        // value is rejected by the column type itself (MySQL
        // error 1265, "Data truncated for column ..."), not by
        // a named CHECK constraint.
        Exception thrown = assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO stock_movements (product_id, movement_type, quantity_delta) "
                                + "VALUES (?, 'BOGUS', 1)",
                        productId));
        String message = thrownMessageChain(thrown);
        assertTrue(message.contains("movement_type"),
                () -> "expected the ENUM column to reject the unknown movement type but got: " + message);
    }

    // ------------------------------------------------------------------
    // Product foreign keys (task 3.2)
    // ------------------------------------------------------------------

    @Test
    void productRequiresAKnownVendorAndCategory() {
        assertConstraintViolation("fk_products_vendor", () ->
                jdbcTemplate.update(
                        "INSERT INTO products (vendor_id, category_id, name, slug, base_price) "
                                + "VALUES (?, ?, 'Orphan Vendor Rose', 'orphan-vendor-rose', ?)",
                        999_999L, categoryId, new BigDecimal("100.00")));

        assertConstraintViolation("fk_products_category", () ->
                jdbcTemplate.update(
                        "INSERT INTO products (vendor_id, category_id, name, slug, base_price) "
                                + "VALUES (?, ?, 'Orphan Category Rose', 'orphan-category-rose', ?)",
                        vendor.getId(), 999_999L, new BigDecimal("100.00")));
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    void deletingAProductCascadesToImagesAndInventory() {
        ProductResponse product = productService.create(
                vendorEmail, request("Cascade Rose"));
        Long productId = product.getId();
        jdbcTemplate.update(
                "INSERT INTO product_images (product_id, storage_key, mime_type, file_size, sort_order, is_primary) "
                        + "VALUES (?, 'cascade/rose.jpg', 'image/jpeg', 1024, 0, b'1')",
                productId);

        productService.delete(vendorEmail, productId);

        assertTrue(productRepository.findById(productId).isEmpty());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_images WHERE product_id = ?",
                Long.class, productId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory WHERE product_id = ?",
                Long.class, productId));
    }

    @Test
    void updateRegeneratesTheSlugWhenTheNameChanges() {
        ProductResponse created = productService.create(
                vendorEmail, request("Original Bouquet"));
        assertEquals("original-bouquet", created.getSlug());

        ProductResponse updated = productService.update(
                vendorEmail, created.getId(), request("Renamed Bouquet"));

        assertEquals("renamed-bouquet", updated.getSlug());
        assertTrue(productRepository.findBySlug("original-bouquet").isEmpty());
    }

    @Test
    void updateAndDeleteRefuseAProductOwnedByAnotherVendor() {
        ProductResponse product = productService.create(
                vendorEmail, request("Foreign Rose"));
        // createVendor records the email of the profile it just
        // created in vendorEmail.
        VendorProfile otherVendor = createVendor();
        String otherVendorEmail = vendorEmail;

        BusinessException update = assertThrows(BusinessException.class, () ->
                productService.update(
                        otherVendorEmail, product.getId(), request("Stolen Rose")));
        assertEquals(ErrorCode.FORBIDDEN, update.getErrorCode());

        BusinessException delete = assertThrows(BusinessException.class, () ->
                productService.delete(otherVendorEmail, product.getId()));
        assertEquals(ErrorCode.FORBIDDEN, delete.getErrorCode());

        assertTrue(productRepository.findById(product.getId()).isPresent());
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private ProductRequest request(String name) {
        return ProductRequest.builder()
                .name(name)
                .categoryId(categoryId)
                .description("Fresh flowers")
                .basePrice(new BigDecimal("299.00"))
                .build();
    }

    private Long createProductId(String name) {
        return productService.create(vendorEmail, request(name)).getId();
    }

    /**
     * Runs one product create, retrying when MySQL elects this
     * transaction as a deadlock victim (error 1213). Several
     * transactions inserting the same slug at once take gap locks
     * on the unique index and can deadlock; InnoDB rolls the
     * losing transaction back, so the retry runs in a fresh
     * transaction. The duplicate-key race itself is handled
     * inside ProductService — this covers only the transient
     * deadlock, which MySQL's own error message tells the caller
     * to retry ("try restarting transaction").
     */
    private ProductResponse createWithDeadlockRetry(
            String vendorEmail, ProductRequest request) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return productService.create(vendorEmail, request);
            } catch (CannotAcquireLockException | UnexpectedRollbackException e) {
                if (attempt == 5) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /**
     * Inserts a product directly, bypassing the service, so the
     * row has no inventory row yet. Used to exercise the
     * inventory CHECK constraints without the one-row-per-product
     * unique key firing first.
     */
    private Long insertRawProduct(String slug) {
        jdbcTemplate.update(
                "INSERT INTO products (vendor_id, category_id, name, slug, base_price, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'DRAFT')",
                vendor.getId(), categoryId, "Raw " + slug, slug, new BigDecimal("100.00"));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE slug = ?", Long.class, slug);
    }

    private VendorProfile createVendor() {
        Role role = roleRepository.findByName("FLORIST").orElseThrow();
        User user = User.builder()
                .email("vendor-" + UUID.randomUUID() + "@test.com")
                .passwordHash("$2a$10$dummyhash")
                .fullName("Vendor Owner")
                .phone("+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);
        vendorEmail = user.getEmail();

        var location = serviceLocationRepository.findByPincode("560034").orElseThrow();
        VendorProfile profile = VendorProfile.builder()
                .user(user)
                .businessName("Test Blossoms " + UUID.randomUUID())
                .addressLine1("12 Test Street")
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(VendorProfile.Status.APPROVED)
                .reviewCount(0)
                .minOrderAmount(new BigDecimal("0.00"))
                .baseDeliveryFee(new BigDecimal("0.00"))
                .perKmFee(new BigDecimal("0.00"))
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build();
        return vendorProfileRepository.saveAndFlush(profile);
    }

    /**
     * Asserts that the given write is refused by the named MySQL
     * constraint. MySQL reports CHECK violations as SQL error 3819
     * and unique/foreign-key violations as error 1062/1452, so the
     * constraint name is asserted from the failure chain to prove
     * the intended rule fired and not some other one.
     */
    private void assertConstraintViolation(String constraintName, Executable write) {
        Exception thrown = assertThrows(Exception.class, write,
                () -> "expected the write to be rejected by " + constraintName);

        String message = thrownMessageChain(thrown);
        assertTrue(message.contains(constraintName),
                () -> "expected " + constraintName + " in the failure chain but got: " + message);
    }

    private static String thrownMessageChain(Throwable thrown) {
        StringBuilder chain = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            chain.append(current.getClass().getSimpleName()).append(": ")
                    .append(current.getMessage()).append(" | ");
        }
        return chain.toString();
    }
}
