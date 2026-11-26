package com.flowerconnect.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.cart.dto.AddCartItemRequest;
import com.flowerconnect.cart.repository.CartRepository;
import com.flowerconnect.cart.service.CartService;
import com.flowerconnect.checkout.dto.CheckoutRequest;
import com.flowerconnect.checkout.dto.CheckoutQuoteRequest;
import com.flowerconnect.checkout.service.CheckoutService;
import com.flowerconnect.customer.domain.Address;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.pos.dto.PosSaleRequest;
import com.flowerconnect.pos.dto.PosSaleRequest.PosSaleLineRequest;
import com.flowerconnect.pos.dto.PosSaleRequest.PosPaymentMethod;
import com.flowerconnect.pos.repository.PosItemRepository;
import com.flowerconnect.pos.repository.PosTransactionRepository;
import com.flowerconnect.pos.service.PosService;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 11 Task 11.T acceptance test: POS walk-in sale vs. online checkout
 * racing for the vendor's last available unit.
 *
 * <p>Both {@code PosService.recordSale} (deducts {@code quantity} via SALE_POS)
 * and {@code CheckoutService.checkout} (increases {@code reserved_quantity} via
 * RESERVE) lock the same inventory row with {@code SELECT ... FOR UPDATE}.
 * With exactly one unit available, exactly one path may succeed; inventory
 * never goes negative and the persisted sale/movement state is consistent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PosCheckoutRaceIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String PINCODE = "560034";

    @Autowired
    private PosService posService;
    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private CartService cartService;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private PosTransactionRepository posTransactionRepository;
    @Autowired
    private PosItemRepository posItemRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductService productService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private VendorHoursRepository vendorHoursRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Long rosesCategoryId;
    private Long locationId;
    private Long vendorProfileId;
    private Long productId;
    private String vendorEmail;
    private String customerEmail;
    private String customerToken;
    private Long addressId;
    private String slotStart;
    private String quoteHash;

    @BeforeEach
    void setUp() throws Exception {
        rosesCategoryId = categoryRepository.findBySlug("roses").orElseThrow().getId();
        locationId = serviceLocationRepository.findByPincode(PINCODE).orElseThrow().getId();

        // --- Vendor (APPROVED, accepting orders, hours open all days) ---
        Role floristRole = roleRepository.findByName("FLORIST").orElseThrow();
        User vUser = userRepository.saveAndFlush(User.builder()
                .email("race-vendor-" + UUID.randomUUID() + "@t.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Race Vendor")
                .phone(uniquePhone())
                .role(floristRole)
                .status(User.Status.ACTIVE)
                .build());
        vendorEmail = vUser.getEmail();

        VendorProfile vendor = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(vUser)
                .businessName("Race Vendor Blossoms")
                .addressLine1("123 Race St")
                .serviceLocation(serviceLocationRepository.findById(locationId).orElseThrow())
                .latitude(new BigDecimal("12.93"))
                .longitude(new BigDecimal("77.62"))
                .deliveryRadiusKm(new BigDecimal("10.00"))
                .status(VendorProfile.Status.APPROVED)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());
        vendorProfileId = vendor.getId();

        for (DayOfWeek dow : DayOfWeek.values()) {
            vendorHoursRepository.saveAndFlush(VendorHours.builder()
                    .vendorProfile(vendor)
                    .weekday(dow)
                    .openTime(LocalTime.of(9, 0))
                    .closeTime(LocalTime.of(18, 0))
                    .closed(false)
                    .build());
        }

        // --- Product: ACTIVE, exactly 1 unit available ---
        productId = productService.create(vendorEmail, ProductRequest.builder()
                .name("Last Unit Rose")
                .categoryId(rosesCategoryId)
                .description("Race probe product")
                .basePrice(new BigDecimal("299.00"))
                .status(ProductStatus.ACTIVE)
                .build()).getId();
        Inventory inv = inventoryRepository.findByProductId(productId).orElseThrow();
        inv.setQuantity(1);
        inv.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(inv);

        // --- Customer (CUSTOMER) ---
        Role customerRole = roleRepository.findByName("CUSTOMER").orElseThrow();
        User customer = userRepository.saveAndFlush(User.builder()
                .email("race-customer-" + UUID.randomUUID() + "@t.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Race Customer")
                .phone(uniquePhone())
                .role(customerRole)
                .status(User.Status.ACTIVE)
                .build());
        customerEmail = customer.getEmail();
        customerToken = tokenFor(customerEmail);

        // --- Address at the same service location (distance = 0, in radius) ---
        ServiceLocation loc = serviceLocationRepository.findById(locationId).orElseThrow();
        addressId = addressRepository.saveAndFlush(Address.builder()
                .user(customer)
                .serviceLocation(loc)
                .label("Home")
                .line1("123 Test St")
                .latitude(loc.getLatitude())
                .longitude(loc.getLongitude())
                .defaultAddress(true)
                .build()).getId();

        // --- Cart: add the product (qty 1); cartService creates the cart row ---
        cartService.addItem(customerEmail, AddCartItemRequest.builder()
                .productId(productId)
                .quantity(1)
                .build());

        // --- Checkout quote -> hash ---
        String slot = futureSlotStart();
        slotStart = slot;

        String quoteBody = objectMapper.writeValueAsString(
                new CheckoutQuoteRequest() {{
                    setAddressId(addressId);
                    setItems(List.of(
                            new CheckoutQuoteRequest.QuoteItem() {{
                                setVendorId(vendorProfileId);
                                setSlotStart(slot);
                            }}
                    ));
                }}
        );

        MvcResult quoteRes = mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + customerToken)
                        .content(quoteBody))
                .andExpect(status().isOk()).andReturn();

        quoteHash = objectMapper.readTree(
                quoteRes.getResponse().getContentAsString()).get("quoteHash").asText();
    }

    @Test
    void posAndCheckoutRaceForLastUnitExactlyOneSucceeds() throws Exception {
        // --- POS sale request (deducts quantity) ---
        PosSaleRequest posRequest = new PosSaleRequest();
        posRequest.setItems(List.of(new PosSaleLineRequest(productId, 1)));
        posRequest.setPaymentMethod(PosPaymentMethod.CASH);
        String posKey = UUID.randomUUID().toString();

        // --- Checkout request (reserves quantity) ---
        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setAddressId(addressId);
        checkoutRequest.setClientOrderId(UUID.randomUUID());
        checkoutRequest.setQuoteHash(quoteHash);
        checkoutRequest.setConfirmPriceChanges(true);
        checkoutRequest.setItems(List.of(new CheckoutRequest.CheckoutItem() {{
            setVendorId(vendorProfileId);
            setSlotStart(slotStart);
        }}));

        // --- Fire both concurrently with a start barrier ---
        int threads = 2;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        Throwable[] failures = new Throwable[threads];
        String[] outcomes = new String[threads];
        Boolean[] posOk = new Boolean[threads];
        Boolean[] checkoutOk = new Boolean[threads];

        ExecutorService pool = Executors.newFixedThreadPool(threads);

        pool.submit(() -> {
            try {
                startGate.await();
                posService.recordSale(vendorEmail, posKey, posRequest);
                outcomes[0] = "pos";
                posOk[0] = true;
            } catch (Throwable t) {
                outcomes[0] = "pos";
                failures[0] = t;
                posOk[0] = false;
            } finally {
                doneGate.countDown();
            }
        });

        pool.submit(() -> {
            try {
                startGate.await();
                checkoutService.checkout(customerEmail, checkoutRequest);
                outcomes[1] = "checkout";
                checkoutOk[1] = true;
            } catch (Throwable t) {
                outcomes[1] = "checkout";
                failures[1] = t;
                checkoutOk[1] = false;
            } finally {
                doneGate.countDown();
            }
        });

        startGate.countDown(); // release both threads simultaneously
        boolean finished = doneGate.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(finished, "both threads should complete within 60s");

        // --- Exactly one path succeeded ---
        long successes = (posOk[0] == Boolean.TRUE ? 1 : 0) + (checkoutOk[1] == Boolean.TRUE ? 1 : 0);
        assertEquals(1, successes,
                "exactly one of POS/checkout must succeed. outcomes: pos=" + outcomes[0] +
                        "(" + posOk[0] + "), checkout=" + outcomes[1] + "(" + checkoutOk[1] + ")");

        // --- Persisted state must be consistent ---
        Inventory after = inventoryRepository.findByProductId(productId).orElseThrow();
        int available = after.getAvailable();
        assertEquals(0, available,
                "available stock must be 0 after the race (quantity=" + after.getQuantity() +
                        ", reserved=" + after.getReservedQuantity() + ")");

        // Only one movement total (either SALE_POS or RESERVE)
        long totalMovements = stockMovementRepository.countByProductId(productId);
        assertEquals(1, totalMovements,
                "exactly one stock movement must persist after the race");

        long salePosMovements = stockMovementRepository.countByProductIdAndMovementType(
                productId, StockMovement.MovementType.SALE_POS);
        long reserveMovements = stockMovementRepository.countByProductIdAndMovementType(
                productId, StockMovement.MovementType.RESERVE);
        assertEquals(1, salePosMovements + reserveMovements,
                "movement must be either SALE_POS (POS won) or RESERVE (checkout won)");

        if (posOk[0] == Boolean.TRUE) {
            // POS won: quantity deducted to 0, no reservation, one SALE_POS movement
            assertEquals(0, after.getQuantity());
            assertEquals(0, after.getReservedQuantity());
            assertEquals(1, salePosMovements);
            assertEquals(0, reserveMovements);
            assertEquals(1L, transactionsOf(vendorProfileId));
            assertEquals(0L, ordersOf(vendorProfileId));
        } else {
            // Checkout won: quantity unchanged (1), reserved to 1, one RESERVE movement
            assertEquals(1, after.getQuantity());
            assertEquals(1, after.getReservedQuantity());
            assertEquals(0, salePosMovements);
            assertEquals(1, reserveMovements);
            assertEquals(0L, transactionsOf(vendorProfileId));
            assertEquals(1L, ordersOf(vendorProfileId));
        }

        // --- No unexpected ledger footprint from POS ---
        if (posOk[0] == Boolean.TRUE) {
            assertEquals(0L, ordersOf(vendorProfileId),
                    "POS sale must create no order row");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String futureSlotStart() {
        ZoneId z = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.now(z);
        ZonedDateTime candidate = ZonedDateTime.of(now.toLocalDate(), LocalTime.of(12, 0), z);
        if (candidate.isBefore(now.plusMinutes(120))) {
            candidate = ZonedDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(12, 0), z);
        }
        return candidate.withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    private String uniquePhone() {
        StringBuilder digits = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return "+91" + digits;
    }

    private String tokenFor(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(email)
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk()).andReturn();
        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getAccessToken();
    }

    private long transactionsOf(Long vendorId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pos_transactions WHERE vendor_id = ?", Long.class, vendorId);
    }

    private long ordersOf(Long vendorId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE vendor_profile_id = ?", Long.class, vendorId);
    }
}
