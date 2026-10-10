package com.flowerconnect.storefront.controller;

import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.test.RoleBoundaryTester;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the public vendor storefront (plan task 4.5).
 *
 * <p>These run the whole Spring Boot application, so the production
 * {@code SecurityConfig} chain — including the public storefront exception
 * matcher placed above the {@code hasRole("FLORIST")} namespace rule — is
 * what actually answers each request. The storefront read is deliberately
 * performed with no {@code Authorization} header at all, which is the only
 * way to prove the route is public rather than merely reachable by a
 * particular role.
 *
 * <p>The shared singleton MySQL container accumulates rows across the whole
 * failsafe run, so every assertion is scoped to the rows each test created
 * (vendor ids, product ids) rather than to table counts or page positions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StorefrontControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String KORAMANGALA_PINCODE = "560034";
    private static final String UNKNOWN_ID = "999999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceLocationRepository serviceLocationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VendorProfileRepository vendorProfileRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Long koramangalaId;

    @BeforeEach
    void setUp() {
        koramangalaId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE)
                .orElseThrow().getId();
    }

    // ---------------------------------------------------------------- fixtures

    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        String uniqueEmail = email.replaceFirst("@", UUID.randomUUID() + "@");
        // Random decimal digits, not a UUID: a phone pattern rejects the hex
        // letters and the failure surfaces as a 400 that masks the test.
        String uniquePhone = String.format("%010d", new Random().nextInt(1_000_000_000));

        User user = User.builder()
                .email(uniqueEmail)
                .passwordHash("dummy")
                .fullName("Test User")
                .phone(uniquePhone)
                .status(User.Status.ACTIVE)
                .build();
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private VendorProfile createVendorProfile(User user, VendorProfile.Status status) {
        return createVendorProfile(user, status, true);
    }

    private VendorProfile createVendorProfile(User user, VendorProfile.Status status, boolean acceptingOrders) {
        ServiceLocation location = serviceLocationRepository.findById(koramangalaId).orElseThrow();
        VendorProfile profile = VendorProfile.builder()
                .user(user)
                .businessName("Storefront Test Florist " + UUID.randomUUID())
                .description("A test storefront")
                .logoUrl("https://example.test/logo.png")
                .addressLine1("123 Test Street")
                .addressLine2("")
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(status)
                .commissionRate(null)
                .avgRating(null)
                .reviewCount(0)
                .minOrderAmount(new BigDecimal("199.00"))
                .baseDeliveryFee(new BigDecimal("39.00"))
                .perKmFee(new BigDecimal("12.00"))
                .freeDeliveryAbove(new BigDecimal("799.00"))
                .prepTimeMinutes(45)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(acceptingOrders)
                .build();
        return vendorProfileRepository.saveAndFlush(profile);
    }

    private Category createCategory(String name) {
        Category category = Category.builder()
                .name(name)
                .slug(name.toLowerCase().replaceAll("[^a-z0-9]", "-")
                        + "-" + UUID.randomUUID().toString().substring(0, 8))
                .displayOrder(0)
                .active(true)
                .build();
        return categoryRepository.saveAndFlush(category);
    }

    /** Creates a product together with its single inventory row. */
    private Product createProduct(VendorProfile vendor, Category category, String name,
                                  ProductStatus status, int quantity, int reservedQuantity) {
        Product product = Product.builder()
                .vendor(vendor)
                .category(category)
                .name(name)
                .slug(name.toLowerCase().replaceAll("[^a-z0-9]", "-")
                        + "-" + UUID.randomUUID().toString().substring(0, 8))
                .description("Test product description")
                .basePrice(new BigDecimal("249.00"))
                .status(status)
                .build();
        Product saved = productRepository.saveAndFlush(product);

        inventoryRepository.saveAndFlush(Inventory.builder()
                .product(saved)
                .quantity(quantity)
                .reservedQuantity(reservedQuantity)
                .lowStockThreshold(5)
                .build());
        return saved;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void anAnonymousCallerGetsAnApprovedStorefront() throws Exception {
        User user = createUser("storefrontAnon@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        Category category = createCategory("Storefront Anon Roses");
        Product product = createProduct(vendor, category, "Storefront Anon Rose",
                ProductStatus.ACTIVE, 10, 0);

        // No Authorization header at all: the route must be genuinely public.
        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendor.id").value(vendor.getId().intValue()))
                .andExpect(jsonPath("$.vendor.businessName").exists())
                .andExpect(jsonPath("$.vendor.city").value("Bengaluru"))
                .andExpect(jsonPath("$.vendor.area").value("Koramangala"))
                .andExpect(jsonPath("$.vendor.pincode").value("560034"))
                .andExpect(jsonPath("$.vendor.acceptingOrders").value(true))
                .andExpect(jsonPath("$.products.content[*].id",
                        org.hamcrest.Matchers.hasItem(product.getId().intValue())))
                .andExpect(jsonPath("$.products.content[?(@.id==" + product.getId() + ")].inStock").value(true));

        cleanupOf(product, category, vendor, user);
    }

    @Test
    void everyAuthenticatedRoleCanReadThePublicStorefront() throws Exception {
        User user = createUser("storefrontRoles@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        Category category = createCategory("Storefront Roles Roses");
        Product product = createProduct(vendor, category, "Storefront Roles Rose",
                ProductStatus.ACTIVE, 10, 0);

        String path = "/api/v1/vendors/" + vendor.getId() + "/storefront";
        new RoleBoundaryTester(mockMvc).assertAllRoles(path, HttpMethod.GET, 200, 200, 200);
        new RoleBoundaryTester(mockMvc).assertUnauthenticated(path, HttpMethod.GET, 200);

        cleanupOf(product, category, vendor, user);
    }

    @Test
    void anUnknownVendorIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", UNKNOWN_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * A pending, rejected or suspended vendor has no public storefront, and the
     * response is the same one an unknown id gets — same code, same message —
     * so the endpoint does not leak which vendor ids exist.
     */
    @Test
    void aNonApprovedVendorIsNotFound() throws Exception {
        for (VendorProfile.Status status : List.of(
                VendorProfile.Status.PENDING_APPROVAL,
                VendorProfile.Status.REJECTED,
                VendorProfile.Status.SUSPENDED)) {

            User user = createUser("storefront" + status + "@example.com", "FLORIST");
            VendorProfile vendor = createVendorProfile(user, status);

            mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Storefront not found"));

            cleanupOf(null, vendor, user);
        }
    }

    @Test
    void aNonApprovedResponseIsIndistinguishableFromAnUnknownId() throws Exception {
        User user = createUser("storefrontHidden@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.PENDING_APPROVAL);

        String unknownMessage = errorMessageAt("/api/v1/vendors/" + UNKNOWN_ID + "/storefront");
        String pendingMessage = errorMessageAt("/api/v1/vendors/" + vendor.getId() + "/storefront");

        assertThat(pendingMessage).isEqualTo(unknownMessage).isEqualTo("Storefront not found");

        cleanupOf(null, vendor, user);
    }

    @Test
    void anApprovedVendorWhoHasPausedOrderingIsStillReadable() throws Exception {
        User user = createUser("storefrontPaused@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED, false);
        Category category = createCategory("Storefront Paused Roses");
        Product product = createProduct(vendor, category, "Storefront Paused Rose",
                ProductStatus.ACTIVE, 10, 0);

        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendor.acceptingOrders").value(false))
                .andExpect(jsonPath("$.products.content[*].id",
                        org.hamcrest.Matchers.hasItem(product.getId().intValue())));

        cleanupOf(product, category, vendor, user);
    }

    @Test
    void onlyActiveProductsAreReturned() throws Exception {
        User user = createUser("storefrontActive@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        Category category = createCategory("Storefront Active Roses");

        Product active = createProduct(vendor, category, "Storefront Active Only Rose",
                ProductStatus.ACTIVE, 10, 0);
        Product draft = createProduct(vendor, category, "Storefront Draft Rose",
                ProductStatus.DRAFT, 10, 0);
        Product inactive = createProduct(vendor, category, "Storefront Inactive Rose",
                ProductStatus.INACTIVE, 10, 0);
        Product archived = createProduct(vendor, category, "Storefront Archived Rose",
                ProductStatus.ARCHIVED, 10, 0);

        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId())
                .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.content[*].id",
                        org.hamcrest.Matchers.hasItem(active.getId().intValue())))
                .andExpect(jsonPath("$.products.content[*].id",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(draft.getId().intValue()))))
                .andExpect(jsonPath("$.products.content[*].id",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(inactive.getId().intValue()))))
                .andExpect(jsonPath("$.products.content[*].id",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(archived.getId().intValue()))))
                .andExpect(jsonPath("$.products.content.length()").value(1));

        cleanup(List.of(active, draft, inactive, archived), category, vendor, user);
    }

    @Test
    void anOutOfStockActiveProductStaysOnThePage() throws Exception {
        User user = createUser("storefrontStock@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        Category category = createCategory("Storefront Stock Roses");

        // Available stock is quantity - reservedQuantity, so this row has none
        // available but every unit is reserved.
        Product outOfStock = createProduct(vendor, category, "Storefront Out Of Stock Rose",
                ProductStatus.ACTIVE, 10, 10);
        Product inStock = createProduct(vendor, category, "Storefront In Stock Rose",
                ProductStatus.ACTIVE, 12, 4);

        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId())
                .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.content[?(@.id==" + outOfStock.getId() + ")].inStock").value(false))
                .andExpect(jsonPath("$.products.content[?(@.id==" + inStock.getId() + ")].inStock").value(true));

        cleanup(List.of(outOfStock, inStock), category, vendor, user);
    }

    @Test
    void paginationReportsTheStandardEnvelopeTotals() throws Exception {
        User user = createUser("storefrontPage@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        Category category = createCategory("Storefront Page Roses");

        Product[] products = new Product[3];
        for (int i = 0; i < products.length; i++) {
            products[i] = createProduct(vendor, category, "Storefront Page Rose " + i + " " + UUID.randomUUID(),
                    ProductStatus.ACTIVE, 10, 0);
        }

        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId())
                .param("size", "2")
                .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.content.length()").value(2))
                .andExpect(jsonPath("$.products.totalElements").value(3))
                .andExpect(jsonPath("$.products.totalPages").value(2))
                .andExpect(jsonPath("$.products.first").value(true))
                .andExpect(jsonPath("$.products.last").value(false))
                .andExpect(jsonPath("$.products.empty").value(false));

        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId())
                .param("size", "2")
                .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.content.length()").value(1))
                .andExpect(jsonPath("$.products.first").value(false))
                .andExpect(jsonPath("$.products.last").value(true));

        cleanup(List.of(products), category, vendor, user);
    }

    @Test
    void anActiveProductOrderIsStableAcrossPages() throws Exception {
        User user = createUser("storefrontOrder@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        Category category = createCategory("Storefront Order Roses");

        Product[] products = new Product[4];
        for (int i = 0; i < products.length; i++) {
            products[i] = createProduct(vendor, category, "Storefront Order Rose " + i + " " + UUID.randomUUID(),
                    ProductStatus.ACTIVE, 10, 0);
        }

        // Walk both pages and confirm the same rows are seen once, in the same
        // relative order: createdAt desc, id desc.
        List<Long> pageOne = idsOnPage(vendor.getId(), 0, 2);
        List<Long> pageTwo = idsOnPage(vendor.getId(), 1, 2);

        assertThat(pageOne).hasSize(2);
        assertThat(pageTwo).hasSize(2);
        assertThat(pageOne).doesNotContainAnyElementsOf(pageTwo);
        assertThat(products).allSatisfy(p ->
                assertThat(pageOne.contains(p.getId()) || pageTwo.contains(p.getId())).isTrue());
        // Newest first: every id on page one is greater than every id on page two.
        assertThat(pageOne.get(0)).isGreaterThan(pageTwo.get(1));

        cleanup(List.of(products), category, vendor, user);
    }

    @Test
    void aVendorWithNoActiveProductsReturnsAnEmptyPage() throws Exception {
        User user = createUser("storefrontEmpty@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        Category category = createCategory("Storefront Empty Roses");
        Product draft = createProduct(vendor, category, "Storefront Empty Draft Rose",
                ProductStatus.DRAFT, 10, 0);

        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.content").isEmpty())
                .andExpect(jsonPath("$.products.totalElements").value(0))
                .andExpect(jsonPath("$.products.totalPages").value(0))
                .andExpect(jsonPath("$.products.empty").value(true))
                // The profile half is still returned, so the banner renders.
                .andExpect(jsonPath("$.vendor.id").value(vendor.getId().intValue()));

        cleanupOf(draft, category, vendor, user);
    }

    @Test
    void approvalStatusIsReReadOnEveryRequest() throws Exception {
        User user = createUser("storefrontFlip@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        Category category = createCategory("Storefront Flip Roses");
        Product product = createProduct(vendor, category, "Storefront Flip Rose",
                ProductStatus.ACTIVE, 10, 0);

        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId()))
                .andExpect(status().isOk());

        // An admin suspends the vendor: the storefront must disappear on the
        // very next request, with no token to re-issue and nothing to flush.
        vendor.setStatus(VendorProfile.Status.SUSPENDED);
        vendorProfileRepository.saveAndFlush(vendor);

        mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendor.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        cleanupOf(product, category, vendor, user);
    }

    @Test
    void aNonPositiveVendorIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/0/storefront"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidPaginationParametersAreRejected() throws Exception {
        User user = createUser("storefrontValidation@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        String path = "/api/v1/vendors/" + vendor.getId() + "/storefront";

        mockMvc.perform(get(path).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(path).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(path).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        cleanupOf(null, vendor, user);
    }

    // ------------------------------------------------------- security boundary

    /**
     * The public exception must stay narrow. Anonymous callers keep full
     * access to the catalog, profile and inventory routes, and a path one
     * segment longer than the storefront is not covered by the wildcard —
     * both fall through to the FLORIST namespace rule and are 401.
     */
    @Test
    void neighbouringVendorRoutesStayProtectedForAnonymousCallers() throws Exception {
        User user = createUser("storefrontNeighbour@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, VendorProfile.Status.APPROVED);
        Category category = createCategory("Storefront Neighbour Roses");
        Product product = createProduct(vendor, category, "Storefront Neighbour Rose",
                ProductStatus.ACTIVE, 10, 0);

        Long id = vendor.getId();
        Long productId = product.getId();

        for (String protectedPath : List.of(
                "/api/v1/vendors/profile",
                "/api/v1/vendors/products",
                "/api/v1/vendors/products/" + productId,
                "/api/v1/vendors/products/" + productId + "/inventory",
                "/api/v1/vendors/products/" + productId + "/images",
                "/api/v1/vendors/products/" + productId + "/inventory/movements",
                "/api/v1/vendors/inventory/low-stock")) {
            mockMvc.perform(get(protectedPath))
                    .andExpect(status().isUnauthorized());
        }

        // A PUT to the public path is not covered by the GET exception either.
        mockMvc.perform(put("/api/v1/vendors/{id}/storefront", id))
                .andExpect(status().isUnauthorized());

        // One segment deeper than the wildcard also falls through.
        mockMvc.perform(get("/api/v1/vendors/{id}/storefront/extra", id))
                .andExpect(status().isUnauthorized());

        // The registration entry point stays public by design.
        mockMvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk());

        cleanupOf(product, category, vendor, user);
    }

    // ----------------------------------------------------------------- helpers

    private List<Long> idsOnPage(Long vendorId, int page, int size) throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/vendors/{id}/storefront", vendorId)
                .param("size", String.valueOf(size))
                .param("page", String.valueOf(page)));
        result.andExpect(status().isOk());

        // Jayway parses only the products array, so the ids are the product
        // ids and not the vendor id sitting beside them.
        List<?> ids = JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.products.content[*].id");
        return ids.stream()
                .map(id -> ((Number) id).longValue())
                .toList();
    }

    private String errorMessageAt(String path) throws Exception {
        ResultActions result = mockMvc.perform(get(path));
        result.andExpect(status().isNotFound());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.message");
    }

    private void cleanupOf(Category category, VendorProfile vendor, User user) {
        cleanup(List.of(), category, vendor, user);
    }

    private void cleanupOf(Product product, Category category, VendorProfile vendor, User user) {
        cleanup(product == null ? List.of() : List.of(product), category, vendor, user);
    }

    private void cleanup(List<Product> products, Category category, VendorProfile vendor, User user) {
        if (products != null && !products.isEmpty()) {
            productRepository.deleteAllByIdInBatch(products.stream().map(Product::getId).toList());
        }
        if (vendor != null) {
            vendorProfileRepository.deleteAllByIdInBatch(List.of(vendor.getId()));
        }
        if (user != null) {
            userRepository.deleteAllByIdInBatch(List.of(user.getId()));
        }
        if (category != null) {
            categoryRepository.deleteAllByIdInBatch(List.of(category.getId()));
        }
    }
}
