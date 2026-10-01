package com.flowerconnect.repository;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VendorProfileRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private VendorProfileRepository vendorProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ServiceLocationRepository serviceLocationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldPersistProfileWithDeliverySettingsAndDefaults() {
        VendorProfile saved = createProfile(createVendorUser(), VendorProfile.Status.PENDING_APPROVAL);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(0, saved.getReviewCount());
        assertTrue(saved.isAcceptingOrders());
        assertNull(saved.getAvgRating());
        assertNull(saved.getCommissionRate());
        assertNull(saved.getFreeDeliveryAbove());
    }

    @Test
    void shouldPersistMoneyFieldsWithoutLosingScale() {
        VendorProfile profile = createProfile(createVendorUser(), VendorProfile.Status.APPROVED);
        profile.setMinOrderAmount(new BigDecimal("199.99"));
        profile.setBaseDeliveryFee(new BigDecimal("25.50"));
        profile.setPerKmFee(new BigDecimal("1.75"));
        profile.setFreeDeliveryAbove(new BigDecimal("500.00"));
        profile.setDeliveryRadiusKm(new BigDecimal("7.50"));
        profile.setCommissionRate(new BigDecimal("0.1250"));

        VendorProfile saved = vendorProfileRepository.saveAndFlush(profile);
        entityManagerClear();

        VendorProfile reloaded = vendorProfileRepository.findById(saved.getId()).orElseThrow();
        assertEquals(new BigDecimal("199.99"), reloaded.getMinOrderAmount());
        assertEquals(new BigDecimal("25.50"), reloaded.getBaseDeliveryFee());
        assertEquals(new BigDecimal("1.75"), reloaded.getPerKmFee());
        assertEquals(new BigDecimal("500.00"), reloaded.getFreeDeliveryAbove());
        assertEquals(new BigDecimal("7.50"), reloaded.getDeliveryRadiusKm());
        assertEquals(new BigDecimal("0.1250"), reloaded.getCommissionRate());
    }

    @Test
    void shouldEnforceOneProfilePerUser() {
        User user = createVendorUser();
        createProfile(user, VendorProfile.Status.PENDING_APPROVAL);

        VendorProfile duplicate = buildProfile(user, VendorProfile.Status.PENDING_APPROVAL);

        assertThrows(DataIntegrityViolationException.class, () -> vendorProfileRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldFindProfileByUserIdWithFetchedUser() {
        User user = createVendorUser();
        VendorProfile saved = createProfile(user, VendorProfile.Status.APPROVED);

        VendorProfile found = vendorProfileRepository.findByUserId(user.getId()).orElseThrow();
        entityManagerClear();

        assertEquals(saved.getId(), found.getId());
        assertEquals(user.getEmail(), found.getUser().getEmail());
    }

    @Test
    void shouldReturnEmptyWhenNoProfileForUser() {
        User user = createVendorUser();

        assertTrue(vendorProfileRepository.findByUserId(user.getId()).isEmpty());
        assertFalse(vendorProfileRepository.existsByUserId(user.getId()));
    }

    @Test
    void shouldCheckProfileExistenceByUserId() {
        User user = createVendorUser();
        createProfile(user, VendorProfile.Status.PENDING_APPROVAL);

        assertTrue(vendorProfileRepository.existsByUserId(user.getId()));
    }

    @Test
    void shouldFindProfileByIdWithUserAndLocation() {
        ServiceLocation location = serviceLocationRepository.findByPincode("560034").orElseThrow();
        VendorProfile saved = createProfile(createVendorUser(), VendorProfile.Status.APPROVED, location);

        VendorProfile found = vendorProfileRepository.findByIdWithDetails(saved.getId()).orElseThrow();
        entityManagerClear();

        assertNotNull(found.getUser());
        assertEquals(location.getId(), found.getServiceLocation().getId());
        assertEquals("Koramangala", found.getServiceLocation().getArea());
    }

    @Test
    void shouldFindAllProfilesByStatus() {
        VendorProfile approvedProfile = createProfile(createVendorUser(), VendorProfile.Status.APPROVED);
        VendorProfile suspendedProfile = createProfile(createVendorUser(), VendorProfile.Status.SUSPENDED);
        VendorProfile pendingProfile = createProfile(createVendorUser(), VendorProfile.Status.PENDING_APPROVAL);

        // The shared singleton MySQL container accumulates profiles across the whole
        // integration run, so membership is asserted on the rows this test created
        // rather than by counting every row of a given status.
        List<Long> approvedIds = idsOf(vendorProfileRepository.findAllByStatus(VendorProfile.Status.APPROVED));
        List<Long> pendingIds = idsOf(vendorProfileRepository.findAllByStatus(VendorProfile.Status.PENDING_APPROVAL));
        List<Long> rejectedIds = idsOf(vendorProfileRepository.findAllByStatus(VendorProfile.Status.REJECTED));

        assertTrue(approvedIds.contains(approvedProfile.getId()));
        assertFalse(approvedIds.contains(suspendedProfile.getId()));
        assertFalse(approvedIds.contains(pendingProfile.getId()));

        assertTrue(pendingIds.contains(pendingProfile.getId()));
        assertFalse(pendingIds.contains(approvedProfile.getId()));

        assertFalse(rejectedIds.contains(approvedProfile.getId()));
        assertFalse(rejectedIds.contains(suspendedProfile.getId()));
        assertFalse(rejectedIds.contains(pendingProfile.getId()));
    }

    private static List<Long> idsOf(List<VendorProfile> profiles) {
        return profiles.stream().map(VendorProfile::getId).toList();
    }

    @Test
    void shouldRejectProfileWithUnknownServiceLocation() {
        VendorProfile profile = buildProfile(createVendorUser(), VendorProfile.Status.PENDING_APPROVAL);
        profile.setServiceLocation(entityManager.getReference(ServiceLocation.class, 9_999_999L));

        assertThrows(DataIntegrityViolationException.class, () -> vendorProfileRepository.saveAndFlush(profile));
    }

    @Test
    void shouldCopyCoordinatesFromServiceLocationCentroid() {
        ServiceLocation location = serviceLocationRepository.findByPincode("560034").orElseThrow();
        VendorProfile profile = buildProfile(createVendorUser(), VendorProfile.Status.PENDING_APPROVAL);
        profile.setServiceLocation(location);
        profile.setLatitude(location.getLatitude());
        profile.setLongitude(location.getLongitude());

        VendorProfile saved = vendorProfileRepository.saveAndFlush(profile);

        assertEquals(location.getLatitude(), saved.getLatitude());
        assertEquals(location.getLongitude(), saved.getLongitude());
    }

    private User createVendorUser() {
        Role role = roleRepository.findByName("FLORIST").orElseThrow();
        User user = User.builder()
                .email("vendor-" + UUID.randomUUID() + "@test.com")
                .passwordHash("$2a$10$dummyhash")
                .fullName("Vendor Owner")
                .phone("+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        return userRepository.saveAndFlush(user);
    }

    private VendorProfile createProfile(User user, VendorProfile.Status status) {
        return createProfile(user, status, serviceLocationRepository.findByPincode("560034").orElseThrow());
    }

    private VendorProfile createProfile(User user, VendorProfile.Status status, ServiceLocation location) {
        VendorProfile profile = buildProfile(user, status);
        profile.setServiceLocation(location);
        profile.setLatitude(location.getLatitude());
        profile.setLongitude(location.getLongitude());
        return vendorProfileRepository.saveAndFlush(profile);
    }

    private VendorProfile buildProfile(User user, VendorProfile.Status status) {
        return VendorProfile.builder()
                .user(user)
                .businessName("Test Blossoms")
                .description("Fresh flowers delivered daily")
                .addressLine1("12 Test Street")
                .addressLine2("Ground floor")
                .serviceLocation(serviceLocationRepository.findByPincode("560034").orElseThrow())
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(status)
                .reviewCount(0)
                .minOrderAmount(new BigDecimal("0.00"))
                .baseDeliveryFee(new BigDecimal("0.00"))
                .perKmFee(new BigDecimal("0.00"))
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build();
    }

    private void entityManagerClear() {
        entityManager.clear();
    }
}
