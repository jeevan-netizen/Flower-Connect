package com.flowerconnect.product;

import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.test.PublicCatalogueFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-boundary tests for the public product detail endpoint (plan task 4.9).
 *
 * <p>Every ineligibility reason — unknown product, non-active status, no
 * available stock, a vendor that is not approved or not accepting orders, and a
 * vendor outside its delivery radius — produces the same non-disclosing 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicProductApiIntegrationTest extends AbstractIntegrationTest {

    private static final String KORAMANGALA_PINCODE = "560034";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.flowerconnect.repository.UserRepository userRepository;

    @Autowired
    private com.flowerconnect.repository.RoleRepository roleRepository;

    @Autowired
    private com.flowerconnect.repository.VendorProfileRepository vendorProfileRepository;

    @Autowired
    private com.flowerconnect.repository.ServiceLocationRepository serviceLocationRepository;

    @Autowired
    private com.flowerconnect.catalog.repository.CategoryRepository categoryRepository;

    @Autowired
    private com.flowerconnect.catalog.repository.ProductRepository productRepository;

    @Autowired
    private com.flowerconnect.inventory.repository.InventoryRepository inventoryRepository;

    private PublicCatalogueFixture fixture;

    private ServiceLocation origin;
    private Category category;
    private User floristUser;

    @BeforeEach
    void setUp() {
        fixture = new PublicCatalogueFixture(userRepository, roleRepository, vendorProfileRepository,
                serviceLocationRepository, categoryRepository, productRepository, inventoryRepository);
        origin = fixture.locationByPincode(KORAMANGALA_PINCODE);
        category = fixture.createCategory();
        floristUser = fixture.createFloristUser();
    }

    @Test
    void productDetailIsPubliclyAccessibleWithoutAToken() throws Exception {
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product product = fixture.createProduct(vendor, category, "Detail Item", ProductStatus.ACTIVE);
        fixture.createInventory(product, 10, 2);

        mockMvc.perform(get("/api/v1/products/" + product.getId())
                        .param("locationId", origin.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId().intValue()))
                .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.containsString("Detail Item")))
                .andExpect(jsonPath("$.available").value(8))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void productDetailRejectsAMissingLocationId() throws Exception {
        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void productDetailRejectsAnUnknownLocationId() throws Exception {
        mockMvc.perform(get("/api/v1/products/1")
                        .param("locationId", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unknown service location"));
    }

    @Test
    void productDetailReturns404ForAnUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/999999")
                        .param("locationId", origin.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void productDetailReturns404ForANonActiveProduct() throws Exception {
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product draft = fixture.createProduct(vendor, category, "Draft Detail", ProductStatus.DRAFT);
        fixture.createInventory(draft, 10, 0);

        mockMvc.perform(get("/api/v1/products/" + draft.getId())
                        .param("locationId", origin.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void productDetailReturns404ForAnOutOfStockProduct() throws Exception {
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product outOfStock = fixture.createProduct(vendor, category, "Sold Out Detail", ProductStatus.ACTIVE);
        fixture.createInventory(outOfStock, 0, 0);

        mockMvc.perform(get("/api/v1/products/" + outOfStock.getId())
                        .param("locationId", origin.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void productDetailReturns404ForANonApprovedVendor() throws Exception {
        VendorProfile pending = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.PENDING_APPROVAL, true);
        Product product = fixture.createProduct(pending, category, "Pending Detail", ProductStatus.ACTIVE);
        fixture.createInventory(product, 10, 0);

        mockMvc.perform(get("/api/v1/products/" + product.getId())
                        .param("locationId", origin.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void productDetailReturns404ForAVendorThatIsNotAcceptingOrders() throws Exception {
        VendorProfile closed = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, false);
        Product product = fixture.createProduct(closed, category, "Closed Detail", ProductStatus.ACTIVE);
        fixture.createInventory(product, 10, 0);

        mockMvc.perform(get("/api/v1/products/" + product.getId())
                        .param("locationId", origin.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void productDetailReturns404OutsideTheDeliveryRadius() throws Exception {
        VendorProfile farVendor = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude().add(BigDecimal.ONE), origin.getLongitude(),
                new BigDecimal("5"), VendorProfile.Status.APPROVED, true);
        Product product = fixture.createProduct(farVendor, category, "Far Detail", ProductStatus.ACTIVE);
        fixture.createInventory(product, 10, 0);

        mockMvc.perform(get("/api/v1/products/" + product.getId())
                        .param("locationId", origin.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void productDetailNeverExposesTheStorageKey() throws Exception {
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product product = fixture.createProduct(vendor, category, "Key Probe Detail", ProductStatus.ACTIVE);
        fixture.createInventory(product, 10, 0);

        String body = mockMvc.perform(get("/api/v1/products/" + product.getId())
                        .param("locationId", origin.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(body.contains("storageKey"),
                "The public product detail response must not expose image storage keys");
    }
}
