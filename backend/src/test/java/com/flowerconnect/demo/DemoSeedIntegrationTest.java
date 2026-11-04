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

import com.flowerconnect.billing.domain.Commission;
import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.repository.CommissionRepository;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.billing.repository.SettlementRepository;
import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.customer.domain.Address;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.dispute.domain.Dispute;
import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.dispute.repository.DisputeRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.Order.PaymentStatus;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.pos.repository.PosTransactionRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.review.domain.Review;
import com.flowerconnect.review.repository.ReviewRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {
        "app.seed.enabled=true",
        "app.seed.demo-password=DemoPass#2026"
})
class DemoSeedIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin-demo-test@flowerconnect.test";
    private static final String DEMO_PASSWORD = "DemoPass#2026";

    @Autowired
    private DemoSeedService demoSeedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VendorProfileRepository vendorProfileRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private PosTransactionRepository posTransactionRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementEntryRepository settlementEntryRepository;

    @Autowired
    private CommissionRepository commissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @BeforeEach
    void ensureAdminUserExists() {
        if (!userRepository.existsByEmail(ADMIN_EMAIL)) {
            Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
            userRepository.save(User.builder()
                    .email(ADMIN_EMAIL)
                    .passwordHash(passwordEncoder.encode("AdminPass#2026"))
                    .fullName("Admin User")
                    .role(adminRole)
                    .status(User.Status.ACTIVE)
                    .build());
        }
    }

    @Test
    @DisplayName("End-to-end seed execution, dataset coverage, idempotency, and reconciliation")
    void testEndToEndSeedDatasetCoverageIdempotencyAndReconciliation() {
        // --- 1. FIRST RUN: Seed all demo data ---
        demoSeedService.seedAll(DEMO_PASSWORD, ADMIN_EMAIL);

        // --- 2. VERIFY DATASET COVERAGE ---

        // A. Vendors: 3 approved, 1 pending, 1 suspended (total 5)
        User v1User = userRepository.findByEmailWithRole(DemoSeedService.VENDOR_1_EMAIL).orElseThrow();
        User v2User = userRepository.findByEmailWithRole(DemoSeedService.VENDOR_2_EMAIL).orElseThrow();
        User v3User = userRepository.findByEmailWithRole(DemoSeedService.VENDOR_3_EMAIL).orElseThrow();
        User v4User = userRepository.findByEmailWithRole(DemoSeedService.VENDOR_4_EMAIL).orElseThrow();
        User v5User = userRepository.findByEmailWithRole(DemoSeedService.VENDOR_5_EMAIL).orElseThrow();

        VendorProfile v1 = vendorProfileRepository.findByUserId(v1User.getId()).orElseThrow();
        VendorProfile v2 = vendorProfileRepository.findByUserId(v2User.getId()).orElseThrow();
        VendorProfile v3 = vendorProfileRepository.findByUserId(v3User.getId()).orElseThrow();
        VendorProfile v4 = vendorProfileRepository.findByUserId(v4User.getId()).orElseThrow();
        VendorProfile v5 = vendorProfileRepository.findByUserId(v5User.getId()).orElseThrow();

        assertThat(v1.getStatus()).isEqualTo(VendorProfile.Status.APPROVED);
        assertThat(v2.getStatus()).isEqualTo(VendorProfile.Status.APPROVED);
        assertThat(v3.getStatus()).isEqualTo(VendorProfile.Status.APPROVED);
        assertThat(v4.getStatus()).isEqualTo(VendorProfile.Status.PENDING_APPROVAL);
        assertThat(v5.getStatus()).isEqualTo(VendorProfile.Status.SUSPENDED);

        // Differing radii and fees
        assertThat(v1.getDeliveryRadiusKm()).isNotEqualByComparingTo(v2.getDeliveryRadiusKm());
        assertThat(v1.getBaseDeliveryFee()).isNotEqualByComparingTo(v2.getBaseDeliveryFee());

        // B. Categories: children seeded under roots
        List<Category> allCategories = categoryRepository.findAll();
        long childCategories = allCategories.stream().filter(c -> c.getParent() != null).count();
        assertThat(childCategories).isGreaterThanOrEqualTo(8);

        // C. Products and stock: 8-10 per approved vendor
        List<Product> v1Products = productRepository.findByVendorId(v1.getId(), PageRequest.of(0, 20)).getContent();
        List<Product> v2Products = productRepository.findByVendorId(v2.getId(), PageRequest.of(0, 20)).getContent();
        List<Product> v3Products = productRepository.findByVendorId(v3.getId(), PageRequest.of(0, 20)).getContent();

        assertThat(v1Products).hasSize(9);
        assertThat(v2Products).hasSize(9);
        assertThat(v3Products).hasSize(9);

        // Low stock items and near expiry items
        boolean v1HasLowStock = v1Products.stream().anyMatch(p -> {
            Inventory inv = inventoryRepository.findByProductId(p.getId()).orElseThrow();
            return inv.getAvailable() <= inv.getLowStockThreshold();
        });
        assertThat(v1HasLowStock).isTrue();

        boolean v1HasNearExpiry = v1Products.stream().anyMatch(p -> {
            Inventory inv = inventoryRepository.findByProductId(p.getId()).orElseThrow();
            return inv.getExpiryDate() != null && inv.getExpiryDate().isAfter(LocalDate.now(clock));
        });
        assertThat(v1HasNearExpiry).isTrue();

        // Product images attached
        for (Product p : v1Products) {
            assertThat(productImageRepository.findByProductId(p.getId())).isNotEmpty();
        }

        // D. Customers and addresses
        User c1 = userRepository.findByEmailWithRole(DemoSeedService.CUSTOMER_1_EMAIL).orElseThrow();
        User c2 = userRepository.findByEmailWithRole(DemoSeedService.CUSTOMER_2_EMAIL).orElseThrow();
        User c3 = userRepository.findByEmailWithRole(DemoSeedService.CUSTOMER_3_EMAIL).orElseThrow();

        assertThat(addressRepository.findByUserId(c1.getId(), PageRequest.of(0, 5)).getContent()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(addressRepository.findByUserId(c2.getId(), PageRequest.of(0, 5)).getContent()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(addressRepository.findByUserId(c3.getId(), PageRequest.of(0, 5)).getContent()).hasSizeGreaterThanOrEqualTo(1);

        // E. Order History (8 orders covering required states)
        List<Order> allOrders = orderRepository.findAll();
        assertThat(allOrders).hasSizeGreaterThanOrEqualTo(8);

        // 1. Delivered COD
        boolean hasDeliveredCod = allOrders.stream().anyMatch(o ->
                o.getStatus() == OrderStatus.DELIVERED &&
                        o.getPaymentMethod() == Order.PaymentMethod.COD &&
                        o.getPaymentStatus() == PaymentStatus.COLLECTED);
        assertThat(hasDeliveredCod).isTrue();

        // 2. Delivered Online
        boolean hasDeliveredOnline = allOrders.stream().anyMatch(o ->
                o.getStatus() == OrderStatus.DELIVERED &&
                        o.getPaymentMethod() == Order.PaymentMethod.ONLINE &&
                        o.getPaymentStatus() == PaymentStatus.PAID);
        assertThat(hasDeliveredOnline).isTrue();

        // 3. Recent PLACED awaiting vendor action
        boolean hasRecentPlaced = allOrders.stream().anyMatch(o ->
                o.getStatus() == OrderStatus.PLACED);
        assertThat(hasRecentPlaced).isTrue();

        // Check age is newer than 60 minutes auto-reject threshold
        Order placedOrder = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PLACED).findFirst().orElseThrow();
        Duration age = Duration.between(placedOrder.getCreatedAt(), LocalDateTime.now(clock));
        assertThat(age.toMinutes()).isLessThan(60);

        // 4. In-progress (PREPARING)
        boolean hasInProgress = allOrders.stream().anyMatch(o ->
                o.getStatus() == OrderStatus.PREPARING);
        assertThat(hasInProgress).isTrue();

        // 5. Customer-cancelled
        boolean hasCancelled = allOrders.stream().anyMatch(o ->
                o.getStatus() == OrderStatus.CANCELLED && o.getPaymentStatus() != PaymentStatus.REFUND_PENDING);
        assertThat(hasCancelled).isTrue();

        // 6. Vendor-rejected
        boolean hasRejected = allOrders.stream().anyMatch(o ->
                o.getStatus() == OrderStatus.REJECTED);
        assertThat(hasRejected).isTrue();

        // 7. Online awaiting refund
        boolean hasRefundPending = allOrders.stream().anyMatch(o ->
                o.getStatus() == OrderStatus.CANCELLED && o.getPaymentStatus() == PaymentStatus.REFUND_PENDING);
        assertThat(hasRefundPending).isTrue();

        // 8. Delivered with resolved dispute
        boolean hasResolvedDispute = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .anyMatch(o -> disputeRepository.findByOrderId(o.getId())
                        .map(d -> d.getStatus() == DisputeStatus.RESOLVED_PARTIAL)
                        .orElse(false));
        assertThat(hasResolvedDispute).isTrue();

        // F. Reviews: vendor 1 has ratings & reply; vendor 3 has no reviews
        VendorProfile reloadedV1 = vendorProfileRepository.findById(v1.getId()).orElseThrow();
        VendorProfile reloadedV3 = vendorProfileRepository.findById(v3.getId()).orElseThrow();
        assertThat(reloadedV1.getAvgRating()).isNotNull();
        assertThat(reloadedV1.getReviewCount()).isGreaterThan(0);
        assertThat(reloadedV3.getReviewCount()).isEqualTo(0);

        // G. POS sales recorded
        assertThat(posTransactionRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);

        // H. Settlements (SETTLED and PENDING)
        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements.stream().anyMatch(s -> s.getStatus() == SettlementStatus.SETTLED)).isTrue();
        assertThat(settlements.stream().anyMatch(s -> s.getStatus() == SettlementStatus.PENDING)).isTrue();

        // --- 3. RECONCILIATION CHECKS ---

        // Stock reconciliation: for every inventory row, available + reserved = quantity
        for (Inventory inv : inventoryRepository.findAll()) {
            assertThat(inv.getAvailable() + inv.getReservedQuantity()).isEqualTo(inv.getQuantity());
        }

        // Billing reconciliation: delivered orders have commission and ledger entries
        for (Order o : allOrders) {
            if (o.getStatus() == OrderStatus.DELIVERED) {
                Commission comm = commissionRepository.findByOrderId(o.getId()).orElseThrow();
                assertThat(comm.getCommissionAmount()).isGreaterThan(BigDecimal.ZERO);

                SettlementEntry entry = settlementEntryRepository.findByOrderId(o.getId()).orElseThrow();
                if (o.getPaymentMethod() == Order.PaymentMethod.COD) {
                    // COD: -(C + P)
                    BigDecimal expected = comm.getCommissionAmount().add(o.getPlatformFee()).negate().setScale(2, RoundingMode.HALF_UP);
                    assertThat(entry.getAmount()).isEqualByComparingTo(expected);
                } else if (o.getPaymentMethod() == Order.PaymentMethod.ONLINE) {
                    // Online: +(S - C + D)
                    BigDecimal expected = o.getSubtotal().subtract(comm.getCommissionAmount()).add(o.getDeliveryFee()).setScale(2, RoundingMode.HALF_UP);
                    assertThat(entry.getAmount()).isEqualByComparingTo(expected);
                }
            }
        }

        // Settlement reconciliation: settlement amounts match sum of attached entries
        for (Settlement s : settlements) {
            List<SettlementEntry> entries = settlementEntryRepository.findBySettlementIdOrderByCreatedAtAsc(s.getId());
            BigDecimal sumCredits = entries.stream()
                    .map(SettlementEntry::getAmount)
                    .filter(a -> a.compareTo(BigDecimal.ZERO) > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sumDues = entries.stream()
                    .map(SettlementEntry::getAmount)
                    .filter(a -> a.compareTo(BigDecimal.ZERO) < 0)
                    .map(BigDecimal::abs)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(s.getTotalCredits()).isEqualByComparingTo(sumCredits);
            assertThat(s.getTotalDues()).isEqualByComparingTo(sumDues);
            assertThat(s.getNetAmount()).isEqualByComparingTo(sumCredits.subtract(sumDues));
        }

        // --- 4. IDEMPOTENCY: Re-run seedAll and assert zero row drift ---
        long countUsersBefore = userRepository.count();
        long countVendorsBefore = vendorProfileRepository.count();
        long countCategoriesBefore = categoryRepository.count();
        long countProductsBefore = productRepository.count();
        long countInventoryBefore = inventoryRepository.count();
        long countAddressesBefore = addressRepository.count();
        long countOrdersBefore = orderRepository.count();
        long countDisputesBefore = disputeRepository.count();
        long countReviewsBefore = reviewRepository.count();
        long countPosBefore = posTransactionRepository.count();
        long countSettlementsBefore = settlementRepository.count();
        long countSettlementEntriesBefore = settlementEntryRepository.count();
        long countCommissionsBefore = commissionRepository.count();

        // Second run
        demoSeedService.seedAll(DEMO_PASSWORD, ADMIN_EMAIL);

        assertThat(userRepository.count()).isEqualTo(countUsersBefore);
        assertThat(vendorProfileRepository.count()).isEqualTo(countVendorsBefore);
        assertThat(categoryRepository.count()).isEqualTo(countCategoriesBefore);
        assertThat(productRepository.count()).isEqualTo(countProductsBefore);
        assertThat(inventoryRepository.count()).isEqualTo(countInventoryBefore);
        assertThat(addressRepository.count()).isEqualTo(countAddressesBefore);
        assertThat(orderRepository.count()).isEqualTo(countOrdersBefore);
        assertThat(disputeRepository.count()).isEqualTo(countDisputesBefore);
        assertThat(reviewRepository.count()).isEqualTo(countReviewsBefore);
        assertThat(posTransactionRepository.count()).isEqualTo(countPosBefore);
        assertThat(settlementRepository.count()).isEqualTo(countSettlementsBefore);
        assertThat(settlementEntryRepository.count()).isEqualTo(countSettlementEntriesBefore);
        assertThat(commissionRepository.count()).isEqualTo(countCommissionsBefore);
    }
}

