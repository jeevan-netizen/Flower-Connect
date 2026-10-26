package com.flowerconnect.pos.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.service.InventoryService;
import com.flowerconnect.pos.config.PosProperties;
import com.flowerconnect.pos.domain.PosMoney;
import com.flowerconnect.pos.dto.PosSaleRequest;
import com.flowerconnect.pos.dto.PosSaleRequest.PosSaleLineRequest;
import com.flowerconnect.pos.dto.PosSaleResponse;
import com.flowerconnect.pos.repository.PosItemRepository;
import com.flowerconnect.pos.repository.PosTransactionRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PosServiceTest {

    private static final String VENDOR_EMAIL = "florist@test.com";
    private static final Long VENDOR_ID = 7L;
    private static final Long PRODUCT_ID = 42L;

    @Mock
    private PosTransactionRepository posTransactionRepository;
    @Mock
    private PosItemRepository posItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PosProperties posProperties;

    private PosService posService;
    private VendorProfile vendor;
    private Product product;

    @BeforeEach
    void setUp() {
        posService = new PosService(posTransactionRepository, posItemRepository,
                productRepository, inventoryRepository, inventoryService,
                vendorProfileRepository, userRepository, posProperties, Clock.systemUTC());

        vendor = VendorProfile.builder()
                .id(VENDOR_ID)
                .businessName("Test Florist")
                .build();

        product = Product.builder()
                .id(PRODUCT_ID)
                .name("Red Rose Bunch")
                .vendor(vendor)
                .basePrice(new BigDecimal("299.00"))
                .status(ProductStatus.ACTIVE)
                .build();

        lenient().when(posProperties.getMaxLinesPerSale()).thenReturn(50);
        lenient().when(posProperties.getMaxQuantityPerLine()).thenReturn(100);
    }

    // ------------------------------------------------------------- idempotency key validation

    @Test
    void missingIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> posService.validateIdempotencyKey(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("Idempotency-Key header is required"));

        assertThatThrownBy(() -> posService.validateIdempotencyKey(""))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> posService.validateIdempotencyKey("   "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void nonUuidIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> posService.validateIdempotencyKey("not-a-uuid"))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("canonical UUID"));

        assertThatThrownBy(() -> posService.validateIdempotencyKey("123e4567-e89b-42d3-a456-42661417400"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validIdempotencyKeyPasses() {
        posService.validateIdempotencyKey("123e4567-e89b-42d3-a456-426614174000");
    }

    // ------------------------------------------------------------- request validation

    @Test
    void emptyItemsAreRejected() {
        PosSaleRequest request = PosSaleRequest.builder()
                .items(List.of())
                .paymentMethod(PosSaleRequest.PosPaymentMethod.CASH)
                .build();
        assertThatThrownBy(() -> posService.validateRequest(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("Items are required"));
    }

    @Test
    void nullPaymentMethodIsRejected() {
        PosSaleRequest request = PosSaleRequest.builder()
                .items(List.of(PosSaleLineRequest.builder().productId(PRODUCT_ID).quantity(1).build()))
                .build();
        assertThatThrownBy(() -> posService.validateRequest(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("Payment method is required"));
    }

    @Test
    void tooManyLinesAreRejected() {
        List<PosSaleLineRequest> items = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) {
            items.add(PosSaleLineRequest.builder().productId((long) i).quantity(1).build());
        }
        PosSaleRequest request = PosSaleRequest.builder()
                .items(items)
                .paymentMethod(PosSaleRequest.PosPaymentMethod.CASH)
                .build();
        assertThatThrownBy(() -> posService.validateRequest(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("at most 50 line(s)"));
    }

    @Test
    void zeroQuantityIsRejected() {
        PosSaleRequest request = PosSaleRequest.builder()
                .items(List.of(PosSaleLineRequest.builder().productId(PRODUCT_ID).quantity(0).build()))
                .paymentMethod(PosSaleRequest.PosPaymentMethod.CASH)
                .build();
        assertThatThrownBy(() -> posService.validateRequest(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("greater than zero"));
    }

    // ------------------------------------------------------------- duplicate item merging

    @Test
    void duplicateProductIdsAreMerged() {
        PosSaleLineRequest a = PosSaleLineRequest.builder().productId(PRODUCT_ID).quantity(2).build();
        PosSaleLineRequest b = PosSaleLineRequest.builder().productId(PRODUCT_ID).quantity(3).build();
        List<PosSaleLineRequest> merged = posService.mergeDuplicateItems(List.of(a, b));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(merged.get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void distinctProductsAreNotMerged() {
        PosSaleLineRequest a = PosSaleLineRequest.builder().productId(1L).quantity(1).build();
        PosSaleLineRequest b = PosSaleLineRequest.builder().productId(2L).quantity(2).build();
        List<PosSaleLineRequest> merged = posService.mergeDuplicateItems(List.of(a, b));
        assertThat(merged).hasSize(2);
    }

    // ------------------------------------------------------------- fingerprint

    @Test
    void fingerprintIsDeterministic() {
        PosSaleRequest request = PosSaleRequest.builder()
                .items(List.of(
                        PosSaleLineRequest.builder().productId(2L).quantity(3).build(),
                        PosSaleLineRequest.builder().productId(1L).quantity(1).build()))
                .paymentMethod(PosSaleRequest.PosPaymentMethod.UPI)
                .build();
        String first = posService.computeFingerprint(request);
        String second = posService.computeFingerprint(request);
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64); // SHA-256 hex
    }

    @Test
    void differentBodiesProduceDifferentFingerprints() {
        PosSaleRequest a = PosSaleRequest.builder()
                .items(List.of(PosSaleLineRequest.builder().productId(1L).quantity(1).build()))
                .paymentMethod(PosSaleRequest.PosPaymentMethod.CASH)
                .build();
        PosSaleRequest b = PosSaleRequest.builder()
                .items(List.of(PosSaleLineRequest.builder().productId(1L).quantity(1).build()))
                .paymentMethod(PosSaleRequest.PosPaymentMethod.CARD)
                .build();
        assertThat(posService.computeFingerprint(a)).isNotEqualTo(posService.computeFingerprint(b));
    }

    // ------------------------------------------------------------- product validation

    @Test
    void unknownProductIsNotFound() {
        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> posService.validateProductsExistence(vendor,
                List.of(PosSaleLineRequest.builder().productId(999L).quantity(1).build())))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("not found"));
    }

    @Test
    void foreignProductIsForbidden() {
        VendorProfile otherVendor = VendorProfile.builder().id(99L).build();
        Product foreign = Product.builder()
                .id(PRODUCT_ID)
                .name("Foreign Product")
                .vendor(otherVendor)
                .basePrice(BigDecimal.ONE)
                .status(ProductStatus.ACTIVE)
                .build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> posService.validateProductsExistence(vendor,
                List.of(PosSaleLineRequest.builder().productId(PRODUCT_ID).quantity(1).build())))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("does not belong to this vendor"));
    }

    @Test
    void inactiveProductIsRefused() {
        Product inactive = Product.builder()
                .id(PRODUCT_ID)
                .name("Inactive Product")
                .vendor(vendor)
                .basePrice(BigDecimal.ONE)
                .status(ProductStatus.INACTIVE)
                .build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> posService.validateProductsExistence(vendor,
                List.of(PosSaleLineRequest.builder().productId(PRODUCT_ID).quantity(1).build())))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("not active"));
    }

    // ------------------------------------------------------------- inventory validation

    @Test
    void missingInventoryIsNotFound() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> posService.validateInventories(
                List.of(product),
                List.of(PosSaleLineRequest.builder().productId(PRODUCT_ID).quantity(1).build())))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("not found"));
    }

    @Test
    void expiredProductIsRefused() {
        Inventory expired = Inventory.builder()
                .id(1L)
                .product(product)
                .quantity(10)
                .reservedQuantity(0)
                .expiryDate(java.time.LocalDate.of(2020, 1, 1))
                .build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> posService.validateInventories(
                List.of(product),
                List.of(PosSaleLineRequest.builder().productId(PRODUCT_ID).quantity(1).build())))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getMessage())
                        .contains("expired"));
    }

    @Test
    void validInventoryPasses() {
        Inventory valid = Inventory.builder()
                .id(1L)
                .product(product)
                .quantity(10)
                .reservedQuantity(0)
                .expiryDate(java.time.LocalDate.now().plusDays(30))
                .build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(valid));
        List<Inventory> result = posService.validateInventories(
                List.of(product),
                List.of(PosSaleLineRequest.builder().productId(PRODUCT_ID).quantity(1).build()));
        assertThat(result).hasSize(1);
    }

    // ------------------------------------------------------------- PosMoney round trip

    @Test
    void lineTotalRoundsHalfUp() {
        BigDecimal result = PosMoney.lineTotal(new BigDecimal("10.005"), 3);
        assertThat(result).isEqualByComparingTo("30.02");
    }

    @Test
    void transactionTotalSumsLineTotals() {
        BigDecimal result = PosMoney.transactionTotal(List.of(
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("5.50")
        ));
        assertThat(result).isEqualByComparingTo("35.50");
    }
}
