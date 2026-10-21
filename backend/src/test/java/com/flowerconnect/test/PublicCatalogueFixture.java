package com.flowerconnect.test;

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
import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

/**
 * Fixture helper for the public discovery/catalogue integration tests
 * (plan tasks 4.4, 4.5 and 4.9).
 *
 * <p>Builds vendors, categories, products and inventory directly through the
 * repositories so each test can set up exactly the visibility state it needs
 * (approved vs. pending, accepting vs. closed, active vs. draft, in stock vs.
 * out of stock) without going through a gated HTTP endpoint.
 */
public class PublicCatalogueFixture {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final ServiceLocationRepository serviceLocationRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    private final Random random = new Random();

    public PublicCatalogueFixture(UserRepository userRepository,
                                  RoleRepository roleRepository,
                                  VendorProfileRepository vendorProfileRepository,
                                  ServiceLocationRepository serviceLocationRepository,
                                  CategoryRepository categoryRepository,
                                  ProductRepository productRepository,
                                  InventoryRepository inventoryRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.serviceLocationRepository = serviceLocationRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /** Creates a FLORIST user with a unique email and phone. */
    public User createFloristUser() {
        Role role = roleRepository.findByName("FLORIST")
                .orElseThrow(() -> new IllegalStateException("FLORIST role missing"));
        User user = User.builder()
                .email("florist-" + UUID.randomUUID() + "@example.com")
                .passwordHash("dummy")
                .fullName("Test Florist")
                .phone(String.format("%010d", random.nextInt(1_000_000_000)))
                .status(User.Status.ACTIVE)
                .build();
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    /**
     * Creates an approved vendor profile at the given service location, with the
     * given coordinates and delivery radius.
     */
    public VendorProfile createVendor(User user,
                                      ServiceLocation location,
                                      BigDecimal latitude,
                                      BigDecimal longitude,
                                      BigDecimal deliveryRadiusKm,
                                      VendorProfile.Status status,
                                      boolean acceptingOrders) {
        VendorProfile profile = VendorProfile.builder()
                .user(user)
                .businessName("Florist " + UUID.randomUUID())
                .description("Test florist")
                .addressLine1("1 Test Street")
                .addressLine2(null)
                .serviceLocation(location)
                .latitude(latitude)
                .longitude(longitude)
                .deliveryRadiusKm(deliveryRadiusKm)
                .status(status)
                .commissionRate(null)
                .avgRating(new BigDecimal("4.50"))
                .reviewCount(7)
                .minOrderAmount(new BigDecimal("100.00"))
                .baseDeliveryFee(new BigDecimal("10.00"))
                .perKmFee(new BigDecimal("2.00"))
                .freeDeliveryAbove(new BigDecimal("500.00"))
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(acceptingOrders)
                .build();
        return vendorProfileRepository.saveAndFlush(profile);
    }

    /** Creates an active top-level category with a unique slug. */
    public Category createCategory() {
        String name = "Category " + UUID.randomUUID();
        return categoryRepository.saveAndFlush(Category.builder()
                .name(name)
                .slug(name.toLowerCase().replace(' ', '-'))
                .displayOrder(0)
                .active(true)
                .build());
    }

    /** Creates a product with a unique slug. */
    public Product createProduct(VendorProfile vendor, Category category, String name, ProductStatus status) {
        String uniqueName = name + " " + UUID.randomUUID();
        return productRepository.saveAndFlush(Product.builder()
                .vendor(vendor)
                .category(category)
                .name(uniqueName)
                .slug(uniqueName.toLowerCase().replace(' ', '-'))
                .description("A lovely product")
                .basePrice(new BigDecimal("199.00"))
                .status(status)
                .build());
    }

    /** Creates the inventory row for a product with the given on-hand and reserved units. */
    public Inventory createInventory(Product product, int quantity, int reservedQuantity) {
        return inventoryRepository.saveAndFlush(Inventory.builder()
                .product(product)
                .quantity(quantity)
                .reservedQuantity(reservedQuantity)
                .lowStockThreshold(0)
                .build());
    }

    /** Resolves a seeded service location by pincode. */
    public ServiceLocation locationByPincode(String pincode) {
        return serviceLocationRepository.findByPincode(pincode)
                .orElseThrow(() -> new IllegalStateException("No seeded location for pincode " + pincode));
    }
}
