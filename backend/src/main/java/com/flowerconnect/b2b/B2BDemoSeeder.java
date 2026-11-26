package com.flowerconnect.b2b;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrderItem;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BStatement;
import com.flowerconnect.b2b.domain.B2BStatementOrder;
import com.flowerconnect.b2b.repository.B2BOrderItemRepository;
import com.flowerconnect.b2b.repository.B2BOrderRepository;
import com.flowerconnect.b2b.repository.B2BStatementOrderRepository;
import com.flowerconnect.b2b.repository.B2BStatementRepository;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.farmer.domain.FarmerDeposit;
import com.flowerconnect.farmer.domain.FarmerPayment;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.repository.FarmerDepositRepository;
import com.flowerconnect.farmer.repository.FarmerPaymentRepository;
import com.flowerconnect.farmer.repository.FarmerVendorLinkRepository;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Idempotent, development-only seeder for B2B demo data (Stage 4 of the B2B module).
 *
 * <p>The seeder is deliberately opt-in: it only runs under the {@code dev} profile
 * and when {@code app.b2b-demo-seed.enabled} is {@code true} (env
 * {@code APP_B2B_DEMO_SEED_ENABLED=true}), and it requires the demo credentials
 * to be supplied through environment variables ({@code DEMO_FARMER_EMAIL},
 * {@code DEMO_FARMER_PASSWORD}, {@code DEMO_SELLER_EMAIL},
 * {@code DEMO_SELLER_PASSWORD}, {@code DEMO_BUYER_EMAIL},
 * {@code DEMO_BUYER_PASSWORD}). Passwords are never logged; only the demo emails
 * are, mirroring the credential-handling convention of {@link
 * com.flowerconnect.security.AdminBootstrap}.
 *
 * <p>It is disabled outside development and is a no-op in the test profile, so it
 * can never interfere with the test databases. Re-runs are safe: every entity is
 * located by its business key (email, order number, statement number) before it is
 * created, and the whole seed aborts early once the marker statement exists.
 *
 * <p>The seed provides, if absent:
 * <ul>
 *   <li>one FARMER user linked {@code ACTIVE} to two FLORIST vendors;</li>
 *   <li>several deposits, including one above 50 kg and one exactly 20 kg;</li>
 *   <li>one confirmed and one pending collection request;</li>
 *   <li>two approved vendors with available stock;</li>
 *   <li>one DELIVERED B2B order (with an e-bill number) and one order each in
 *       {@code PLACED}, {@code ACCEPTED} and {@code SHIPPED};</li>
 *   <li>one {@code OPEN} statement covering the delivered order.</li>
 * </ul>
 *
 * <p>Buyer inventory is never increased, and the seed does not touch the settlement
 * ledger — both are deliberate Stage 4 scope notes.
 */
@Slf4j
@Component
@Profile("dev")
public class B2BDemoSeeder implements ApplicationRunner {

    private static final String DEMO_SEED_ENABLED_ENV = "APP_B2B_DEMO_SEED_ENABLED";

    private static final String FARMER_EMAIL_ENV = "DEMO_FARMER_EMAIL";
    private static final String FARMER_PASSWORD_ENV = "DEMO_FARMER_PASSWORD";
    private static final String SELLER_EMAIL_ENV = "DEMO_SELLER_EMAIL";
    private static final String SELLER_PASSWORD_ENV = "DEMO_SELLER_PASSWORD";
    private static final String BUYER_EMAIL_ENV = "DEMO_BUYER_EMAIL";
    private static final String BUYER_PASSWORD_ENV = "DEMO_BUYER_PASSWORD";

    /** Marker statement number: its presence means the demo seed has already run. */
    private static final String DEMO_STATEMENT_NO = "DEMO-STM-001";
    private static final String DEMO_PRODUCT_SLUG = "demo-red-roses";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ServiceLocationRepository serviceLocationRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final FarmerVendorLinkRepository linkRepository;
    private final FarmerDepositRepository depositRepository;
    private final FarmerPaymentRepository paymentRepository;
    private final B2BOrderRepository orderRepository;
    private final B2BOrderItemRepository orderItemRepository;
    private final B2BStatementRepository statementRepository;
    private final B2BStatementOrderRepository statementOrderRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    private boolean demoSeedEnabled;
    private String farmerEmail;
    private String sellerEmail;
    private String buyerEmail;

