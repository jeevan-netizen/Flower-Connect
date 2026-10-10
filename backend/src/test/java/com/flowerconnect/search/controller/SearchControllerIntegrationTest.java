package com.flowerconnect.search.controller;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Random;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String KORAMANGALA_PINCODE = "560034";

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

    // Helper to create a user with a given role
    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        String uniqueEmail = email.replaceFirst("@", UUID.randomUUID() + "@");
        Random random = new Random();
        String uniquePhone = String.format("%010d", random.nextInt(1000000000));

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

    // Helper to create a vendor profile
    private VendorProfile createVendorProfile(User user, ServiceLocation location, BigDecimal deliveryRadiusKm, boolean acceptingOrders) {
        VendorProfile profile = VendorProfile.builder()
                .user(user)
                .businessName("Test Vendor " + UUID.randomUUID())
                .description("Test description")
                .addressLine1("123 Test Street")
                .addressLine2("")
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .deliveryRadiusKm(deliveryRadiusKm)
                .status(VendorProfile.Status.APPROVED)
                .commissionRate(null)
                .avgRating(null)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.valueOf(100))
                .baseDeliveryFee(BigDecimal.valueOf(10))
                .perKmFee(BigDecimal.valueOf(2))
                .freeDeliveryAbove(BigDecimal.valueOf(1000))
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(acceptingOrders)
                .build();
        return vendorProfileRepository.saveAndFlush(profile);
    }

    // Helper to create a category
    private Category createCategory(String name, Long parentId, boolean active) {
        Category category = Category.builder()
                .name(name)
                .slug(name.toLowerCase().replaceAll("[^a-z0-9]", "-") + "-" + UUID.randomUUID().toString().substring(0, 8))
                .displayOrder(0)
                .active(active)
                .build();
        if (parentId != null) {
            Category parent = categoryRepository.findById(parentId).orElseThrow();
            category.setParent(parent);
        }
        return categoryRepository.saveAndFlush(category);
    }

    // Helper to create a product
    private Product createProduct(VendorProfile vendor, Category category, String name, BigDecimal basePrice, ProductStatus status) {
        Product product = Product.builder()
                .vendor(vendor)
                .category(category)
                .name(name)
                .slug(name.toLowerCase().replaceAll("[^a-z0-9]", "-") + "-" + UUID.randomUUID().toString().substring(0, 8))
                .description("Test product description")
                .basePrice(basePrice)
                .status(status)
                .build();
        Product saved = productRepository.saveAndFlush(product);

        // Create inventory with available stock
        Inventory inventory = Inventory.builder()
                .product(saved)
                .quantity(10) // More than reserved to ensure availability
                .reservedQuantity(0)
                .lowStockThreshold(5)
                .build();
        inventoryRepository.saveAndFlush(inventory);

        return saved;
    }

    // Clean up helper to avoid test interference
    private void cleanupTestData(List<Long> productIds, List<Long> vendorIds, List<Long> userIds) {
        if (productIds != null) {
            productRepository.deleteAllByIdInBatch(productIds);
        }
        if (vendorIds != null) {
            vendorProfileRepository.deleteAllByIdInBatch(vendorIds);
        }
        if (userIds != null) {
            userRepository.deleteAllByIdInBatch(userIds);
        }
    }

    /**
     * Deletes the categories a test created, newest-first, so a parent is never
     * deleted while a child still references it. ``categories`` must be given in
     * creation order (parent first).
     */
    private void cleanupCategories(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        for (int i = categories.size() - 1; i >= 0; i--) {
            categoryRepository.delete(categories.get(i));
        }
    }

    @Test
    void searchReturnsProductsWithinDeliveryRadius() throws Exception {
        // Given: a service location and vendors at different distances
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();

        // Vendor A: same location (distance 0)
        User userA = createUser("searchVendorA@example.com", "FLORIST");
        VendorProfile vendorA = createVendorProfile(userA, origin, new BigDecimal("5"), true);

        // Vendor B: far away (100 km), will be filtered out
        User userB = createUser("searchVendorB@example.com", "FLORIST");
        VendorProfile vendorB = createVendorProfile(userB, origin, new BigDecimal("5"), true);
        BigDecimal oneDegree = new BigDecimal("1");
        vendorB.setLatitude(origin.getLatitude().add(oneDegree));
        vendorB.setLongitude(origin.getLongitude());
        vendorProfileRepository.saveAndFlush(vendorB);

        // Create categories
        Category categoryRoses = createCategory("Roses", null, true);
        Category categoryTulips = createCategory("Tulips", null, true);

        // Create products for Vendor A
        Product productA1 = createProduct(vendorA, categoryRoses, "Red Rose", new BigDecimal("100"), ProductStatus.ACTIVE);
        Product productA2 = createProduct(vendorA, categoryTulips, "White Tulip", new BigDecimal("80"), ProductStatus.ACTIVE);

        // Create product for Vendor B (far away - will be filtered out)
        Product productB = createProduct(vendorB, categoryRoses, "Rose from Far Away", new BigDecimal("120"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(productA1.getId(), productA2.getId(), productB.getId());
        List<Long> vendorIds = List.of(vendorA.getId(), vendorB.getId());
        List<Long> userIds = List.of(userA.getId(), userB.getId());

        try {
            // When: search for products near the origin location
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: Vendor A's products should be present, Vendor B's product excluded
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[*].id", hasItem(productA1.getId().intValue())))
                    .andExpect(jsonPath("$.content[*].id", hasItem(productA2.getId().intValue())))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(productB.getId().intValue()))))
                    // Verify distances for Vendor A's products (should be 0)
                    .andExpect(jsonPath("$.content[?(@.id=='" + productA1.getId() + "')].distanceKm").value(0.0))
                    .andExpect(jsonPath("$.content[?(@.id=='" + productA2.getId() + "')].distanceKm").value(0.0));

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(categoryRoses, categoryTulips));
        }
    }

    @Test
    void searchReturns400WhenLocationIdIsMissing() throws Exception {
        // Spring rejects an absent required parameter before bean validation runs,
        // so the message comes from GlobalExceptionHandler's
        // MissingServletRequestParameterException handler — the same one discovery asserts.
        mockMvc.perform(get("/api/v1/search")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Required parameter 'locationId' is not present"));
    }

    @Test
    void searchReturns400WhenLocationIdIsUnknown() throws Exception {
        Long unknownLocationId = 999999L;

        mockMvc.perform(get("/api/v1/search")
                .param("locationId", unknownLocationId.toString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unknown service location"));
    }

    @Test
    void searchReturns400WhenPriceMinExceedsPriceMax() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                .param("locationId", koramangalaId.toString())
                .param("priceMin", "200")
                .param("priceMax", "100")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("priceMin must not exceed priceMax"));
    }

    @Test
    void searchReturns400WhenSortIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                .param("locationId", koramangalaId.toString())
                .param("sort", "invalidSort")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid sort value: invalidSort"));
    }

    @Test
    void searchReturns400WhenCategoryIsUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                .param("locationId", koramangalaId.toString())
                .param("category", "999999")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unknown category"));
    }

    @Test
    void searchReturns400WhenCategoryIsInactive() throws Exception {
        // Create an inactive category
        Category inactiveCategory = createCategory("Inactive Category", null, false);

        try {
            mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("category", inactiveCategory.getId().toString())
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("Category is inactive"));
        } finally {
            categoryRepository.delete(inactiveCategory);
        }
    }

    @Test
    void searchExcludesProductsUnderAnInactiveIntermediateCategory() throws Exception {
        // D-17/D-35 publication rule: the category filter walks the whole
        // subtree but prunes at an inactive node, so a product under an ACTIVE
        // child of an INACTIVE category is NOT published, while a product
        // directly under the active root still is.
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();
        User user = createUser("searchPruneVendor@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, origin, new BigDecimal("10"), true);

        Category activeRoot = createCategory("Prune Root", null, true);
        Category inactiveMiddle = createCategory("Prune Inactive Middle", activeRoot.getId(), false);
        Category activeChild = createCategory("Prune Active Child", inactiveMiddle.getId(), true);

        Product publishedProduct = createProduct(vendor, activeRoot, "Published Under Root", new BigDecimal("100"), ProductStatus.ACTIVE);
        Product hiddenProduct = createProduct(vendor, activeChild, "Hidden Under Inactive Middle", new BigDecimal("90"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(publishedProduct.getId(), hiddenProduct.getId());
        List<Long> vendorIds = List.of(vendor.getId());
        List<Long> userIds = List.of(user.getId());

        try {
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("category", activeRoot.getId().toString())
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON));

            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[*].id", hasItem(publishedProduct.getId().intValue())))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(hiddenProduct.getId().intValue()))))
                    .andExpect(jsonPath("$.content[*].name", hasItem("Published Under Root")));

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(activeRoot, inactiveMiddle, activeChild));
        }
    }

    @Test
    void searchFiltersByQuery() throws Exception {
        // Given: create products with different names
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();
        User user = createUser("searchQueryVendor@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, origin, new BigDecimal("10"), true);
        Category category = createCategory("Test Category", null, true);

        Product productRose = createProduct(vendor, category, "Red Rose", new BigDecimal("100"), ProductStatus.ACTIVE);
        Product productTulip = createProduct(vendor, category, "White Tulip", new BigDecimal("80"), ProductStatus.ACTIVE);
        Product productDaisy = createProduct(vendor, category, "Yellow Daisy", new BigDecimal("60"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(productRose.getId(), productTulip.getId(), productDaisy.getId());
        List<Long> vendorIds = List.of(vendor.getId());
        List<Long> userIds = List.of(user.getId());

        try {
            // When: search for products containing "Rose" in name
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("q", "Rose")
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: only Rose product should be returned
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[*].id", hasItem(productRose.getId().intValue())))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(productTulip.getId().intValue()))))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(productDaisy.getId().intValue()))))
                    .andExpect(jsonPath("$.content[*].name", hasItem("Red Rose")));

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(category));
        }
    }

    @Test
    void searchFiltersByCategory() throws Exception {
        // Given: create two categories and products in each
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();
        User user = createUser("searchCategoryVendor@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, origin, new BigDecimal("10"), true);

        // Create categories
        Category flowersCategory = createCategory("Flowers", null, true);
        Category plantsCategory = createCategory("Plants", null, true);

        // Create products - one in each category
        Product productFlower = createProduct(vendor, flowersCategory, "Flower Pot", new BigDecimal("50"), ProductStatus.ACTIVE);
        Product productPlant = createProduct(vendor, plantsCategory, "Plant Pot", new BigDecimal("40"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(productFlower.getId(), productPlant.getId());
        List<Long> vendorIds = List.of(vendor.getId());
        List<Long> userIds = List.of(user.getId());

        try {
            // When: search for products in Flowers category
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("category", flowersCategory.getId().toString())
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: only Flower product should be returned
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[*].id", hasItem(productFlower.getId().intValue())))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(productPlant.getId().intValue()))))
                    .andExpect(jsonPath("$.content[*].categoryName", hasItem("Flowers")));

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(flowersCategory, plantsCategory));
        }
    }

    @Test
    void searchFiltersByPriceRange() throws Exception {
        // Given: create products with different prices
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();
        User user = createUser("searchPriceVendor@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, origin, new BigDecimal("10"), true);
        Category category = createCategory("Price Category", null, true);

        Product productCheap = createProduct(vendor, category, "Cheap Product", new BigDecimal("30"), ProductStatus.ACTIVE);
        Product productInRange = createProduct(vendor, category, "In Range Product", new BigDecimal("100"), ProductStatus.ACTIVE);
        Product productExpensive = createProduct(vendor, category, "Expensive Product", new BigDecimal("200"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(productCheap.getId(), productInRange.getId(), productExpensive.getId());
        List<Long> vendorIds = List.of(vendor.getId());
        List<Long> userIds = List.of(user.getId());

        try {
            // When: search for products priced between $50 and $150
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("priceMin", "50")
                    .param("priceMax", "150")
                    .param("vendorId", vendor.getId().toString())
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: only the in-range product should be returned
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[*].id", hasItem(productInRange.getId().intValue())))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(productCheap.getId().intValue()))))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(productExpensive.getId().intValue()))));

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(category));
        }
    }

    @Test
    void searchFiltersByVendorId() throws Exception {
        // Given: create two vendors with products
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();

        // Vendor A
        User userA = createUser("searchVendorA2@example.com", "FLORIST");
        VendorProfile vendorA = createVendorProfile(userA, origin, new BigDecimal("10"), true);
        Category category = createCategory("Filter Vendor Category", null, true);
        Product productA = createProduct(vendorA, category, "Vendor A Product", new BigDecimal("100"), ProductStatus.ACTIVE);

        // Vendor B
        User userB = createUser("searchVendorB2@example.com", "FLORIST");
        VendorProfile vendorB = createVendorProfile(userB, origin, new BigDecimal("10"), true);
        Product productB = createProduct(vendorB, category, "Vendor B Product", new BigDecimal("150"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(productA.getId(), productB.getId());
        List<Long> vendorIds = List.of(vendorA.getId(), vendorB.getId());
        List<Long> userIds = List.of(userA.getId(), userB.getId());

        try {
            // When: search for products from Vendor A only
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("vendorId", vendorA.getId().toString())
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: only Vendor A's product should be returned
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[*].id", hasItem(productA.getId().intValue())))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(productB.getId().intValue()))))
                    .andExpect(jsonPath("$.content[*].vendorId", hasItem(vendorA.getId().intValue())));

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(category));
        }
    }

    @Test
    void searchSortsByDistance() throws Exception {
        // Given: create vendors at different distances
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();

        // Vendor A: same location
        User userA = createUser("searchSortVendorA@example.com", "FLORIST");
        VendorProfile vendorA = createVendorProfile(userA, origin, new BigDecimal("10"), true);
        Category category = createCategory("Sort Category", null, true);
        Product productA = createProduct(vendorA, category, "Vendor A Product", new BigDecimal("100"), ProductStatus.ACTIVE);

        // Vendor B: 1 km away
        User userB = createUser("searchSortVendorB@example.com", "FLORIST");
        VendorProfile vendorB = createVendorProfile(userB, origin, new BigDecimal("10"), true);
        BigDecimal oneKmInLat = new BigDecimal("1").divide(new BigDecimal("111"), 6, BigDecimal.ROUND_HALF_UP);
        vendorB.setLatitude(origin.getLatitude().add(oneKmInLat));
        vendorB.setLongitude(origin.getLongitude());
        vendorB = vendorProfileRepository.saveAndFlush(vendorB);
        Product productB = createProduct(vendorB, category, "Vendor B Product", new BigDecimal("120"), ProductStatus.ACTIVE);

        // Vendor C: 2 km away
        User userC = createUser("searchSortVendorC@example.com", "FLORIST");
        VendorProfile vendorC = createVendorProfile(userC, origin, new BigDecimal("10"), true);
        BigDecimal twoKmInLat = oneKmInLat.multiply(new BigDecimal("2"));
        vendorC.setLatitude(origin.getLatitude().add(twoKmInLat));
        vendorC.setLongitude(origin.getLongitude());
        vendorC = vendorProfileRepository.saveAndFlush(vendorC);
        Product productC = createProduct(vendorC, category, "Vendor C Product", new BigDecimal("140"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(productA.getId(), productB.getId(), productC.getId());
        List<Long> vendorIds = List.of(vendorA.getId(), vendorB.getId(), vendorC.getId());
        List<Long> userIds = List.of(userA.getId(), userB.getId(), userC.getId());

        try {
            // When: search with default sort (distance)
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: products should be sorted by distance (A closest, B middle, C farthest)
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[*].id", hasItems(productA.getId().intValue(), productB.getId().intValue(), productC.getId().intValue())));

            // Verify the order in the response array
            result.andExpect(result1 -> {
                String content = result1.getResponse().getContentAsString();
                int indexA = content.indexOf("\"id\":" + productA.getId());
                int indexB = content.indexOf("\"id\":" + productB.getId());
                int indexC = content.indexOf("\"id\":" + productC.getId());
                org.junit.jupiter.api.Assertions.assertTrue(indexA >= 0 && indexB >= 0 && indexC >= 0,
                        "All three products should be in results");
                org.junit.jupiter.api.Assertions.assertTrue(indexA < indexB && indexB < indexC,
                        "Products should be sorted by distance: A (0km) < B (1km) < C (2km)");
            });

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(category));
        }
    }

    @Test
    void searchSortsByPriceDesc() throws Exception {
        // Given: create products with different prices
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();
        User user = createUser("searchPriceDescVendor@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, origin, new BigDecimal("10"), true);
        Category category = createCategory("Price Desc Category", null, true);

        Product productCheap = createProduct(vendor, category, "Cheap Product", new BigDecimal("50"), ProductStatus.ACTIVE);
        Product productExpensive = createProduct(vendor, category, "Expensive Product", new BigDecimal("200"), ProductStatus.ACTIVE);
        Product productMedium = createProduct(vendor, category, "Medium Product", new BigDecimal("100"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(productCheap.getId(), productExpensive.getId(), productMedium.getId());
        List<Long> vendorIds = List.of(vendor.getId());
        List<Long> userIds = List.of(user.getId());

        try {
            // When: search with price_desc sort
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("vendorId", vendor.getId().toString())
                    .param("sort", "price_desc")
                    .param("page", "0")
                    .param("size", "100")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: products should be sorted by price descending, in order
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(result1 -> {
                        String content = result1.getResponse().getContentAsString();
                        int indexExpensive = content.indexOf("\"id\":" + productExpensive.getId());
                        int indexMedium = content.indexOf("\"id\":" + productMedium.getId());
                        int indexCheap = content.indexOf("\"id\":" + productCheap.getId());
                        org.junit.jupiter.api.Assertions.assertTrue(
                                indexExpensive >= 0 && indexMedium >= 0 && indexCheap >= 0,
                                "All three products should be in results");
                        org.junit.jupiter.api.Assertions.assertTrue(
                                indexExpensive < indexMedium && indexMedium < indexCheap,
                                "Products should be sorted by price descending: 200 > 100 > 50");
                    });

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(category));
        }
    }

    @Test
    void searchPaginationWorks() throws Exception {
        // Given: create multiple products
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();
        User user = createUser("searchPageVendor@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, origin, new BigDecimal("10"), true);
        Category category = createCategory("Pagination Category", null, true);

        Product[] products = new Product[5];
        for (int i = 0; i < 5; i++) {
            products[i] = createProduct(vendor, category, "Product " + i, new BigDecimal((i + 1) * 10), ProductStatus.ACTIVE);
        }

        List<Long> productIds = List.of(
                products[0].getId(), products[1].getId(), products[2].getId(),
                products[3].getId(), products[4].getId());
        List<Long> vendorIds = List.of(vendor.getId());
        List<Long> userIds = List.of(user.getId());

        try {
            // When: request first page (size 2) — scoped to this test's vendor so
            // the shared database's other products cannot shift the totals
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("vendorId", vendor.getId().toString())
                    .param("page", "0")
                    .param("size", "2")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: first page should have 2 products
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(false));

            // When: request second page
            result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("vendorId", vendor.getId().toString())
                    .param("page", "1")
                    .param("size", "2")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: second page should have 2 products
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.first").value(false))
                    .andExpect(jsonPath("$.last").value(false));

            // When: request third page (last page with 1 item)
            result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("vendorId", vendor.getId().toString())
                    .param("page", "2")
                    .param("size", "2")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: third page should have 1 product
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.last").value(true));

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(category));
        }
    }

    @Test
    void searchReturnsEmptyPageWhenNoResults() throws Exception {
        // Given: create a vendor with a product
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();
        User user = createUser("searchEmptyVendor@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, origin, new BigDecimal("5"), true);
        Category category = createCategory("Empty Category", null, true);
        Product product = createProduct(vendor, category, "Existing Product", new BigDecimal("100"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(product.getId());
        List<Long> vendorIds = List.of(vendor.getId());
        List<Long> userIds = List.of(user.getId());

        try {
            // When: search with a filter that won't match anything (price below
            // every possible base_price), scoped to this test's vendor
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("vendorId", vendor.getId().toString())
                    .param("priceMax", "1")
                    .param("page", "0")
                    .param("size", "10")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: should return empty page
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.empty").value(true));

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(category));
        }
    }

    @Test
    void searchPublicAccessNoAuthenticationRequired() throws Exception {
        // When: unauthenticated request to search endpoint
        ResultActions result = mockMvc.perform(get("/api/v1/search")
                .param("locationId", koramangalaId.toString())
                .param("page", "0")
                .param("size", "10")
                .accept(MediaType.APPLICATION_JSON));

        // Then: should succeed without authentication (public endpoint)
        result.andExpect(status().isOk());
    }

    @Test
    void searchUnknownVendorIdReturnsEmptyPage() throws Exception {
        // Given: create a vendor and product
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId).orElseThrow();
        User user = createUser("searchUnknownVendor@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, origin, new BigDecimal("10"), true);
        Category category = createCategory("Unknown Vendor Category", null, true);
        Product product = createProduct(vendor, category, "Vendor Product", new BigDecimal("100"), ProductStatus.ACTIVE);

        List<Long> productIds = List.of(product.getId());
        List<Long> vendorIds = List.of(vendor.getId());
        List<Long> userIds = List.of(user.getId());

        try {
            // When: search with a non-existent vendor ID
            ResultActions result = mockMvc.perform(get("/api/v1/search")
                    .param("locationId", koramangalaId.toString())
                    .param("vendorId", "999999")
                    .param("page", "0")
                    .param("size", "10")
                    .accept(MediaType.APPLICATION_JSON));

            // Then: should return empty page (no results for unknown vendor)
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));

        } finally {
            cleanupTestData(productIds, vendorIds, userIds);
            cleanupCategories(List.of(category));
        }
    }
}
