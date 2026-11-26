/*
 * Copyright 2026 FlowerConnect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flowerconnect.demo;

import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.repository.SettlementRepository;
import com.flowerconnect.billing.service.SettlementService;
import com.flowerconnect.cart.dto.AddCartItemRequest;
import com.flowerconnect.cart.service.CartService;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.CategoryRequest;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.dto.ProductResponse;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.service.CategoryService;
import com.flowerconnect.catalog.service.ProductImageService;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.checkout.dto.CheckoutQuoteRequest;
import com.flowerconnect.checkout.dto.CheckoutQuoteResponse;
import com.flowerconnect.checkout.dto.CheckoutRequest;
import com.flowerconnect.checkout.dto.CheckoutResponse;
import com.flowerconnect.checkout.service.CheckoutService;
import com.flowerconnect.customer.domain.Address;
import com.flowerconnect.customer.dto.AddressRequest;
import com.flowerconnect.customer.dto.AddressResponse;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.customer.service.AddressService;
import com.flowerconnect.dispute.domain.Dispute;
import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.dispute.dto.DisputeDetailResponse;
import com.flowerconnect.dispute.dto.RaiseDisputeRequest;
import com.flowerconnect.dispute.dto.ResolveDisputeRequest;
import com.flowerconnect.dispute.dto.VendorDisputeResponseRequest;
import com.flowerconnect.dispute.repository.DisputeRepository;
import com.flowerconnect.dispute.service.DisputeService;
import com.flowerconnect.domain.AuditActorType;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.dto.ExpiryDateRequest;
import com.flowerconnect.inventory.dto.LowStockThresholdRequest;
import com.flowerconnect.inventory.dto.StockInRequest;
import com.flowerconnect.inventory.service.InventoryService;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentMethod;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.service.CancellationService;
import com.flowerconnect.order.service.OrderStateMachine;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.pos.dto.PosSaleRequest;
import com.flowerconnect.pos.dto.PosSaleRequest.PosPaymentMethod;
import com.flowerconnect.pos.dto.PosSaleRequest.PosSaleLineRequest;
import com.flowerconnect.pos.repository.PosTransactionRepository;
import com.flowerconnect.pos.service.PosService;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.review.dto.ReviewDetailResponse;
import com.flowerconnect.review.dto.ReviewRequest;
import com.flowerconnect.review.repository.ReviewRepository;
import com.flowerconnect.review.service.ReviewService;
import com.flowerconnect.security.AuthService;
import com.flowerconnect.security.dto.RegisterRequest;
import com.flowerconnect.vendor.dto.SlotResponse;
import com.flowerconnect.vendor.dto.VendorHoursRequest;
import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import com.flowerconnect.vendor.service.SlotService;
import com.flowerconnect.vendor.service.VendorAdminService;
import com.flowerconnect.vendor.service.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Service managing idempotent dev demo data creation (Phase 14 task 14.5).
 * Only loaded under the {@code dev} Spring profile.
 * Creates all demo state exclusively through existing service layer methods and state machines.
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DemoSeedService {

    // Demo customer emails
    public static final String CUSTOMER_1_EMAIL = "demo-customer-1@flowerconnect.test";
    public static final String CUSTOMER_2_EMAIL = "demo-customer-2@flowerconnect.test";
    public static final String CUSTOMER_3_EMAIL = "demo-customer-3@flowerconnect.test";

    // Demo vendor emails
    public static final String VENDOR_1_EMAIL = "vendor-blossom@flowerconnect.test";
    public static final String VENDOR_2_EMAIL = "vendor-petalcraft@flowerconnect.test";
    public static final String VENDOR_3_EMAIL = "vendor-gardenia@flowerconnect.test";
    public static final String VENDOR_4_EMAIL = "vendor-oasis@flowerconnect.test";
    public static final String VENDOR_5_EMAIL = "vendor-boutique@flowerconnect.test";

    // Fixed idempotency key for POS sales
    public static final String POS_IDEMPOTENCY_KEY_1 = "00000000-0000-4000-8000-000000000001";
    public static final String POS_IDEMPOTENCY_KEY_2 = "00000000-0000-4000-8000-000000000002";

    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DisputeRepository disputeRepository;
    private final ReviewRepository reviewRepository;
    private final PosTransactionRepository posTransactionRepository;
    private final SettlementRepository settlementRepository;
    private final ServiceLocationRepository serviceLocationRepository;

    private final AuthService authService;
    private final VendorService vendorService;
    private final VendorAdminService vendorAdminService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final ProductImageService productImageService;
    private final AddressService addressService;
    private final CartService cartService;
    private final SlotService slotService;
    private final CheckoutService checkoutService;
    private final OrderStateMachine orderStateMachine;
    private final PaymentService paymentService;
    private final CancellationService cancellationService;
    private final DisputeService disputeService;
    private final ReviewService reviewService;
    private final PosService posService;
    private final SettlementService settlementService;
    private final Clock clock;

    /**
     * Seeds the full representative demo dataset idempotently.
     */
    public void seedAll(String demoPassword, String adminEmail) {
        log.info("Starting demo dataset seeding...");

        // 1. Resolve admin
        User adminUser = resolveAdmin(adminEmail);
        String resolvedAdminEmail = adminUser.getEmail();

        // 2. Child categories
        seedCategories(resolvedAdminEmail);

        // 3. Vendors (3 approved, 1 pending, 1 suspended)
        seedVendors(demoPassword, resolvedAdminEmail);

        // 4. Products, stock, and images
        seedProductsAndStock(resolvedAdminEmail);

        // 5. Customers and addresses
        seedCustomersAndAddresses(demoPassword);

        // 6. Representative order history, disputes, reviews
        seedOrderHistoryAndReviews(resolvedAdminEmail);

        // 7. POS sales
        seedPosSales();

        // 8. Weekly settlements
        seedSettlements(adminUser);

        log.info("Demo dataset seeding completed successfully.");
    }

    /**
     * Resolves the admin user to use for administrative operations.
     */
    public User resolveAdmin(String adminEmail) {
        if (adminEmail != null && !adminEmail.isBlank()) {
            Optional<User> adminOpt = userRepository.findByEmailWithRole(adminEmail.trim().toLowerCase());
            if (adminOpt.isPresent()) {
                return adminOpt.get();
            }
        }
        // Fallback: look for any active ADMIN user
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getName()))
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No active ADMIN user found in database. Please configure ADMIN_EMAIL and ADMIN_PASSWORD."));
    }

    /**
     * Seeds child categories under the pre-existing baseline root categories.
     */
    public void seedCategories(String adminEmail) {
        Category roses = categoryRepository.findBySlug("roses").orElse(null);
        Category bouquets = categoryRepository.findBySlug("bouquets").orElse(null);
        Category arrangements = categoryRepository.findBySlug("arrangements").orElse(null);
        Category occasions = categoryRepository.findBySlug("occasions").orElse(null);

        if (roses != null) {
            createCategoryIfAbsent(adminEmail, "Dutch Long Stem Roses", roses.getId(), 1);
            createCategoryIfAbsent(adminEmail, "Miniature Spray Roses", roses.getId(), 2);
        }
        if (bouquets != null) {
            createCategoryIfAbsent(adminEmail, "Hand-Tied Bridal Bouquets", bouquets.getId(), 1);
            createCategoryIfAbsent(adminEmail, "Celebration Mixed Bouquets", bouquets.getId(), 2);
        }
        if (arrangements != null) {
            createCategoryIfAbsent(adminEmail, "Dining Table Centerpieces", arrangements.getId(), 1);
            createCategoryIfAbsent(adminEmail, "Basket Flower Arrangements", arrangements.getId(), 2);
        }
        if (occasions != null) {
            createCategoryIfAbsent(adminEmail, "Anniversary Specials", occasions.getId(), 1);
            createCategoryIfAbsent(adminEmail, "Sympathy & Condolence Florals", occasions.getId(), 2);
        }
    }

    private void createCategoryIfAbsent(String adminEmail, String name, Long parentId, int displayOrder) {
        boolean exists = categoryRepository.findAll().stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(name));
        if (!exists) {
            categoryService.create(adminEmail, CategoryRequest.builder()
                    .name(name)
                    .parentId(parentId)
                    .displayOrder(displayOrder)
                    .active(true)
                    .build());
            log.info("Seeded category '{}' under parent ID {}", name, parentId);
        }
    }

    /**
     * Seeds 5 vendors: 3 approved with differing delivery parameters, 1 pending approval, 1 suspended.
     */
    public void seedVendors(String demoPassword, String adminEmail) {
        List<ServiceLocation> locations = serviceLocationRepository.findAll();
        if (locations.isEmpty()) {
            throw new IllegalStateException("Service locations table is empty; check Flyway V4 migration");
        }

        // Helper to find service location by area
        ServiceLocation koramangala = locations.stream().filter(l -> "Koramangala".equalsIgnoreCase(l.getArea())).findFirst().orElse(locations.get(0));
        ServiceLocation indiranagar = locations.stream().filter(l -> "Indiranagar".equalsIgnoreCase(l.getArea())).findFirst().orElse(locations.get(0));
        ServiceLocation whitefield = locations.stream().filter(l -> "Whitefield".equalsIgnoreCase(l.getArea())).findFirst().orElse(locations.get(0));
        ServiceLocation hsrLayout = locations.stream().filter(l -> "HSR Layout".equalsIgnoreCase(l.getArea())).findFirst().orElse(locations.get(0));
        ServiceLocation jayanagar = locations.stream().filter(l -> "Jayanagar".equalsIgnoreCase(l.getArea())).findFirst().orElse(locations.get(0));

        // 1. Vendor 1: Blossom Haven (Approved, Koramangala)
        if (!userRepository.existsByEmail(VENDOR_1_EMAIL)) {
            VendorProfileResponse resp = vendorService.register(VendorRegisterRequest.builder()
                    .email(VENDOR_1_EMAIL)
                    .password(demoPassword)
                    .fullName("Ramesh Kumar")
                    .phone("+919100000001")
                    .businessName("Blossom Haven")
                    .description("Artisan floristry in the heart of Koramangala. Premium fresh cut flowers and daily arrangements.")
                    .addressLine1("42, 80 Feet Road, 4th Block")
                    .addressLine2("Near Sony World Signal")
                    .serviceLocationId(koramangala.getId())
                    .deliveryRadiusKm(new BigDecimal("8.00"))
                    .minOrderAmount(new BigDecimal("200.00"))
                    .baseDeliveryFee(new BigDecimal("40.00"))
                    .perKmFee(new BigDecimal("10.00"))
                    .freeDeliveryAbove(new BigDecimal("1000.00"))
                    .prepTimeMinutes(30)
                    .slotDurationMinutes(60)
                    .maxOrdersPerSlot(5)
                    .acceptingOrders(true)
                    .hours(standardWeeklyHours(LocalTime.of(8, 0), LocalTime.of(20, 0)))
                    .build());
            vendorAdminService.approve(adminEmail, resp.getId());
            log.info("Registered and approved vendor: {}", VENDOR_1_EMAIL);
        }

        // 2. Vendor 2: Petal Craft Florals (Approved, Indiranagar)
        if (!userRepository.existsByEmail(VENDOR_2_EMAIL)) {
            VendorProfileResponse resp = vendorService.register(VendorRegisterRequest.builder()
                    .email(VENDOR_2_EMAIL)
                    .password(demoPassword)
                    .fullName("Priya Nair")
                    .phone("+919100000002")
                    .businessName("Petal Craft Florals")
                    .description("Boutique contemporary floral studio specializing in bespoke hand-tied bouquets and exotic blooms.")
                    .addressLine1("108, 12th Main Road")
                    .addressLine2("HAL 2nd Stage")
                    .serviceLocationId(indiranagar.getId())
                    .deliveryRadiusKm(new BigDecimal("12.00"))
                    .minOrderAmount(new BigDecimal("350.00"))
                    .baseDeliveryFee(new BigDecimal("50.00"))
                    .perKmFee(new BigDecimal("12.00"))
                    .freeDeliveryAbove(new BigDecimal("1500.00"))
                    .prepTimeMinutes(45)
                    .slotDurationMinutes(90)
                    .maxOrdersPerSlot(8)
                    .acceptingOrders(true)
                    .hours(standardWeeklyHours(LocalTime.of(9, 0), LocalTime.of(21, 0)))
                    .build());
            vendorAdminService.approve(adminEmail, resp.getId());
            log.info("Registered and approved vendor: {}", VENDOR_2_EMAIL);
        }

        // 3. Vendor 3: Green Gardenia (Approved, HSR Layout)
        if (!userRepository.existsByEmail(VENDOR_3_EMAIL)) {
            VendorProfileResponse resp = vendorService.register(VendorRegisterRequest.builder()
                    .email(VENDOR_3_EMAIL)
                    .password(demoPassword)
                    .fullName("Anand Verma")
                    .phone("+919100000003")
                    .businessName("Green Gardenia")
                    .description("Eco-friendly local flower shop offering locally sourced blossoms, potted plants, and festive centerpieces.")
                    .addressLine1("24, 27th Main Road")
                    .addressLine2("Sector 1, HSR Layout")
                    .serviceLocationId(hsrLayout.getId())
                    .deliveryRadiusKm(new BigDecimal("6.00"))
                    .minOrderAmount(new BigDecimal("150.00"))
                    .baseDeliveryFee(new BigDecimal("30.00"))
                    .perKmFee(new BigDecimal("8.00"))
                    .freeDeliveryAbove(new BigDecimal("800.00"))
                    .prepTimeMinutes(20)
                    .slotDurationMinutes(60)
                    .maxOrdersPerSlot(4)
                    .acceptingOrders(true)
                    .hours(standardWeeklyHours(LocalTime.of(7, 30), LocalTime.of(19, 30)))
                    .build());
            vendorAdminService.approve(adminEmail, resp.getId());
            log.info("Registered and approved vendor: {}", VENDOR_3_EMAIL);
        }

        // 4. Vendor 4: Orchid Oasis (Pending approval, Jayanagar)
        if (!userRepository.existsByEmail(VENDOR_4_EMAIL)) {
            vendorService.register(VendorRegisterRequest.builder()
                    .email(VENDOR_4_EMAIL)
                    .password(demoPassword)
                    .fullName("Sunita Rao")
                    .phone("+919100000004")
                    .businessName("Orchid Oasis")
                    .description("Rare and exotic orchids collector boutique.")
                    .addressLine1("15, 4th Block, 9th Main")
                    .addressLine2("Opposite City Central Library")
                    .serviceLocationId(jayanagar.getId())
                    .deliveryRadiusKm(new BigDecimal("10.00"))
                    .minOrderAmount(new BigDecimal("500.00"))
                    .baseDeliveryFee(new BigDecimal("60.00"))
                    .perKmFee(new BigDecimal("15.00"))
                    .prepTimeMinutes(60)
                    .slotDurationMinutes(60)
                    .maxOrdersPerSlot(3)
                    .acceptingOrders(true)
                    .hours(standardWeeklyHours(LocalTime.of(9, 0), LocalTime.of(20, 0)))
                    .build());
            // Intentionally left in PENDING_APPROVAL
            log.info("Registered pending vendor: {}", VENDOR_4_EMAIL);
        }

        // 5. Vendor 5: Flora Boutique (Suspended, Whitefield)
        if (!userRepository.existsByEmail(VENDOR_5_EMAIL)) {
            VendorProfileResponse resp = vendorService.register(VendorRegisterRequest.builder()
                    .email(VENDOR_5_EMAIL)
                    .password(demoPassword)
                    .fullName("Vikram Seth")
                    .phone("+919100000005")
                    .businessName("Flora Boutique")
                    .description("Luxury floral arrangements and seasonal exotic decor.")
                    .addressLine1("88, Whitefield Main Road")
                    .addressLine2("Near ITPL")
                    .serviceLocationId(whitefield.getId())
                    .deliveryRadiusKm(new BigDecimal("15.00"))
                    .minOrderAmount(new BigDecimal("400.00"))
                    .baseDeliveryFee(new BigDecimal("70.00"))
                    .perKmFee(new BigDecimal("15.00"))
                    .prepTimeMinutes(40)
                    .slotDurationMinutes(60)
                    .maxOrdersPerSlot(5)
                    .acceptingOrders(false)
                    .hours(standardWeeklyHours(LocalTime.of(8, 30), LocalTime.of(20, 30)))
                    .build());
            vendorAdminService.approve(adminEmail, resp.getId());
            vendorAdminService.suspend(adminEmail, resp.getId(), "Annual business licence verification pending");
            log.info("Registered and suspended vendor: {}", VENDOR_5_EMAIL);
        }
    }

    private List<VendorHoursRequest> standardWeeklyHours(LocalTime open, LocalTime close) {
        List<VendorHoursRequest> list = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            list.add(VendorHoursRequest.builder()
                    .weekday(day)
                    .openTime(open)
                    .closeTime(close)
                    .closed(false)
                    .build());
        }
        return list;
    }

    /**
     * Seeds 8-10 products per approved vendor, with low-stock and near-expiry items and generated images.
     */
    public void seedProductsAndStock(String adminEmail) {
        Category roses = categoryRepository.findBySlug("roses").orElseThrow();
        Category bouquets = categoryRepository.findBySlug("bouquets").orElseThrow();
        Category arrangements = categoryRepository.findBySlug("arrangements").orElseThrow();
        Category occasions = categoryRepository.findBySlug("occasions").orElseThrow();

        // 1. Vendor 1 Catalog
        User vendor1User = userRepository.findByEmailWithRole(VENDOR_1_EMAIL).orElseThrow();
        VendorProfile vendor1 = vendorProfileRepository.findByUserId(vendor1User.getId()).orElseThrow();
        if (productRepository.findByVendorId(vendor1.getId(), PageRequest.of(0, 1)).isEmpty()) {
            // 9 products for Vendor 1
            seedProduct(VENDOR_1_EMAIL, "Red Grand Gala Roses", roses.getId(), "50 Red Grand Gala roses arranged with fresh greens.", new BigDecimal("450.00"), 50, 10, null, new Color(220, 20, 60), Color.YELLOW);
            seedProduct(VENDOR_1_EMAIL, "Blush Pink Spray Roses", roses.getId(), "Delicate bunch of fresh pink spray roses.", new BigDecimal("380.00"), 40, 8, null, new Color(255, 105, 180), Color.WHITE);
            seedProduct(VENDOR_1_EMAIL, "Classic Romance Bouquet", bouquets.getId(), "Romantic pairing of roses and lilies in signature wrap.", new BigDecimal("799.00"), 30, 5, null, new Color(178, 34, 34), Color.ORANGE);
            seedProduct(VENDOR_1_EMAIL, "Sunshine Yellow Lilies", bouquets.getId(), "Bright yellow Asiatic lilies for cheerful celebrations.", new BigDecimal("550.00"), 25, 5, null, new Color(255, 215, 0), Color.WHITE);
            seedProduct(VENDOR_1_EMAIL, "Elegant Glass Vase Orchids", arrangements.getId(), "Purple dendrobium orchids in a cylindrical glass vase.", new BigDecimal("1200.00"), 15, 3, null, new Color(138, 43, 226), Color.YELLOW);
            seedProduct(VENDOR_1_EMAIL, "Pastel Dream Flower Basket", arrangements.getId(), "Hand-woven basket filled with pastel carnations and daisies.", new BigDecimal("850.00"), 20, 4, null, new Color(240, 128, 128), Color.YELLOW);
            seedProduct(VENDOR_1_EMAIL, "Golden Jubilee Bouquet", occasions.getId(), "Opulent golden-toned roses and chrysanthemums for milestones.", new BigDecimal("1499.00"), 12, 3, null, new Color(218, 165, 32), Color.WHITE);
            // Low-stock item (available 4 <= threshold 5)
            seedProduct(VENDOR_1_EMAIL, "Scarlet Miniature Posy", roses.getId(), "Compact petite posy of scarlet red rose buds.", new BigDecimal("250.00"), 4, 5, null, new Color(139, 0, 0), Color.PINK);
            // Near-expiry item (not yet expired, 3 days ahead)
            seedProduct(VENDOR_1_EMAIL, "Fresh Morning Cut Tulips", occasions.getId(), "Farm-fresh imported Dutch tulips.", new BigDecimal("600.00"), 15, 3, LocalDate.now(clock).plusDays(3), new Color(255, 69, 0), Color.YELLOW);
            log.info("Seeded 9 products for {}", VENDOR_1_EMAIL);
        }

        // 2. Vendor 2 Catalog
        User vendor2User = userRepository.findByEmailWithRole(VENDOR_2_EMAIL).orElseThrow();
        VendorProfile vendor2 = vendorProfileRepository.findByUserId(vendor2User.getId()).orElseThrow();
        if (productRepository.findByVendorId(vendor2.getId(), PageRequest.of(0, 1)).isEmpty()) {
            // 9 products for Vendor 2
            seedProduct(VENDOR_2_EMAIL, "White Avalanche Roses", roses.getId(), "Pristine white long-stem roses for pure elegance.", new BigDecimal("480.00"), 45, 10, null, new Color(245, 245, 245), Color.LIGHT_GRAY);
            seedProduct(VENDOR_2_EMAIL, "Crimson Romance Spray", roses.getId(), "Deep red spray roses with eucalyptus leaves.", new BigDecimal("420.00"), 35, 8, null, new Color(165, 42, 42), Color.YELLOW);
            seedProduct(VENDOR_2_EMAIL, "Boho Chic Dried Bouquet", bouquets.getId(), "Long-lasting rustic dried pampas and dried floral bunch.", new BigDecimal("950.00"), 20, 4, null, new Color(205, 133, 63), Color.WHITE);
            seedProduct(VENDOR_2_EMAIL, "Enchanted Garden Mix", bouquets.getId(), "Wildflower-inspired bouquet with hydrangeas and daisies.", new BigDecimal("890.00"), 30, 6, null, new Color(75, 0, 130), Color.PINK);
            seedProduct(VENDOR_2_EMAIL, "Royal Purple Dendrobium", arrangements.getId(), "Striking purple orchid blooms on floral foam.", new BigDecimal("1350.00"), 15, 3, null, new Color(148, 0, 211), Color.WHITE);
            seedProduct(VENDOR_2_EMAIL, "Rustic Wicker Flower Basket", arrangements.getId(), "Traditional wicker basket with marigolds and seasonal blooms.", new BigDecimal("780.00"), 25, 5, null, new Color(255, 140, 0), Color.RED);
            seedProduct(VENDOR_2_EMAIL, "Forever Love Anniversary Floral", occasions.getId(), "Magnificent arrangement of red roses and white orchids.", new BigDecimal("1650.00"), 10, 2, null, new Color(199, 21, 133), Color.YELLOW);
            // Low-stock item (available 3 <= threshold 6)
            seedProduct(VENDOR_2_EMAIL, "Budget Bloom Bunch", bouquets.getId(), "Simple daily decorative flower bunch.", new BigDecimal("320.00"), 3, 6, null, new Color(100, 149, 237), Color.YELLOW);
            // Near-expiry item (2 days ahead)
            seedProduct(VENDOR_2_EMAIL, "Exotic Purple Carnations", bouquets.getId(), "Freshly plucked ruffled purple carnations.", new BigDecimal("520.00"), 18, 4, LocalDate.now(clock).plusDays(2), new Color(186, 85, 211), Color.WHITE);
            log.info("Seeded 9 products for {}", VENDOR_2_EMAIL);
        }

        // 3. Vendor 3 Catalog
        User vendor3User = userRepository.findByEmailWithRole(VENDOR_3_EMAIL).orElseThrow();
        VendorProfile vendor3 = vendorProfileRepository.findByUserId(vendor3User.getId()).orElseThrow();
        if (productRepository.findByVendorId(vendor3.getId(), PageRequest.of(0, 1)).isEmpty()) {
            // 9 products for Vendor 3
            seedProduct(VENDOR_3_EMAIL, "Sunburst Yellow Roses", roses.getId(), "Warm sunny yellow roses wrapped in jute cloth.", new BigDecimal("420.00"), 60, 10, null, new Color(255, 215, 0), Color.ORANGE);
            seedProduct(VENDOR_3_EMAIL, "Peach Blossom Posy", bouquets.getId(), "Soft peach and cream roses tied with silk ribbon.", new BigDecimal("499.00"), 40, 8, null, new Color(255, 218, 185), Color.PINK);
            seedProduct(VENDOR_3_EMAIL, "Pure Peace White Lilies", occasions.getId(), "Graceful white oriental lilies with soothing fragrance.", new BigDecimal("850.00"), 25, 5, null, new Color(250, 250, 250), Color.GREEN);
            seedProduct(VENDOR_3_EMAIL, "Autumn Glow Flower Box", arrangements.getId(), "Wooden keepsake box arranged with orange roses and foliage.", new BigDecimal("1100.00"), 20, 4, null, new Color(255, 69, 0), Color.YELLOW);
            seedProduct(VENDOR_3_EMAIL, "Sweet Valentine Bunch", bouquets.getId(), "Sweet pink roses and baby's breath wrapped in Korean paper.", new BigDecimal("650.00"), 35, 7, null, new Color(255, 182, 193), Color.WHITE);
            seedProduct(VENDOR_3_EMAIL, "Serene Zen Floral Bowl", arrangements.getId(), "Minimalist Ikebana-inspired ceramic bowl flower arrangement.", new BigDecimal("980.00"), 15, 3, null, new Color(46, 139, 87), Color.WHITE);
            seedProduct(VENDOR_3_EMAIL, "Vibrant Gerbera Burst", occasions.getId(), "Multi-colored gerbera daisies radiating positivity.", new BigDecimal("399.00"), 50, 10, null, new Color(255, 20, 147), Color.YELLOW);
            // Low-stock item (available 2 <= threshold 5)
            seedProduct(VENDOR_3_EMAIL, "Petite Desktop Daisy", arrangements.getId(), "Mini pot with cheerful white and yellow daisies.", new BigDecimal("180.00"), 2, 5, null, new Color(245, 255, 250), Color.YELLOW);
            // Near-expiry item (4 days ahead)
            seedProduct(VENDOR_3_EMAIL, "Fragrant Night Jasmine Bunch", occasions.getId(), "Aromatic fresh mogra and night jasmine strands.", new BigDecimal("299.00"), 10, 3, LocalDate.now(clock).plusDays(4), new Color(240, 255, 240), Color.YELLOW);
            log.info("Seeded 9 products for {}", VENDOR_3_EMAIL);
        }
    }

    private void seedProduct(String vendorEmail, String name, Long categoryId, String description,
                              BigDecimal price, int stock, int lowStockThreshold, LocalDate expiryDate,
                              Color petalColor, Color centerColor) {
        ProductResponse product = productService.create(vendorEmail, ProductRequest.builder()
                .name(name)
                .categoryId(categoryId)
                .description(description)
                .basePrice(price)
                .status(ProductStatus.ACTIVE)
                .build());

        // Stock in
        inventoryService.stockIn(vendorEmail, product.getId(), new StockInRequest(stock, "Initial seed inventory"));

        // Low stock threshold
        if (lowStockThreshold > 0) {
            inventoryService.updateLowStockThreshold(vendorEmail, product.getId(),
                    new LowStockThresholdRequest(lowStockThreshold));
        }

        // Expiry date
        if (expiryDate != null) {
            inventoryService.updateExpiryDate(vendorEmail, product.getId(),
                    new ExpiryDateRequest(expiryDate));
        }

        // Attach local generated flower image
        productImageService.upload(vendorEmail, product.getId(),
                DemoImageGenerator.generateFlowerImage(name, petalColor, centerColor), true);
    }

    /**
     * Seeds 3 demo customers with saved addresses.
     */
    public void seedCustomersAndAddresses(String demoPassword) {
        List<ServiceLocation> locations = serviceLocationRepository.findAll();
        ServiceLocation koramangala = locations.stream().filter(l -> "Koramangala".equalsIgnoreCase(l.getArea())).findFirst().orElse(locations.get(0));
        ServiceLocation indiranagar = locations.stream().filter(l -> "Indiranagar".equalsIgnoreCase(l.getArea())).findFirst().orElse(locations.get(0));
        ServiceLocation hsrLayout = locations.stream().filter(l -> "HSR Layout".equalsIgnoreCase(l.getArea())).findFirst().orElse(locations.get(0));

        // Customer 1: Alice Sharma (Koramangala + HSR Layout)
        if (!userRepository.existsByEmail(CUSTOMER_1_EMAIL)) {
            authService.register(new RegisterRequest(CUSTOMER_1_EMAIL, demoPassword, "Alice Sharma", "+919876543210"));
            addressService.create(CUSTOMER_1_EMAIL, AddressRequest.builder()
                    .label("Home")
                    .line1("123, 4th Cross, 5th Block")
                    .line2("Near Jyoti Nivas College")
                    .serviceLocationId(koramangala.getId())
                    .defaultAddress(true)
                    .build());
            addressService.create(CUSTOMER_1_EMAIL, AddressRequest.builder()
                    .label("Office")
                    .line1("Flat 402, Sunshine Apartments")
                    .line2("Sector 2, HSR Layout")
                    .serviceLocationId(hsrLayout.getId())
                    .defaultAddress(false)
                    .build());
            log.info("Registered demo customer: {}", CUSTOMER_1_EMAIL);
        }

        // Customer 2: Bob Patel (Indiranagar + Koramangala)
        if (!userRepository.existsByEmail(CUSTOMER_2_EMAIL)) {
            authService.register(new RegisterRequest(CUSTOMER_2_EMAIL, demoPassword, "Bob Patel", "+919876543211"));
            addressService.create(CUSTOMER_2_EMAIL, AddressRequest.builder()
                    .label("Home")
                    .line1("77, 100 Feet Road")
                    .line2("Indiranagar Stage 1")
                    .serviceLocationId(indiranagar.getId())
                    .defaultAddress(true)
                    .build());
            addressService.create(CUSTOMER_2_EMAIL, AddressRequest.builder()
                    .label("Office")
                    .line1("Tower B, Embassy Golf Links")
                    .line2("Koramangala Intermediate Ring Road")
                    .serviceLocationId(koramangala.getId())
                    .defaultAddress(false)
                    .build());
            log.info("Registered demo customer: {}", CUSTOMER_2_EMAIL);
        }

        // Customer 3: Charlie Rao (HSR Layout + Indiranagar)
        if (!userRepository.existsByEmail(CUSTOMER_3_EMAIL)) {
            authService.register(new RegisterRequest(CUSTOMER_3_EMAIL, demoPassword, "Charlie Rao", "+919876543212"));
            addressService.create(CUSTOMER_3_EMAIL, AddressRequest.builder()
                    .label("Home")
                    .line1("12, 17th Main, Sector 3")
                    .line2("Near BDA Complex")
                    .serviceLocationId(hsrLayout.getId())
                    .defaultAddress(true)
                    .build());
            addressService.create(CUSTOMER_3_EMAIL, AddressRequest.builder()
                    .label("Studio")
                    .line1("55, 1st Cross, Indiranagar")
                    .line2("Near Metro Station")
                    .serviceLocationId(indiranagar.getId())
                    .defaultAddress(false)
                    .build());
            log.info("Registered demo customer: {}", CUSTOMER_3_EMAIL);
        }
    }

    /**
     * Seeds representative order history through checkout, payment simulator, order state machine,
     * cancellations, disputes, and reviews.
     */
    public void seedOrderHistoryAndReviews(String adminEmail) {
        User customer1 = userRepository.findByEmailWithRole(CUSTOMER_1_EMAIL).orElseThrow();
        User customer2 = userRepository.findByEmailWithRole(CUSTOMER_2_EMAIL).orElseThrow();
        User customer3 = userRepository.findByEmailWithRole(CUSTOMER_3_EMAIL).orElseThrow();

        // Idempotency check: if customer 1 already has orders, assume order history has been seeded.
        if (!orderRepository.findByUserIdOrderByCreatedAtDesc(customer1.getId(), PageRequest.of(0, 1)).isEmpty()) {
            log.info("Demo orders already exist; skipping order history seeding.");
            return;
        }

        User vendor1User = userRepository.findByEmailWithRole(VENDOR_1_EMAIL).orElseThrow();
        VendorProfile vendor1 = vendorProfileRepository.findByUserId(vendor1User.getId()).orElseThrow();

        User vendor2User = userRepository.findByEmailWithRole(VENDOR_2_EMAIL).orElseThrow();
        VendorProfile vendor2 = vendorProfileRepository.findByUserId(vendor2User.getId()).orElseThrow();

        User vendor3User = userRepository.findByEmailWithRole(VENDOR_3_EMAIL).orElseThrow();
        VendorProfile vendor3 = vendorProfileRepository.findByUserId(vendor3User.getId()).orElseThrow();

        List<Product> vendor1Products = productRepository.findByVendorId(vendor1.getId(), PageRequest.of(0, 10)).getContent();
        List<Product> vendor2Products = productRepository.findByVendorId(vendor2.getId(), PageRequest.of(0, 10)).getContent();
        List<Product> vendor3Products = productRepository.findByVendorId(vendor3.getId(), PageRequest.of(0, 10)).getContent();

        Address customer1Address = addressRepository.findByUserId(customer1.getId(), PageRequest.of(0, 1)).getContent().get(0);
        Address customer2Address = addressRepository.findByUserId(customer2.getId(), PageRequest.of(0, 1)).getContent().get(0);
        Address customer3Address = addressRepository.findByUserId(customer3.getId(), PageRequest.of(0, 1)).getContent().get(0);

        // -------------------------------------------------------------
        // Order 1: Delivered COD order with 5-star review and vendor reply
        // -------------------------------------------------------------
        Long order1Id = placeOrder(CUSTOMER_1_EMAIL, customer1Address.getId(), vendor1.getId(),
                vendor1Products.get(0).getId(), 1, PaymentMethod.COD, null);
        transitionOrderToDelivered(order1Id, vendor1User.getId());
        ReviewDetailResponse review1 = reviewService.submitReview(CUSTOMER_1_EMAIL, order1Id,
                new ReviewRequest(5, "Absolutely fresh and stunning roses! Highly recommended."));
        reviewService.replyToReview(VENDOR_1_EMAIL, review1.getId(),
                "Thank you so much! We are thrilled you loved them.");
        log.info("Seeded Order 1: Delivered COD with review (id={})", order1Id);

        // -------------------------------------------------------------
        // Order 2: Delivered simulated-online order with 4-star review
        // -------------------------------------------------------------
        OrderPlacement placement2 = placeOnlineOrder(CUSTOMER_2_EMAIL, customer2Address.getId(), vendor2.getId(),
                vendor2Products.get(0).getId(), 1);
        Long order2Id = placement2.orderId();
        // Online simulator webhook: confirm payment
        paymentService.processProviderEvent(placement2.paymentGroupId(), UUID.randomUUID().toString(),
                "payment.succeeded", "success", placement2.total());
        transitionOrderToDelivered(order2Id, vendor2User.getId());
        reviewService.submitReview(CUSTOMER_2_EMAIL, order2Id,
                new ReviewRequest(4, "Great packaging and on-time delivery."));
        log.info("Seeded Order 2: Delivered Online with review (id={})", order2Id);

        // -------------------------------------------------------------
        // Order 3: Recent PLACED order awaiting vendor action
        // -------------------------------------------------------------
        Long order3Id = placeOrder(CUSTOMER_3_EMAIL, customer3Address.getId(), vendor1.getId(),
                vendor1Products.get(2).getId(), 1, PaymentMethod.COD, null);
        log.info("Seeded Order 3: Recent PLACED order awaiting action (id={})", order3Id);

        // -------------------------------------------------------------
        // Order 4: In-progress order (CONFIRMED -> PREPARING)
        // -------------------------------------------------------------
        Long order4Id = placeOrder(CUSTOMER_1_EMAIL, customer1Address.getId(), vendor2.getId(),
                vendor2Products.get(3).getId(), 1, PaymentMethod.COD, null);
        orderStateMachine.transition(order4Id, OrderStatus.CONFIRMED, ActorType.VENDOR, vendor2User.getId(), null);
        orderStateMachine.transition(order4Id, OrderStatus.PREPARING, ActorType.VENDOR, vendor2User.getId(), null);
        log.info("Seeded Order 4: In-progress order in PREPARING (id={})", order4Id);

        // -------------------------------------------------------------
        // Order 5: Customer-cancelled order
        // -------------------------------------------------------------
        Long order5Id = placeOrder(CUSTOMER_2_EMAIL, customer2Address.getId(), vendor1.getId(),
                vendor1Products.get(3).getId(), 1, PaymentMethod.COD, null);
        cancellationService.cancel(CUSTOMER_2_EMAIL, ActorType.CUSTOMER, order5Id, "Change in delivery schedule");
        log.info("Seeded Order 5: Customer-cancelled order (id={})", order5Id);

        // -------------------------------------------------------------
        // Order 6: Vendor-rejected order
        // -------------------------------------------------------------
        Long order6Id = placeOrder(CUSTOMER_3_EMAIL, customer3Address.getId(), vendor2.getId(),
                vendor2Products.get(5).getId(), 1, PaymentMethod.COD, null);
        cancellationService.cancel(VENDOR_2_EMAIL, ActorType.VENDOR, order6Id,
                "Selected flower variety unavailable for this date");
        log.info("Seeded Order 6: Vendor-rejected order (id={})", order6Id);

        // -------------------------------------------------------------
        // Order 7: Online order awaiting refund
        // -------------------------------------------------------------
        OrderPlacement placement7 = placeOnlineOrder(CUSTOMER_1_EMAIL, customer1Address.getId(), vendor3.getId(),
                vendor3Products.get(0).getId(), 1);
        Long order7Id = placement7.orderId();
        paymentService.processProviderEvent(placement7.paymentGroupId(), UUID.randomUUID().toString(),
                "payment.succeeded", "success", placement7.total());
        cancellationService.cancel(CUSTOMER_1_EMAIL, ActorType.CUSTOMER, order7Id, "Ordered by mistake");
        log.info("Seeded Order 7: Online order awaiting refund (id={})", order7Id);

        // -------------------------------------------------------------
        // Order 8: Delivered order with a resolved dispute (Vendor 3 has no review)
        // -------------------------------------------------------------
        Long order8Id = placeOrder(CUSTOMER_3_EMAIL, customer3Address.getId(), vendor3.getId(),
                vendor3Products.get(1).getId(), 1, PaymentMethod.COD, null);
        transitionOrderToDelivered(order8Id, vendor3User.getId());
        // Customer raises dispute
        DisputeDetailResponse disputeResp = disputeService.raiseDispute(CUSTOMER_3_EMAIL, order8Id,
                new RaiseDisputeRequest("ITEM_DAMAGED", "Flowers arrived wilted during transit"));
        // Vendor responds
        disputeService.respondToDispute(VENDOR_3_EMAIL, disputeResp.getId(),
                new VendorDisputeResponseRequest("Apologies for transit delay; offering partial refund"));
        // Admin resolves dispute with 50% partial refund
        Order order8 = orderRepository.findById(order8Id).orElseThrow();
        BigDecimal partialRefund = order8.getTotal().multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
        disputeService.resolveDispute(adminEmail, disputeResp.getId(),
                new ResolveDisputeRequest(DisputeStatus.RESOLVED_PARTIAL, "Approved 50% partial refund after reviewing photos", partialRefund));
        log.info("Seeded Order 8: Delivered order with resolved dispute (id={})", order8Id);
    }

    private void transitionOrderToDelivered(Long orderId, Long vendorUserId) {
        orderStateMachine.transition(orderId, OrderStatus.CONFIRMED, ActorType.VENDOR, vendorUserId, null);
        orderStateMachine.transition(orderId, OrderStatus.PREPARING, ActorType.VENDOR, vendorUserId, null);
        orderStateMachine.transition(orderId, OrderStatus.OUT_FOR_DELIVERY, ActorType.VENDOR, vendorUserId, null);
        orderStateMachine.transition(orderId, OrderStatus.DELIVERED, ActorType.VENDOR, vendorUserId, null);
    }

    private Long placeOrder(String customerEmail, Long addressId, Long vendorId, Long productId,
                            int quantity, PaymentMethod paymentMethod, String slotStartOverride) {
        cartService.addItem(customerEmail, AddCartItemRequest.builder()
                .productId(productId)
                .quantity(quantity)
                .note("Demo floral order")
                .build());

        String slotStart = slotStartOverride != null ? slotStartOverride : selectSlotStart(vendorId, addressId, customerEmail);

        CheckoutQuoteResponse quote = checkoutService.generateQuote(customerEmail, CheckoutQuoteRequest.builder()
                .addressId(addressId)
                .items(List.of(CheckoutQuoteRequest.QuoteItem.builder()
                        .vendorId(vendorId)
                        .slotStart(slotStart)
                        .build()))
                .build());

        UUID clientOrderId = UUID.randomUUID();
        CheckoutResponse checkout = checkoutService.checkout(customerEmail, CheckoutRequest.builder()
                .addressId(addressId)
                .clientOrderId(clientOrderId)
                .quoteHash(quote.getQuoteHash())
                .confirmPriceChanges(true)
                .paymentMethod(paymentMethod)
                .items(List.of(CheckoutRequest.CheckoutItem.builder()
                        .vendorId(vendorId)
                        .slotStart(slotStart)
                        .build()))
                .build());

        return checkout.getOrders().get(0).getOrderId();
    }

    private record OrderPlacement(Long orderId, String paymentGroupId, BigDecimal total) {}

    private OrderPlacement placeOnlineOrder(String customerEmail, Long addressId, Long vendorId,
                                            Long productId, int quantity) {
        cartService.addItem(customerEmail, AddCartItemRequest.builder()
                .productId(productId)
                .quantity(quantity)
                .note("Demo online order")
                .build());

        String slotStart = selectSlotStart(vendorId, addressId, customerEmail);

        CheckoutQuoteResponse quote = checkoutService.generateQuote(customerEmail, CheckoutQuoteRequest.builder()
                .addressId(addressId)
                .items(List.of(CheckoutQuoteRequest.QuoteItem.builder()
                        .vendorId(vendorId)
                        .slotStart(slotStart)
                        .build()))
                .build());

        UUID clientOrderId = UUID.randomUUID();
        CheckoutResponse checkout = checkoutService.checkout(customerEmail, CheckoutRequest.builder()
                .addressId(addressId)
                .clientOrderId(clientOrderId)
                .quoteHash(quote.getQuoteHash())
                .confirmPriceChanges(true)
                .paymentMethod(PaymentMethod.ONLINE)
                .items(List.of(CheckoutRequest.CheckoutItem.builder()
                        .vendorId(vendorId)
                        .slotStart(slotStart)
                        .build()))
                .build());

        return new OrderPlacement(checkout.getOrders().get(0).getOrderId(),
                checkout.getPaymentGroupId(), checkout.getTotal());
    }

    private String selectSlotStart(Long vendorId, Long addressId, String customerEmail) {
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        List<SlotResponse> slots = slotService.getAvailableSlots(vendorId, addressId, tomorrow, customerEmail);
        SlotResponse availableSlot = slots.stream()
                .filter(SlotResponse::isAvailable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No available slot found for vendor " + vendorId));
        return availableSlot.getSlotStart().toString() + "Z";
    }

    /**
     * Seeds POS walk-in sales preserving commission-free behavior.
     */
    public void seedPosSales() {
        User vendor1User = userRepository.findByEmailWithRole(VENDOR_1_EMAIL).orElseThrow();
        VendorProfile vendor1 = vendorProfileRepository.findByUserId(vendor1User.getId()).orElseThrow();
        List<Product> vendor1Products = productRepository.findByVendorId(vendor1.getId(), PageRequest.of(0, 5)).getContent();

        if (posTransactionRepository.findByVendorIdAndIdempotencyKey(vendor1.getId(), POS_IDEMPOTENCY_KEY_1).isEmpty()) {
            posService.recordSale(VENDOR_1_EMAIL, POS_IDEMPOTENCY_KEY_1, PosSaleRequest.builder()
                    .paymentMethod(PosPaymentMethod.CASH)
                    .items(List.of(new PosSaleLineRequest(vendor1Products.get(0).getId(), 2)))
                    .build());
            log.info("Seeded POS sale for {}", VENDOR_1_EMAIL);
        }

        User vendor2User = userRepository.findByEmailWithRole(VENDOR_2_EMAIL).orElseThrow();
        VendorProfile vendor2 = vendorProfileRepository.findByUserId(vendor2User.getId()).orElseThrow();
        List<Product> vendor2Products = productRepository.findByVendorId(vendor2.getId(), PageRequest.of(0, 5)).getContent();

        if (posTransactionRepository.findByVendorIdAndIdempotencyKey(vendor2.getId(), POS_IDEMPOTENCY_KEY_2).isEmpty()) {
            posService.recordSale(VENDOR_2_EMAIL, POS_IDEMPOTENCY_KEY_2, PosSaleRequest.builder()
                    .paymentMethod(PosPaymentMethod.UPI)
                    .items(List.of(new PosSaleLineRequest(vendor2Products.get(0).getId(), 1)))
                    .build());
            log.info("Seeded POS sale for {}", VENDOR_2_EMAIL);
        }
    }

    /**
     * Seeds weekly settlements in each required state (SETTLED and PENDING).
     */
    public void seedSettlements(User adminUser) {
        LocalDate periodStart = LocalDate.now(clock).minusDays(7);
        LocalDate periodEnd = LocalDate.now(clock);

        User vendor1User = userRepository.findByEmailWithRole(VENDOR_1_EMAIL).orElseThrow();
        VendorProfile vendor1 = vendorProfileRepository.findByUserId(vendor1User.getId()).orElseThrow();

        // Vendor 1: create settlement and finalize to SETTLED
        if (settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendor1.getId(), periodStart, periodEnd).isEmpty()) {
            Settlement s1 = settlementService.runSettlement(vendor1.getId(), periodStart, periodEnd,
                    AuditActorType.USER, adminUser);
            settlementService.finalizeSettlement(s1.getId(), adminUser.getEmail(),
                    "Weekly settlement payout executed via NEFT transfer ref # FC-SETTLE-001");
            log.info("Seeded finalized SETTLED settlement for {} (id={})", VENDOR_1_EMAIL, s1.getId());
        }

        User vendor2User = userRepository.findByEmailWithRole(VENDOR_2_EMAIL).orElseThrow();
        VendorProfile vendor2 = vendorProfileRepository.findByUserId(vendor2User.getId()).orElseThrow();

        // Vendor 2: create settlement and leave in PENDING
        if (settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(vendor2.getId(), periodStart, periodEnd).isEmpty()) {
            Settlement s2 = settlementService.runSettlement(vendor2.getId(), periodStart, periodEnd,
                    AuditActorType.USER, adminUser);
            log.info("Seeded PENDING settlement for {} (id={})", VENDOR_2_EMAIL, s2.getId());
        }
    }
}
