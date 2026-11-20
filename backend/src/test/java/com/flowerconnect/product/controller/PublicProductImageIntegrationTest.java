package com.flowerconnect.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.storage.image.ImageFixtures;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the public image read endpoint against real MySQL,
 * the production security chain, and a real filesystem.
 *
 * <p>Four things are proven here: a successful image read returns the stored
 * bytes with the recorded MIME type; an unknown image is a non-disclosing 404;
 * an image of an ineligible product (inactive, not accepting, outside radius)
 * is also a 404; and a missing {@code locationId} is a 400. No authentication
 * is required or accepted — the endpoint is permitAll.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicProductImageIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String KORAMANGALA_PINCODE = "560034";
    private static final Path UPLOAD_ROOT = Paths.get("target", "test-uploads", "public-images");

    @DynamicPropertySource
    static void isolateStorage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-directory", UPLOAD_ROOT::toString);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductImageRepository imageRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long rosesCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE).orElseThrow().getId();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        deleteRecursively(UPLOAD_ROOT);
    }

    @Test
    void shouldServeAnImageForAPublicProduct() throws Exception {
        Vendor vendor = registerApprovedVendor();
        Long productId = createProduct(vendor, "Public Roses", ProductStatus.ACTIVE, true);
        // Give the product stock so it is eligible for public display.
        jdbcTemplate.update("UPDATE inventory SET quantity = 10 WHERE product_id = ?", productId);
        Long imageId = uploadImage(vendor, productId, "rose.jpg", ImageFixtures.jpeg(200, 100));

        var stored = imageRepository.findByIdAndProductId(imageId, productId).orElseThrow();
        byte[] storedBytes = Files.readAllBytes(UPLOAD_ROOT.resolve(stored.getStorageKey()));

        mockMvc.perform(get("/api/v1/products/" + productId + "/images/" + imageId)
                        .param("locationId", locationId.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"))
                .andExpect(content().bytes(storedBytes));
    }

    @Test
    void shouldReturn404ForAnInactiveProductImage() throws Exception {
        Vendor vendor = registerApprovedVendor();
        Long productId = createProduct(vendor, "Inactive Roses", ProductStatus.INACTIVE, true);
        Long imageId = uploadImage(vendor, productId, "rose.jpg", ImageFixtures.jpeg(200, 100));

        mockMvc.perform(get("/api/v1/products/" + productId + "/images/" + imageId)
                        .param("locationId", locationId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn404ForANonAcceptingVendorImage() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED, false);
        Long productId = createProduct(vendor, "NonAccepting Roses", ProductStatus.ACTIVE, true);
        Long imageId = uploadImage(vendor, productId, "rose.jpg", ImageFixtures.jpeg(200, 100));

        mockMvc.perform(get("/api/v1/products/" + productId + "/images/" + imageId)
                        .param("locationId", locationId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn404ForAnImageOutsideTheDeliveryRadius() throws Exception {
        // Electronic City (560100) is far from Koramangala (560034), outside the
        // vendor's 6.50 km delivery radius.
        Long farLocationId = serviceLocationRepository.findByPincode("560100").orElseThrow().getId();

        Vendor vendor = registerApprovedVendor();
        Long productId = createProduct(vendor, "Distant Roses", ProductStatus.ACTIVE, true);
        Long imageId = uploadImage(vendor, productId, "rose.jpg", ImageFixtures.jpeg(200, 100));

        mockMvc.perform(get("/api/v1/products/" + productId + "/images/" + imageId)
                        .param("locationId", farLocationId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn404ForASuspendedVendorImage() throws Exception {
        Vendor vendor = registerApprovedVendor();
        Long productId = createProduct(vendor, "Suspended Roses", ProductStatus.ACTIVE, true);
        // Upload while approved, then suspend — the image row remains but the
        // product must no longer be publicly readable.
        Long imageId = uploadImage(vendor, productId, "rose.jpg", ImageFixtures.jpeg(200, 100));
        suspend(vendorProfileRepository.findByUserId(
                userRepository.findByEmail(vendor.email()).orElseThrow().getId())
                .orElseThrow().getId());

        mockMvc.perform(get("/api/v1/products/" + productId + "/images/" + imageId)
                        .param("locationId", locationId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("product-images"))));
    }

    @Test
    void shouldReturn404ForAMissingImage() throws Exception {
        Vendor vendor = registerApprovedVendor();
        Long productId = createProduct(vendor, "Missing Image Roses", ProductStatus.ACTIVE, true);

        mockMvc.perform(get("/api/v1/products/" + productId + "/images/99999999")
                        .param("locationId", locationId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("product-images"))));
    }

    @Test
    void shouldReturn404ForAnImageOfAnotherProduct() throws Exception {
        Vendor vendor = registerApprovedVendor();
        Long firstProduct = createProduct(vendor, "First Roses", ProductStatus.ACTIVE, true);
        Long firstImageId = uploadImage(vendor, firstProduct, "a.jpg", ImageFixtures.jpeg(20, 20));
        Long secondProduct = createProduct(vendor, "Second Roses", ProductStatus.ACTIVE, true);

        mockMvc.perform(get("/api/v1/products/" + secondProduct + "/images/" + firstImageId)
                        .param("locationId", locationId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("product-images"))));
    }

    @Test
    void shouldReturn400WhenLocationIdIsMissing() throws Exception {
        Vendor vendor = registerApprovedVendor();
        Long productId = createProduct(vendor, "No Location Roses", ProductStatus.ACTIVE, true);
        Long imageId = uploadImage(vendor, productId, "rose.jpg", ImageFixtures.jpeg(200, 100));

        mockMvc.perform(get("/api/v1/products/" + productId + "/images/" + imageId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("product-images"))));
    }

    @Test
    void shouldReturn404ForAnUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/99999999/images/1")
                        .param("locationId", locationId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("product-images"))));
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private record Vendor(String email, String token) {
    }

    private Long uploadImage(Vendor vendor, Long productId, String filename, byte[] content) throws Exception {
        MockMultipartFile part = new MockMultipartFile("file", filename, "application/octet-stream", content);
        MvcResult result = mockMvc.perform(multipart("/api/v1/vendors/products/" + productId + "/images")
                        .file(part)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + vendor.token()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Vendor registerApprovedVendor() throws Exception {
        return registerVendor(VendorProfile.Status.APPROVED, true);
    }

    private void suspend(Long profileId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/vendors/" + profileId + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"policy violation\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    private Vendor registerVendor(VendorProfile.Status status, boolean acceptingOrders) throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(multipart("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(registerRequest(email, acceptingOrders))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        Long profileId = vendorProfileRepository.findByUserId(user.getId()).orElseThrow().getId();

        if (status == VendorProfile.Status.APPROVED) {
            approve(profileId);
        }

        String token = tokenFor(user);
        return new Vendor(email, token);
    }

    private Long createProduct(Vendor vendor, String name, ProductStatus status, boolean acceptingOrders) {
        return productService.create(vendor.email, ProductRequest.builder()
                        .name(name)
                        .categoryId(rosesCategoryId)
                        .description("Fresh flowers")
                        .basePrice(new BigDecimal("299.00"))
                        .status(status)
                        .build())
                .getId();
    }

    private void approve(Long profileId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/vendors/" + profileId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    private String adminToken() throws Exception {
        return tokenFor(createUser("admin-" + uniqueEmail(), "ADMIN"));
    }

    private String tokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                LoginRequest.builder().email(user.getEmail()).password(PASSWORD).build())))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(auth.getAccessToken());
        return auth.getAccessToken();
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

    private VendorRegisterRequest registerRequest(String email, boolean acceptingOrders) {
        VendorRegisterRequest request = new VendorRegisterRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setFullName("Public Image Vendor");
        request.setPhone(uniquePhone());
        request.setBusinessName("Public Image Blossoms");
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
        request.setAcceptingOrders(acceptingOrders);
        return request;
    }

    private static String uniqueEmail() {
        return "public-images-" + UUID.randomUUID() + "@test.com";
    }

    private static String uniquePhone() {
        Random random = new Random();
        StringBuilder digits = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            digits.append(random.nextInt(10));
        }
        return "+91" + digits;
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walk(root)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