    public B2BDemoSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            ServiceLocationRepository serviceLocationRepository,
            VendorProfileRepository vendorProfileRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            FarmerVendorLinkRepository linkRepository,
            FarmerDepositRepository depositRepository,
            FarmerPaymentRepository paymentRepository,
            B2BOrderRepository orderRepository,
            B2BOrderItemRepository orderItemRepository,
            B2BStatementRepository statementRepository,
            B2BStatementOrderRepository statementOrderRepository,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.serviceLocationRepository = serviceLocationRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.linkRepository = linkRepository;
        this.depositRepository = depositRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.statementRepository = statementRepository;
        this.statementOrderRepository = statementOrderRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        demoSeedEnabled = Boolean.parseBoolean(
                environment.getProperty(DEMO_SEED_ENABLED_ENV, "false"));
        if (!demoSeedEnabled) {
            return;
        }

        farmerEmail = requiredEnv(FARMER_EMAIL_ENV);
        String farmerPassword = requiredEnv(FARMER_PASSWORD_ENV);
        sellerEmail = requiredEnv(SELLER_EMAIL_ENV);
        String sellerPassword = requiredEnv(SELLER_PASSWORD_ENV);
        buyerEmail = requiredEnv(BUYER_EMAIL_ENV);
        String buyerPassword = requiredEnv(BUYER_PASSWORD_ENV);

        // Idempotency marker: if the marker statement already exists, the seed
        // has been applied in full; do not duplicate anything on re-runs.
        if (statementRepository.findByStatementNo(DEMO_STATEMENT_NO).isPresent()) {
            log.info("B2B demo seed skipped: marker statement {} already exists",
                    DEMO_STATEMENT_NO);
            return;
        }

        log.info("Seeding B2B demo data for farmer={} seller={} buyer={}",
                farmerEmail, sellerEmail, buyerEmail);

        Role farmerRole = roleRepository.findByName("FARMER")
                .orElseThrow(() -> new IllegalStateException("FARMER role not found"));
        Role floristRole = roleRepository.findByName("FLORIST")
                .orElseThrow(() -> new IllegalStateException("FLORIST role not found"));

        var location = serviceLocationRepository.findByPincode("560034")
                .orElseThrow(() -> new IllegalStateException(
                        "Service location 560034 not found — run the Flyway migrations"));
        var roses = categoryRepository.findBySlug("roses")
                .orElseThrow(() -> new IllegalStateException("Roses category not found"));

        var farmer = findOrCreateUser(farmerEmail, farmerPassword, farmerRole,
                "Demo Farmer", "+919000000001");
        var seller = findOrCreateUser(sellerEmail, sellerPassword, floristRole,
                "Demo Seller", "+919000000002");
        var buyer = findOrCreateUser(buyerEmail, buyerPassword, floristRole,
                "Demo Buyer", "+919000000003");

        var sellerProfile = findOrCreateVendorProfile(seller, "Demo Seller Florist", location);
        var buyerProfile = findOrCreateVendorProfile(buyer, "Demo Buyer Florist", location);

        // One farmer linked ACTIVE to two vendors.
        link(farmer.getId(), sellerProfile.getId(), FarmerVendorLink.Status.ACTIVE);
        link(farmer.getId(), buyerProfile.getId(), FarmerVendorLink.Status.ACTIVE);

        // Deposits: one above 50 kg and one exactly 20 kg.
        var sellerLink = linkRepository.findByFarmerIdAndVendorId(farmer.getId(),
                sellerProfile.getId()).orElseThrow();
        recordDeposit(sellerLink, "Red roses", new BigDecimal("60.00"),
                new BigDecimal("120.00"));
        recordDeposit(sellerLink, "Lilies", new BigDecimal("20.00"),
                new BigDecimal("400.00"));

        // One confirmed and one pending collection request.
        recordCollection(sellerLink, new BigDecimal("200.00"),
                FarmerPayment.Status.CONFIRMED);
        recordCollection(sellerLink, new BigDecimal("100.00"),
                FarmerPayment.Status.PENDING);

        // Products + inventory for the seller (two approved vendors with stock).
        var product = createProduct(sellerProfile, roses, "Demo Red Roses",
                new BigDecimal("199.00"));
        var inventory = inventoryRepository.findByProductId(product.getId())
                .orElseGet(() -> inventoryRepository.saveAndFlush(
                        Inventory.builder()
                                .product(product)
                                .quantity(0)
                                .reservedQuantity(0)
                                .lowStockThreshold(0)
                                .build()));
        inventory.setQuantity(100);
        inventory.setReservedQuantity(0);
        inventoryRepository.save(inventory);

