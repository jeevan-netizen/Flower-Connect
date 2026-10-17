package com.flowerconnect.vendor.service;

import com.flowerconnect.customer.service.AddressService;
import com.flowerconnect.customer.dto.AddressResponse;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.vendor.dto.SlotResponse;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
class SlotServiceTest {

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @Mock
    private ServiceLocationRepository serviceLocationRepository;

    @Mock
    private VendorHoursRepository vendorHoursRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressService addressService;

    @Mock
    private Clock clock;

    // Configuration values from application-test.yml
    private int minLeadTimeMinutes = 120; // 2 hours
    private int maxBookingHorizonDays = 7; // 1 week

    @InjectMocks
    private SlotService slotService;

    private VendorProfile testVendor;
    private ServiceLocation testServiceLocation;
    private VendorHours testVendorHours;
    private User testUser;
    private static final LocalDate TEST_TODAY = LocalDate.of(2026, 10, 21);

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();
        
        // Set up test service location
        testServiceLocation = ServiceLocation.builder()
                .id(1L)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .timezone("Asia/Kolkata")
                .build();
        
        // Set up test vendor - approved and accepting orders
        testVendor = VendorProfile.builder()
                .id(1L)
                .businessName("Test Vendor")
                .addressLine1("123 Test Street")
                .serviceLocation(testServiceLocation)
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .status(VendorProfile.Status.APPROVED)
                .acceptingOrders(true)
                .prepTimeMinutes(0) // No prep time to simplify calculations
                .slotDurationMinutes(60) // 1 hour slots
                .maxOrdersPerSlot(2)
                .build();
        
        // Set up vendor hours for today - morning
        // Open at 9:00, close at 17:00
        DayOfWeek testDayOfWeek = TEST_TODAY.getDayOfWeek();
        testVendorHours = VendorHours.builder()
                .id(1L)
                .vendorProfile(testVendor)
                .weekday(testDayOfWeek)
                .openTime(LocalTime.of(9, 0))  // 9:00 AM
                .closeTime(LocalTime.of(17, 0)) // 5:00 PM
                .closed(false)
                .build();

