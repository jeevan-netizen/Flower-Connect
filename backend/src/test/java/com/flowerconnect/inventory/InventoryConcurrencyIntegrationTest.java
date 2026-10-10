package com.flowerconnect.inventory;

import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.dto.LowStockThresholdRequest;
import com.flowerconnect.inventory.dto.StockAdjustmentRequest;
import com.flowerconnect.inventory.dto.StockInRequest;
import com.flowerconnect.inventory.dto.StockOutRequest;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.inventory.service.InventoryService;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency coverage for the inventory write path (plan task 3.6).
 *
 * <p>These tests exist because the pessimistic lock in
 * {@link InventoryRepository#findByProductIdForUpdate(Long)} is the only thing standing between
 * two simultaneous stock changes and a lost update, and a lock cannot be verified by a test that
 * never runs two transactions at once. Every assertion here is about the <em>end state</em>: what
 * the quantity is, and how many movements exist for it, after N threads have hammered the same
 * product.
 *
 * <p>The service is called directly rather than through HTTP so each thread gets a genuinely
 * separate transaction with no shared MockMvc or security context in the way; the transactions are
 * the same {@code @Transactional} methods the controller invokes. {@code @SpringBootTest} without
 * {@code @Transactional} is essential here — a test-managed transaction would hold the changes
 * uncommitted and hide exactly the interleaving being tested.
 *
 * <p>Nothing here is probabilistic: the threads are released together by a latch, the assertions are
 * on totals, and the outcome is the same whether they interleave perfectly or not.
 */
@SpringBootTest
class InventoryConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private ProductService productService;
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

    private String vendorEmail;
    private Long productId;

    @BeforeEach
    void setUp() {
        vendorEmail = createVendor();
        productId = createProduct();
    }

    // ------------------------------------------------------------------
    // Concurrent additions must all land
    // ------------------------------------------------------------------

    @Test
    void concurrentStockInsAllLandAndProduceOneMovementEach() throws Exception {
        int threads = 8;
        int perThread = 5;

        List<Throwable> failures = runConcurrently(threads, index -> {
            for (int i = 0; i < perThread; i++) {
                inventoryService.stockIn(vendorEmail, productId,
                        StockInRequest.builder().quantity(1).reason("concurrent probe").build());
            }
        });

        assertNoFailures(failures);
        // 8 threads x 5 units: a lost update would leave the total short.
        assertEquals(threads * perThread, quantity());
        // One movement per change, never zero and never two.
        assertEquals(threads * perThread, movements());
    }

    @Test
    void concurrentAdjustmentsAllLandAndProduceOneMovementEach() throws Exception {
        int threads = 8;
        int perThread = 4;

        inventoryService.stockIn(vendorEmail, productId,
                StockInRequest.builder().quantity(100).reason("opening stock").build());

        List<Throwable> failures = runConcurrently(threads, index -> {
            for (int i = 0; i < perThread; i++) {
                inventoryService.adjust(vendorEmail, productId,
                        StockAdjustmentRequest.builder()
                                .quantity(1)
                                .reason("concurrent adjustment " + index)
                                .build());
            }
        });

        assertNoFailures(failures);
        assertEquals(100 + threads * perThread, quantity());
        // The opening stock plus one movement per adjustment.
        assertEquals(1L + threads * perThread, movements());
    }

    @Test
    void mixedConcurrentSignsConvergeOnTheSumOfTheDeltas() throws Exception {
        int threads = 6;
        inventoryService.stockIn(vendorEmail, productId,
                StockInRequest.builder().quantity(1000).reason("opening stock").build());

        // Half add 10, half remove 10. Without the lock, interleaved
        // read-modify-write pairs lose half of each pair and the
        // final total drifts well away from 1000.
        List<Throwable> failures = runConcurrently(threads, index -> {
            int delta = index % 2 == 0 ? 10 : -10;
            inventoryService.adjust(vendorEmail, productId,
                    StockAdjustmentRequest.builder()
                            .quantity(delta)
                            .reason("concurrent mixed adjustment")
                            .build());
        });

        assertNoFailures(failures);
        assertEquals(1000, quantity());
        assertEquals(1L + threads, movements());
    }

    // ------------------------------------------------------------------
    // The reserved-quantity floor under concurrency
    // ------------------------------------------------------------------

    @Test
    void onlyOneOfManyConcurrentRemovalsOfTheSameUnitsSucceeds() throws Exception {
        int threads = 8;
        // One unit of stock, eight threads each trying to remove it.
        inventoryService.stockIn(vendorEmail, productId,
                StockInRequest.builder().quantity(1).reason("the last stem").build());

        List<Boolean> outcomes = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = runConcurrently(threads, index -> {
            try {
                inventoryService.stockOut(vendorEmail, productId,
                        StockOutRequest.builder().quantity(1).reason("concurrent removal").build());
                outcomes.add(Boolean.TRUE);
            } catch (BusinessException refused) {
                // Expected for all but one: the floor is enforced
                // against the level the lock just serialised.
                assertEquals(ErrorCode.INSUFFICIENT_STOCK, refused.getErrorCode());
                outcomes.add(Boolean.FALSE);
            }
        });

        assertNoFailures(failures);
        assertEquals(threads, outcomes.size());
        assertEquals(1L, outcomes.stream().filter(Boolean::booleanValue).count(),
                "exactly one thread may take the last unit: " + outcomes);
        assertEquals(0, quantity());
        assertEquals(2L, movements());
    }

    @Test
    void concurrentRemovalsNeverPushTheQuantityBelowTheReservedUnits() throws Exception {
        int threads = 8;
        inventoryService.stockIn(vendorEmail, productId,
                StockInRequest.builder().quantity(10).reason("opening stock").build());
        // Reserve 6 of the 10, directly: reservation arrives with
        // checkout in Phase 5 and has no route yet.
        assertEquals(1, jdbcTemplate.update(
                "UPDATE inventory SET reserved_quantity = 6 WHERE product_id = ?", productId));

        List<Boolean> outcomes = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = runConcurrently(threads, index -> {
            try {
                inventoryService.stockOut(vendorEmail, productId,
                        StockOutRequest.builder().quantity(2).reason("concurrent removal").build());
                outcomes.add(Boolean.TRUE);
            } catch (BusinessException refused) {
                assertEquals(ErrorCode.INSUFFICIENT_STOCK, refused.getErrorCode());
                outcomes.add(Boolean.FALSE);
            }
        });

        assertNoFailures(failures);
        // 10 units, 6 reserved: at most two removals of 2 can succeed,
        // and the level must never go below the reservation.
        assertEquals(2L, outcomes.stream().filter(Boolean::booleanValue).count(),
                "the floor should admit exactly two removals: " + outcomes);
        assertEquals(6, quantity());
        assertEquals(1L + 2L, movements());
    }

    @Test
    void concurrentThresholdWritesNeverLoseAConcurrentStockChange() throws Exception {
        int threads = 6;
        int stockIns = 3;

        runConcurrently(threads, index -> {
            if (index == 0) {
                for (int i = 0; i < stockIns; i++) {
                    inventoryService.stockIn(vendorEmail, productId,
                            StockInRequest.builder().quantity(5).reason("concurrent stock").build());
                }
            } else {
                inventoryService.updateLowStockThreshold(vendorEmail, productId,
                        LowStockThresholdRequest.builder()
                                .lowStockThreshold(index)
                                .build());
            }
        });

        // Hibernate issues whole-row updates, so an unlocked threshold
        // write would roll the quantity back to whatever it read before
        // the stock changes landed. Every stock-in must survive.
        assertEquals(5 * stockIns, quantity());
        assertEquals(stockIns, movements());
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /**
     * Releases {@code threads} real threads simultaneously against the same product and returns
     * whatever they threw. Sequential calls would not exercise the lock at all: each would see the
     * previous one's committed level, which is also why "it works when run one after another" is
     * not evidence that a lock is present.
     */
    private List<Throwable> runConcurrently(int threads, ThrowingTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            int index = i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    task.run(index);
                } catch (Throwable thrown) {
                    failures.add(thrown);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(30, TimeUnit.SECONDS), "worker threads did not come up");
        start.countDown();
        assertTrue(done.await(120, TimeUnit.SECONDS), "concurrent writes did not finish in time");
        pool.shutdownNow();
        return failures;
    }

    private void assertNoFailures(List<Throwable> failures) {
        assertTrue(failures.isEmpty(), () -> "concurrent writes failed: " + failures);
    }

    private int quantity() {
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        return inventory.getQuantity();
    }

    private long movements() {
        return stockMovementRepository.countByProductId(productId);
    }

    @FunctionalInterface
    private interface ThrowingTask {
        void run(int index) throws Exception;
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private String createVendor() {
        Role role = roleRepository.findByName("FLORIST").orElseThrow();
        User user = User.builder()
                .email("concurrency-" + UUID.randomUUID() + "@test.com")
                .passwordHash("$2a$10$dummyhash")
                .fullName("Vendor Owner")
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        var location = serviceLocationRepository.findByPincode("560034").orElseThrow();
        vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(user)
                .businessName("Concurrency Blossoms " + UUID.randomUUID())
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
                .build());

        return user.getEmail();
    }

    private Long createProduct() {
        Long categoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        return productService.create(vendorEmail, ProductRequest.builder()
                        .name("Concurrency Rose " + UUID.randomUUID())
                        .categoryId(categoryId)
                        .description("Fresh flowers")
                        .basePrice(new BigDecimal("299.00"))
                        .build())
                .getId();
    }
}