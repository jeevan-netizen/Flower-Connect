package com.flowerconnect.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.dto.ProductResponse;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the vendor catalog API (plan task 3.5) against a real MySQL
 * instance and the production security chain.
 *
 * <p>Two things can only be proven here. First, {@code @RequiresApprovedVendor}: it is a
 * composed {@code @PreAuthorize} enabled by the production {@code SecurityConfig}, and a
 * {@code @WebMvcTest} slice supplies its own filter chain and never loads that class, so the
 * annotation is inert in slices (D-13). Second, which rows the optional listing filters
 * actually select — that is Criteria-API behaviour over real SQL, not something a mocked
 * repository can assert.
 *
 * <p>Each test creates its own vendor, so the assertions on the listing are scoped to rows
 * this class created rather than to the whole table: the shared singleton container
 * accumulates products from every IT class in the run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VendorCatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String KORAMANGALA_PINCODE = "560034";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long rosesCategoryId;
    private Long bouquetsCategoryId;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE)
                .orElseThrow().getId();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        bouquetsCategoryId = categoryRepository.findBySlug("bouquets").orElseThrow().getId();
        adminToken = tokenFor(createUser(uniqueEmail(), "ADMIN"));
        customerToken = tokenFor(createUser(uniqueEmail(), "CUSTOMER"));
    }

    // ------------------------------------------------------------------
    // Approval gating (D-13)
    // ------------------------------------------------------------------

    @Test
    void aPendingVendorIsRefusedWithTheApprovalErrorCode() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.PENDING_APPROVAL);

        mockMvc.perform(get("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isForbidden())
                // The code, not just the status, is what distinguishes
                // "not approved yet" from a plain permission failure.
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));

        assertEquals(0, productCountFor(vendor));
    }

    @Test
    void aRejectedVendorIsRefused() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.REJECTED);

        mockMvc.perform(post("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("Blocked Rose"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));
    }

    @Test
    void aSuspendedVendorLosesCatalogAccessImmediately() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        createProduct(vendor, "Suspension Rose");

        suspend(vendor.profileId());

        mockMvc.perform(get("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));
    }

    @Test
    void anApprovedVendorCanCreateAndReadBack() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(post("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("Red Rose Bunch"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("red-rose-bunch"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.vendorId").value(vendor.profileId().intValue()));

        mockMvc.perform(get("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == 'red-rose-bunch')]").exists());
    }

    @Test
    void reinstatementRestoresCatalogAccess() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.SUSPENDED);

        reinstate(vendor.profileId());

        mockMvc.perform(get("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Role boundary
    // ------------------------------------------------------------------

    @Test
    void aCustomerIsStoppedByTheNamespaceRuleBeforeTheApprovalGuard() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden())
                // FORBIDDEN, not VENDOR_NOT_APPROVED: the hasRole
                // rule runs first and a customer has no profile.
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anAnonymousCallerIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminCannotUseTheVendorCatalogRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Ownership
    // ------------------------------------------------------------------

    @Test
    void oneVendorCannotSeeAnotherVendorsProduct() throws Exception {
        Vendor owner = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(owner, "Private Rose");
        Vendor other = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/vendors/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void oneVendorCannotUpdateOrDeactivateAnotherVendorsProduct() throws Exception {
        Vendor owner = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(owner, "Protected Tulip");
        Vendor other = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(put("/api/v1/vendors/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("Stolen Tulip"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/vendors/products/" + productId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isForbidden());

        // The owner's product is untouched.
        assertEquals(Product.ProductStatus.DRAFT, statusOf(productId));
    }

    @Test
    void aListingOnlyEverContainsTheCallersOwnProducts() throws Exception {
        Vendor mine = registerVendor(VendorProfile.Status.APPROVED);
        Vendor theirs = registerVendor(VendorProfile.Status.APPROVED);
        createProduct(theirs, "Not Mine Bouquet");

        mockMvc.perform(get("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == 'not-mine-bouquet')]").doesNotExist());
    }

    @Test
    void anUnknownProductIsNotFoundRatherThanForbidden() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/vendors/products/99999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // Slug uniqueness
    // ------------------------------------------------------------------

    @Test
    void twoVendorsMayBothCreateTheSameProductName() throws Exception {
        Vendor first = registerVendor(VendorProfile.Status.APPROVED);
        Vendor second = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(post("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("Signature Orchid"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("signature-orchid"));

        // Slugs are globally unique, so the second create is suffixed.
        mockMvc.perform(post("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(second.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("Signature Orchid"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("signature-orchid-2"));
    }

    @Test
    void aRenameRegeneratesTheSlug() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Old Tulip");

        mockMvc.perform(put("/api/v1/vendors/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("New Tulip"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("new-tulip"));
    }

    // ------------------------------------------------------------------
    // Listing filters, pagination and soft delete
    // ------------------------------------------------------------------

    @Test
    void theListingCanBeFilteredByStatus() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        createProduct(vendor, "Draft Lily");
        Long activeId = createProduct(vendor, "Active Lily");
        activate(vendor, activeId);

        mockMvc.perform(get("/api/v1/vendors/products")
                        .param("status", "DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == 'draft-lily')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'active-lily')]").doesNotExist());
    }

    @Test
    void theListingCanBeFilteredByCategory() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        createProduct(vendor, "Rose One", rosesCategoryId);
        createProduct(vendor, "Bouquet One", bouquetsCategoryId);

        mockMvc.perform(get("/api/v1/vendors/products")
                        .param("categoryId", String.valueOf(bouquetsCategoryId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == 'bouquet-one')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'rose-one')]").doesNotExist());
    }

    @Test
    void theListingCanBeFilteredByAPartialCaseInsensitiveName() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        createProduct(vendor, "Yellow Tulip Bunch");
        createProduct(vendor, "White Rose Stem");

        mockMvc.perform(get("/api/v1/vendors/products")
                        .param("name", "TULIP")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == 'yellow-tulip-bunch')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'white-rose-stem')]").doesNotExist());
    }

    @Test
    void aBlankNameFilterNarrowsNothing() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        createProduct(vendor, "Filter Blank Rose");

        mockMvc.perform(get("/api/v1/vendors/products")
                        .param("name", "   ")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == 'filter-blank-rose')]").exists());
    }

    @Test
    void theListingIsPaginated() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        createProduct(vendor, "Page One Rose");
        createProduct(vendor, "Page Two Rose");

        mockMvc.perform(get("/api/v1/vendors/products")
                        .param("page", "0")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/vendors/products")
                        .param("page", "1")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void deactivationHidesTheProductWithoutRemovingIt() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Doomed Carnation");

        mockMvc.perform(patch("/api/v1/vendors/products/" + productId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isNoContent());

        assertEquals(Product.ProductStatus.INACTIVE, statusOf(productId));

        // Still readable by its owner — the row survives — but no
        // longer returned by a status=DRAFT listing.
        mockMvc.perform(get("/api/v1/vendors/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void aDeactivatedProductCanBeBroughtBackByAnUpdate() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Revived Peony");
        mockMvc.perform(patch("/api/v1/vendors/products/" + productId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isNoContent());

        ProductRequest request = validRequest("Revived Peony");
        request.setStatus(Product.ProductStatus.ACTIVE);
        mockMvc.perform(put("/api/v1/vendors/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void anUpdateWithoutAStatusLeavesTheStoredStatusAlone() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Keep Status Rose");
        activate(vendor, productId);

        mockMvc.perform(put("/api/v1/vendors/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("Keep Status Rose"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void aZeroPriceIsRefusedAndNoProductIsCreated() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        ProductRequest request = validRequest("Free Rose");
        request.setBasePrice(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.basePrice").exists());

        assertEquals(0, productCountFor(vendor));
    }

    @Test
    void anUnknownCategoryIsRefused() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        ProductRequest request = validRequest("Nowhere Rose");
        request.setCategoryId(99999999L);

        mockMvc.perform(post("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category not found"));

        assertEquals(0, productCountFor(vendor));
    }

    @Test
    void aDeactivatedCategoryCannotBeAssignedToANewProduct() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long hiddenId = createHiddenCategory();
        ProductRequest request = validRequest("Seasonal Wreath");
        request.setCategoryId(hiddenId);

        mockMvc.perform(post("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category is not active"));

        assertEquals(0, productCountFor(vendor));
    }

    @Test
    void aNonPositiveProductIdIsRejected() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/vendors/products/-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private record Vendor(String email, String token, Long profileId) {
    }

    /** Registers a vendor and drives it to the requested approval status by legal transitions only. */
    private Vendor registerVendor(VendorProfile.Status target) throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerRequest(email))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        Long profileId = vendorProfileRepository.findByUserId(user.getId()).orElseThrow().getId();
        String token = tokenFor(user);

        switch (target) {
            case APPROVED -> approve(profileId);
            case REJECTED -> adminAction(profileId, "reject")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
            case SUSPENDED -> {
                approve(profileId);
                adminAction(profileId, "suspend")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("SUSPENDED"));
            }
            case PENDING_APPROVAL -> {
                // freshly registered
            }
            default -> throw new IllegalArgumentException("unhandled status " + target);
        }

        return new Vendor(email, token, profileId);
    }

    private Long createProduct(Vendor vendor, String name) throws Exception {
        return createProduct(vendor, name, rosesCategoryId);
    }

    private Long createProduct(Vendor vendor, String name, Long categoryId) throws Exception {
        ProductRequest request = validRequest(name);
        request.setCategoryId(categoryId);
        MvcResult result = mockMvc.perform(post("/api/v1/vendors/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn();
        ProductResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);
        return response.getId();
    }

    private void activate(Vendor vendor, Long productId) throws Exception {
        ProductRequest request = validRequest(nameOf(productId));
        request.setStatus(Product.ProductStatus.ACTIVE);
        mockMvc.perform(put("/api/v1/vendors/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private Product.ProductStatus statusOf(Long productId) {
        return productRepository.findById(productId).orElseThrow().getStatus();
    }

    private String nameOf(Long productId) {
        return productRepository.findById(productId).orElseThrow().getName();
    }

    private long productCountFor(Vendor vendor) {
        return productRepository.findByVendorId(vendor.profileId(), PageRequest.of(0, 100))
                .getTotalElements();
    }

    /** A category created inactive, so it is absent from the public listing but still assignable by id. */
    private Long createHiddenCategory() {
        return categoryRepository.saveAndFlush(Category.builder()
                .name("Hidden " + UUID.randomUUID())
                .slug("hidden-" + UUID.randomUUID())
                .displayOrder(0)
                .active(false)
                .build()).getId();
    }

    private void approve(Long profileId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private void suspend(Long profileId) throws Exception {
        adminAction(profileId, "suspend").andExpect(status().isOk());
    }

    private void reinstate(Long profileId) throws Exception {
        adminAction(profileId, "reinstate").andExpect(status().isOk());
    }

    private ResultActions adminAction(Long profileId, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"catalog integration probe\"}"));
    }

    private ProductRequest validRequest(String name) {
        return ProductRequest.builder()
                .name(name)
                .categoryId(rosesCategoryId)
                .description("Fresh flowers")
                .basePrice(new BigDecimal("299.00"))
                .build();
    }

    private VendorRegisterRequest registerRequest(String email) {
        VendorRegisterRequest request = new VendorRegisterRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setFullName("Vendor Owner");
        request.setPhone(uniquePhone());
        request.setBusinessName("Catalog Probe Blossoms");
        request.setDescription("Fresh flowers delivered daily");
        request.setAddressLine1("12 Test Street");
        request.setServiceLocationId(locationId);
        request.setDeliveryRadiusKm(new BigDecimal("6.50"));
        request.setMinOrderAmount(new BigDecimal("199.99"));
        request.setBaseDeliveryFee(new BigDecimal("25.50"));
        request.setPerKmFee(new BigDecimal("1.75"));
        request.setPrepTimeMinutes(45);
        request.setSlotDurationMinutes(60);
        request.setMaxOrdersPerSlot(10);
        request.setAcceptingOrders(true);
        return request;
    }

    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Test " + roleName)
                .phone(uniquePhone())
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
    }

    private String tokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail())
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(auth.getAccessToken());
        return auth.getAccessToken();
    }

    private static String uniqueEmail() {
        return "vendor-" + UUID.randomUUID() + "@test.com";
    }

    /** Random decimal digits: UUID hex contains letters, which the phone pattern rejects. */
    private static String uniquePhone() {
        Random random = new Random();
        StringBuilder digits = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            digits.append(random.nextInt(10));
        }
        return "+91" + digits;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}