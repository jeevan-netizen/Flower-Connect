package com.flowerconnect.storefront;

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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-boundary tests for the public vendor storefront (plan task 4.5).
 *
 * <p>Covers the storefront eligibility policy: an approved, order-accepting
 * vendor, ACTIVE products with available stock, and no delivery-radius
 * filtering (the customer has already chosen the vendor).
 */
@SpringBootTest
@AutoConfigureMockMvc
class StorefrontApiIntegrationTest extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() {
        fixture = new PublicCatalogueFixture(userRepository, roleRepository, vendorProfileRepository,
                serviceLocationRepository, categoryRepository, productRepository, inventoryRepository);
        origin = fixture.locationByPincode(KORAMANGALA_PINCODE);
        category = fixture.createCategory();
    }

    @Test
    void storefrontIsPubliclyAccessibleWithoutAToken() throws Exception {
        VendorProfile vendor = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product product = fixture.createProduct(vendor, category, "Storefront Item", ProductStatus.ACTIVE);
        fixture.createInventory(product, 10, 0);

        mockMvc.perform(get("/api/v1/vendors/" + vendor.getId() + "/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vendor.getId().intValue()))
                .andExpect(jsonPath("$.acceptingOrders").value(true))
                .andExpect(jsonPath("$.products.content[*].id", hasItem(product.getId().intValue())));
    }

    @Test
    void storefrontReturns404ForAnUnknownVendor() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/999999/storefront"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void storefrontReturns404ForANonApprovedVendor() throws Exception {
        VendorProfile pending = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.PENDING_APPROVAL, true);

        mockMvc.perform(get("/api/v1/vendors/" + pending.getId() + "/storefront"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void storefrontReturns404ForASuspendedVendor() throws Exception {
        VendorProfile suspended = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.SUSPENDED, true);

        mockMvc.perform(get("/api/v1/vendors/" + suspended.getId() + "/storefront"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void storefrontReturns404ForAVendorThatIsNotAcceptingOrders() throws Exception {
        VendorProfile closed = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, false);

        mockMvc.perform(get("/api/v1/vendors/" + closed.getId() + "/storefront"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void storefrontExcludesNonActiveAndOutOfStockProducts() throws Exception {
        VendorProfile vendor = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);

        Product active = fixture.createProduct(vendor, category, "Visible Item", ProductStatus.ACTIVE);
        fixture.createInventory(active, 10, 0);
        Product draft = fixture.createProduct(vendor, category, "Hidden Draft", ProductStatus.DRAFT);
        fixture.createInventory(draft, 10, 0);
        Product outOfStock = fixture.createProduct(vendor, category, "Hidden Sold Out", ProductStatus.ACTIVE);
        fixture.createInventory(outOfStock, 0, 0);

        mockMvc.perform(get("/api/v1/vendors/" + vendor.getId() + "/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.content[*].id", hasItem(active.getId().intValue())))
                .andExpect(jsonPath("$.products.content[*].id", not(hasItem(draft.getId().intValue()))))
                .andExpect(jsonPath("$.products.content[*].id", not(hasItem(outOfStock.getId().intValue()))));
    }

    @Test
    void storefrontDoesNotApplyDeliveryRadiusFiltering() throws Exception {
        // The vendor sits roughly 111 km away with a 5 km radius, yet its
        // storefront is still readable: the customer chose this vendor, and
        // radius is a discovery/search concern only.
        VendorProfile farVendor = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude().add(BigDecimal.ONE), origin.getLongitude(),
                new BigDecimal("5"), VendorProfile.Status.APPROVED, true);
        Product farProduct = fixture.createProduct(farVendor, category, "Far But Visible", ProductStatus.ACTIVE);
        fixture.createInventory(farProduct, 10, 0);

        mockMvc.perform(get("/api/v1/vendors/" + farVendor.getId() + "/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.content[*].id", hasItem(farProduct.getId().intValue())));
    }

    @Test
    void storefrontNeverExposesTheStorageKey() throws Exception {
        VendorProfile vendor = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product product = fixture.createProduct(vendor, category, "Key Probe Item", ProductStatus.ACTIVE);
        fixture.createInventory(product, 10, 0);

        String body = mockMvc.perform(get("/api/v1/vendors/" + vendor.getId() + "/storefront"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(body.contains("storageKey"),
                "The public storefront response must not expose image storage keys");
    }

    @Test
    void otherVendorEndpointsRemainProtected() throws Exception {
        // The public exception is scoped to the storefront GET route only.
        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isUnauthorized());
    }
}