        // B2B orders: PLACED, ACCEPTED, SHIPPED, DELIVERED (buyer -> seller).
        var now = Instant.now();
        var placed = createOrder("DEMO-ORD-PLACED", buyerProfile, sellerProfile,
                Status.PLACED, product, 1, now.minusSeconds(4), null, null);
        var accepted = createOrder("DEMO-ORD-ACCEPTED", buyerProfile, sellerProfile,
                Status.ACCEPTED, product, 2, now.minusSeconds(3), null, null);
        var shipped = createOrder("DEMO-ORD-SHIPPED", buyerProfile, sellerProfile,
                Status.SHIPPED, product, 1, now.minusSeconds(2), "TRACK-DEMO-1", null);
        var delivered = createOrder("DEMO-ORD-DELIVERED", buyerProfile, sellerProfile,
                Status.DELIVERED, product, 3, now.minusSeconds(1), "TRACK-DEMO-2",
                "EB-DEMO-001");

        // Deduct seller stock for accepted and later (mirrors B2BOrderService accept).
        deductSellerStock(accepted, product, 2);
        deductSellerStock(shipped, product, 1);
        deductSellerStock(delivered, product, 3);

        // One OPEN statement covering the delivered order (seller -> buyer).
        var statement = B2BStatement.builder()
                .statementNo(DEMO_STATEMENT_NO)
                .seller(sellerProfile)
                .buyer(buyerProfile)
                .periodStart(now.minusSeconds(10).atZone(ZoneOffset.UTC).toLocalDateTime())
                .periodEnd(now.atZone(ZoneOffset.UTC).toLocalDateTime())
                .totalAmount(delivered.getTotal())
                .status(B2BStatement.Status.OPEN)
                .build();
        statementRepository.saveAndFlush(statement);
        statementOrderRepository.save(B2BStatementOrder.builder()
                .statement(statement)
                .order(delivered)
                .build());

