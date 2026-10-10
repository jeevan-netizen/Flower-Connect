package com.flowerconnect.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the inventory API (plan task 3.6) against a real MySQL instance and the
 * production security chain.
 *
 * <p>Three things can only be proven here. First, {@code @RequiresApprovedVendor}: it is a composed
 * {@code @PreAuthorize} enabled by the production {@code SecurityConfig}, and a {@code @WebMvcTest}
 * slice supplies its own filter chain and never loads that class, so the annotation is inert in slices
 * (D-13). Second, which rows the low-stock predicate actually selects — that is Criteria-API behaviour
 * over real SQL (D-23), and no mocked repository can show that a row with
 * {@code quantity − reserved = 4} against a threshold of 3 is excluded while one with 3 is included.
 * Third, that a movement survives the round trip with the delta, the reason and the actor the service
 * wrote.
 *
 * <p>{@code reserved_quantity} has no API in this phase — reservation arrives with checkout in
 * Phase 5 — so the tests that need pending reservations set the column directly with JDBC. That is
 * the honest way to establish a precondition a later phase will produce through a route, and it is
 * what makes the 409 floor meaningful: without reserved units the floor could never bind.
 *
 * <p>Each test creates its own vendor, so every assertion is scoped to rows this class created: the
 * shared singleton container accumulates products from every IT class in the run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VendorInventoryIntegrationTest extends AbstractIntegrationTest {

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
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long locationId;
    private Long rosesCategoryId;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        locationId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE)
                .orElseThrow().getId();
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        adminToken = tokenFor(createUser(uniqueEmail(), "ADMIN"));
        customerToken = tokenFor(createUser(uniqueEmail(), "CUSTOMER"));
    }

    // ------------------------------------------------------------------
    // Approval gating (D-13)
    // ------------------------------------------------------------------

    @Test
    void aPendingVendorIsRefusedWithTheApprovalErrorCode() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.PENDING_APPROVAL);
        Long productId = createProduct(vendor, "Pending Stock Rose");

        mockMvc.perform(get(inventoryPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isForbidden())
                // The code, not just the status, is what distinguishes
                // "not approved yet" from a plain permission failure.
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));

        assertEquals(0L, movementsOf(productId));
    }

    @Test
    void aSuspendedVendorLosesInventoryAccessImmediately() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Suspension Stock Rose");
        stockIn(vendor, productId, 4, "delivery");

        adminAction(vendor.profileId(), "suspend").andExpect(status().isOk());

        mockMvc.perform(post(inventoryPath(productId) + "/stock-in")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 3))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));

        assertEquals(4, quantityOf(productId));
        assertEquals(1L, movementsOf(productId));
    }

    @Test
    void reinstatementRestoresInventoryAccess() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.SUSPENDED);
        Long productId = createProduct(vendor, "Reinstate Stock Rose");

        adminAction(vendor.profileId(), "reinstate").andExpect(status().isOk());

        mockMvc.perform(get(inventoryPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Role boundary
    // ------------------------------------------------------------------

    @Test
    void aCustomerIsStoppedByTheNamespaceRuleBeforeTheApprovalGuard() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Role Boundary Rose");

        mockMvc.perform(get(inventoryPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden())
                // FORBIDDEN, not VENDOR_NOT_APPROVED: the hasRole rule
                // runs first and a customer has no vendor profile.
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anAnonymousCallerIsUnauthenticated() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Anonymous Stock Rose");

        mockMvc.perform(get(inventoryPath(productId))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminCannotUseTheInventoryRoutes() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Admin Probe Rose");

        mockMvc.perform(get(inventoryPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Ownership
    // ------------------------------------------------------------------

    @Test
    void oneVendorCannotSeeAnotherVendorsInventory() throws Exception {
        Vendor owner = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(owner, "Private Stock Rose");
        stockIn(owner, productId, 12, "delivery");
        Vendor other = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get(inventoryPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get(inventoryPath(productId) + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void oneVendorCannotMutateAnotherVendorsInventory() throws Exception {
        Vendor owner = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(owner, "Protected Stock Rose");
        stockIn(owner, productId, 10, "delivery");
        Vendor other = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(post(inventoryPath(productId) + "/stock-in")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 50))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(inventoryPath(productId) + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", -10, "reason", "theft"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(inventoryPath(productId) + "/low-stock-threshold")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lowStockThreshold", 99))))
                .andExpect(status().isForbidden());

        // The owner's stock is untouched and unlogged.
        assertEquals(10, quantityOf(productId));
        assertEquals(1L, movementsOf(productId));
    }

    @Test
    void anUnknownProductIsNotFoundRatherThanForbidden() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get(inventoryPath(99999999L))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get(inventoryPath(99999999L) + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNonPositiveProductIdIsRejected() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        mockMvc.perform(get("/api/v1/vendors/products/-1/inventory")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Stock in / out and the movement log
    // ------------------------------------------------------------------

    @Test
    void aNewProductStartsAtZeroAvailability() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Zero Stock Rose");

        mockMvc.perform(get(inventoryPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.reservedQuantity").value(0))
                .andExpect(jsonPath("$.available").value(0))
                .andExpect(jsonPath("$.lowStockThreshold").value(0))
                .andExpect(jsonPath("$.lowStock").value(true));
    }

    @Test
    void stockInAddsTheQuantityAndWritesExactlyOneMovement() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Stock In Rose");

        mockMvc.perform(post(inventoryPath(productId) + "/stock-in")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 25, "reason", "Morning wholesale delivery"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(25))
                .andExpect(jsonPath("$.available").value(25))
                .andExpect(jsonPath("$.lowStock").value(false));

        assertEquals(25, quantityOf(productId));
        assertEquals(1L, movementsOf(productId));
        assertEquals(MovementType.STOCK_IN, movementTypeOf(productId));
        assertEquals(25, movementDeltaOf(productId));
        assertEquals("Morning wholesale delivery", movementReasonOf(productId));
    }

    @Test
    void theMovementActorIsTheAuthenticatedVendor() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Actor Stock Rose");

        stockIn(vendor, productId, 6, "delivery");

        Long vendorUserId = userRepository.findByEmail(vendor.email()).orElseThrow().getId();
        assertEquals(vendorUserId, actorOf(productId));

        // Two vendors writing to their own products each get their own
        // id on the movement: the actor is the identity, not a field.
        Vendor second = registerVendor(VendorProfile.Status.APPROVED);
        Long secondProduct = createProduct(second, "Second Actor Rose");
        stockIn(second, secondProduct, 6, "delivery");
        assertEquals(userRepository.findByEmail(second.email()).orElseThrow().getId(),
                actorOf(secondProduct));
        assertEquals(vendorUserId, actorOf(productId));
    }

    @Test
    void stockOutRemovesTheQuantityAndRecordsANegativeMovement() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Stock Out Rose");
        stockIn(vendor, productId, 30, "delivery");

        mockMvc.perform(post(inventoryPath(productId) + "/stock-out")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 12, "reason", "Moved to the Koramangala pop-up"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(18))
                .andExpect(jsonPath("$.available").value(18));

        assertEquals(MovementType.STOCK_OUT, movementTypeOf(productId));
        assertEquals(-12, movementDeltaOf(productId));
        assertEquals("Moved to the Koramangala pop-up", movementReasonOf(productId));
        assertEquals(2L, movementsOf(productId));
    }

    @Test
    void anAdjustmentAppliesASignedDeltaAndStoresItsReason() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Adjustment Rose");
        stockIn(vendor, productId, 20, "delivery");

        mockMvc.perform(post(inventoryPath(productId) + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", -3, "reason", "Recount found three fewer stems"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(17));

        assertEquals(MovementType.ADJUSTMENT, movementTypeOf(productId));
        assertEquals(-3, movementDeltaOf(productId));
        assertEquals("Recount found three fewer stems", movementReasonOf(productId));
        assertEquals(2L, movementsOf(productId));
    }

    @Test
    void anAdjustmentWithoutAReasonIsRefusedAndChangesNothing() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "No Reason Rose");
        stockIn(vendor, productId, 20, "delivery");

        mockMvc.perform(post(inventoryPath(productId) + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", -5, "reason", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.reason").exists());

        assertEquals(20, quantityOf(productId));
        assertEquals(1L, movementsOf(productId));
    }

    @Test
    void aZeroAdjustmentIsRefusedAndWritesNoMovement() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Zero Adjustment Rose");
        stockIn(vendor, productId, 20, "delivery");

        mockMvc.perform(post(inventoryPath(productId) + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 0, "reason", "Nothing actually changed"))))
                .andExpect(status().isBadRequest());

        assertEquals(20, quantityOf(productId));
        assertEquals(1L, movementsOf(productId));
    }

    @Test
    void aWriteOffIsRecordedAsWasteWithItsReason() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Write Off Rose");
        stockIn(vendor, productId, 40, "delivery");

        mockMvc.perform(post(inventoryPath(productId) + "/write-offs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 7, "reason", "Cooler failed overnight"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(33))
                .andExpect(jsonPath("$.available").value(33));

        assertEquals(MovementType.WASTE, movementTypeOf(productId));
        assertEquals(-7, movementDeltaOf(productId));
        assertEquals("Cooler failed overnight", movementReasonOf(productId));
    }

    @Test
    void aWriteOffWithoutAReasonIsRefusedAndChangesNothing() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Unreasoned Write Off Rose");
        stockIn(vendor, productId, 40, "delivery");

        mockMvc.perform(post(inventoryPath(productId) + "/write-offs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 7))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        assertEquals(40, quantityOf(productId));
        assertEquals(1L, movementsOf(productId));
    }

    // ------------------------------------------------------------------
    // Availability and the reserved-quantity floor
    // ------------------------------------------------------------------

    @Test
    void availabilityIsQuantityMinusReserved() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Reserved Rose");
        stockIn(vendor, productId, 12, "delivery");
        reserve(productId, 5);

        mockMvc.perform(get(inventoryPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(12))
                .andExpect(jsonPath("$.reservedQuantity").value(5))
                .andExpect(jsonPath("$.available").value(7));
    }

    @Test
    void aStockOutThatWouldDropBelowReservedIsAConflictWithItsOwnCode() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Floor Rose");
        stockIn(vendor, productId, 10, "delivery");
        reserve(productId, 8);

        mockMvc.perform(post(inventoryPath(productId) + "/stock-out")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 5, "reason", "too eager"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value(
                        "Stock change refused: it would leave quantity at 5, below the 8 reserved unit(s)"));

        // Not clamped: the level is exactly what it was, and no movement
        // claims a change that did not happen.
        assertEquals(10, quantityOf(productId));
        assertEquals(1L, movementsOf(productId));
    }

    @Test
    void aStockOutDownToTheReservedQuantityIsAllowed() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Floor Exactly Rose");
        stockIn(vendor, productId, 10, "delivery");
        reserve(productId, 8);

        mockMvc.perform(post(inventoryPath(productId) + "/stock-out")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void aNegativeAdjustmentCannotConsumeReservedUnits() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Adjustment Floor Rose");
        stockIn(vendor, productId, 10, "delivery");
        reserve(productId, 6);

        mockMvc.perform(post(inventoryPath(productId) + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", -5,
                                "reason", "Recount, but the pending sales are not written off yet"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertEquals(10, quantityOf(productId));
        assertEquals(1L, movementsOf(productId));
    }

    @Test
    void aWriteOffLargerThanTheStockIsRefusedRatherThanClamped() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Over Write Off Rose");
        stockIn(vendor, productId, 4, "delivery");

        mockMvc.perform(post(inventoryPath(productId) + "/write-offs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 9, "reason", "Whole shelf collapsed"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertEquals(4, quantityOf(productId));
        assertEquals(1L, movementsOf(productId));
    }

    @Test
    void aStockInThatClearsAFloorRaisesAvailabilityAgain() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Recovering Rose");
        stockIn(vendor, productId, 3, "delivery");
        reserve(productId, 3);

        stockIn(vendor, productId, 5, "second delivery");

        mockMvc.perform(get(inventoryPath(productId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8))
                .andExpect(jsonPath("$.available").value(5));
    }

    // ------------------------------------------------------------------
    // Alert settings
    // ------------------------------------------------------------------

    @Test
    void theLowStockThresholdIsReplacedAndWritesNoMovement() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Threshold Rose");
        stockIn(vendor, productId, 20, "delivery");

        mockMvc.perform(put(inventoryPath(productId) + "/low-stock-threshold")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lowStockThreshold", 6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockThreshold").value(6))
                .andExpect(jsonPath("$.quantity").value(20));

        assertEquals(1L, movementsOf(productId));
    }

    @Test
    void theExpiryDateIsSetAndClearedWithoutWritingAMovement() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Expiry Rose");
        stockIn(vendor, productId, 20, "delivery");

        mockMvc.perform(put(inventoryPath(productId) + "/expiry-date")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":\"2026-04-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiryDate").value("2026-04-30"));

        mockMvc.perform(put(inventoryPath(productId) + "/expiry-date")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiryDate").doesNotExist());

        assertEquals(1L, movementsOf(productId));
        assertNull(inventoryRepository.findByProductId(productId).orElseThrow().getExpiryDate());
    }

    @Test
    void aThresholdUpdateDoesNotDisturbTheQuantity() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Threshold Quantity Rose");
        stockIn(vendor, productId, 17, "delivery");
        reserve(productId, 9);

        mockMvc.perform(put(inventoryPath(productId) + "/low-stock-threshold")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lowStockThreshold", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(17))
                .andExpect(jsonPath("$.reservedQuantity").value(9))
                .andExpect(jsonPath("$.available").value(8));
    }

    // ------------------------------------------------------------------
    // Low-stock listing
    // ------------------------------------------------------------------

    @Test
    void theLowStockListHoldsOnlyProductsAtOrBelowTheirOwnThreshold() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);

        // available 4 against a threshold of 3: above it, so excluded.
        Long healthy = createProduct(vendor, "Healthy Stock Rose");
        stockIn(vendor, healthy, 40, "delivery");
        setThreshold(vendor, healthy, 3);

        // available 3 against a threshold of 3: exactly at it, included.
        Long boundary = createProduct(vendor, "Boundary Stock Rose");
        stockIn(vendor, boundary, 30, "delivery");
        setThreshold(vendor, boundary, 3);
        stockOut(vendor, boundary, 27, "sold at the door");

        // Availability counts only what is not reserved: 9 - 8 = 1.
        Long reserved = createProduct(vendor, "Reserved Stock Rose");
        stockIn(vendor, reserved, 9, "delivery");
        reserve(reserved, 8);
        setThreshold(vendor, reserved, 2);

        String healthyRow = "$.content[?(@.productId == %d)]".formatted(healthy);
        String boundaryRow = "$.content[?(@.productId == %d)]".formatted(boundary);
        String reservedRow = "$.content[?(@.productId == %d)]".formatted(reserved);

        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                // Scoped to this vendor's own rows: the shared
                // integration database holds many other vendors.
                .andExpect(jsonPath(healthyRow).doesNotExist())
                .andExpect(jsonPath(boundaryRow).exists())
                .andExpect(jsonPath(boundaryRow + ".available").value(3))
                .andExpect(jsonPath(reservedRow).exists())
                .andExpect(jsonPath(reservedRow + ".available").value(1))
                .andExpect(jsonPath(reservedRow + ".lowStock").value(true));
    }

    @Test
    void theLowStockListNeverContainsAnotherVendorsProducts() throws Exception {
        Vendor theirs = registerVendor(VendorProfile.Status.APPROVED);
        Long theirProduct = createProduct(theirs, "Their Low Stock Rose");
        stockIn(theirs, theirProduct, 1, "delivery");
        setThreshold(theirs, theirProduct, 5);

        Vendor mine = registerVendor(VendorProfile.Status.APPROVED);
        createProduct(mine, "My Low Stock Rose");

        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.productId == %d)]".formatted(theirProduct)).doesNotExist());
    }

    @Test
    void theLowStockListIsPaginated() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        for (int i = 1; i <= 3; i++) {
            Long productId = createProduct(vendor, "Paged Low Stock Rose " + i);
            stockIn(vendor, productId, i, "delivery");
            setThreshold(vendor, productId, 10);
        }

        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void theLowStockListIsEmptyForAVendorWithNothingToRestock() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Plenty Stock Rose");
        stockIn(vendor, productId, 50, "delivery");
        setThreshold(vendor, productId, 2);

        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.empty").value(true));
    }

    // ------------------------------------------------------------------
    // Movement history
    // ------------------------------------------------------------------

    @Test
    void theMovementHistoryIsPaginatedNewestFirst() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "History Rose");
        stockIn(vendor, productId, 30, "delivery");
        stockOut(vendor, productId, 5, "sold at the door");
        adjust(vendor, productId, -2, "recount");

        mockMvc.perform(get(inventoryPath(productId) + "/movements")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2))
                // Newest first: the adjustment was the last write.
                .andExpect(jsonPath("$.content[0].movementType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.content[0].quantityDelta").value(-2))
                .andExpect(jsonPath("$.content[0].reason").value("recount"))
                .andExpect(jsonPath("$.content[1].movementType").value("STOCK_OUT"));

        mockMvc.perform(get(inventoryPath(productId) + "/movements")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].movementType").value("STOCK_IN"))
                .andExpect(jsonPath("$.content[0].quantityDelta").value(30))
                .andExpect(jsonPath("$.content[0].actorEmail").value(vendor.email()));
    }

    @Test
    void aProductWithNoMovementsHasAnEmptyHistory() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Silent Rose");

        mockMvc.perform(get(inventoryPath(productId) + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.empty").value(true));
    }

    @Test
    void theMovementHistoryRejectsAPageSizeAboveTheCap() throws Exception {
        Vendor vendor = registerVendor(VendorProfile.Status.APPROVED);
        Long productId = createProduct(vendor, "Cap Rose");

        mockMvc.perform(get(inventoryPath(productId) + "/movements")
                        .param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private record Vendor(String email, String token, Long profileId) {
    }

    private static String inventoryPath(Long productId) {
        return "/api/v1/vendors/products/" + productId + "/inventory";
    }

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
     * Creates the product through {@link ProductService} rather than the catalog HTTP route. The
     * approval gate lives on the controller (D-13), so a PENDING or SUSPENDED vendor — two of the
     * states these tests must set up — cannot create a product over HTTP even though the product is
     * only a fixture here. Nothing else about the flow differs: the same service method, the same
     * product ⇒ inventory-at-zero transaction (task 3.3).
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

    private void stockIn(Vendor vendor, Long productId, int quantity, String reason) throws Exception {
        mockMvc.perform(post(inventoryPath(productId) + "/stock-in")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", quantity, "reason", reason))))
                .andExpect(status().isOk());
    }

    private void stockOut(Vendor vendor, Long productId, int quantity, String reason) throws Exception {
        mockMvc.perform(post(inventoryPath(productId) + "/stock-out")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", quantity, "reason", reason))))
                .andExpect(status().isOk());
    }

    private void adjust(Vendor vendor, Long productId, int quantity, String reason) throws Exception {
        mockMvc.perform(post(inventoryPath(productId) + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", quantity, "reason", reason))))
                .andExpect(status().isOk());
    }

    private void setThreshold(Vendor vendor, Long productId, int threshold) throws Exception {
        mockMvc.perform(put(inventoryPath(productId) + "/low-stock-threshold")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lowStockThreshold", threshold))))
                .andExpect(status().isOk());
    }

    /**
     * Establishes pending-order reservations directly in the database. Task 3.6 has no reserve
     * route — {@code RESERVE} movements arrive with checkout in Phase 5 — and without reserved
     * units the {@code quantity >= reserved_quantity} floor could never bind, so the 409 this API
     * exists to return would be untestable.
     */
    private void reserve(Long productId, int reserved) {
        assertEquals(1, jdbcTemplate.update(
                "UPDATE inventory SET reserved_quantity = ? WHERE product_id = ?", reserved, productId));
    }

    private int quantityOf(Long productId) {
        return inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();
    }

    private long movementsOf(Long productId) {
        return stockMovementRepository.countByProductId(productId);
    }

    private MovementType movementTypeOf(Long productId) {
        return newestMovement(productId).getMovementType();
    }

    private int movementDeltaOf(Long productId) {
        return newestMovement(productId).getQuantityDelta();
    }

    private String movementReasonOf(Long productId) {
        return newestMovement(productId).getReason();
    }

    private Long actorOf(Long productId) {
        return newestMovement(productId).getActor().getId();
    }

    private StockMovement newestMovement(Long productId) {
        List<StockMovement> movements = stockMovementRepository
                .findByProductIdOrderByCreatedAtDescIdDesc(productId);
        assertFalse(movements.isEmpty(), "expected at least one movement for product " + productId);
        return movements.get(0);
    }

    private void approve(Long profileId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private ResultActions adminAction(Long profileId, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"inventory integration probe\"}"));
    }

    private VendorRegisterRequest registerRequest(String email) {
        VendorRegisterRequest request = new VendorRegisterRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setFullName("Vendor Owner");
        request.setPhone(uniquePhone());
        request.setBusinessName("Inventory Probe Blossoms");
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
        return "inventory-" + UUID.randomUUID() + "@test.com";
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