        when(clock.instant()).thenReturn(Instant.parse("2026-10-21T04:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Kolkata"));
        setPrivateField(slotService, "minLeadTimeMinutes", 120);
        setPrivateField(slotService, "maxBookingHorizonDays", 7);
    }

    @Test
    void testRegularHoursReturnsSlotsWhenOpenAndOutsideLeadTime() {
        // Arrange - set up vendor to open late today so slots are outside lead time
        VendorHours lateHours = VendorHours.builder()
                .id(1L)
                .vendorProfile(testVendor)
                .weekday(testVendorHours.getWeekday())
                .openTime(LocalTime.of(14, 0))  // 2:00 PM
                .closeTime(LocalTime.of(18, 0)) // 6:00 PM
                .closed(false)
                .build();

        // Set up fixed clock to return 10:00 AM today in the vendor's timezone
        LocalDate fixedDate = TEST_TODAY;
        LocalTime fixedTime = LocalTime.of(10, 0); // 10:00 AM
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        LocalDateTime fixedDateTime = LocalDateTime.of(fixedDate, fixedTime);
        ZonedDateTime fixedZonedDateTime = fixedDateTime.atZone(zoneId);
        Clock fixedClock = Clock.fixed(fixedZonedDateTime.toInstant(), zoneId);
        
        // Create a new slot service with the fixed clock
        SlotService slotServiceWithFixedClock = new SlotService(
                vendorProfileRepository, serviceLocationRepository, vendorHoursRepository,
                orderRepository, userRepository, addressService, fixedClock);
        
        // Set the configuration values using reflection (normally injected via @Value)
        setPrivateField(slotServiceWithFixedClock, "minLeadTimeMinutes", 120); // 2 hours
        setPrivateField(slotServiceWithFixedClock, "maxBookingHorizonDays", 7); // 1 week

        // Mock repository calls
        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(testVendor));
        when(vendorHoursRepository.findByVendorProfileIdAndWeekday(1L, lateHours.getWeekday()))
                .thenReturn(Optional.of(lateHours));
        when(addressService.getById("test@example.com", 1L))
                .thenReturn(AddressResponse.builder().build());
        when(userRepository.findByEmailWithRole("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(orderRepository.countBookedOrdersForSlot(anyLong(), any()))
                .thenReturn(0L);
        
        // Act
        List<SlotResponse> slots = slotServiceWithFixedClock.getAvailableSlots(
                1L, 1L, TEST_TODAY, "test@example.com");
         
        // Assert
        assertFalse(slots.isEmpty(), "Should have slots available when vendor is open and outside lead time");
         
        // First slot should be for today
        SlotResponse firstSlot = slots.get(0);
        assertEquals(lateHours.getWeekday(), firstSlot.getWeekday());
        // Should be available (outside lead time and no booked orders)
        assertTrue(firstSlot.isAvailable(), "Slot should be available when outside lead time and no booked orders");
    }
    
    /**
     * Helper method to set a private field using reflection.
     */
    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Error setting private field: " + fieldName, e);
        }
    }

    @Test
    void testClosedDayReturnsNoSlots() {
        // Arrange
        VendorHours closedHours = VendorHours.builder()
                .id(1L)
                .vendorProfile(testVendor)
                .weekday(testVendorHours.getWeekday())
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(17, 0))
                .closed(true) // Vendor is closed
                .build();

        // Mock repository calls
        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(testVendor));
        when(vendorHoursRepository.findByVendorProfileIdAndWeekday(1L, closedHours.getWeekday()))
                .thenReturn(Optional.of(closedHours));
        when(addressService.getById("test@example.com", 1L))
                .thenReturn(AddressResponse.builder().build());
        when(userRepository.findByEmailWithRole("test@example.com"))
                .thenReturn(Optional.of(testUser));
        
        // Act
        List<SlotResponse> slots = slotService.getAvailableSlots(
                1L, 1L, TEST_TODAY, "test@example.com");
        
        // Assert
        assertTrue(slots.isEmpty(), "Should have no slots when vendor is closed");
    }

    @Test
    void testVendorNotFound() {
        // Arrange
        when(vendorProfileRepository.findById(999L)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            slotService.getAvailableSlots(999L, 1L, TEST_TODAY, "test@example.com");
        });
    }

    @Test
    void testVendorNotApproved() {
        // Arrange
        VendorProfile notApprovedVendor = VendorProfile.builder()
                .id(1L)
                .businessName("Test Vendor")
                .addressLine1("123 Test Street")
                .serviceLocation(testServiceLocation)
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .status(VendorProfile.Status.PENDING_APPROVAL) // NOT approved
                .acceptingOrders(true)
                .prepTimeMinutes(0)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(2)
                .build();

        // Mock repository calls
        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(notApprovedVendor));
        
        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            slotService.getAvailableSlots(1L, 1L, TEST_TODAY, "test@example.com");
        });
    }

    @Test
    void testVendorNotAcceptingOrders() {
        // Arrange
        VendorProfile notAcceptingVendor = VendorProfile.builder()
                .id(1L)
                .businessName("Test Vendor")
                .addressLine1("123 Test Street")
                .serviceLocation(testServiceLocation)
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .status(VendorProfile.Status.APPROVED) // Approved
                .acceptingOrders(false) // But NOT accepting orders
                .prepTimeMinutes(0)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(2)
                .build();

        // Mock repository calls
        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(notAcceptingVendor));
        
        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            slotService.getAvailableSlots(1L, 1L, TEST_TODAY, "test@example.com");
        });
    }

    @Test
    void testAddressNotFound() {
        // Arrange
        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(testVendor));
        when(addressService.getById("test@example.com", 1L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Address not found"));
        when(userRepository.findByEmailWithRole("test@example.com"))
                .thenReturn(Optional.of(testUser));
        
        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            slotService.getAvailableSlots(1L, 1L, TEST_TODAY, "test@example.com");
        });
    }

    @Test
    void testDateBeyondHorizon() {
        // Arrange
        // Set up fixed clock to return 10:00 AM today in the vendor's timezone
        LocalDate fixedDate = TEST_TODAY;
        LocalTime fixedTime = LocalTime.of(10, 0); // 10:00 AM
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        LocalDateTime fixedDateTime = LocalDateTime.of(fixedDate, fixedTime);
        ZonedDateTime fixedZonedDateTime = fixedDateTime.atZone(zoneId);
        Clock fixedClock = Clock.fixed(fixedZonedDateTime.toInstant(), zoneId);
        
        // Create a new slot service with the fixed clock
        SlotService slotServiceWithFixedClock = new SlotService(
                vendorProfileRepository, serviceLocationRepository, vendorHoursRepository,
                orderRepository, userRepository, addressService, fixedClock);
        
        // Set the configuration values using reflection (normally injected via @Value)
        setPrivateField(slotServiceWithFixedClock, "minLeadTimeMinutes", 120); // 2 hours
        setPrivateField(slotServiceWithFixedClock, "maxBookingHorizonDays", 7); // 1 week
        
        LocalDate futureDate = TEST_TODAY.plusDays(8); // Beyond horizon of 7 days
        
        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            slotServiceWithFixedClock.getAvailableSlots(1L, 1L, futureDate, "test@example.com");
        });
    }

    @Test
    void testInvalidTimezone() {
        // Arrange
        ServiceLocation invalidTzLocation = ServiceLocation.builder()
                .id(1L)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .timezone("Invalid/Timezone") // INVALID timezone
                .build();
        
        // Set invalid timezone on vendor's service location
        testVendor.setServiceLocation(invalidTzLocation);

        // Mock repository calls
        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(testVendor));
        
        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            slotService.getAvailableSlots(1L, 1L, TEST_TODAY, "test@example.com");
        });
    }

    @Test
    void testBeforeOpeningGridStartsAtOpeningAndLeadTimeInvalidSlotsUnavailable() {
        // Vendor opens 09:00, closes 18:00, 60m duration
        // Current time: 08:30 IST on Wednesday (before opening)
        // Lead time: 120m -> leadTimeLimit is 10:30 IST
        LocalDate testDate = LocalDate.of(2026, 10, 21); // Wednesday
        LocalTime testTime = LocalTime.of(8, 30);
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        Clock fixedClock = Clock.fixed(LocalDateTime.of(testDate, testTime).atZone(zoneId).toInstant(), zoneId);

        SlotService service = new SlotService(
                vendorProfileRepository, serviceLocationRepository, vendorHoursRepository,
                orderRepository, userRepository, addressService, fixedClock);
        setPrivateField(service, "minLeadTimeMinutes", 120);
        setPrivateField(service, "maxBookingHorizonDays", 7);

        VendorHours hours = VendorHours.builder()
                .id(1L)
                .vendorProfile(testVendor)
                .weekday(DayOfWeek.WEDNESDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .closed(false)
                .build();

        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(testVendor));
        when(vendorHoursRepository.findByVendorProfileIdAndWeekday(1L, DayOfWeek.WEDNESDAY))
                .thenReturn(Optional.of(hours));
        when(addressService.getById("test@example.com", 1L)).thenReturn(AddressResponse.builder().build());
        when(userRepository.findByEmailWithRole("test@example.com")).thenReturn(Optional.of(testUser));
        when(orderRepository.countBookedOrdersForSlot(anyLong(), any())).thenReturn(0L);

        List<SlotResponse> slots = service.getAvailableSlots(1L, 1L, testDate, "test@example.com");

        // 09:00 to 18:00 in 60m steps -> 9 slots
        assertEquals(9, slots.size());
        // First slot starts at opening time: 09:00 IST = 03:30 UTC
        assertEquals(LocalDateTime.of(2026, 10, 21, 3, 30), slots.get(0).getSlotStart());
        // Lead time limit is 10:30 IST:
        // 09:00 IST (< 10:30) -> available: false
        assertFalse(slots.get(0).isAvailable(), "Slot 09:00-10:00 should be unavailable due to lead time");
        // 10:00 IST (< 10:30) -> available: false
        assertFalse(slots.get(1).isAvailable(), "Slot 10:00-11:00 should be unavailable due to lead time");
        // 11:00 IST (>= 10:30) -> available: true
        assertTrue(slots.get(2).isAvailable(), "Slot 11:00-12:00 should be available");
    }

    @Test
    void testAfterClosingReturnsEmptyAndNoCrossDayRecursion() {
        // Vendor opens 09:00, closes 18:00
        // Current time: 18:30 IST on Wednesday (after closing)
        LocalDate testDate = LocalDate.of(2026, 10, 21); // Wednesday
        LocalTime testTime = LocalTime.of(18, 30);
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        Clock fixedClock = Clock.fixed(LocalDateTime.of(testDate, testTime).atZone(zoneId).toInstant(), zoneId);

        SlotService service = new SlotService(
                vendorProfileRepository, serviceLocationRepository, vendorHoursRepository,
                orderRepository, userRepository, addressService, fixedClock);
        setPrivateField(service, "minLeadTimeMinutes", 120);
        setPrivateField(service, "maxBookingHorizonDays", 7);

        VendorHours hours = VendorHours.builder()
                .id(1L)
                .vendorProfile(testVendor)
                .weekday(DayOfWeek.WEDNESDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .closed(false)
                .build();

        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(testVendor));
        when(vendorHoursRepository.findByVendorProfileIdAndWeekday(1L, DayOfWeek.WEDNESDAY))
                .thenReturn(Optional.of(hours));
        when(addressService.getById("test@example.com", 1L)).thenReturn(AddressResponse.builder().build());
        when(userRepository.findByEmailWithRole("test@example.com")).thenReturn(Optional.of(testUser));

        List<SlotResponse> slots = service.getAvailableSlots(1L, 1L, testDate, "test@example.com");

        assertTrue(slots.isEmpty(), "Should return empty list after closing on the requested day without cross-day recursion");
    }

    @Test
    void testFutureDateGeneratesFullOperatingGridNotClampedToClock() {
        // Current time: 14:00 IST on Wednesday
        // Requested date: Thursday (future date)
        LocalDate today = LocalDate.of(2026, 10, 21);
        LocalDate tomorrow = today.plusDays(1);
        LocalTime testTime = LocalTime.of(14, 0);
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        Clock fixedClock = Clock.fixed(LocalDateTime.of(today, testTime).atZone(zoneId).toInstant(), zoneId);

        SlotService service = new SlotService(
                vendorProfileRepository, serviceLocationRepository, vendorHoursRepository,
                orderRepository, userRepository, addressService, fixedClock);
        setPrivateField(service, "minLeadTimeMinutes", 120);
        setPrivateField(service, "maxBookingHorizonDays", 7);

        VendorHours hours = VendorHours.builder()
                .id(1L)
                .vendorProfile(testVendor)
                .weekday(DayOfWeek.THURSDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .closed(false)
                .build();

        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(testVendor));
        when(vendorHoursRepository.findByVendorProfileIdAndWeekday(1L, DayOfWeek.THURSDAY))
                .thenReturn(Optional.of(hours));
        when(addressService.getById("test@example.com", 1L)).thenReturn(AddressResponse.builder().build());
        when(userRepository.findByEmailWithRole("test@example.com")).thenReturn(Optional.of(testUser));
        when(orderRepository.countBookedOrdersForSlot(anyLong(), any())).thenReturn(0L);

        List<SlotResponse> slots = service.getAvailableSlots(1L, 1L, tomorrow, "test@example.com");

        // Full grid 09:00 to 18:00 (9 slots) not clamped to 14:00!
        assertEquals(9, slots.size());
        assertEquals(LocalDateTime.of(2026, 10, 22, 3, 30), slots.get(0).getSlotStart()); // 09:00 IST = 03:30 UTC
        assertTrue(slots.stream().allMatch(SlotResponse::isAvailable), "All future slots outside lead time should be available");
    }

    @Test
    void testCapacityExhaustedSlotsAreMarkedUnavailable() {
        // Current time: 09:00 IST on Wednesday
        LocalDate testDate = LocalDate.of(2026, 10, 21);
        LocalTime testTime = LocalTime.of(9, 0);
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        Clock fixedClock = Clock.fixed(LocalDateTime.of(testDate, testTime).atZone(zoneId).toInstant(), zoneId);

        SlotService service = new SlotService(
                vendorProfileRepository, serviceLocationRepository, vendorHoursRepository,
                orderRepository, userRepository, addressService, fixedClock);
        setPrivateField(service, "minLeadTimeMinutes", 120);
        setPrivateField(service, "maxBookingHorizonDays", 7);

        VendorHours hours = VendorHours.builder()
                .id(1L)
                .vendorProfile(testVendor)
                .weekday(DayOfWeek.WEDNESDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .closed(false)
                .build();

        // maxOrdersPerSlot = 2
        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(testVendor));
        when(vendorHoursRepository.findByVendorProfileIdAndWeekday(1L, DayOfWeek.WEDNESDAY))
                .thenReturn(Optional.of(hours));
        when(addressService.getById("test@example.com", 1L)).thenReturn(AddressResponse.builder().build());
        when(userRepository.findByEmailWithRole("test@example.com")).thenReturn(Optional.of(testUser));

        // Slot 12:00 IST = 06:30 UTC has 2 booked orders (full!)
        LocalDateTime slot12Utc = LocalDateTime.of(2026, 10, 21, 6, 30);
        // Slot 13:00 IST = 07:30 UTC has 1 booked order (available!)
        LocalDateTime slot13Utc = LocalDateTime.of(2026, 10, 21, 7, 30);

        // Default zero booked orders, except slot12 is full and slot13 has 1
        when(orderRepository.countBookedOrdersForSlot(anyLong(), any())).thenReturn(0L);
        when(orderRepository.countBookedOrdersForSlot(eq(1L), eq(slot12Utc))).thenReturn(2L);
        when(orderRepository.countBookedOrdersForSlot(eq(1L), eq(slot13Utc))).thenReturn(1L);

        List<SlotResponse> slots = service.getAvailableSlots(1L, 1L, testDate, "test@example.com");

        SlotResponse slot12 = slots.stream().filter(s -> s.getSlotStart().equals(slot12Utc)).findFirst().orElseThrow();
        assertFalse(slot12.isAvailable(), "Capacity-exhausted slot should be marked unavailable");

        SlotResponse slot13 = slots.stream().filter(s -> s.getSlotStart().equals(slot13Utc)).findFirst().orElseThrow();
        assertTrue(slot13.isAvailable(), "Slot with remaining capacity should be marked available");
    }

    @Test
    void testOperatingHoursBoundaryWhenNotEvenlyDivisibleBySlotDuration() {
        // Vendor opens 09:00, closes 17:30 (510 minutes). Slot duration = 60 minutes.
        // 510 / 60 = 8 full slots (09:00 to 17:00). Slot 17:00-18:00 exceeds 17:30 and must NOT be emitted.
        LocalDate testDate = LocalDate.of(2026, 10, 21);
        LocalTime testTime = LocalTime.of(8, 0); // 08:00 IST (02:30 UTC)
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        Clock fixedClock = Clock.fixed(LocalDateTime.of(testDate, testTime).atZone(zoneId).toInstant(), zoneId);

        SlotService service = new SlotService(
                vendorProfileRepository, serviceLocationRepository, vendorHoursRepository,
                orderRepository, userRepository, addressService, fixedClock);
        setPrivateField(service, "minLeadTimeMinutes", 120);
        setPrivateField(service, "maxBookingHorizonDays", 7);

        VendorHours hours = VendorHours.builder()
                .id(1L)
                .vendorProfile(testVendor)
                .weekday(DayOfWeek.WEDNESDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(17, 30))
                .closed(false)
                .build();

        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(testVendor));
        when(vendorHoursRepository.findByVendorProfileIdAndWeekday(1L, DayOfWeek.WEDNESDAY))
                .thenReturn(Optional.of(hours));
        when(addressService.getById("test@example.com", 1L)).thenReturn(AddressResponse.builder().build());
        when(userRepository.findByEmailWithRole("test@example.com")).thenReturn(Optional.of(testUser));
        when(orderRepository.countBookedOrdersForSlot(anyLong(), any())).thenReturn(0L);

        List<SlotResponse> slots = service.getAvailableSlots(1L, 1L, testDate, "test@example.com");

        // Exactly 8 slots (09:00-10:00 through 16:00-17:00)
        assertEquals(8, slots.size(), "Should produce exactly 8 complete slots when closing time does not align with duration");
        assertEquals(LocalDateTime.of(2026, 10, 21, 3, 30), slots.get(0).getSlotStart()); // 09:00 IST = 03:30 UTC
        assertEquals(LocalDateTime.of(2026, 10, 21, 10, 30), slots.get(7).getSlotStart()); // 16:00 IST = 10:30 UTC
        assertEquals(LocalDateTime.of(2026, 10, 21, 11, 30), slots.get(7).getSlotEnd());   // 17:00 IST = 11:30 UTC

        // Ensure no slot exceeds 17:30 IST = 12:00 UTC
        LocalDateTime closeTimeUtc = LocalDateTime.of(2026, 10, 21, 12, 0);
        assertTrue(slots.stream().noneMatch(s -> s.getSlotEnd().isAfter(closeTimeUtc)),
                "No slot should end after the vendor closing time of 17:30 IST");
    }

    @Test
    void testExact120MinuteLeadTimeBoundaryDiscovery() {
        // Vendor opens 09:00, closes 17:00, with 30-minute slot duration
        VendorProfile vendor30m = VendorProfile.builder()
                .id(1L)
                .businessName("Test Vendor 30m")
                .serviceLocation(testServiceLocation)
                .status(VendorProfile.Status.APPROVED)
                .acceptingOrders(true)
                .slotDurationMinutes(30)
                .maxOrdersPerSlot(5)
                .build();

        // Clock fixed at 08:30:00 IST on Wednesday 2026-10-21 (03:00:00 UTC)
        // 120-minute lead time threshold is exactly 10:30:00 IST (05:00:00 UTC)
        LocalDate testDate = LocalDate.of(2026, 10, 21);
        LocalTime testTime = LocalTime.of(8, 30);
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        Clock fixedClock = Clock.fixed(LocalDateTime.of(testDate, testTime).atZone(zoneId).toInstant(), zoneId);

        SlotService service = new SlotService(
                vendorProfileRepository, serviceLocationRepository, vendorHoursRepository,
                orderRepository, userRepository, addressService, fixedClock);
        setPrivateField(service, "minLeadTimeMinutes", 120);
        setPrivateField(service, "maxBookingHorizonDays", 7);

        VendorHours hours = VendorHours.builder()
                .id(1L)
                .vendorProfile(vendor30m)
                .weekday(DayOfWeek.WEDNESDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(17, 0))
                .closed(false)
                .build();

        when(vendorProfileRepository.findById(1L)).thenReturn(Optional.of(vendor30m));
        when(vendorHoursRepository.findByVendorProfileIdAndWeekday(1L, DayOfWeek.WEDNESDAY))
                .thenReturn(Optional.of(hours));
        when(addressService.getById("test@example.com", 1L)).thenReturn(AddressResponse.builder().build());
        when(userRepository.findByEmailWithRole("test@example.com")).thenReturn(Optional.of(testUser));
        when(orderRepository.countBookedOrdersForSlot(anyLong(), any())).thenReturn(0L);

        List<SlotResponse> slots = service.getAvailableSlots(1L, 1L, testDate, "test@example.com");

        // 10:00 IST = 04:30 UTC: 90m ahead (< 120m lead time) -> unavailable
        LocalDateTime slot1000Utc = LocalDateTime.of(2026, 10, 21, 4, 30);
        SlotResponse slot1000 = slots.stream().filter(s -> s.getSlotStart().equals(slot1000Utc)).findFirst().orElseThrow();
        assertFalse(slot1000.isAvailable(), "Slot starting at 10:00 IST (< 120m lead time) must be unavailable");

        // 10:30 IST = 05:00 UTC: exactly 120m ahead -> available
        LocalDateTime slot1030Utc = LocalDateTime.of(2026, 10, 21, 5, 0);
        SlotResponse slot1030 = slots.stream().filter(s -> s.getSlotStart().equals(slot1030Utc)).findFirst().orElseThrow();
        assertTrue(slot1030.isAvailable(), "Slot starting at exactly 10:30 IST (== 120m lead time) must be available");

        // 11:00 IST = 05:30 UTC: 150m ahead (> 120m lead time) -> available
        LocalDateTime slot1100Utc = LocalDateTime.of(2026, 10, 21, 5, 30);
        SlotResponse slot1100 = slots.stream().filter(s -> s.getSlotStart().equals(slot1100Utc)).findFirst().orElseThrow();
        assertTrue(slot1100.isAvailable(), "Slot starting at 11:00 IST (> 120m lead time) must be available");
    }
}