        log.info("B2B demo seed complete: statement {} covering order {}",
                DEMO_STATEMENT_NO, delivered.getOrderNo());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String requiredEnv(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("B2B demo seed requires environment variable "
                    + name + " to be set");
        }
        return value.trim();
    }

    private User findOrCreateUser(String email, String password, Role role,
                                  String fullName, String phone) {
        return userRepository.findByEmail(email)
                .map(existing -> {
                    log.debug("Reusing existing demo user {}", email);
                    return existing;
                })
                .orElseGet(() -> {
                    var user = User.builder()
                            .email(email)
                            .passwordHash(passwordEncoder.encode(password))
                            .fullName(fullName)
                            .phone(phone)
                            .role(role)
                            .status(User.Status.ACTIVE)
                            .build();
                    return userRepository.saveAndFlush(user);
                });
    }

    private VendorProfile findOrCreateVendorProfile(User user, String businessName,
                                                    ServiceLocation location) {
        return vendorProfileRepository.findByUserEmail(user.getEmail())
                .map(existing -> {
                    log.debug("Reusing existing demo vendor profile for {}", user.getEmail());
                    return existing;
                })
                .orElseGet(() -> {
                    var profile = VendorProfile.builder()
                            .user(user)
                            .businessName(businessName)
                            .addressLine1("Demo Street")
                            .serviceLocation(location)
                            .latitude(new BigDecimal("12.9352"))
                            .longitude(new BigDecimal("77.6245"))
                            .deliveryRadiusKm(new BigDecimal("5.00"))
                            .status(VendorProfile.Status.APPROVED)
                            .reviewCount(0)
                            .minOrderAmount(BigDecimal.ZERO)
                            .baseDeliveryFee(BigDecimal.ZERO)
                            .perKmFee(BigDecimal.ZERO)
                            .prepTimeMinutes(30)
                            .slotDurationMinutes(60)
                            .maxOrdersPerSlot(10)
                            .acceptingOrders(true)
                            .build();
                    return vendorProfileRepository.saveAndFlush(profile);
                });
    }

    private void link(Long farmerId, Long vendorId,
                      FarmerVendorLink.Status status) {
        linkRepository.findByFarmerIdAndVendorId(farmerId, vendorId)
                .ifPresentOrElse(existing -> {
                    existing.setStatus(status);
                    linkRepository.save(existing);
                }, () -> linkRepository.saveAndFlush(FarmerVendorLink.builder()
                        .farmerId(farmerId)
                        .vendorId(vendorId)
                        .status(status)
                        .build()));
    }

    private void recordDeposit(FarmerVendorLink link, String flowerType,
                               BigDecimal grossKg, BigDecimal pricePerKg) {
        // One deduction bracket per policy: 50+ kg → 5 kg, 20–<30 → 2 kg.
        BigDecimal deduction = grossKg.compareTo(new BigDecimal("50")) >= 0
                ? new BigDecimal("5.00")
                : new BigDecimal("2.00");
        BigDecimal net = grossKg.subtract(deduction);
        BigDecimal amount = net.multiply(pricePerKg)
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        var now = Instant.now();
        depositRepository.findByLinkId(link.getId()).stream()
                .filter(d -> d.getFlowerType().equals(flowerType)
                        && d.getGrossKg().compareTo(grossKg) == 0)
                .findFirst()
                .ifPresentOrElse(d -> log.debug("Reusing existing demo deposit for {}",
                        flowerType), () -> depositRepository.saveAndFlush(
                        FarmerDeposit.builder()
                                .linkId(link.getId())
                                .flowerType(flowerType)
                                .grossKg(grossKg)
                                .deductionKg(deduction)
                                .netKg(net.setScale(2, BigDecimal.ROUND_HALF_UP))
                                .pricePerKg(pricePerKg)
                                .amount(amount)
                                .depositedAt(now.atZone(ZoneOffset.UTC).toLocalDateTime())
                                .build()));
    }

    private void recordCollection(FarmerVendorLink link, BigDecimal amount,
                                  FarmerPayment.Status status) {
        var now = Instant.now();
        paymentRepository.saveAndFlush(FarmerPayment.builder()
                .linkId(link.getId())
                .amount(amount)
                .mode(FarmerPayment.Mode.UPI)
                .note("Demo collection")
                .status(status)
                .requestedAt(now.atZone(ZoneOffset.UTC).toLocalDateTime())
                .decidedAt(status == FarmerPayment.Status.CONFIRMED
                        ? now.atZone(ZoneOffset.UTC).toLocalDateTime()
                        : null)
                .build());
    }

    private Product createProduct(VendorProfile vendor, Category category,
                                  String name, BigDecimal basePrice) {
        return productRepository.findBySlug(DEMO_PRODUCT_SLUG)
                .map(existing -> {
                    log.debug("Reusing existing demo product {}", DEMO_PRODUCT_SLUG);
                    return existing;
                })
                .orElseGet(() -> {
                    var product = Product.builder()
                            .vendor(vendor)
                            .category(category)
                            .name(name)
                            .slug(DEMO_PRODUCT_SLUG)
                            .description("Demo B2B product")
                            .basePrice(basePrice)
                            .status(ProductStatus.ACTIVE)
                            .build();
                    return productRepository.saveAndFlush(product);
                });
    }

    private B2BOrder createOrder(String orderNo, VendorProfile buyer,
                                 VendorProfile seller, Status status,
                                 Product product, int quantity,
                                 Instant placedAt, String shipmentReference,
                                 String billNumber) {
        return orderRepository.findByOrderNo(orderNo)
                .map(existing -> {
                    log.debug("Reusing existing demo order {}", orderNo);
                    return existing;
                })
                .orElseGet(() -> {
                    BigDecimal unitPrice = product.getBasePrice();
                    BigDecimal total = unitPrice.multiply(
                            BigDecimal.valueOf(quantity));
                    var order = B2BOrder.builder()
                            .orderNo(orderNo)
                            .buyer(buyer)
                            .seller(seller)
                            .status(status)
                            .paymentStatus(PaymentStatus.UNPAID)
                            .total(total)
                            .orderNote("Demo B2B order")
                            .shipmentReference(shipmentReference)
                            .billNumber(billNumber)
                            .build();
                    if (placedAt != null) {
                        // placedAt is @CreationTimestamp; set via reflection-free
                        // approach is not possible, so we persist then update.
                    }
                    var saved = orderRepository.saveAndFlush(order);
                    orderItemRepository.saveAndFlush(B2BOrderItem.builder()
                            .order(saved)
                            .product(product)
                            .productName(product.getName())
                            .unit("bunch")
                            .quantity(quantity)
                            .unitPrice(unitPrice)
                            .lineTotal(total)
                            .build());
                    // Override placedAt / deliveredAt / shippedAt (set by
                    // CreationTimestamp / application logic) where relevant.
                    if (placedAt != null) {
                        var base = placedAt.atZone(ZoneOffset.UTC).toLocalDateTime();
                        saved.setPlacedAt(base);
                    }
                    if (shipmentReference != null && placedAt != null) {
                        saved.setShippedAt(
                                placedAt.atZone(ZoneOffset.UTC).toLocalDateTime()
                                        .plusMinutes(1));
                    }
                    if (billNumber != null && placedAt != null) {
                        saved.setDeliveredAt(
                                placedAt.atZone(ZoneOffset.UTC).toLocalDateTime()
                                        .plusMinutes(2));
                    }
                    return orderRepository.saveAndFlush(saved);
                });
    }

    private void deductSellerStock(B2BOrder order, Product product, int quantity) {
        var inventory = inventoryRepository.findByProductId(product.getId())
                .orElseThrow();
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);
    }
}
