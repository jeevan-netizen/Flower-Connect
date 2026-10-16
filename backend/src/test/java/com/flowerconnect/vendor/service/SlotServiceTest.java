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

@ExtendWith(MockitoExtension.class)
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

    // Configuration values from application-test.yml
    private int minLeadTimeMinutes = 120; // 2 hours
    private int maxBookingHorizonDays = 7; // 1 week

    @InjectMocks
    private SlotService slotService;

    private VendorProfile testVendor;
    private ServiceLocation testServiceLocation;
    private VendorHours testVendorHours;
    private User testUser;

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
        DayOfWeek testDayOfWeek = LocalDate.now().getDayOfWeek();
        testVendorHours = VendorHours.builder()
                .id(1L)
                .vendorProfile(testVendor)
                .weekday(testDayOfWeek)
                .openTime(LocalTime.of(9, 0))  // 9:00 AM
                .closeTime(LocalTime.of(17, 0)) // 5:00 PM
                .closed(false)
                .build();
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
        LocalDate fixedDate = LocalDate.now();
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
                1L, 1L, LocalDate.now(), "test@example.com");
         
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
                1L, 1L, LocalDate.now(), "test@example.com");
        
        // Assert
        assertTrue(slots.isEmpty(), "Should have no slots when vendor is closed");
    }

    @Test
    void testVendorNotFound() {
        // Arrange
        when(vendorProfileRepository.findById(999L)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            slotService.getAvailableSlots(999L, 1L, LocalDate.now(), "test@example.com");
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
            slotService.getAvailableSlots(1L, 1L, LocalDate.now(), "test@example.com");
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
            slotService.getAvailableSlots(1L, 1L, LocalDate.now(), "test@example.com");
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
            slotService.getAvailableSlots(1L, 1L, LocalDate.now(), "test@example.com");
        });
    }

    @Test
    void testDateBeyondHorizon() {
        // Arrange
        // Set up fixed clock to return 10:00 AM today in the vendor's timezone
        LocalDate fixedDate = LocalDate.now();
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
        
        LocalDate futureDate = LocalDate.now().plusDays(8); // Beyond horizon of 7 days
        
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
            slotService.getAvailableSlots(1L, 1L, LocalDate.now(), "test@example.com");
        });
    }
}