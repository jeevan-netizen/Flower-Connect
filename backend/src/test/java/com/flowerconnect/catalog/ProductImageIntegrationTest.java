package com.flowerconnect.catalog;

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
import com.flowerconnect.storage.image.ImageFormat;
import com.flowerconnect.storage.image.ImageTypeDetector;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for product images (plan task 3.8) against real MySQL, the
 * production security chain, a real ImageIO pipeline and a real filesystem.
 *
 * <p>Four things can only be proven here.
 *
 * <ol>
 *   <li><b>The approval gate.</b> {@code @RequiresApprovedVendor} is a composed
 *       {@code @PreAuthorize} enabled by the production {@code SecurityConfig}, and a
 *       {@code @WebMvcTest} slice never loads that class, so the annotation is inert in
 *       slices (D-13).</li>
 *   <li><b>The one-primary invariant in the database.</b> It is a service-level rule
 *       (D-21), so only a real transaction can show that a cover swap leaves exactly one
 *       primary — including when two requests race for it.</li>
 *   <li><b>What actually landed on disk.</b> A mocked storage service would let a key
 *       pointing outside the storage root pass unnoticed.</li>
 *   <li><b>That the rejected uploads leave nothing behind</b> — no row and no file. A
 *       unit test can assert the throw; only this level can assert the absence of a
 *       side effect.</li>
 * </ol>
 *
 * <p>Each test creates its own vendor and product, so every assertion is scoped to rows
 * this class created: the shared singleton container accumulates products from every IT
 * class in the run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductImageIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String KORAMANGALA_PINCODE = "560034";
    private static final Path UPLOAD_ROOT = Paths.get("target", "test-uploads", "images");

    @DynamicPropertySource
    static void isolateStorage(DynamicPropertyRegistry registry) {
        // A directory under target/ so the suite never writes into a developer's
        // real uploads/ folder, and so the tests can read the stored bytes back.
        registry.add("app.storage.local-directory", UPLOAD_ROOT::toString);
    }

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
    private ProductService productService;
    @Autowired
    private ProductImageRepository imageRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long rosesCategoryId;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE).orElseThrow().getId();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        adminToken = tokenFor(createUser(uniqueEmail(), "ADMIN"));
        customerToken = tokenFor(createUser(uniqueEmail(), "CUSTOMER"));
        deleteRecursively(UPLOAD_ROOT);
    }

    // ------------------------------------------------------------------
    // Accepted formats
    // ------------------------------------------------------------------

    @Test
    void aValidJpegIsStoredAndBecomesTheFirstCover() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Jpeg Cover Roses");
        byte[] uploaded = ImageFixtures.jpeg(200, 100);

        mockMvc.perform(upload(vendor, productId, "rose.jpg", "image/jpeg", uploaded))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.primary").value(true))
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.originalFilename").value("rose.jpg"));

        String key = onlyImageOf(productId).getStorageKey();
        assertTrue(key.startsWith("product-images/" + productId + "/"), key);
        assertTrue(key.endsWith(".jpg"), key);
        assertTrue(Files.isRegularFile(UPLOAD_ROOT.resolve(key)), "the object must exist on disk");
        assertEquals(200, ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(UPLOAD_ROOT.resolve(key))))
                .getWidth());
    }

    @Test
    void aValidPngIsStoredAsPngAndDoesNotStealTheCover() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Png Roses");
        uploadOk(vendor, productId, "first.jpg", ImageFixtures.jpeg(50, 50));

        mockMvc.perform(upload(vendor, productId, "second.png", "image/png", ImageFixtures.png(60, 40)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.primary").value(false))
                .andExpect(jsonPath("$.sortOrder").value(1));

        assertEquals(1, primaryCount(productId));
    }

    @Test
    void aValidWebpIsAcceptedAndStoredAsJpeg() throws Exception {
        // WebP in, JPEG out: the build decodes WebP but cannot encode it (D-27).
        // The test states that contract rather than leaving it implied.
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Webp Roses");

        mockMvc.perform(upload(vendor, productId, "rose.webp", "image/webp", ImageFixtures.webp()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.originalFilename").value("rose.webp"));

        String key = onlyImageOf(productId).getStorageKey();
        assertTrue(key.endsWith(".jpg"), key);
        byte[] stored = Files.readAllBytes(UPLOAD_ROOT.resolve(key));
        assertTrue(new ImageTypeDetector().detect(stored).orElseThrow() == ImageFormat.JPEG,
                "the stored object must itself be a JPEG");
    }

    @Test
    void anOversizedImageIsScaledDownToTheConfiguredMaximumEdge() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Huge Roses");

        mockMvc.perform(upload(vendor, productId, "huge.jpg", "image/jpeg", ImageFixtures.jpeg(2400, 1200)))
                .andExpect(status().isCreated());

        var stored = ImageIO.read(new ByteArrayInputStream(
                Files.readAllBytes(UPLOAD_ROOT.resolve(onlyImageOf(productId).getStorageKey()))));
        assertEquals(1600, stored.getWidth(), "2400px must scale to max-dimension 1600");
        assertEquals(800, stored.getHeight());
    }

    @Test
    void theStoredBytesAreTheServersOwnEncodingNotTheUploadedFile() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Reencoded Roses");
        byte[] uploaded = ImageFixtures.noisyJpeg(300, 300);

        mockMvc.perform(upload(vendor, productId, "noisy.jpg", "image/jpeg", uploaded))
                .andExpect(status().isCreated());

        byte[] stored = Files.readAllBytes(UPLOAD_ROOT.resolve(onlyImageOf(productId).getStorageKey()));
        assertFalse(java.util.Arrays.equals(uploaded, stored),
                "the client's bytes must not be what is stored");
        assertEquals(stored.length, onlyImageOf(productId).getFileSize(),
                "the stored file_size must describe the stored bytes");
    }

    // ------------------------------------------------------------------
    // Content sniffing: the client's claims are ignored
    // ------------------------------------------------------------------

    @Test
    void anExecutableRenamedToJpgIsStoredAsAJpegAndNeverNamedAfterTheUpload() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Renamed Executable Roses");
        // Real JPEG bytes under an .exe name, declared as a binary. Content wins.
        mockMvc.perform(upload(vendor, productId, "payload.exe", "application/octet-stream",
                ImageFixtures.jpeg(40, 40)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.originalFilename").value("payload.exe"));

        String key = onlyImageOf(productId).getStorageKey();
        assertTrue(key.endsWith(".jpg"), key);
        assertFalse(key.contains("payload"), key);
        assertFalse(key.contains("exe"), key);
        assertTrue(Files.isRegularFile(UPLOAD_ROOT.resolve(key)));
    }

    @Test
    void aFileWearingAnImageExtensionIsRefusedWith415() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Disguised Executable Roses");

        mockMvc.perform(upload(vendor, productId, "rose.png", "image/png",
                ImageFixtures.windowsExecutable()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        assertEquals(0, imageCount(productId));
        assertNothingWasStored();
    }

    @Test
    void aZipAndAPdfAreAlsoRefusedWith415() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Document Upload Roses");

        mockMvc.perform(upload(vendor, productId, "archive.png", "image/png", ImageFixtures.zip()))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(upload(vendor, productId, "brochure.jpg", "image/jpeg", ImageFixtures.pdf()))
                .andExpect(status().isUnsupportedMediaType());

        assertEquals(0, imageCount(productId));
    }

    @Test
    void aDeclaredContentTypeOfImagePngDoesNotMakeJpegBytesAPng() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Lying Content Type Roses");

        // The part claims PNG; the bytes are JPEG. The stored format follows the
        // bytes, so a client cannot choose the stored type by lying in a header.
        mockMvc.perform(upload(vendor, productId, "rose.png", "image/png", ImageFixtures.jpeg(30, 30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"));

        assertTrue(onlyImageOf(productId).getStorageKey().endsWith(".jpg"));
    }

    @Test
    void contentThatDoesNotDecodeIsRefusedWith400() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Corrupt Upload Roses");

        // Correct signature, unusable payload: the pipeline decodes, so this fails.
        mockMvc.perform(upload(vendor, productId, "rose.png", "image/png", ImageFixtures.fakePng()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // A well-formed WebP container whose payload is garbage: sniffing passes,
        // decoding does not.
        mockMvc.perform(upload(vendor, productId, "rose.webp", "image/webp", ImageFixtures.corruptWebp()))
                .andExpect(status().isBadRequest());

        assertEquals(0, imageCount(productId));
        assertNothingWasStored();
    }

    @Test
    void anEmptyUploadIsRefusedWith400() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Empty Upload Roses");

        mockMvc.perform(upload(vendor, productId, "rose.jpg", "image/jpeg", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(0, imageCount(productId));
        assertNothingWasStored();
    }

    @Test
    void anUploadOverTheSizeLimitIsRefusedWith413() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Oversized Upload Roses");
        byte[] tooBig = ImageFixtures.padded(ImageFixtures.jpeg(64, 64), 5 * 1024 * 1024 + 1);

        mockMvc.perform(upload(vendor, productId, "huge.jpg", "image/jpeg", tooBig))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));

        assertEquals(0, imageCount(productId));
        assertNothingWasStored();
    }

    @Test
    void anUploadWithoutAFilePartIsRefusedWith400() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Partless Upload Roses");

        mockMvc.perform(multipart(imagesPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------
    // Path traversal
    // ------------------------------------------------------------------

    @Test
    void aTraversalFilenameIsNeverUsedAsAPath() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Traversal Roses");

        mockMvc.perform(upload(vendor, productId, "../../../../etc/passwd.jpg", "image/jpeg",
                ImageFixtures.jpeg(24, 24)))
                .andExpect(status().isCreated())
                // Only the display name is sanitised; the key never carries it.
                .andExpect(jsonPath("$.originalFilename").value("passwd.jpg"));

        String key = onlyImageOf(productId).getStorageKey();
        assertFalse(key.contains(".."), key);
        assertFalse(key.contains("passwd"), key);
        assertTrue(Files.isRegularFile(UPLOAD_ROOT.resolve(key)));
        assertNothingWasStoredOutsideTheRoot();
    }

    @Test
    void aWindowsStyleTraversalFilenameIsAlsoJustADisplayName() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Windows Traversal Roses");

        mockMvc.perform(upload(vendor, productId, "..\\..\\windows\\system32\\cmd.jpg", "image/jpeg",
                ImageFixtures.jpeg(24, 24)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("cmd.jpg"));

        assertFalse(onlyImageOf(productId).getStorageKey().contains(".."));
        assertNothingWasStoredOutsideTheRoot();
    }

    @Test
    void everyStoredObjectLivesUnderTheProductDirectory() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Key Shape Roses");
        uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));
        uploadOk(vendor, productId, "b.png", ImageFixtures.png(20, 20));
        uploadOk(vendor, productId, "c.webp", ImageFixtures.webp());

        for (com.flowerconnect.catalog.domain.ProductImage image
                : imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)) {
            assertTrue(image.getStorageKey().matches("product-images/" + productId
                    + "/[0-9a-f-]{36}\\.(jpg|png)"), image.getStorageKey());
        }
    }

    // ------------------------------------------------------------------
    // The image count limit
    // ------------------------------------------------------------------

    @Test
    void aProductCannotHoldMoreThanTheConfiguredNumberOfImages() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Many Images Roses");
        for (int i = 0; i < 8; i++) {
            uploadOk(vendor, productId, "image-" + i + ".jpg", ImageFixtures.jpeg(20, 20));
        }
        assertEquals(8, imageCount(productId));

        mockMvc.perform(upload(vendor, productId, "image-8.jpg", "image/jpeg", ImageFixtures.jpeg(20, 20)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("maximum of 8")));

        assertEquals(8, imageCount(productId), "the refused upload must not be stored");
    }

    // ------------------------------------------------------------------
    // Ownership and the role boundary
    // ------------------------------------------------------------------

    @Test
    void aForeignVendorCannotTouchAnotherVendorsImages() throws Exception {
        Vendor owner = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(owner, "Owned Image Roses");
        Long imageId = uploadOk(owner, productId, "mine.jpg", ImageFixtures.jpeg(20, 20));
        Vendor other = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(upload(other, productId, "theirs.jpg", "image/jpeg", ImageFixtures.jpeg(20, 20)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(imagesPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(imagesPath(productId) + "/" + imageId + "/primary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(imagesPath(productId) + "/order")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("imageIds", List.of(imageId)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(imagesPath(productId) + "/" + imageId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isForbidden());

        assertEquals(1, imageCount(productId));
        assertEquals(1, primaryCount(productId));
    }

    @Test
    void anUnknownProductIsNotFoundRatherThanForbidden() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get(imagesPath(99999999L))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(upload(vendor, 99999999L, "rose.jpg", "image/jpeg", ImageFixtures.jpeg(20, 20)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anImageIdFromAnotherProductIsNotFoundForThisProduct() throws Exception {
        Vendor owner = registerVendor(VendorProfile.Status.APPROVED);
        Long firstProduct = createProduct(owner, "First Image Roses");
        Long foreignImageId = uploadOk(owner, firstProduct, "a.jpg", ImageFixtures.jpeg(20, 20));
        Long secondProduct = createProduct(owner, "Second Image Roses");

        mockMvc.perform(put(imagesPath(secondProduct) + "/" + foreignImageId + "/primary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(delete(imagesPath(secondProduct) + "/" + foreignImageId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNotFound());

        assertEquals(1, primaryCount(firstProduct));
    }

    @Test
    void anAnonymousCallerIsUnauthenticatedOnEveryRoute() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Anonymous Image Roses");
        Long imageId = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));

        mockMvc.perform(multipart(imagesPath(productId)).file(jpegPart("a.jpg"))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(imagesPath(productId))).andExpect(status().isUnauthorized());
        mockMvc.perform(put(imagesPath(productId) + "/" + imageId + "/primary")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(imagesPath(productId) + "/" + imageId)).andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerIsStoppedByTheNamespaceRuleAndAnAdminIsRefusedToo() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Role Boundary Image Roses");
        Long imageId = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));

        // FORBIDDEN, not VENDOR_NOT_APPROVED: the hasRole rule runs first.
        mockMvc.perform(get(imagesPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(put(imagesPath(productId) + "/" + imageId + "/primary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(imagesPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Approval gating (D-13)
    // ------------------------------------------------------------------

    @Test
    void aPendingVendorCannotUploadWithTheApprovalErrorCode() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.PENDING_APPROVAL);
        // The product is a fixture, so it is created below the gate (D-13).
        Long productId = createProduct(vendor, "Pending Image Roses");

        mockMvc.perform(upload(vendor, productId, "rose.jpg", "image/jpeg", ImageFixtures.jpeg(20, 20)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));

        assertEquals(0, imageCount(productId));
    }

    @Test
    void aSuspendedVendorLosesImageAccessImmediatelyAndReinstatementRestoresIt() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Suspension Image Roses");
        uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));

        adminAction(vendor.profileId(), "suspend").andExpect(status().isOk());

        mockMvc.perform(upload(vendor, productId, "b.jpg", "image/jpeg", ImageFixtures.jpeg(20, 20)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));
        assertEquals(1, imageCount(productId));

        adminAction(vendor.profileId(), "reinstate").andExpect(status().isOk());

        mockMvc.perform(upload(vendor, productId, "b.jpg", "image/jpeg", ImageFixtures.jpeg(20, 20)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // The one-primary invariant (D-21 / D-28)
    // ------------------------------------------------------------------

    @Test
    void aProductNeverEndsUpWithMoreThanOnePrimaryImage() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Primary Invariant Roses");
        uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));
        uploadOk(vendor, productId, "b.jpg", ImageFixtures.jpeg(20, 20));
        uploadOk(vendor, productId, "c.jpg", ImageFixtures.jpeg(20, 20));

        assertEquals(1, primaryCount(productId));
        assertEquals(3, imageCount(productId));
    }

    @Test
    void settingAnotherImageAsTheCoverReplacesThePreviousOne() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Cover Swap Roses");
        Long first = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));
        Long second = uploadOk(vendor, productId, "b.jpg", ImageFixtures.jpeg(20, 20));

        mockMvc.perform(put(imagesPath(productId) + "/" + second + "/primary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].primary").value(false))
                .andExpect(jsonPath("$[1].id").value(second))
                .andExpect(jsonPath("$[1].primary").value(true));

        assertEquals(1, primaryCount(productId));
        assertFalse(imageRepository.findById(first).orElseThrow().isPrimary());
    }

    @Test
    void anUploadCanAskForTheCoverDirectly() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Cover On Upload Roses");
        Long first = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));

        mockMvc.perform(upload(vendor, productId, "b.jpg", "image/jpeg", ImageFixtures.jpeg(20, 20), true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary").value(true));

        assertEquals(1, primaryCount(productId));
        assertFalse(imageRepository.findById(first).orElseThrow().isPrimary());
    }

    @Test
    void twoConcurrentRequestsToChangeTheCoverLeaveExactlyOnePrimary() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Concurrent Cover Roses");
        Long first = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));
        Long second = uploadOk(vendor, productId, "b.jpg", ImageFixtures.jpeg(20, 20));

        int contenders = 2;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            for (long imageId : new long[]{first, second}) {
                pool.submit(() -> {
                    start.await();
                    try {
                        mockMvc.perform(put(imagesPath(productId) + "/" + imageId + "/primary")
                                .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                                .andExpect(status().isOk());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "contenders must finish");
        } finally {
            pool.shutdownNow();
        }

        // Both calls were released together by the latch, so neither could read the
        // other's committed state: run them one after another and each would see
        // the previous one's result, leaving the absence of the lock invisible.
        assertEquals(1, primaryCount(productId),
                "the one-primary invariant must hold under concurrent cover changes");
    }

    @Test
    void deletingTheCoverPromotesTheNextImageAndRemovesTheStoredObject() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Cover Deletion Roses");
        Long first = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));
        Long second = uploadOk(vendor, productId, "b.jpg", ImageFixtures.jpeg(20, 20));
        String firstKey = imageOf(productId, first).getStorageKey();

        mockMvc.perform(delete(imagesPath(productId) + "/" + first)
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isNoContent());

        assertEquals(1, imageCount(productId));
        assertEquals(1, primaryCount(productId));
        assertTrue(imageRepository.findById(second).orElseThrow().isPrimary());
        assertFalse(Files.exists(UPLOAD_ROOT.resolve(firstKey)), "the object must be gone too");
    }

    @Test
    void deletingTheLastImageLeavesTheProductWithNone() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Last Image Roses");
        Long only = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));

        mockMvc.perform(delete(imagesPath(productId) + "/" + only)
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isNoContent());

        assertEquals(0, imageCount(productId));
        assertEquals(0, primaryCount(productId));
        mockMvc.perform(get(imagesPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------
    // Ordering
    // ------------------------------------------------------------------

    @Test
    void imagesAreListedInDisplayOrderAndCanBeReordered() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Reorder Roses");
        Long a = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));
        Long b = uploadOk(vendor, productId, "b.jpg", ImageFixtures.jpeg(20, 20));
        Long c = uploadOk(vendor, productId, "c.jpg", ImageFixtures.jpeg(20, 20));

        mockMvc.perform(get(imagesPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(a))
                .andExpect(jsonPath("$[1].id").value(b))
                .andExpect(jsonPath("$[2].id").value(c));

        mockMvc.perform(put(imagesPath(productId) + "/order")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("imageIds", List.of(c, a, b)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(c))
                .andExpect(jsonPath("$[1].id").value(a))
                .andExpect(jsonPath("$[2].id").value(b));

        List<Long> ids = new ArrayList<>();
        for (com.flowerconnect.catalog.domain.ProductImage image
                : imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)) {
            ids.add(image.getId());
        }
        assertEquals(List.of(c, a, b), ids);
        // Reordering is display order only: it must not move the cover.
        assertTrue(imageRepository.findById(a).orElseThrow().isPrimary());
    }

    @Test
    void aPartialReorderIsRefusedAndChangesNothing() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Partial Reorder Roses");
        Long a = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));
        Long b = uploadOk(vendor, productId, "b.jpg", ImageFixtures.jpeg(20, 20));

        mockMvc.perform(put(imagesPath(productId) + "/order")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("imageIds", List.of(b)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(0, imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)
                .get(0).getSortOrder());
        assertEquals(a, imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).get(0).getId());
    }

    @Test
    void aReorderThatRepeatsAnImageIsRefused() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Repeated Reorder Roses");
        Long a = uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));
        Long b = uploadOk(vendor, productId, "b.jpg", ImageFixtures.jpeg(20, 20));

        mockMvc.perform(put(imagesPath(productId) + "/order")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("imageIds", List.of(a, a)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anEmptyReorderListIsRefusedByValidation() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Empty Reorder Roses");
        uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));

        mockMvc.perform(put(imagesPath(productId) + "/order")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("imageIds", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.imageIds").exists());
    }

    @Test
    void aNonPositiveProductIdIsRejected() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/vendors/products/-1/images")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/v1/vendors/products/-1/images").file(jpegPart("a.jpg"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theProductReadModelCarriesTheImages() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Product Payload Roses");
        uploadOk(vendor, productId, "a.jpg", ImageFixtures.jpeg(20, 20));

        mockMvc.perform(get("/api/v1/vendors/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].primary").value(true))
                .andExpect(jsonPath("$.images[0].storageKey").exists());
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private record Vendor(String email, String token, Long profileId) {
    }

    private static String imagesPath(Long productId) {
        return "/api/v1/vendors/products/" + productId + "/images";
    }

    private MockMultipartHttpServletRequestBuilder upload(
            Vendor vendor, Long productId, String filename, String contentType, byte[] content) {
        return upload(vendor, productId, filename, contentType, content, false);
    }

    private MockMultipartHttpServletRequestBuilder upload(
            Vendor vendor, Long productId, String filename, String contentType, byte[] content,
            boolean primary) {
        // Built in steps: file()/header() mutate the builder but are declared on the
        // parent type, so chaining them would erase the multipart type.
        MockMultipartHttpServletRequestBuilder request = multipart(imagesPath(productId));
        request.file(new MockMultipartFile("file", filename, contentType, content));
        request.header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()));
        if (primary) {
            request.param("primary", "true");
        }
        return request;
    }

    private MockMultipartFile jpegPart(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg", ImageFixtures.jpeg(20, 20));
    }

    /** Uploads an image that must succeed and returns its id. */
    private Long uploadOk(Vendor vendor, Long productId, String filename, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(upload(vendor, productId, filename, "application/octet-stream", content))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private com.flowerconnect.catalog.domain.ProductImage onlyImageOf(Long productId) {
        List<com.flowerconnect.catalog.domain.ProductImage> images =
                imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        assertEquals(1, images.size(), "expected exactly one image for product " + productId);
        return images.get(0);
    }

    private com.flowerconnect.catalog.domain.ProductImage imageOf(Long productId, Long imageId) {
        return imageRepository.findByIdAndProductId(imageId, productId).orElseThrow();
    }

    private long imageCount(Long productId) {
        return imageRepository.countByProductId(productId);
    }

    private long primaryCount(Long productId) {
        return imageRepository.countByProductIdAndPrimaryIsTrue(productId);
    }

    /** Nothing at all under the storage root: a refused upload writes no file. */
    private void assertNothingWasStored() throws IOException {
        if (!Files.exists(UPLOAD_ROOT)) {
            return;
        }
        try (Stream<Path> files = Files.walk(UPLOAD_ROOT)) {
            List<Path> stored = files.filter(Files::isRegularFile).toList();
            assertTrue(stored.isEmpty(), "a refused upload must leave no file behind: " + stored);
        }
    }

    private void assertNothingWasStoredOutsideTheRoot() {
        for (String name : List.of("passwd.jpg", "cmd.jpg", "escaped.jpg")) {
            assertFalse(Files.exists(UPLOAD_ROOT.getParent().resolve(name)),
                    "an object was written outside the storage root: " + name);
            assertFalse(Files.exists(Paths.get("etc").resolve(name)),
                    "an object was written outside the storage root: " + name);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    private Vendor registerVendor(VendorProfile.Status target) throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerRequest(email))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        Long profileId = vendorProfileRepository.findByUserId(user.getId()).orElseThrow().getId();
        String token = tokenFor(user);

        switch (target) {
            case APPROVED -> approve(profileId);
            case SUSPENDED -> {
                approve(profileId);
                adminAction(profileId, "suspend").andExpect(status().isOk());
            }
            case PENDING_APPROVAL -> {
                // freshly registered
            }
            default -> throw new IllegalArgumentException("unhandled status " + target);
        }
        return new Vendor(email, token, profileId);
    }

    /**
     * Creates the product through {@link ProductService} rather than the catalog HTTP
     * route, because the approval gate lives on the controller (D-13) and two of these
     * tests need a PENDING or SUSPENDED vendor that could not create a product over HTTP.
     */
    private Long createProduct(Vendor vendor, String name) {
        return productService.create(vendor.email(), ProductRequest.builder()
                        .name(name)
                        .categoryId(rosesCategoryId)
                        .description("Fresh flowers")
                        .basePrice(new BigDecimal("299.00"))
                        .status(ProductStatus.ACTIVE)
                        .build())
                .getId();
    }

    private void approve(Long profileId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/vendors/" + profileId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private ResultActions adminAction(Long profileId, String action) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/admin/vendors/" + profileId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"image integration probe\"}"));
    }

    private VendorRegisterRequest registerRequest(String email) {
        VendorRegisterRequest request = new VendorRegisterRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setFullName("Vendor Owner");
        request.setPhone(uniquePhone());
        request.setBusinessName("Image Probe Blossoms");
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
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
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
        return "images-" + UUID.randomUUID() + "@test.com";
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