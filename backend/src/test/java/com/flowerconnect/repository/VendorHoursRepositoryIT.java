package com.flowerconnect.repository;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.test.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VendorHoursRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private VendorHoursRepository vendorHoursRepository;

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
    void shouldPersistWeekWithOpeningHours() {
        VendorProfile profile = createProfile();
        for (DayOfWeek weekday : DayOfWeek.values()) {
            vendorHoursRepository.save(buildHours(profile, weekday, LocalTime.of(9, 0), LocalTime.of(21, 0), false));
        }
        vendorHoursRepository.flush();

        List<VendorHours> hours = vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(profile.getId());

        assertEquals(7, hours.size());
        assertEquals(DayOfWeek.MONDAY, hours.get(0).getWeekday());
        assertEquals(DayOfWeek.SUNDAY, hours.get(6).getWeekday());
        assertEquals(LocalTime.of(9, 0), hours.get(0).getOpenTime());
        assertEquals(LocalTime.of(21, 0), hours.get(0).getCloseTime());
        assertFalse(hours.get(0).isClosed());
        assertNotNull(hours.get(0).getCreatedAt());
    }

    @Test
    void shouldPersistClosedDayWithoutTimes() {
        VendorProfile profile = createProfile();
        VendorHours sunday = buildHours(profile, DayOfWeek.SUNDAY, null, null, true);

        VendorHours saved = vendorHoursRepository.saveAndFlush(sunday);

        assertTrue(saved.isClosed());
        assertNull(saved.getOpenTime());
        assertNull(saved.getCloseTime());
    }

    @Test
    void shouldEnforceOneRowPerProfileAndWeekday() {
        VendorProfile profile = createProfile();
        vendorHoursRepository.saveAndFlush(buildHours(profile, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), false));

        VendorHours duplicate = buildHours(profile, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(19, 0), false);

        assertThrows(DataIntegrityViolationException.class, () -> vendorHoursRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldFindHoursForSingleWeekday() {
        VendorProfile profile = createProfile();
        vendorHoursRepository.saveAndFlush(buildHours(profile, DayOfWeek.FRIDAY, LocalTime.of(8, 30), LocalTime.of(20, 0), false));

        VendorHours friday = vendorHoursRepository
                .findByVendorProfileIdAndWeekday(profile.getId(), DayOfWeek.FRIDAY)
                .orElseThrow();

        assertEquals(DayOfWeek.FRIDAY, friday.getWeekday());
        assertEquals(LocalTime.of(8, 30), friday.getOpenTime());
        assertTrue(vendorHoursRepository
                .findByVendorProfileIdAndWeekday(profile.getId(), DayOfWeek.SATURDAY)
                .isEmpty());
    }

    @Test
    void shouldReturnEmptyForProfileWithoutHours() {
        VendorProfile profile = createProfile();

        assertTrue(vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(profile.getId()).isEmpty());
    }

    @Test
    void shouldDeleteHoursWhenProfileIsDeleted() {
        VendorProfile profile = createProfile();
        vendorHoursRepository.saveAndFlush(buildHours(profile, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(21, 0), false));

        vendorProfileRepository.delete(profile);
        vendorProfileRepository.flush();

        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vendor_hours WHERE vendor_profile_id = ?", Long.class, profile.getId());
        assertNotNull(remaining);
        assertEquals(0L, remaining);
    }

    @Test
    void shouldRejectHoursForUnknownProfile() {
        VendorHours orphan = VendorHours.builder()
                .vendorProfile(entityManager.getReference(VendorProfile.class, 9_999_999L))
                .weekday(DayOfWeek.MONDAY)
                .closed(false)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> vendorHoursRepository.saveAndFlush(orphan));
    }

    private VendorHours buildHours(VendorProfile profile, DayOfWeek weekday,
                                   LocalTime open, LocalTime close, boolean closed) {
        return VendorHours.builder()
                .vendorProfile(profile)
                .weekday(weekday)
                .openTime(open)
                .closeTime(close)
                .closed(closed)
                .build();
    }

    private VendorProfile createProfile() {
        Role role = roleRepository.findByName("FLORIST").orElseThrow();
        User user = userRepository.saveAndFlush(User.builder()
                .email("vendor-" + UUID.randomUUID() + "@test.com")
                .passwordHash("$2a$10$dummyhash")
                .fullName("Vendor Owner")
                .phone("+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
        ServiceLocation location = serviceLocationRepository.findByPincode("560034").orElseThrow();

        return vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(user)
                .businessName("Hours Test Shop")
                .addressLine1("1 Test Road")
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(VendorProfile.Status.PENDING_APPROVAL)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());
    }
}
