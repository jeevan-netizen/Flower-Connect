package com.flowerconnect.inventory;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductPageResponse;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import com.flowerconnect.inventory.dto.ExpiryDateRequest;
import com.flowerconnect.inventory.dto.StockInRequest;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.inventory.service.InventoryExpiryService;
import com.flowerconnect.inventory.service.InventoryExpiryService.SweepResult;
import com.flowerconnect.inventory.service.InventoryService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The expiry sweep against a real MySQL database (plan task 3.7).
 *
 * <p>Everything asserted here is what a mocked repository cannot show: which rows
 * {@code expiryDate < :today AND (quantity > reserved OR status = ACTIVE)} actually selects
 * against real SQL (including the ENUM comparison on {@code status}, D-20), that the
 * locked re-read and the write-off commit together, that the {@code WASTE} movement lands in
 * the append-only log with a null actor, that {@code reserved_quantity <= quantity} survives
 * the write-off, and that a second sweep has nothing left to do.
 *
 * <p>The clock is the {@link MutableClock} bean, so "tomorrow" is reachable without waiting
 * for it. It and the batch cap are restored after every test: the context is cached and
 * shared with the rest of the integration suite.
 */
@SpringBootTest
class InventoryExpiryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryExpiryService expiryService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private ProductRepository productRepository;
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
    private MutableClock clock;
    @Autowired
    private AppProperties appProperties;

    private String vendorEmail;
    private Instant clockAtStart;
    private int maxRowsAtStart;

    @BeforeEach
    void setUp() {
        vendorEmail = createVendor();
        clockAtStart = clock.instant();
        maxRowsAtStart = appProperties.getExpirySweepMaxRows();
    }

    @AfterEach
    void restoreClockAndBatchCap() {
        clock.setInstant(clockAtStart);
        appProperties.setExpirySweepMaxRows(maxRowsAtStart);
    }

    // ------------------------------------------------------------------
    // The expiry date
    // ------------------------------------------------------------------

    @Test
    void stockIsWrittenOffOnceTheExpiryDateHasPassedAndTheProductIsDelisted() {
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        stockIn(productId, 10);
        setExpiry(productId, LocalDate.now(clock).minusDays(1));

        SweepResult result = expiryService.sweepExpiredStock();

        Inventory inventory = inventoryOf(productId);
        assertEquals(0, inventory.getQuantity());
        assertEquals(0, inventory.getAvailable());

        StockMovement waste = wasteMovementOf(productId);
        assertEquals(MovementType.WASTE, waste.getMovementType());
        assertEquals(-10, waste.getQuantityDelta());
        // A scheduled job has no authenticated user behind it.
        assertNull(waste.getActor());
        assertNull(waste.getReferenceId());
        assertNull(waste.getReferenceType());
        assertEquals("Expired stock write-off: expiry date " + LocalDate.now(clock).minusDays(1)
                + " has passed (sweep date " + LocalDate.now(clock) + ")", waste.getReason());

        assertEquals(ProductStatus.INACTIVE, productOf(productId).getStatus());
        assertEquals(1, result.productsWrittenOff());
        assertEquals(1, result.productsDelisted());
    }

    @Test
    void stockIsStillUsableOnItsExpiryDateItself() {
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        stockIn(productId, 10);
        // The stored date is the last usable day, so nothing happens on it.
        setExpiry(productId, LocalDate.now(clock));

        expiryService.sweepExpiredStock();

        assertEquals(10, inventoryOf(productId).getQuantity());
        assertEquals(ProductStatus.ACTIVE, productOf(productId).getStatus());
        assertEquals(0, wasteMovementsOf(productId));
    }

    @Test
    void stockIsUntouchedBeforeItsExpiryDate() {
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        stockIn(productId, 10);
        setExpiry(productId, LocalDate.now(clock).plusDays(1));

        expiryService.sweepExpiredStock();

        assertEquals(10, inventoryOf(productId).getQuantity());
        assertEquals(ProductStatus.ACTIVE, productOf(productId).getStatus());
        assertEquals(0, wasteMovementsOf(productId));
    }

    @Test
    void aProductWithoutAnExpiryDateIsNeverSwept() {
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        stockIn(productId, 10);

        expiryService.sweepExpiredStock();

        assertEquals(10, inventoryOf(productId).getQuantity());
        assertEquals(ProductStatus.ACTIVE, productOf(productId).getStatus());
        assertEquals(0, wasteMovementsOf(productId));
    }

    @Test
    void theSweepFollowsTheInjectedClock() {
        // Four days before the expiry date, the day before it, then the day after it.
        clock.setInstant(Instant.parse("2026-10-01T09:00:00Z"));
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        stockIn(productId, 10);
        setExpiry(productId, LocalDate.of(2026, 10, 5));

        expiryService.sweepExpiredStock();
        assertEquals(10, inventoryOf(productId).getQuantity());
        assertEquals(ProductStatus.ACTIVE, productOf(productId).getStatus());

        clock.setInstant(Instant.parse("2026-10-04T11:00:00Z"));
        expiryService.sweepExpiredStock();
        assertEquals(10, inventoryOf(productId).getQuantity());

        clock.setInstant(Instant.parse("2026-10-06T09:00:00Z"));
        expiryService.sweepExpiredStock();
        assertEquals(0, inventoryOf(productId).getQuantity());
        assertEquals(ProductStatus.INACTIVE, productOf(productId).getStatus());
    }

    // ------------------------------------------------------------------
    // Reserved stock
    // ------------------------------------------------------------------

    @Test
    void reservedUnitsSurviveTheWriteOff() {
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        stockIn(productId, 10);
        // No route in this phase writes reserved_quantity (RESERVE arrives with checkout),
        // so the floor is set directly.
        jdbcTemplate.update("UPDATE inventory SET reserved_quantity = 4 WHERE product_id = ?", productId);
        setExpiry(productId, LocalDate.now(clock).minusDays(1));

        expiryService.sweepExpiredStock();

        Inventory inventory = inventoryOf(productId);
        assertEquals(4, inventory.getQuantity());
        assertEquals(4, inventory.getReservedQuantity());
        assertEquals(0, inventory.getAvailable());
        // Only the six sellable units are waste; the four promised units stay.
        assertEquals(-6, wasteMovementOf(productId).getQuantityDelta());
        assertEquals(ProductStatus.INACTIVE, productOf(productId).getStatus());
    }

    @Test
    void anExpiredProductWithNothingAvailableIsDelistedWithoutAMovement() {
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        setExpiry(productId, LocalDate.now(clock).minusDays(1));

        expiryService.sweepExpiredStock();

        assertEquals(0, inventoryOf(productId).getQuantity());
        assertEquals(ProductStatus.INACTIVE, productOf(productId).getStatus());
        // A movement with a zero delta would record a change that never happened.
        assertEquals(0, wasteMovementsOf(productId));
    }

    // ------------------------------------------------------------------
    // Idempotency and listing
    // ------------------------------------------------------------------

    @Test
    void aSecondSweepHasNothingLeftToDo() {
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        stockIn(productId, 10);
        setExpiry(productId, LocalDate.now(clock).minusDays(1));

        expiryService.sweepExpiredStock();
        long movementsAfterFirstSweep = stockMovementRepository.countByProductId(productId);

        SweepResult second = expiryService.sweepExpiredStock();

        assertEquals(0, second.productsWrittenOff());
        assertEquals(0, second.productsDelisted());
        assertEquals(movementsAfterFirstSweep, stockMovementRepository.countByProductId(productId));
        assertEquals(0, inventoryOf(productId).getQuantity());
        assertEquals(ProductStatus.INACTIVE, productOf(productId).getStatus());
    }

    @Test
    void aReactivatedExpiredProductIsTakenOutOfTheStorefrontAgain() {
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        stockIn(productId, 10);
        setExpiry(productId, LocalDate.now(clock).minusDays(1));
        expiryService.sweepExpiredStock();

        // The vendor restocks and relists without replacing the expiry date.
        inventoryService.stockIn(vendorEmail, productId,
                StockInRequest.builder().quantity(4).reason("restock").build());
        setStatus(productId, ProductStatus.ACTIVE);
        assertTrue(activeProductIds().contains(productId));

        expiryService.sweepExpiredStock();

        assertEquals(0, inventoryOf(productId).getQuantity());
        assertEquals(ProductStatus.INACTIVE, productOf(productId).getStatus());
    }

    @Test
    void aDelistedProductLeavesTheActiveListing() {
        Long productId = productWithStatus(ProductStatus.ACTIVE);
        stockIn(productId, 10);
        setExpiry(productId, LocalDate.now(clock).minusDays(1));
        assertTrue(activeProductIds().contains(productId));

        expiryService.sweepExpiredStock();

        assertFalse(activeProductIds().contains(productId), "the expired product is still listed as active");
        assertTrue(inactiveProductIds().contains(productId), "the expired product is not listed as inactive");
    }

    @Test
    void aDraftProductLosesItsExpiredStockButKeepsItsStatus() {
        Long productId = productWithStatus(ProductStatus.DRAFT);
        stockIn(productId, 6);
        setExpiry(productId, LocalDate.now(clock).minusDays(1));

        expiryService.sweepExpiredStock();

        assertEquals(0, inventoryOf(productId).getQuantity());
        assertEquals(-6, wasteMovementOf(productId).getQuantityDelta());
        // Rewriting DRAFT to INACTIVE would report a change that did not happen.
        assertEquals(ProductStatus.DRAFT, productOf(productId).getStatus());
    }

    // ------------------------------------------------------------------
    // The batch cap
    // ------------------------------------------------------------------

    @Test
    void aBacklogLargerThanTheBatchCapIsFinishedByTheNextRun() {
        appProperties.setExpirySweepMaxRows(2);
        LocalDate expired = LocalDate.now(clock).minusDays(1);
        Long first = productWithStatus(ProductStatus.ACTIVE);
        Long second = productWithStatus(ProductStatus.ACTIVE);
        Long third = productWithStatus(ProductStatus.ACTIVE);
        for (Long productId : List.of(first, second, third)) {
            stockIn(productId, 5);
            setExpiry(productId, expired);
        }

        SweepResult firstSweep = expiryService.sweepExpiredStock();

        assertEquals(2, firstSweep.candidates());
        assertEquals(2, firstSweep.productsWrittenOff());
        // The third row is untouched: the cap bounds one run, it does not discard work.
        assertEquals(5, inventoryOf(third).getQuantity());

        SweepResult secondSweep = expiryService.sweepExpiredStock();

        assertEquals(1, secondSweep.candidates());
        assertEquals(1, secondSweep.productsWrittenOff());
        for (Long productId : List.of(first, second, third)) {
            assertEquals(0, inventoryOf(productId).getQuantity());
            assertEquals(ProductStatus.INACTIVE, productOf(productId).getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private List<Long> activeProductIds() {
        return productIdsWithStatus(ProductStatus.ACTIVE);
    }

    private List<Long> inactiveProductIds() {
        return productIdsWithStatus(ProductStatus.INACTIVE);
    }

    /**
     * Walks the vendor's own listing. Scoped to this vendor's products, so the rows other
     * integration classes have accumulated in the shared container cannot affect it.
     */
    private List<Long> productIdsWithStatus(ProductStatus status) {
        ProductPageResponse page = productService.list(vendorEmail, status, null, null, 0, 100);
        return page.getContent().stream().map(product -> product.getId()).toList();
    }

    private Inventory inventoryOf(Long productId) {
        return inventoryRepository.findByProductId(productId).orElseThrow();
    }

    private Product productOf(Long productId) {
        return productRepository.findById(productId).orElseThrow();
    }

    private StockMovement wasteMovementOf(Long productId) {
        List<StockMovement> movements =
                stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
        assertNotNull(movements, "no movements recorded for product " + productId);
        return movements.stream()
                .filter(movement -> movement.getMovementType() == MovementType.WASTE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no WASTE movement recorded for product " + productId));
    }

    private long wasteMovementsOf(Long productId) {
        return stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
                .filter(movement -> movement.getMovementType() == MovementType.WASTE)
                .count();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void stockIn(Long productId, int quantity) {
        inventoryService.stockIn(vendorEmail, productId,
                StockInRequest.builder().quantity(quantity).reason("opening stock").build());
    }

    private void setExpiry(Long productId, LocalDate expiryDate) {
        inventoryService.updateExpiryDate(vendorEmail, productId,
                ExpiryDateRequest.builder().expiryDate(expiryDate).build());
    }

    /**
     * A product of this vendor in the given status. The status is set directly because the
     * fixtures sit below the approval gate and the HTTP update path needs a whole request
     * payload; the sweep itself only ever reads the stored status.
     */
    private Long productWithStatus(ProductStatus status) {
        Long categoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        Long productId = productService.create(vendorEmail, ProductRequest.builder()
                        .name("Expiry Rose " + UUID.randomUUID())
                        .categoryId(categoryId)
                        .description("Perishable stems")
                        .basePrice(new BigDecimal("299.00"))
                        .build())
                .getId();
        setStatus(productId, status);
        return productId;
    }

    /**
     * One loaded instance for both the change and the save. {@code productRepository.findById}
     * outside a transaction returns a fresh detached entity each call, so
     * {@code findById(..).setStatus(x); saveAndFlush(findById(..))} writes back an untouched
     * copy and the product silently stays DRAFT.
     */
    private void setStatus(Long productId, ProductStatus status) {
        Product product = productOf(productId);
        product.setStatus(status);
        productRepository.saveAndFlush(product);
    }

    private String createVendor() {
        Role role = roleRepository.findByName("FLORIST").orElseThrow();
        User user = User.builder()
                .email("expiry-" + UUID.randomUUID() + "@test.com")
                .passwordHash("$2a$10$dummyhash")
                .fullName("Expiry Vendor Owner")
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        var location = serviceLocationRepository.findByPincode("560034").orElseThrow();
        vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(user)
                .businessName("Expiry Blossoms " + UUID.randomUUID())
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
}
