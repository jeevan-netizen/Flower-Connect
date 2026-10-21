package com.flowerconnect.search;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-boundary tests for the public product search (plan task 4.4).
 *
 * <p>Covers the discovery/search eligibility policy: ACTIVE products, available
 * stock, approved and accepting vendors, and delivery-radius filtering against
 * the selected service location.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchApiIntegrationTest extends AbstractIntegrationTest {

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
    void searchIsPubliclyAccessibleWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void searchRejectsAMissingLocationId() throws Exception {
        mockMvc.perform(get("/api/v1/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void searchRejectsAnUnknownLocationId() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unknown service location"));
    }

    @Test
    void searchRejectsAnUnknownSortValue() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("sort", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void searchReturnsAnActiveInStockProduct() throws Exception {
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product product = fixture.createProduct(vendor, category, "Searchable Roses", ProductStatus.ACTIVE);
        fixture.createInventory(product, 10, 0);

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(product.getId().intValue())))
                .andExpect(jsonPath("$.content[?(@.id==" + product.getId() + ")].name",
                        hasItem(org.hamcrest.Matchers.containsString("Searchable Roses"))))
                .andExpect(jsonPath("$.content[?(@.id==" + product.getId() + ")].available",
                        hasItem(10)));
    }

    @Test
    void searchExcludesNonActiveProducts() throws Exception {
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);

        Product draft = fixture.createProduct(vendor, category, "Draft Product", ProductStatus.DRAFT);
        fixture.createInventory(draft, 10, 0);
        Product inactive = fixture.createProduct(vendor, category, "Inactive Product", ProductStatus.INACTIVE);
        fixture.createInventory(inactive, 10, 0);
        Product archived = fixture.createProduct(vendor, category, "Archived Product", ProductStatus.ARCHIVED);
        fixture.createInventory(archived, 10, 0);

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(draft.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(inactive.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(archived.getId().intValue()))));
    }

    @Test
    void searchExcludesOutOfStockProducts() throws Exception {
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product outOfStock = fixture.createProduct(vendor, category, "Sold Out Product", ProductStatus.ACTIVE);
        fixture.createInventory(outOfStock, 0, 0);
        Product fullyReserved = fixture.createProduct(vendor, category, "Reserved Product", ProductStatus.ACTIVE);
        fixture.createInventory(fullyReserved, 5, 5);

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(outOfStock.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(fullyReserved.getId().intValue()))));
    }

    @Test
    void searchExcludesVendorsThatAreNotApprovedOrNotAccepting() throws Exception {
        VendorProfile pending = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.PENDING_APPROVAL, true);
        Product pendingProduct = fixture.createProduct(pending, category, "Pending Vendor Item", ProductStatus.ACTIVE);
        fixture.createInventory(pendingProduct, 10, 0);

        VendorProfile suspended = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.SUSPENDED, true);
        Product suspendedProduct = fixture.createProduct(suspended, category, "Suspended Vendor Item", ProductStatus.ACTIVE);
        fixture.createInventory(suspendedProduct, 10, 0);

        VendorProfile closed = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, false);
        Product closedProduct = fixture.createProduct(closed, category, "Closed Vendor Item", ProductStatus.ACTIVE);
        fixture.createInventory(closedProduct, 10, 0);

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(pendingProduct.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(suspendedProduct.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(closedProduct.getId().intValue()))));
    }

    @Test
    void searchExcludesVendorsOutsideTheirDeliveryRadius() throws Exception {
        // One degree of latitude is roughly 111 km; a 5 km radius cannot reach it.
        VendorProfile farVendor = fixture.createVendor(fixture.createFloristUser(), origin,
                origin.getLatitude().add(BigDecimal.ONE), origin.getLongitude(),
                new BigDecimal("5"), VendorProfile.Status.APPROVED, true);
        Product farProduct = fixture.createProduct(farVendor, category, "Far Away Item", ProductStatus.ACTIVE);
        fixture.createInventory(farProduct, 10, 0);

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(farProduct.getId().intValue()))));
    }

    @Test
    void searchFiltersByName() throws Exception {
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product tulips = fixture.createProduct(vendor, category, "Yellow Tulip Bunch", ProductStatus.ACTIVE);
        fixture.createInventory(tulips, 10, 0);
        Product roses = fixture.createProduct(vendor, category, "Red Rose Bunch", ProductStatus.ACTIVE);
        fixture.createInventory(roses, 10, 0);

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("q", "Tulip")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(tulips.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(roses.getId().intValue()))));
    }

    @Test
    void searchFiltersByCategory() throws Exception {
        Category otherCategory = fixture.createCategory();
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product inCategory = fixture.createProduct(vendor, category, "Categorised Item", ProductStatus.ACTIVE);
        fixture.createInventory(inCategory, 10, 0);
        Product otherProduct = fixture.createProduct(vendor, otherCategory, "Other Category Item", ProductStatus.ACTIVE);
        fixture.createInventory(otherProduct, 10, 0);

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("category", category.getId().toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(inCategory.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(otherProduct.getId().intValue()))));
    }

    @Test
    void searchNeverExposesTheStorageKey() throws Exception {
        VendorProfile vendor = fixture.createVendor(floristUser, origin,
                origin.getLatitude(), origin.getLongitude(),
                new BigDecimal("10"), VendorProfile.Status.APPROVED, true);
        Product product = fixture.createProduct(vendor, category, "Key Probe Item", ProductStatus.ACTIVE);
        fixture.createInventory(product, 10, 0);

        String body = mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(body.contains("storageKey"),
                "The public search response must not expose image storage keys");
    }

    @Test
    void searchPaginatesWithinTheReturnedPage() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", origin.getId().toString())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(0)));
    }

    /**
     * The public search matcher is method-scoped: only {@code GET} is public.
     *
     * <p>Spring Security's authorization filter runs before the dispatcher, so a
     * non-GET request under the search namespace is refused with 401 rather than
     * reaching MVC's 405. That distinction is what makes this a test of the
     * matcher rather than of the routing: with the older path-only matcher both
     * requests below would have been permitted anonymously.
     */
    @Test
    void aNonGetRequestUnderTheSearchNamespaceIsNotPublic() throws Exception {
        mockMvc.perform(post("/api/v1/search")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/search/anything")).andExpect(status().isUnauthorized());
    }
}
