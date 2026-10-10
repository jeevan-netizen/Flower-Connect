package com.flowerconnect.inventory.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.inventory.service.InventoryExpiryService.SweepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the inventory expiry sweep (plan task 3.7).
 *
 * <p>What is asserted here is everything decidable without a database: the date the sweep
 * compares against (proving the {@link Clock} is the only source of "today"), the
 * candidate query it issues, the size of the batch it asks for, the delta and fields of
 * the movement it writes, that reserved units survive, that only {@code ACTIVE} products
 * are delisted, that every condition is re-checked under the lock, and that the rows are
 * locked in ascending product-id order.
 *
 * <p>Not asserted here, because a mocked repository cannot: which rows
 * {@code expiryDate < :today AND (quantity > reserved OR status = ACTIVE)} selects, that
 * the locking query really emits {@code SELECT ... FOR UPDATE}, and that the movement
 * survives a commit. Those live in {@code InventoryExpiryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class InventoryExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T02:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate EXPIRED = LocalDate.of(2026, 10, 3);

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private ProductRepository productRepository;

    private AppProperties appProperties;
    private InventoryExpiryService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        service = new InventoryExpiryService(inventoryRepository, stockMovementRepository, productRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), appProperties);
    }

    // ------------------------------------------------------------------
    // Which rows are considered, and when
    // ------------------------------------------------------------------

    @Test
    void theSweepComparesAgainstTheInjectedClockNotTheSystemDate() {
        stubCandidates();

        service.sweepExpiredStock();

        ArgumentCaptor<LocalDate> today = ArgumentCaptor.forClass(LocalDate.class);
        verify(inventoryRepository).findExpiredNeedingAction(today.capture(),
                eq(ProductStatus.ACTIVE), any(Pageable.class));
        assertThat(today.getValue()).isEqualTo(TODAY);
    }

    @Test
    void theBatchIsCappedByConfiguration() {
        appProperties.setExpirySweepMaxRows(2);
        stubCandidates();

        service.sweepExpiredStock();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryRepository).findExpiredNeedingAction(any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void aNonPositiveCapStillProcessesOneRowRatherThanDisablingTheJob() {
        appProperties.setExpirySweepMaxRows(0);
        stubCandidates();

        service.sweepExpiredStock();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryRepository).findExpiredNeedingAction(any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void aSweepWithNoCandidatesTakesNoLocksAndWritesNothing() {
        when(inventoryRepository.findExpiredNeedingAction(any(), any(), any())).thenReturn(List.of());

        SweepResult result = service.sweepExpiredStock();

        assertThat(result).isEqualTo(SweepResult.empty());
        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
        verify(stockMovementRepository, never()).save(any());
        verify(productRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------
    // The write-off
    // ------------------------------------------------------------------

    @Test
    void expiredAvailableStockIsWrittenOffAsOneWasteMovement() {
        Product product = product(1L, ProductStatus.ACTIVE);
        stubCandidates(candidate(product, 10, 0, EXPIRED));

        SweepResult result = service.sweepExpiredStock();

        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.productsWrittenOff()).isEqualTo(1);
        assertThat(result.productsDelisted()).isEqualTo(1);

        StockMovement movement = capturedMovement();
        assertThat(movement.getMovementType()).isEqualTo(MovementType.WASTE);
        assertThat(movement.getQuantityDelta()).isEqualTo(-10);
        assertThat(movement.getProduct()).isSameAs(product);
        // A scheduled job has no authenticated user and no originating document.
        assertThat(movement.getActor()).isNull();
        assertThat(movement.getReferenceId()).isNull();
        assertThat(movement.getReferenceType()).isNull();
        assertThat(movement.getReason())
                .isEqualTo("Expired stock write-off: expiry date 2026-10-03 has passed (sweep date 2026-10-05)");
    }

    @Test
    void reservedUnitsSurviveTheWriteOff() {
        Product product = product(1L, ProductStatus.ACTIVE);
        Inventory candidate = candidate(product, 10, 4, EXPIRED);
        stubCandidates(candidate);

        service.sweepExpiredStock();

        // quantity drops to reserved_quantity, never below it (ck_inventory_reserved_le_quantity).
        assertThat(candidate.getQuantity()).isEqualTo(4);
        assertThat(candidate.getReservedQuantity()).isEqualTo(4);
        assertThat(candidate.getAvailable()).isZero();
        // Only the 6 available units are reported as waste.
        assertThat(capturedMovement().getQuantityDelta()).isEqualTo(-6);
    }

    @Test
    void aRowWithNothingAvailableIsDelistedWithoutAZeroMovement() {
        Product product = product(1L, ProductStatus.ACTIVE);
        Inventory candidate = candidate(product, 3, 3, EXPIRED);
        stubCandidates(candidate);

        SweepResult result = service.sweepExpiredStock();

        assertThat(result.productsWrittenOff()).isZero();
        assertThat(result.productsDelisted()).isEqualTo(1);
        assertThat(candidate.getQuantity()).isEqualTo(3);
        verify(stockMovementRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // The delist
    // ------------------------------------------------------------------

    @Test
    void anActiveProductIsDelistedToInactive() {
        Product product = product(1L, ProductStatus.ACTIVE);
        stubCandidates(candidate(product, 5, 0, EXPIRED));

        service.sweepExpiredStock();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        verify(productRepository).saveAndFlush(product);
    }

    @Test
    void aProductThatIsAlreadyOffTheStorefrontIsNotRewritten() {
        for (ProductStatus status : List.of(ProductStatus.DRAFT, ProductStatus.INACTIVE, ProductStatus.ARCHIVED)) {
            Product product = product(1L, status);
            stubCandidates(candidate(product, 5, 0, EXPIRED));

            service.sweepExpiredStock();

            assertThat(product.getStatus()).isEqualTo(status);
            verify(productRepository, never()).saveAndFlush(any());
            // The stock write-off still happens: expiry is expiry regardless of visibility.
            verify(stockMovementRepository).save(any());
            clearInvocations(productRepository, stockMovementRepository);
        }
    }

    // ------------------------------------------------------------------
    // Re-checking under the lock
    // ------------------------------------------------------------------

    @Test
    void aRowThatIsNoLongerExpiredWhenLockedIsSkipped() {
        Inventory candidate = candidate(product(1L, ProductStatus.ACTIVE), 5, 0, EXPIRED);
        stubCandidates(candidate);
        // The vendor corrected the expiry date between selection and lock.
        when(inventoryRepository.findByProductIdForUpdate(1L))
                .thenReturn(Optional.of(candidate(product(1L, ProductStatus.ACTIVE), 5, 0, TODAY)));

        SweepResult result = service.sweepExpiredStock();

        assertThat(result.productsWrittenOff()).isZero();
        assertThat(result.productsDelisted()).isZero();
        verify(stockMovementRepository, never()).save(any());
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void aRowThatNoLongerNeedsWorkWhenLockedIsSkipped() {
        Inventory candidate = candidate(product(1L, ProductStatus.ACTIVE), 5, 0, EXPIRED);
        stubCandidates(candidate);
        Inventory current = candidate(product(1L, ProductStatus.INACTIVE), 2, 2, EXPIRED);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(current));

        SweepResult result = service.sweepExpiredStock();

        assertThat(result.productsWrittenOff()).isZero();
        assertThat(result.productsDelisted()).isZero();
        assertThat(current.getQuantity()).isEqualTo(2);
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void aRowThatVanishesBeforeItCanBeLockedIsSkipped() {
        stubCandidates(candidate(product(1L, ProductStatus.ACTIVE), 5, 0, EXPIRED));
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.empty());

        SweepResult result = service.sweepExpiredStock();

        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.productsWrittenOff()).isZero();
        assertThat(result.productsDelisted()).isZero();
        verify(stockMovementRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Several rows
    // ------------------------------------------------------------------

    @Test
    void rowsAreLockedInAscendingProductIdOrder() {
        Product first = product(1L, ProductStatus.ACTIVE);
        Product second = product(2L, ProductStatus.ACTIVE);
        stubCandidates(candidate(first, 5, 0, EXPIRED), candidate(second, 5, 0, EXPIRED));

        SweepResult result = service.sweepExpiredStock();

        assertThat(result).isEqualTo(new SweepResult(2, 2, 2));
        InOrder lockOrder = inOrder(inventoryRepository);
        lockOrder.verify(inventoryRepository).findByProductIdForUpdate(1L);
        lockOrder.verify(inventoryRepository).findByProductIdForUpdate(2L);
    }

    @Test
    void everyProductThatLosesStockGetsItsOwnMovement() {
        stubCandidates(
                candidate(product(1L, ProductStatus.ACTIVE), 5, 0, EXPIRED),
                candidate(product(2L, ProductStatus.ACTIVE), 9, 0, EXPIRED));

        service.sweepExpiredStock();

        ArgumentCaptor<StockMovement> movements = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(movements.capture());
        assertThat(movements.getAllValues()).extracting(StockMovement::getQuantityDelta)
                .containsExactly(-5, -9);
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private void stubCandidates(Inventory... candidates) {
        when(inventoryRepository.findExpiredNeedingAction(any(), any(), any())).thenReturn(List.of(candidates));
        for (Inventory candidate : candidates) {
            when(inventoryRepository.findByProductIdForUpdate(candidate.getProduct().getId()))
                    .thenReturn(Optional.of(candidate));
        }
    }

    private StockMovement capturedMovement() {
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movement.capture());
        return movement.getValue();
    }

    private static Product product(Long id, ProductStatus status) {
        return Product.builder()
                .id(id)
                .name("Product " + id)
                .basePrice(new BigDecimal("299.00"))
                .status(status)
                .build();
    }

    private static Inventory candidate(Product product, int quantity, int reserved, LocalDate expiryDate) {
        return Inventory.builder()
                .id(product.getId() * 100)
                .product(product)
                .quantity(quantity)
                .reservedQuantity(reserved)
                .lowStockThreshold(0)
                .expiryDate(expiryDate)
                .build();
    }
}
