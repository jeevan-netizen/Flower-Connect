package com.flowerconnect.catalog.service;

import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.dto.ProductResponse;
import com.flowerconnect.catalog.mapper.ProductMapper;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the product domain service (plan tasks 3.2–3.4):
 * slug generation, the product ⇒ inventory invariant, the
 * unique-constraint retry, and ownership scoping.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private ProductMapper mapper;
    @Mock
    private EntityManager entityManager;

    private ProductService productService;

    private VendorProfile vendor;
    private Category category;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository,
                vendorProfileRepository, inventoryRepository, productImageRepository, mapper);
        ReflectionTestUtils.setField(productService, "entityManager", entityManager);

        vendor = VendorProfile.builder().id(1L).businessName("Test Blossoms").build();
        // active defaults to false on the builder; task 3.5 refuses
        // to assign a product to a deactivated category, so the
        // fixture has to say so explicitly.
        category = Category.builder().id(1L).name("Roses").slug("roses").active(true).build();
    }

    @Test
    void createGeneratesSlugFromNameAndDefaultsToDraft() {
        stubHappyPath();
        when(productRepository.existsBySlug("hybrid-tea")).thenReturn(false);

        productService.create("florist@test.com", request("Hybrid Tea"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getSlug()).isEqualTo("hybrid-tea");
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(saved.getVendor().getId()).isEqualTo(1L);
        assertThat(saved.getCategory().getId()).isEqualTo(1L);
    }

    @Test
    void createAppendsNumericSuffixWhenSlugIsTaken() {
        stubHappyPath();
        when(productRepository.existsBySlug("hybrid-tea")).thenReturn(true);
        when(productRepository.existsBySlug("hybrid-tea-2")).thenReturn(false);

        productService.create("florist@test.com", request("Hybrid Tea"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("hybrid-tea-2");
    }

    @Test
    void createCreatesInventoryRowAtZeroInTheSameTransaction() {
        stubHappyPath();
        when(productRepository.existsBySlug("hybrid-tea")).thenReturn(false);

        productService.create("florist@test.com", request("Hybrid Tea"));

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(captor.capture());
        Inventory saved = captor.getValue();
        assertThat(saved.getQuantity()).isZero();
        assertThat(saved.getReservedQuantity()).isZero();
        assertThat(saved.getLowStockThreshold()).isZero();
        assertThat(saved.getProduct().getId()).isEqualTo(10L);
    }

    @Test
    void createRetriesWithSuffixedSlugAfterUniqueConstraintFailure() {
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        // The existence check sees a free slug, but a concurrent
        // transaction claims it before the insert lands.
        when(productRepository.existsBySlug("hybrid-tea")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry 'hybrid-tea' for key 'products.uq_products_slug'"))
                .thenAnswer(invocation -> {
                    Product p = invocation.getArgument(0);
                    p.setId(10L);
                    return p;
                });
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(List.of());
        when(mapper.toFullResponse(any(), any(), any()))
                .thenReturn(ProductResponse.builder().id(10L).build());

        productService.create("florist@test.com", request("Hybrid Tea"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(2)).saveAndFlush(captor.capture());
        List<Product> attempts = captor.getAllValues();
        assertThat(attempts.get(0).getSlug()).isEqualTo("hybrid-tea");
        assertThat(attempts.get(1).getSlug()).isEqualTo("hybrid-tea-2");
        // The failed insert is cleared from the persistence context
        // before the retry.
        verify(entityManager).clear();
    }

    @Test
    void createRejectsAnUnknownCategory() {
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.create("florist@test.com", request("Hybrid Tea")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void createRejectsAnUnknownVendor() {
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.create("florist@test.com", request("Hybrid Tea")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vendor profile not found");
    }

    @Test
    void updateRegeneratesTheSlugWhenTheNameChanges() {
        Product existing = Product.builder()
                .id(10L).vendor(vendor).category(category)
                .name("Original Name").slug("original-name")
                .basePrice(new BigDecimal("100.00")).status(ProductStatus.DRAFT)
                .build();
        when(productRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsBySlugAndIdNot("renamed-product", 10L))
                .thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(List.of());
        when(mapper.toFullResponse(any(), any(), any()))
                .thenReturn(ProductResponse.builder().id(10L).build());

        ProductResponse updated = productService.update(
                "florist@test.com", 10L, request("Renamed Product"));

        assertThat(updated.getId()).isEqualTo(10L);
        verify(productRepository).saveAndFlush(argThat(
                p -> "renamed-product".equals(p.getSlug())));
    }

    @Test
    void updateRefusesAProductOwnedByAnotherVendor() {
        Product foreign = Product.builder()
                .id(10L).vendor(VendorProfile.builder().id(2L).build())
                .category(category).name("Foreign").slug("foreign")
                .basePrice(new BigDecimal("100.00")).status(ProductStatus.DRAFT)
                .build();
        when(productRepository.findById(10L)).thenReturn(Optional.of(foreign));
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));

        BusinessException thrown = catchBusinessException(() ->
                productService.update("florist@test.com", 10L, request("Stolen")));

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivateRefusesAProductOwnedByAnotherVendor() {
        Product foreign = foreignProduct();
        when(productRepository.findById(10L)).thenReturn(Optional.of(foreign));
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));

        BusinessException thrown = catchBusinessException(() ->
                productService.deactivate("florist@test.com", 10L));

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(productRepository, never()).saveAndFlush(any());
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void deactivateMovesTheProductToInactiveWithoutDeletingIt() {
        Product own = ownProduct("Roses Only");
        when(productRepository.findById(10L)).thenReturn(Optional.of(own));
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        productService.deactivate("florist@test.com", 10L);

        assertThat(own.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        verify(productRepository).saveAndFlush(own);
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void getByIdForVendorReturnsTheProduct() {
        Product own = ownProduct("Roses Only");
        when(productRepository.findById(10L)).thenReturn(Optional.of(own));
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(List.of());
        when(mapper.toFullResponse(any(), any(), any()))
                .thenReturn(ProductResponse.builder().id(10L).build());

        assertThat(productService.getByIdForVendor("florist@test.com", 10L).getId()).isEqualTo(10L);
    }

    @Test
    void getByIdForVendorRefusesAProductOwnedByAnotherVendor() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(foreignProduct()));
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));

        BusinessException thrown = catchBusinessException(() ->
                productService.getByIdForVendor("florist@test.com", 10L));

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void getByIdForVendorReportsAMissingProductAsNotFound() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        BusinessException thrown = catchBusinessException(() ->
                productService.getByIdForVendor("florist@test.com", 404L));

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Category and price rules (task 3.5)
    // ------------------------------------------------------------------

    @Test
    void createRejectsADeactivatedCategory() {
        Category hidden = Category.builder()
                .id(2L).name("Seasonal").slug("seasonal").active(false).build();
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> productService.create("florist@test.com",
                ProductRequest.builder()
                        .name("Winter Rose")
                        .categoryId(2L)
                        .basePrice(new BigDecimal("150.00"))
                        .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Category is not active");
    }

    @Test
    void createRejectsAZeroPrice() {
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> productService.create("florist@test.com",
                ProductRequest.builder()
                        .name("Free Rose")
                        .categoryId(1L)
                        .basePrice(BigDecimal.ZERO)
                        .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsAZeroPriceWithoutSaving() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(ownProduct("Roses Only")));
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> productService.update("florist@test.com", 10L,
                ProductRequest.builder()
                        .name("Roses Only")
                        .categoryId(1L)
                        .basePrice(new BigDecimal("-1.00"))
                        .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");

        verify(productRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------
    // Listing (task 3.5)
    //
    // Only the parts of the listing that the service itself decides
    // are unit-tested here: the vendor is resolved from the JWT
    // subject, and page/size are clamped. Which rows the composed
    // predicates actually select is Criteria-API behaviour, so it is
    // asserted against real SQL in VendorCatalogIntegrationTest.
    // ------------------------------------------------------------------

    @Test
    void listClampsThePageSizeAndRejectsNegativePages() {
        stubList();
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        productService.list("florist@test.com", null, null, null, -3, 5000);

        verify(productRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void listRejectsAnUnknownVendor() {
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.list("florist@test.com", null, null, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vendor profile not found");
    }

    /** Stubs an empty page for the listing tests. */
    private void stubList() {
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    // ------------------------------------------------------------------

    /** Stubs the happy path for a create of "Hybrid Tea" by vendor 1. */
    private void stubHappyPath() {
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product p = invocation.getArgument(0);
                    p.setId(10L);
                    return p;
                });
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(List.of());
        when(mapper.toFullResponse(any(), any(), any()))
                .thenReturn(ProductResponse.builder().id(10L).build());
    }

    /** A product owned by vendor 1, ready to be updated or deactivated. */
    private Product ownProduct(String name) {
        return Product.builder()
                .id(10L).vendor(vendor).category(category)
                .name(name).slug("roses-only")
                .basePrice(new BigDecimal("100.00")).status(ProductStatus.DRAFT)
                .build();
    }

    /** A product owned by vendor 2 — out of scope for vendor 1. */
    private Product foreignProduct() {
        return Product.builder()
                .id(10L).vendor(VendorProfile.builder().id(2L).build())
                .category(category).name("Foreign").slug("foreign")
                .basePrice(new BigDecimal("100.00")).status(ProductStatus.DRAFT)
                .build();
    }

    private ProductRequest request(String name) {
        return ProductRequest.builder()
                .name(name)
                .categoryId(1L)
                .description("Fresh flowers")
                .basePrice(new BigDecimal("299.00"))
                .build();
    }

    private BusinessException catchBusinessException(Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            return e;
        }
        throw new AssertionError("expected a BusinessException");
    }
}
