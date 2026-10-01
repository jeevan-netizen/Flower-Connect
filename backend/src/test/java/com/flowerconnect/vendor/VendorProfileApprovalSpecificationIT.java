package com.flowerconnect.vendor;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.vendor.specification.VendorProfileSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The discovery side of approval gating (plan task 2.7): only {@code APPROVED}
 * profiles are selectable, so a pending, rejected or suspended vendor cannot
 * appear in any customer-facing result set.
 *
 * <p>The MySQL container is shared by the whole integration run, so assertions
 * scope on the business names this test created rather than on the whole table.
 */
@SpringBootTest
class VendorProfileApprovalSpecificationIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String KORAMANGALA_PINCODE = "560034";

    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Fixture approved;
    private Fixture pending;
    private Fixture rejected;
    private Fixture suspended;

    @BeforeEach
    void setUp() {
        approved = createProfile("APPROVED", VendorProfile.Status.APPROVED);
        pending = createProfile("PENDING", VendorProfile.Status.PENDING_APPROVAL);
        rejected = createProfile("REJECTED", VendorProfile.Status.REJECTED);
        suspended = createProfile("SUSPENDED", VendorProfile.Status.SUSPENDED);
    }

    @Test
    void theApprovedSpecificationReturnsOnlyApprovedVendors() {
        List<String> names = approvedBusinessNames();

        assertTrue(names.contains(approved.name), "the approved vendor must be discoverable");
        assertFalse(names.contains(pending.name), "a pending vendor must not be discoverable");
        assertFalse(names.contains(rejected.name), "a rejected vendor must not be discoverable");
        assertFalse(names.contains(suspended.name), "a suspended vendor must not be discoverable");
    }

    @Test
    void hidingASuspendedVendorTakesEffectOnTheNextQuery() {
        assertFalse(approvedBusinessNames().contains(suspended.name));

        setStatus(suspended, VendorProfile.Status.APPROVED);
        assertTrue(approvedBusinessNames().contains(suspended.name),
                "reinstating a vendor must make it discoverable again");

        setStatus(suspended, VendorProfile.Status.SUSPENDED);
        assertFalse(approvedBusinessNames().contains(suspended.name),
                "suspending a vendor must hide it from the next query");
    }

    @Test
    void theSpecificationComposesWithOtherPredicates() {
        List<String> names = vendorProfileRepository
                .findAll(VendorProfileSpecifications.approved()
                        .and((root, query, builder) -> builder.isNull(root.get("logoUrl"))))
                .stream()
                .map(VendorProfile::getBusinessName)
                .toList();

        assertTrue(names.contains(approved.name),
                "an approved vendor with no logo must survive the composed query");
        assertFalse(names.contains(pending.name));
    }

    @Test
    void findByUserEmailResolvesTheProfileOfTheAuthenticatedSubject() {
        User owner = userRepository.findByEmail(approved.email).orElseThrow();

        assertEquals(approved.name, vendorProfileRepository.findByUserEmail(owner.getEmail())
                .orElseThrow().getBusinessName());
        assertTrue(vendorProfileRepository
                .findByUserEmail("nobody-" + UUID.randomUUID() + "@test.com").isEmpty());
    }

    // ------------------------------------------------------------------- helpers

    private List<String> approvedBusinessNames() {
        return vendorProfileRepository.findAll(VendorProfileSpecifications.approved()).stream()
                .map(VendorProfile::getBusinessName)
                .toList();
    }

    private void setStatus(Fixture fixture, VendorProfile.Status status) {
        VendorProfile profile = vendorProfileRepository.findById(fixture.profileId).orElseThrow();
        profile.setStatus(status);
        vendorProfileRepository.saveAndFlush(profile);
    }

    private Fixture createProfile(String tag, VendorProfile.Status status) {
        String name = "Spec " + tag + " " + UUID.randomUUID();
        String email = "spec-" + tag.toLowerCase() + "-" + UUID.randomUUID() + "@test.com";

        Role role = roleRepository.findByName("FLORIST").orElseThrow();
        ServiceLocation location = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE)
                .orElseThrow();

        User user = userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Spec Owner " + tag)
                .phone("+91" + String.format("%010d",
                        Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 10_000_000_000L)))
                .role(role)
                .status(User.Status.ACTIVE)
                .build());

        VendorProfile saved = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(user)
                .businessName(name)
                .addressLine1("12 Spec Street")
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(status)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());

        return new Fixture(name, email, saved.getId());
    }

    private record Fixture(String name, String email, Long profileId) {
    }
}
