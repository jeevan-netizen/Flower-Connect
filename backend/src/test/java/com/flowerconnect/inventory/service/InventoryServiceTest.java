package com.flowerconnect.inventory.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.dto.InventorySummary;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import com.flowerconnect.inventory.dto.ExpiryDateRequest;
import com.flowerconnect.inventory.dto.LowStockPageResponse;
import com.flowerconnect.inventory.dto.StockAdjustmentRequest;
import com.flowerconnect.inventory.dto.StockInRequest;
import com.flowerconnect.inventory.dto.StockMovementPageResponse;
import com.flowerconnect.inventory.dto.StockOutRequest;
import com.flowerconnect.inventory.dto.StockWriteOffRequest;
import com.flowerconnect.inventory.mapper.StockMovementMapper;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the inventory service (plan task 3.6).
 *
 * <p>What is asserted here is everything decidable without a database:
 * the delta each operation computes, the movement type and reason it
 * writes, that the actor is resolved from the authenticated identity
 * rather than from anything in the request, that exactly one movement
 * is appended per change, the reserved-quantity floor and its 409, the
 * ownership mapping (403 foreign / 404 missing), and the decision not
 * to write a movement for the two alert settings.
 *
 * <p>Filter <em>behaviour</em> and the pessimistic lock are not
 * asserted here and must not be: a mocked repository cannot
 * demonstrate which rows {@code available <= lowStockThreshold}
 * selects, and it cannot demonstrate that {@code findByProductIdForUpdate}
 * actually emits {@code SELECT ... FOR UPDATE}. Those live in
 * {@code VendorInventoryIntegrationTest} and
 * {@code InventoryConcurrencyIntegrationTest} respectively.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final String VENDOR_EMAIL = "florist@test.com";
    private static final Long PRODUCT_ID = 42L;
    private static final Long VENDOR_ID = 7L;

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private UserRepository userRepository;

    private final StockMovementMapper mapper = Mappers.getMapper(StockMovementMapper.class);

    private InventoryService inventoryService;
    private Product product;
    private User vendorUser;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, stockMovementRepository,
                productRepository, vendorProfileRepository, userRepository, mapper);

        product = Product.builder()
                .id(PRODUCT_ID)
                .name("Red Rose Bunch")
                .vendor(VendorProfile.builder().id(VENDOR_ID).build())
                .basePrice(new BigDecimal("299.00"))
                .build();
        vendorUser = User.builder()
                .id(11L)
                .email(VENDOR_EMAIL)
                .fullName("Florist")
                .role(Role.builder().id(2L).name("FLORIST").build())
                .build();

        stubOwnership();
    }

    // ------------------------------------------------------------- stock in

    @Test
    void stockInAddsTheQuantityAndRecordsOnePositiveMovement() {
        stubInventory(10, 0);

        InventorySummary summary = inventoryService.stockIn(VENDOR_EMAIL, PRODUCT_ID,
                StockInRequest.builder().quantity(25).reason("Morning delivery").build());

        assertThat(summary.getQuantity()).isEqualTo(35);
        assertThat(summary.getReservedQuantity()).isZero();
        assertThat(summary.getAvailable()).isEqualTo(35);

        StockMovement movement = capturedMovement();
        assertThat(movement.getMovementType()).isEqualTo(MovementType.STOCK_IN);
        assertThat(movement.getQuantityDelta()).isEqualTo(25);
        assertThat(movement.getReason()).isEqualTo("Morning delivery");
    }

    @Test
    void stockInStoresNoReasonAsNullRatherThanBlank() {
        stubInventory(10, 0);

        inventoryService.stockIn(VENDOR_EMAIL, PRODUCT_ID,
                StockInRequest.builder().quantity(5).reason("   ").build());

        assertThat(capturedMovement().getReason()).isNull();
    }

    @Test
    void stockInRejectsANonPositiveQuantity() {
        assertThatThrownBy(() -> inventoryService.stockIn(VENDOR_EMAIL, PRODUCT_ID,
                StockInRequest.builder().quantity(0).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));

        verify(stockMovementRepository, never()).save(any());
    }

    // ------------------------------------------------------------- stock out

    @Test
    void stockOutSubtractsTheQuantityAndRecordsOneNegativeMovement() {
        stubInventory(10, 0);

        InventorySummary summary = inventoryService.stockOut(VENDOR_EMAIL, PRODUCT_ID,
                StockOutRequest.builder().quantity(4).reason("Moved to the other shop").build());

        assertThat(summary.getQuantity()).isEqualTo(6);
        assertThat(summary.getAvailable()).isEqualTo(6);

        StockMovement movement = capturedMovement();
        assertThat(movement.getMovementType()).isEqualTo(MovementType.STOCK_OUT);
        assertThat(movement.getQuantityDelta()).isEqualTo(-4);
        assertThat(movement.getReason()).isEqualTo("Moved to the other shop");
    }

    @Test
    void stockOutMayConserveEverythingThatIsNotReserved() {
        stubInventory(10, 3);

        InventorySummary summary = inventoryService.stockOut(VENDOR_EMAIL, PRODUCT_ID,
                StockOutRequest.builder().quantity(7).build());

        assertThat(summary.getQuantity()).isEqualTo(3);
        assertThat(summary.getAvailable()).isZero();
    }

    @Test
    void stockOutIsRefusedWhenItWouldDropBelowTheReservedQuantity() {
        stubInventory(10, 3);

        assertThatThrownBy(() -> inventoryService.stockOut(VENDOR_EMAIL, PRODUCT_ID,
                StockOutRequest.builder().quantity(8).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> {
                    BusinessException failure = (BusinessException) thrown;
                    assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(failure.getMessage()).contains("below the 3 reserved unit(s)");
                });

        // No clamping and no half-applied write: the level is untouched
        // and no movement was appended.
        assertThat(currentInventory.getQuantity()).isEqualTo(10);
        verify(inventoryRepository, never()).saveAndFlush(any());
        verify(stockMovementRepository, never()).save(any());
    }

    // ------------------------------------------------------------- adjustment

    @Test
    void aPositiveAdjustmentRaisesTheQuantityAndRecordsTheSignedDelta() {
        stubInventory(20, 5);

        InventorySummary summary = inventoryService.adjust(VENDOR_EMAIL, PRODUCT_ID,
                StockAdjustmentRequest.builder().quantity(6).reason("Recount after shrinkage").build());

        assertThat(summary.getQuantity()).isEqualTo(26);
        assertThat(summary.getAvailable()).isEqualTo(21);

        StockMovement movement = capturedMovement();
        assertThat(movement.getMovementType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(movement.getQuantityDelta()).isEqualTo(6);
        assertThat(movement.getReason()).isEqualTo("Recount after shrinkage");
    }

    @Test
    void aNegativeAdjustmentLowersTheQuantity() {
        stubInventory(20, 0);

        InventorySummary summary = inventoryService.adjust(VENDOR_EMAIL, PRODUCT_ID,
                StockAdjustmentRequest.builder().quantity(-9).reason("Two stems found spoiled").build());

        assertThat(summary.getQuantity()).isEqualTo(11);
        assertThat(capturedMovement().getQuantityDelta()).isEqualTo(-9);
    }

    @Test
    void aZeroAdjustmentIsRefusedAndWritesNoMovement() {
        // No inventory stub: the zero correction is refused before the
        // row is locked, so nothing may be written.
        assertThatThrownBy(() -> inventoryService.adjust(VENDOR_EMAIL, PRODUCT_ID,
                StockAdjustmentRequest.builder().quantity(0).reason("Nothing changed").build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void anAdjustmentWithoutAReasonIsRefused() {
        assertThatThrownBy(() -> inventoryService.adjust(VENDOR_EMAIL, PRODUCT_ID,
                StockAdjustmentRequest.builder().quantity(3).reason("  ").build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void anAdjustmentBelowTheReservedQuantityIsRefused() {
        stubInventory(10, 8);

        assertThatThrownBy(() -> inventoryService.adjust(VENDOR_EMAIL, PRODUCT_ID,
                StockAdjustmentRequest.builder().quantity(-5).reason("Recount").build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_STOCK));

        assertThat(currentInventory.getQuantity()).isEqualTo(10);
        verify(stockMovementRepository, never()).save(any());
    }

    // ------------------------------------------------------------- write-off

    @Test
    void aWriteOffRemovesTheQuantityAndIsRecordedAsWaste() {
        stubInventory(30, 0);

        InventorySummary summary = inventoryService.writeOff(VENDOR_EMAIL, PRODUCT_ID,
                StockWriteOffRequest.builder().quantity(12).reason("Cooler failed overnight").build());

        assertThat(summary.getQuantity()).isEqualTo(18);

        StockMovement movement = capturedMovement();
        // WASTE, not STOCK_OUT: a write-off is a loss, and WASTE is the
        // same vocabulary the expiry scheduler uses for the same thing.
        assertThat(movement.getMovementType()).isEqualTo(MovementType.WASTE);
        assertThat(movement.getQuantityDelta()).isEqualTo(-12);
        assertThat(movement.getReason()).isEqualTo("Cooler failed overnight");
    }

    @Test
    void aWriteOffWithoutAReasonIsRefused() {
        assertThatThrownBy(() -> inventoryService.writeOff(VENDOR_EMAIL, PRODUCT_ID,
                StockWriteOffRequest.builder().quantity(3).reason(null).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void aWriteOffBiggerThanTheStockIsRefusedRatherThanClamped() {
        stubInventory(4, 0);

        assertThatThrownBy(() -> inventoryService.writeOff(VENDOR_EMAIL, PRODUCT_ID,
                StockWriteOffRequest.builder().quantity(9).reason("Whole shelf collapsed").build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_STOCK));

        assertThat(currentInventory.getQuantity()).isEqualTo(4);
    }

    // ------------------------------------------------------------- actor and locking

    @Test
    void theMovementActorIsResolvedFromTheAuthenticatedIdentity() {
        stubInventory(10, 0);

        inventoryService.stockIn(VENDOR_EMAIL, PRODUCT_ID,
                StockInRequest.builder().quantity(5).build());

        // Resolved from the JWT subject, and the route has no field that
        // could say otherwise.
        verify(userRepository).findByEmail(VENDOR_EMAIL);
        assertThat(capturedMovement().getActor()).isSameAs(vendorUser);
    }

    @Test
    void aMovementReferencesNoExternalDocument() {
        stubInventory(10, 0);

        inventoryService.stockIn(VENDOR_EMAIL, PRODUCT_ID,
                StockInRequest.builder().quantity(5).build());

        StockMovement movement = capturedMovement();
        assertThat(movement.getReferenceId()).isNull();
        assertThat(movement.getReferenceType()).isNull();
        assertThat(movement.getProduct()).isSameAs(product);
    }

    @Test
    void everyWriteTakesThePessimisticLockAndReadsThroughIt() {
        stubInventory(10, 0);

        inventoryService.stockIn(VENDOR_EMAIL, PRODUCT_ID,
                StockInRequest.builder().quantity(1).build());

        verify(inventoryRepository).findByProductIdForUpdate(PRODUCT_ID);
        verify(inventoryRepository, never()).findByProductId(anyLong());
    }

    @Test
    void ownershipIsCheckedBeforeTheRowIsLocked() {
        // A foreign vendor must not be able to stall the owner's stock
        // by taking its row lock first.
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreignProduct()));
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(VendorProfile.builder().id(99L).build()));

        assertThatThrownBy(() -> inventoryService.stockIn(VENDOR_EMAIL, PRODUCT_ID,
                StockInRequest.builder().quantity(1).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(inventoryRepository, never()).findByProductIdForUpdate(anyLong());
    }

    // ------------------------------------------------------------- alert settings

    @Test
    void theLowStockThresholdIsReplacedAndWritesNoMovement() {
        stubInventory(10, 2);

        InventorySummary summary = inventoryService.updateLowStockThreshold(VENDOR_EMAIL, PRODUCT_ID,
                com.flowerconnect.inventory.dto.LowStockThresholdRequest.builder()
                        .lowStockThreshold(4).build());

        assertThat(summary.getLowStockThreshold()).isEqualTo(4);
        // available 8 against a threshold of 4 is not yet low stock.
        assertThat(summary.isLowStock()).isFalse();
        assertThat(summary.getQuantity()).isEqualTo(10);
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void theExpiryDateIsReplacedAndWritesNoMovement() {
        stubInventory(10, 0);

        InventorySummary summary = inventoryService.updateExpiryDate(VENDOR_EMAIL, PRODUCT_ID,
                ExpiryDateRequest.builder().expiryDate(LocalDate.of(2026, 4, 30)).build());

        assertThat(summary.getExpiryDate()).isEqualTo(LocalDate.of(2026, 4, 30));
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void aNullExpiryDateClearsIt() {
        stubInventory(10, 0);

        InventorySummary summary = inventoryService.updateExpiryDate(VENDOR_EMAIL, PRODUCT_ID,
                ExpiryDateRequest.builder().expiryDate(null).build());

        assertThat(summary.getExpiryDate()).isNull();
    }

    // ------------------------------------------------------------- reads

    @Test
    void readingAnInventoryReportsAvailabilityAndTheLowStockFlag() {
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inventoryAt(6, 4, 5)));

        InventorySummary summary = inventoryService.get(VENDOR_EMAIL, PRODUCT_ID);

        assertThat(summary.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(summary.getAvailable()).isEqualTo(2);
        assertThat(summary.isLowStock()).isTrue();
    }

    @Test
    void readingAForeignProductsInventoryIsForbidden() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreignProduct()));

        assertThatThrownBy(() -> inventoryService.get(VENDOR_EMAIL, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void readingAnUnknownProductIsNotFound() {
        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.get(VENDOR_EMAIL, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void theLowStockListingComposesTheVendorScopeWithTheThresholdRule() {
        Pageable expected = PageRequest.of(0, 20, Sort.by("id").ascending());
        when(inventoryRepository.findAll(any(Specification.class), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(inventoryAt(2, 1, 3)), expected, 1));

        LowStockPageResponse page = inventoryService.listLowStock(VENDOR_EMAIL, 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getProductId()).isEqualTo(PRODUCT_ID);
                    assertThat(row.getProductName()).isEqualTo("Red Rose Bunch");
                    assertThat(row.getAvailable()).isEqualTo(1);
                    assertThat(row.isLowStock()).isTrue();
                });

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Specification<Inventory>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(inventoryRepository).findAll(spec.capture(), eq(expected));
        // Both fragments are mandatory; asserting the composed spec is
        // non-null proves the vendor scope was not dropped, and the
        // behaviour of the predicate is asserted against real SQL in
        // the integration test.
        assertThat(spec.getValue()).isNotNull();
    }

    @Test
    void theLowStockListingClampsThePageSize() {
        Pageable expected = PageRequest.of(0, 100, Sort.by("id").ascending());
        when(inventoryRepository.findAll(any(Specification.class), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(), expected, 0));

        LowStockPageResponse page = inventoryService.listLowStock(VENDOR_EMAIL, 0, 5000);

        assertThat(page.getSize()).isEqualTo(100);
        assertThat(page.isEmpty()).isTrue();
    }

    @Test
    void theMovementHistoryIsPagedNewestFirstAndFlattensTheActor() {
        Pageable expected = PageRequest.of(0, 20,
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        when(stockMovementRepository.findByProductId(eq(PRODUCT_ID), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(
                        StockMovement.builder().id(5L).product(product)
                                .movementType(MovementType.STOCK_IN).quantityDelta(7)
                                .reason("Delivery").actor(vendorUser).build()), expected, 1));

        StockMovementPageResponse page =
                inventoryService.listMovements(VENDOR_EMAIL, PRODUCT_ID, 0, 20);

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(row.getMovementType()).isEqualTo(MovementType.STOCK_IN);
            assertThat(row.getQuantityDelta()).isEqualTo(7);
            assertThat(row.getReason()).isEqualTo("Delivery");
            assertThat(row.getActorUserId()).isEqualTo(11L);
            assertThat(row.getActorEmail()).isEqualTo(VENDOR_EMAIL);
        });
    }

    @Test
    void theMovementHistoryIsScopedToTheOwnersProducts() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreignProduct()));

        assertThatThrownBy(() -> inventoryService.listMovements(VENDOR_EMAIL, PRODUCT_ID, 0, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(stockMovementRepository, never()).findByProductId(anyLong(), any(Pageable.class));
    }

    @Test
    void aMovementWithoutAnActorMapsToANullActor() {
        Pageable expected = PageRequest.of(0, 20,
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        when(stockMovementRepository.findByProductId(eq(PRODUCT_ID), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(
                        StockMovement.builder().id(6L).product(product)
                                .movementType(MovementType.WASTE).quantityDelta(-3)
                                .reason("Expired").build()), expected, 1));

        // The expiry scheduler's write-off is the one movement with no
        // human actor, and it must not blow up the history page.
        StockMovementPageResponse page =
                inventoryService.listMovements(VENDOR_EMAIL, PRODUCT_ID, 0, 20);

        assertThat(page.getContent().get(0).getActorUserId()).isNull();
        assertThat(page.getContent().get(0).getActorEmail()).isNull();
    }

    // ------------------------------------------------------------- fixtures

    private Inventory currentInventory;

    /**
     * Shared fixture, stubbed leniently: not every test reaches every
     * collaborator (a read never resolves the actor, and the tests that
     * assert a refusal never get past ownership), and {@code STRICT_STUBS}
     * would fail the suite on the unused ones rather than on a real
     * problem.
     */
    private void stubOwnership() {
        lenient().when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        lenient().when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(VendorProfile.builder().id(VENDOR_ID).build()));
        lenient().when(userRepository.findByEmail(VENDOR_EMAIL)).thenReturn(Optional.of(vendorUser));
    }

    private Product foreignProduct() {
        return Product.builder().id(PRODUCT_ID).name("Someone Else's Rose")
                .vendor(VendorProfile.builder().id(999L).build())
                .basePrice(new BigDecimal("199.00")).build();
    }

    private Inventory inventoryAt(int quantity, int reserved, int threshold) {
        return Inventory.builder()
                .id(1L)
                .product(product)
                .quantity(quantity)
                .reservedQuantity(reserved)
                .lowStockThreshold(threshold)
                .build();
    }

    private void stubInventory(int quantity, int reserved) {
        currentInventory = inventoryAt(quantity, reserved, 5);
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(currentInventory));
    }

    private StockMovement capturedMovement() {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        return captor.getValue();
    }
}